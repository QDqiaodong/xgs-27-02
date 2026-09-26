package com.example.scriptreview.controller;

import com.example.scriptreview.dto.request.CreateScriptRequest;
import com.example.scriptreview.dto.response.ScriptResponse;
import com.example.scriptreview.service.ScriptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/scripts")
@RequiredArgsConstructor
public class ScriptController {

    private final ScriptService scriptService;

    @PostMapping
    public ResponseEntity<ScriptResponse> createScript(@Valid @RequestBody CreateScriptRequest request) {
        ScriptResponse response = scriptService.createScript(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 列表查询：状态、等级、题材三个条件可任意组合，内容/总数/总页数来自同一条查询；
     * 越界页由服务端回退到最后一个有效页，响应 number 为实际返回页码。
     */
    @GetMapping
    public ResponseEntity<Page<ScriptResponse>> getScripts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String genre) {
        return ResponseEntity.ok(scriptService.searchScripts(status, grade, genre, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScriptResponse> getScriptById(@PathVariable Long id) {
        return scriptService.getScriptById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ScriptResponse> updateScript(
            @PathVariable Long id,
            @Valid @RequestBody CreateScriptRequest request) {
        return scriptService.updateScript(id, request)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteScript(@PathVariable Long id) {
        scriptService.deleteScript(id);
        return ResponseEntity.noContent().build();
    }
}