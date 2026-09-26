package com.example.scriptreview.config;

import com.example.scriptreview.entity.AuditLog;
import com.example.scriptreview.repository.AuditLogRepository;
import com.example.scriptreview.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Around("execution(* com.example.scriptreview.controller.*.*(..)) && !execution(* com.example.scriptreview.controller.HealthController.*(..))")
    public Object logAudit(ProceedingJoinPoint joinPoint) throws Throwable {
        AuditLog auditLog = new AuditLog();
        
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        
        auditLog.setModule(className.replace("Controller", ""));
        auditLog.setAction(methodName);
        
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        auditLog.setIpAddress(getClientIp(request));
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User) {
            User user = (User) auth.getPrincipal();
            auditLog.setUserId(user.getId());
            auditLog.setUserName(user.getName());
        }
        
        try {
            Object[] args = joinPoint.getArgs();
            if (args != null && args.length > 0) {
                try {
                    auditLog.setRequestParams(objectMapper.writeValueAsString(args));
                } catch (Exception e) {
                    auditLog.setRequestParams("无法序列化");
                }
            }
            
            Object result = joinPoint.proceed();
            auditLog.setSuccess(true);
            
            if (result != null) {
                try {
                    String resultJson = objectMapper.writeValueAsString(result);
                    auditLog.setResponseResult(resultJson.length() > 500 ? resultJson.substring(0, 500) : resultJson);
                } catch (Exception e) {
                    auditLog.setResponseResult("无法序列化");
                }
            }
            
            return result;
        } catch (Throwable e) {
            auditLog.setSuccess(false);
            auditLog.setErrorMessage(e.getMessage());
            throw e;
        } finally {
            auditLog.setCreatedAt(LocalDateTime.now());
            auditLogRepository.save(auditLog);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}