package com.example.scriptreview.controller;

import com.example.scriptreview.dto.request.AddClueRequest;
import com.example.scriptreview.dto.request.ConfirmClueRequest;
import com.example.scriptreview.dto.response.MaskingMapResponse;
import com.example.scriptreview.service.MaskingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 脚本脱敏映射管理（仅管理员，见 SecurityConfig）：
 * 查看“原稿 / 线索 / 脱敏结果”映射、核对模糊线索、手工补充线索。
 * 每次查看映射与每次核对都会写入审计记录。
 */
@RestController
@RequestMapping("/api/scripts/{scriptId}/masking")
@RequiredArgsConstructor
public class MaskingController {

    private final MaskingService maskingService;

    @GetMapping
    public ResponseEntity<MaskingMapResponse> getMapping(@PathVariable Long scriptId) {
        return ResponseEntity.ok(maskingService.getMapping(scriptId, true));
    }

    @PostMapping("/clues/{clueId}/confirm")
    public ResponseEntity<MaskingMapResponse> confirmClue(
            @PathVariable Long scriptId,
            @PathVariable Long clueId,
            @Valid @RequestBody ConfirmClueRequest request) {
        return ResponseEntity.ok(maskingService.confirmClue(
                scriptId, clueId, request.getDecision(), request.getIdentityId()));
    }

    @PostMapping("/clues")
    public ResponseEntity<MaskingMapResponse> addClue(
            @PathVariable Long scriptId,
            @Valid @RequestBody AddClueRequest request) {
        return ResponseEntity.ok(maskingService.addManualClue(
                scriptId, request.getClueType(), request.getFieldOrigin(),
                request.getOriginalText(), request.getIdentityId()));
    }
}
