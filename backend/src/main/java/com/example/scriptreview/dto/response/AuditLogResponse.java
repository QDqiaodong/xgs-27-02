package com.example.scriptreview.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {

    private Long id;
    private String action;
    private String module;
    private Long targetId;
    private Long userId;
    private String userName;
    private String ipAddress;
    private String requestParams;
    private String responseResult;
    private Boolean success;
    private String errorMessage;
    private String createdAt;
}