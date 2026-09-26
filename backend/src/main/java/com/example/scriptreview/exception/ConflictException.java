package com.example.scriptreview.exception;

/**
 * 资源状态与请求冲突（如并发审批同一回避申请、重复创建替补分配），映射 HTTP 409。
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
