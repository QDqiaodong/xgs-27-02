package com.example.scriptreview.controller;

import com.example.scriptreview.dto.request.CreateRecusalRequest;
import com.example.scriptreview.dto.request.DecideRecusalRequest;
import com.example.scriptreview.dto.request.SubstituteJudgeRequest;
import com.example.scriptreview.dto.request.SupplementRecusalRequest;
import com.example.scriptreview.dto.response.RecusalApplicationResponse;
import com.example.scriptreview.dto.response.RecusalReviewLogResponse;
import com.example.scriptreview.dto.response.ScriptAssignmentResponse;
import com.example.scriptreview.dto.response.SubstituteCandidateResponse;
import com.example.scriptreview.entity.User;
import com.example.scriptreview.service.RecusalService;
import com.example.scriptreview.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recusals")
@RequiredArgsConstructor
public class RecusalController {

    private final RecusalService recusalService;

    /** 申报回避：评委为本人申报，或管理员代替评委申报。 */
    @PostMapping
    public ResponseEntity<RecusalApplicationResponse> create(@Valid @RequestBody CreateRecusalRequest request) {
        User currentUser = SecurityUtils.getCurrentUser();
        RecusalApplicationResponse response = recusalService.create(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 评委补充材料。 */
    @PostMapping("/{id}/supplement")
    public ResponseEntity<RecusalApplicationResponse> supplement(
            @PathVariable Long id,
            @Valid @RequestBody SupplementRecusalRequest request) {
        User currentUser = SecurityUtils.getCurrentUser();
        RecusalApplicationResponse response =
                recusalService.supplement(id, request.getSupplementInfo(), currentUser);
        return ResponseEntity.ok(response);
    }

    /** 管理员批准 / 驳回 / 要求补充（并发处理时仅一个决定生效）。 */
    @PostMapping("/{id}/decision")
    public ResponseEntity<RecusalApplicationResponse> decide(
            @PathVariable Long id,
            @Valid @RequestBody DecideRecusalRequest request) {
        User currentUser = SecurityUtils.getCurrentUser();
        RecusalApplicationResponse response = recusalService.decide(id, request, currentUser);
        return ResponseEntity.ok(response);
    }

    /** 已批准回避后，管理员指定替补评委接管（不可重复创建）。 */
    @PostMapping("/{id}/substitute")
    public ResponseEntity<ScriptAssignmentResponse> assignSubstitute(
            @PathVariable Long id,
            @Valid @RequestBody SubstituteJudgeRequest request) {
        User currentUser = SecurityUtils.getCurrentUser();
        ScriptAssignmentResponse response = recusalService.assignSubstitute(id, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 替补候选评委（未参与该脚本、状态正常、专业方向匹配）。 */
    @GetMapping("/{id}/substitute-candidates")
    public ResponseEntity<List<SubstituteCandidateResponse>> getSubstituteCandidates(@PathVariable Long id) {
        User currentUser = SecurityUtils.getCurrentUser();
        List<SubstituteCandidateResponse> response = recusalService.getSubstituteCandidates(id, currentUser);
        return ResponseEntity.ok(response);
    }

    /** 全部回避申请（管理员，含敏感内容）。 */
    @GetMapping
    public ResponseEntity<List<RecusalApplicationResponse>> listAll() {
        User currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(recusalService.listAll(currentUser));
    }

    /** 某脚本的回避申请：所有角色可见状态；敏感字段仅本人/管理员可见。 */
    @GetMapping("/script/{scriptId}")
    public ResponseEntity<List<RecusalApplicationResponse>> listByScript(@PathVariable Long scriptId) {
        User currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(recusalService.listByScript(scriptId, currentUser));
    }

    /** 当前评委本人的回避申请。 */
    @GetMapping("/mine")
    public ResponseEntity<List<RecusalApplicationResponse>> listMine() {
        User currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(recusalService.listMine(currentUser));
    }

    /** 申请详情：非授权角色访问完整内容时返回 403。 */
    @GetMapping("/{id}")
    public ResponseEntity<RecusalApplicationResponse> getDetail(@PathVariable Long id) {
        User currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(recusalService.getDetail(id, currentUser));
    }

    /** 审批/操作轨迹（操作者、前后状态、理由），供审计查询。 */
    @GetMapping("/{id}/logs")
    public ResponseEntity<List<RecusalReviewLogResponse>> getReviewLogs(@PathVariable Long id) {
        User currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(recusalService.getReviewLogs(id, currentUser));
    }
}
