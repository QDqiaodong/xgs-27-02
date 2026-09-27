package com.example.scriptreview.controller;

import com.example.scriptreview.dto.request.CreateScriptRequest;
import com.example.scriptreview.dto.request.DecideIdentityGroupRequest;
import com.example.scriptreview.dto.response.IdentityMapResponse;
import com.example.scriptreview.dto.response.ScriptRawResponse;
import com.example.scriptreview.dto.response.ScriptResponse;
import com.example.scriptreview.entity.User;
import com.example.scriptreview.service.IdentityReviewService;
import com.example.scriptreview.service.ScriptService;
import com.example.scriptreview.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
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
    private final IdentityReviewService identityReviewService;

    @PostMapping
    public ResponseEntity<ScriptResponse> createScript(@Valid @RequestBody CreateScriptRequest request) {
        // 录入原稿是管理员动作；返回值为脱敏视图，原稿仅能通过受控入口回看
        SecurityUtils.requireRole("ADMIN");
        ScriptResponse response = scriptService.createScript(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 列表查询：状态、等级、题材三个条件可任意组合，内容/总数/总页数来自同一条查询；
     * 越界页由服务端回退到最后一个有效页，响应 number 为实际返回页码。
     * 所有角色拿到的都是脱敏视图，响应结构中不包含任何原始身份字段。
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

    /** 脚本脱敏详情：评委/查看员/管理员常规入口共用，任何角色都无法经此入口取得原文。 */
    @GetMapping("/{id}")
    public ResponseEntity<ScriptResponse> getScriptById(@PathVariable Long id) {
        return scriptService.getScriptById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 管理员受控原稿详情：完整作者/来源机构/联系方式/正文原文。
     * 服务层强制 ADMIN 并记录“谁在何时查看过原稿”的审计。
     */
    @GetMapping("/{id}/raw")
    public ResponseEntity<ScriptRawResponse> getRawScript(@PathVariable Long id,
                                                          HttpServletRequest request) {
        User viewer = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(
                identityReviewService.viewRaw(id, viewer, clientIp(request)));
    }

    /** 管理员代号映射核对视图：原始内容 / 识别线索 / 脱敏结果三区分明，查看即审计。 */
    @GetMapping("/{id}/identity-map")
    public ResponseEntity<IdentityMapResponse> getIdentityMap(@PathVariable Long id,
                                                              HttpServletRequest request) {
        User viewer = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(
                identityReviewService.viewMap(id, viewer, clientIp(request)));
    }

    /** 管理员核对可疑身份：确认投稿身份（代号替换）或确认剧情人物（保留原文），不改写原稿。 */
    @PostMapping("/{id}/identity-groups/{groupId}/decision")
    public ResponseEntity<IdentityMapResponse> decideIdentityGroup(
            @PathVariable Long id,
            @PathVariable Long groupId,
            @Valid @RequestBody DecideIdentityGroupRequest requestBody,
            HttpServletRequest request) {
        User viewer = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(
                identityReviewService.decideGroup(id, groupId, requestBody, viewer, clientIp(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ScriptResponse> updateScript(
            @PathVariable Long id,
            @Valid @RequestBody CreateScriptRequest request) {
        SecurityUtils.requireRole("ADMIN");
        return scriptService.updateScript(id, request)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteScript(@PathVariable Long id) {
        SecurityUtils.requireRole("ADMIN");
        scriptService.deleteScript(id);
        return ResponseEntity.noContent().build();
    }

    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : request.getRemoteAddr();
    }
}
