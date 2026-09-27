package com.example.scriptreview.controller;

import com.example.scriptreview.dto.response.IdentityMapResponse;
import com.example.scriptreview.entity.DesensitizationAudit;
import com.example.scriptreview.entity.User;
import com.example.scriptreview.repository.DesensitizationAuditRepository;
import com.example.scriptreview.service.IdentityReviewService;
import com.example.scriptreview.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;

/**
 * 脱敏查看审计追溯：谁、在何时、查看过哪个脚本的原稿或代号映射、核对了哪个身份。
 * 仅管理员可访问。
 */
@RestController
@RequestMapping("/api/desensitization/audits")
@RequiredArgsConstructor
public class DesensitizationAuditController {

    private final DesensitizationAuditRepository auditRepository;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @GetMapping
    public ResponseEntity<Page<IdentityMapResponse.AuditView>> list(
            @RequestParam(required = false) Long scriptId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        SecurityUtils.requireRole("ADMIN");
        int pageIndex = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), 200);
        Page<DesensitizationAudit> result = scriptId == null
                ? auditRepository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(pageIndex, pageSize))
                : auditRepository.findByScriptIdOrderByCreatedAtDescIdDesc(
                        scriptId, PageRequest.of(pageIndex, pageSize));
        return ResponseEntity.ok(result.map(this::toView));
    }

    private IdentityMapResponse.AuditView toView(DesensitizationAudit a) {
        return IdentityMapResponse.AuditView.builder()
                .id(a.getId())
                .action(a.getAction())
                .actionLabel(IdentityReviewService.actionLabel(a.getAction()))
                .viewerName(a.getViewerName())
                .ipAddress(a.getIpAddress())
                .detail(a.getDetail())
                .createdAt(a.getCreatedAt() != null ? a.getCreatedAt().format(FORMATTER) : null)
                .build();
    }
}
