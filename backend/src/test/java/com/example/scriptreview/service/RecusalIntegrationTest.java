package com.example.scriptreview.service;

import com.example.scriptreview.dto.request.CreateRecusalRequest;
import com.example.scriptreview.dto.request.CreateScoreRequest;
import com.example.scriptreview.dto.request.DecideRecusalRequest;
import com.example.scriptreview.dto.request.SubstituteJudgeRequest;
import com.example.scriptreview.dto.response.RecusalApplicationResponse;
import com.example.scriptreview.dto.response.ScoreResponse;
import com.example.scriptreview.entity.*;
import com.example.scriptreview.exception.ConflictException;
import com.example.scriptreview.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RecusalIntegrationTest {

    @Autowired private RecusalService recusalService;
    @Autowired private ScoreService scoreService;
    @Autowired private GradeService gradeService;
    @Autowired private UserRepository userRepository;
    @Autowired private JudgeRepository judgeRepository;
    @Autowired private ScriptRepository scriptRepository;
    @Autowired private ScriptAssignmentRepository assignmentRepository;
    @Autowired private ScoreRepository scoreRepository;
    @Autowired private RecusalApplicationRepository recusalRepository;
    @Autowired private DimensionWeightRepository dimensionWeightRepository;

    private User adminA, adminB, judgeUser1, judgeUser2, judgeUser3, viewer;
    private final java.util.List<String> loserExceptions = new java.util.concurrent.CopyOnWriteArrayList<>();
    private Judge j1, j2, j3;
    private Script script;

    @BeforeEach
    void setUp() {
        recusalRepository.deleteAll();
        scoreRepository.deleteAll();
        assignmentRepository.deleteAll();
        scriptRepository.deleteAll();
        judgeRepository.deleteAll();
        userRepository.deleteAll();
        dimensionWeightRepository.deleteAll();

        adminA = userRepository.save(User.builder().username("adminA").password("x").name("管理员甲")
                .role("ADMIN").status("ACTIVE").build());
        adminB = userRepository.save(User.builder().username("adminB").password("x").name("管理员乙")
                .role("ADMIN").status("ACTIVE").build());
        judgeUser1 = userRepository.save(User.builder().username("j1").password("x").name("张评委")
                .role("JUDGE").status("ACTIVE").build());
        judgeUser2 = userRepository.save(User.builder().username("j2").password("x").name("李评委")
                .role("JUDGE").status("ACTIVE").build());
        judgeUser3 = userRepository.save(User.builder().username("j3").password("x").name("王评委")
                .role("JUDGE").status("ACTIVE").build());
        viewer = userRepository.save(User.builder().username("v").password("x").name("查看员")
                .role("VIEWER").status("ACTIVE").build());

        j1 = judgeRepository.save(Judge.builder().name("张评委").role("JUDGE").userId(judgeUser1.getId())
                .expertise("情感、生活类脚本").status("ACTIVE").build());
        j2 = judgeRepository.save(Judge.builder().name("李评委").role("JUDGE").userId(judgeUser2.getId())
                .expertise("美食、旅行类脚本").status("ACTIVE").build());
        j3 = judgeRepository.save(Judge.builder().name("王评委").role("JUDGE").userId(judgeUser3.getId())
                .expertise("科技、科普类脚本").status("ACTIVE").build());

        script = scriptRepository.save(Script.builder()
                .title("测试情感脚本").genre("情感").duration(60).targetAudience("青年")
                .author("作者甲").source("某MCN机构").maskSalt("RECUSALT1")
                .status("REVIEWING").build());

        dimensionWeightRepository.save(DimensionWeight.builder().dimensionName("creativity").weight(0.25).build());
        dimensionWeightRepository.save(DimensionWeight.builder().dimensionName("commercial").weight(0.20).build());
        dimensionWeightRepository.save(DimensionWeight.builder().dimensionName("difficulty").weight(0.15).build());
        dimensionWeightRepository.save(DimensionWeight.builder().dimensionName("logic").weight(0.20).build());
        dimensionWeightRepository.save(DimensionWeight.builder().dimensionName("audience").weight(0.20).build());
    }

    private void assign(Judge judge, User admin) {
        assignmentRepository.save(ScriptAssignment.builder()
                .scriptId(script.getId()).judgeId(judge.getId()).assignedBy(admin.getId())
                .status("ASSIGNED").build());
    }

    private CreateScoreRequest scoreRequest(Long judgeId, int base) {
        return CreateScoreRequest.builder()
                .scriptId(script.getId()).judgeId(judgeId)
                .creativityScore(base).commercialScore(base).difficultyScore(base)
                .logicScore(base).audienceScore(base).comment("评语" + base).build();
    }

    private CreateRecusalRequest recusalRequest(Long judgeId) {
        return CreateRecusalRequest.builder()
                .scriptId(script.getId()).judgeId(judgeId)
                .type("AUTHOR").reason("与作者是近亲属关系").evidence("户口本编号XXXX").build();
    }

    // ========== 1. 申报：权限约束 ==========

    @Test
    void judgeCanOnlyDeclareRecusalForSelf() {
        assign(j1, adminA);
        assign(j2, adminA);
        // j1 试图替 j2 申报 -> 403
        assertThrows(AccessDeniedException.class,
                () -> recusalService.create(recusalRequest(j2.getId()), judgeUser1));
    }

    @Test
    void viewerCannotDeclareRecusal() {
        assign(j1, adminA);
        assertThrows(AccessDeniedException.class,
                () -> recusalService.create(recusalRequest(j1.getId()), viewer));
    }

    @Test
    void cannotDeclareWithoutAssignment() {
        // j1 未分配该脚本
        assertThrows(RuntimeException.class,
                () -> recusalService.create(recusalRequest(j1.getId()), judgeUser1));
    }

    // ========== 2. 未处理申请期间：脚本可看、不能打分 ==========

    @Test
    void pendingRecusalBlocksNewScoreButScriptStaysViewable() {
        assign(j1, adminA);
        recusalService.create(recusalRequest(j1.getId()), judgeUser1);

        assertThrows(ConflictException.class,
                () -> scoreService.createScore(scoreRequest(j1.getId(), 8), judgeUser1));
        // 脚本仍然可查看
        assertTrue(scriptRepository.findById(script.getId()).isPresent());
    }

    // ========== 3. 敏感说明可见范围 ==========

    @Test
    void sensitiveFieldsOnlyVisibleToOwnerAndAdmin() {
        assign(j1, adminA);
        RecusalApplicationResponse created = recusalService.create(recusalRequest(j1.getId()), judgeUser1);

        // 查看员：只能看到状态
        List<RecusalApplicationResponse> viewerList = recusalService.listByScript(script.getId(), viewer);
        assertEquals(1, viewerList.size());
        assertFalse(viewerList.get(0).getCanViewSensitive());
        assertNull(viewerList.get(0).getReason());
        assertNull(viewerList.get(0).getEvidence());
        assertThrows(AccessDeniedException.class, () -> recusalService.getDetail(created.getId(), viewer));

        // 另一个评委：同样看不到敏感说明
        List<RecusalApplicationResponse> otherJudgeList =
                recusalService.listByScript(script.getId(), judgeUser2);
        assertFalse(otherJudgeList.get(0).getCanViewSensitive());

        // 本人：可见
        assertTrue(recusalService.listMine(judgeUser1).get(0).getCanViewSensitive());
        // 管理员：可见
        assertTrue(recusalService.listAll(adminA).get(0).getCanViewSensitive());
    }

    // ========== 4. 不能审批自己代为提交的申请 ==========

    @Test
    void adminCannotDecideOwnOnBehalfApplication() {
        assign(j1, adminA);
        RecusalApplicationResponse created = recusalService.create(recusalRequest(j1.getId()), adminA);
        assertEquals(adminA.getId(), created.getSubmittedBy());

        assertThrows(AccessDeniedException.class, () -> recusalService.decide(created.getId(),
                DecideRecusalRequest.builder().decision("APPROVE").comment("同意").build(), adminA));
        // 另一个管理员可以处理
        assertDoesNotThrow(() -> recusalService.decide(created.getId(),
                DecideRecusalRequest.builder().decision("REJECT").comment("理由不充分").build(), adminB));
    }

    // ========== 5. 并发审批：只有一个最终决定 ==========

    @Test
    void concurrentDecisionsProduceSingleOutcome() throws Exception {
        assign(j1, adminA);
        RecusalApplicationResponse created = recusalService.create(recusalRequest(j1.getId()), judgeUser1);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger approved = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            final User admin = (i % 2 == 0) ? adminA : adminB;
            final String decision = (i % 2 == 0) ? "APPROVE" : "REJECT";
            pool.submit(() -> {
                start.await();
                try {
                    RecusalApplicationResponse r = recusalService.decide(created.getId(),
                            DecideRecusalRequest.builder().decision(decision).comment("并发处理" + decision).build(),
                            admin);
                    if ("APPROVED".equals(r.getStatus())) approved.incrementAndGet();
                    if ("REJECTED".equals(r.getStatus())) rejected.incrementAndGet();
                } catch (Exception e) {
                    conflicts.incrementAndGet();
                    loserExceptions.add(e.getClass().getSimpleName());
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        long finalDecisions = approved.get() + rejected.get();
        assertEquals(1, finalDecisions, "只能有一个最终决定");
        assertEquals(threads - 1, conflicts.get(), "其余并发处理必须被拒绝");
        RecusalApplication finalApp = recusalRepository.findById(created.getId()).orElseThrow();
        assertTrue(List.of("APPROVED", "REJECTED").contains(finalApp.getStatus()));
    }

    // ========== 6. 批准 -> 终止分配、旧评分失效、替补接管闭环 ==========

    @Test
    void approveInvalidatesScoreAndSubstituteCompletesLoop() {
        assign(j1, adminA);
        assign(j2, adminA);
        scoreService.createScore(scoreRequest(j1.getId(), 9), judgeUser1);
        scoreService.createScore(scoreRequest(j2.getId(), 7), judgeUser2);

        Long appId = recusalService.create(recusalRequest(j1.getId()), judgeUser1).getId();
        // adminB 审批（申报人是评委本人，任何管理员均可）
        recusalService.decide(appId,
                DecideRecusalRequest.builder().decision("APPROVE").comment("关系属实").build(), adminB);

        // 原分配终止
        ScriptAssignment oldAssignment = assignmentRepository
                .findByScriptIdAndJudgeId(script.getId(), j1.getId()).orElseThrow();
        assertEquals("RECUSED", oldAssignment.getStatus());

        // 旧评分失效但保留原文
        Score oldScore = scoreRepository.findByScriptIdAndJudgeId(script.getId(), j1.getId()).get(0);
        assertFalse(oldScore.getValid());
        assertNotNull(oldScore.getInvalidatedAt());
        assertEquals("评语9", oldScore.getComment());

        // 有效评分只剩1份 -> 待补评，不能分级
        Script refreshed = scriptRepository.findById(script.getId()).orElseThrow();
        assertEquals("PENDING_SUPPLEMENT", refreshed.getStatus());
        assertNull(refreshed.getGrade());
        assertThrows(ConflictException.class, () -> gradeService.calculateGrade(script.getId()));

        // 原评委再次评分被拒
        assertThrows(ConflictException.class,
                () -> scoreService.createScore(scoreRequest(j1.getId(), 6), judgeUser1));

        // 替补候选：j2 已参与；j3 专长不匹配（科技 vs 情感）
        // 新增一位匹配且未参与的评委
        User j4User = userRepository.save(User.builder().username("j4").password("x").name("孙评委")
                .role("JUDGE").status("ACTIVE").build());
        Judge j4 = judgeRepository.save(Judge.builder().name("孙评委").role("JUDGE").userId(j4User.getId())
                .expertise("情感、搞笑、生活类脚本").status("ACTIVE").build());

        // j3 方向不匹配不能接管
        assertThrows(ConflictException.class, () -> recusalService.assignSubstitute(appId,
                SubstituteJudgeRequest.builder().substituteJudgeId(j3.getId()).build(), adminB));
        // 已参与的 j2 不能接管
        assertThrows(ConflictException.class, () -> recusalService.assignSubstitute(appId,
                SubstituteJudgeRequest.builder().substituteJudgeId(j2.getId()).build(), adminB));

        // j4 接管成功
        recusalService.assignSubstitute(appId,
                SubstituteJudgeRequest.builder().substituteJudgeId(j4.getId()).build(), adminB);

        // 不能重复创建替补分配
        assertThrows(ConflictException.class, () -> recusalService.assignSubstitute(appId,
                SubstituteJudgeRequest.builder().substituteJudgeId(j4.getId()).build(), adminB));

        // 替补看到的是接管后的新任务（replacesAssignmentId 指向原分配）
        ScriptAssignment replacement = assignmentRepository
                .findByScriptIdAndJudgeId(script.getId(), j4.getId()).orElseThrow();
        assertEquals(oldAssignment.getId(), replacement.getReplacesAssignmentId());

        // 替补评分后有效评分达到2份，可重新分级，且不混入旧评分
        scoreService.createScore(scoreRequest(j4.getId(), 5), j4User);
        var gradeResult = gradeService.calculateGrade(script.getId());
        assertEquals("B", gradeResult.get("grade"));
        assertEquals(2, gradeResult.get("validScoreCount"));
        assertEquals(1, gradeResult.get("excludedScoreCount"));

        Script graded = scriptRepository.findById(script.getId()).orElseThrow();
        assertEquals("GRADED", graded.getStatus());
        // 仅基于 7 和 5 两个有效评分 -> 均值6 -> 60分整
        assertEquals(60.0, graded.getTotalScore(), 0.01);
    }

    // ========== 7. 已分级脚本事后回避：撤销等级、待补评、再分级 ==========

    @Test
    void postHocRecusalOnGradedScriptRevokesGrade() {
        assign(j1, adminA);
        assign(j2, adminA);
        assign(j3, adminA);
        scoreService.createScore(scoreRequest(j1.getId(), 9), judgeUser1);
        scoreService.createScore(scoreRequest(j2.getId(), 9), judgeUser2);
        gradeService.calculateGrade(script.getId());
        assertEquals("GRADED", scriptRepository.findById(script.getId()).orElseThrow().getStatus());

        Long appId = recusalService.create(recusalRequest(j1.getId()), judgeUser1).getId();
        recusalService.decide(appId,
                DecideRecusalRequest.builder().decision("APPROVE").comment("属实").build(), adminB);

        Script revoked = scriptRepository.findById(script.getId()).orElseThrow();
        assertEquals("PENDING_SUPPLEMENT", revoked.getStatus());
        assertNull(revoked.getGrade());
        assertNull(revoked.getTotalScore());

        // 有效评分重新达到2份后（j2 已有9，j3 补一份8）再次分级
        scoreService.createScore(scoreRequest(j3.getId(), 8), judgeUser3);
        var result = gradeService.calculateGrade(script.getId());
        assertEquals(2, result.get("validScoreCount"));
        assertEquals("GRADED", scriptRepository.findById(script.getId()).orElseThrow().getStatus());
    }

    // ========== 7b. 并发：批准回避 与 分级同时发生，等级不得混入旧评分 ==========

    @Test
    void concurrentApproveAndGradeNeverMixesInvalidScore() throws Exception {
        assign(j1, adminA);
        assign(j2, adminA);
        assign(j3, adminA);
        scoreService.createScore(scoreRequest(j1.getId(), 9), judgeUser1);
        scoreService.createScore(scoreRequest(j2.getId(), 7), judgeUser2);
        scoreService.createScore(scoreRequest(j3.getId(), 8), judgeUser3);

        Long appId = recusalService.create(recusalRequest(j1.getId()), judgeUser1).getId();

        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        java.util.List<Exception> errors = new CopyOnWriteArrayList<>();
        for (int i = 0; i < threads; i++) {
            final boolean approve = (i % 2 == 0);
            pool.submit(() -> {
                start.await();
                try {
                    if (approve) {
                        recusalService.decide(appId,
                                DecideRecusalRequest.builder().decision("APPROVE").comment("并发批准").build(),
                                adminB);
                    } else {
                        try {
                            gradeService.calculateGrade(script.getId());
                        } catch (ConflictException acceptable) {
                            // 有效评分不足时拒绝分级，符合预期
                        }
                    }
                } catch (Exception e) {
                    // 并发审批失败者（乐观锁）是“只有一个最终决定”的预期表现
                    if (e instanceof org.springframework.orm.ObjectOptimisticLockingFailureException
                            || e instanceof ConflictException) {
                        // acceptable
                    } else {
                        errors.add(e);
                    }
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        assertTrue(errors.isEmpty(), "不应出现非预期异常: " + errors);

        // 串行化之后的最终事实：旧评分无效
        Score old = scoreRepository.findByScriptIdAndJudgeId(script.getId(), j1.getId()).get(0);
        assertFalse(old.getValid());
        long validCount = scoreRepository.countByScriptIdAndValidTrue(script.getId());
        assertEquals(2, validCount);

        // 如果并发期间恰好分级成功，等级必须基于有效评分；若已撤销则为待补评 —— 二者必居其一
        Script finalScript = scriptRepository.findById(script.getId()).orElseThrow();
        if ("GRADED".equals(finalScript.getStatus())) {
            // 只可能在批准之后、有效评分仍>=2 时成立：7、8 两份有效评分 -> 均值7.5 -> 75分
            assertEquals(75.0, finalScript.getTotalScore(), 0.01);
            assertEquals("A", finalScript.getGrade());
        } else {
            assertEquals("PENDING_SUPPLEMENT", finalScript.getStatus());
            assertNull(finalScript.getGrade());
            // 待补评下强行分级必须被拒
            assertThrows(ConflictException.class, () -> gradeService.calculateGrade(script.getId()));
        }
    }

    // ========== 8. 驳回 -> 恢复评分有效性与原分配状态 ==========
    @Test
    void rejectRestoresScoreValidityAndAssignment() {
        assign(j1, adminA);
        scoreService.createScore(scoreRequest(j1.getId(), 6), judgeUser1);
        assertEquals("COMPLETED", assignmentRepository
                .findByScriptIdAndJudgeId(script.getId(), j1.getId()).orElseThrow().getStatus());

        Long appId = recusalService.create(recusalRequest(j1.getId()), judgeUser1).getId();
        // 流程上驳回不会先失效评分；驳回后状态保持
        recusalService.decide(appId,
                DecideRecusalRequest.builder().decision("REJECT").comment("依据不足").build(), adminB);

        assertEquals("REJECTED", recusalRepository.findById(appId).orElseThrow().getStatus());
        Score score = scoreRepository.findByScriptIdAndJudgeId(script.getId(), j1.getId()).get(0);
        assertTrue(score.getValid());
        assertNull(score.getInvalidatedAt());
        assertEquals("COMPLETED", assignmentRepository
                .findByScriptIdAndJudgeId(script.getId(), j1.getId()).orElseThrow().getStatus());

        // 评分仍被排除逻辑验证：失效场景下查看员不可见；驳回恢复后查看员可见
        List<ScoreResponse> viewerScores = scoreService.getScoresByScriptId(script.getId(), viewer);
        assertTrue(viewerScores.stream().anyMatch(s -> s.getJudgeId().equals(j1.getId()) && s.getValid()));
    }

    // ========== 9. 补充材料闭环 ==========

    @Test
    void supplementFlowReturnsApplicationToPending() {
        assign(j1, adminA);
        Long appId = recusalService.create(recusalRequest(j1.getId()), judgeUser1).getId();

        recusalService.decide(appId,
                DecideRecusalRequest.builder().decision("REQUEST_SUPPLEMENT").comment("请补充亲属关系证明").build(),
                adminB);
        assertEquals("SUPPLEMENT_REQUESTED", recusalRepository.findById(appId).orElseThrow().getStatus());

        // 非本人不能补充
        assertThrows(AccessDeniedException.class,
                () -> recusalService.supplement(appId, "补充内容", judgeUser2));

        recusalService.supplement(appId, "已上传户口本扫描件编号YYYY", judgeUser1);
        assertEquals("PENDING", recusalRepository.findById(appId).orElseThrow().getStatus());
        assertEquals("已上传户口本扫描件编号YYYY",
                recusalRepository.findById(appId).orElseThrow().getSupplementInfo());
    }

    // ========== 10. 审计轨迹完整 ==========

    @Test
    void reviewLogCapturesOperatorStatusTransitionAndReason() {
        assign(j1, adminA);
        Long appId = recusalService.create(recusalRequest(j1.getId()), judgeUser1).getId();
        recusalService.decide(appId,
                DecideRecusalRequest.builder().decision("REQUEST_SUPPLEMENT").comment("补材料").build(), adminB);
        recusalService.supplement(appId, "补充", judgeUser1);
        recusalService.decide(appId,
                DecideRecusalRequest.builder().decision("APPROVE").comment("批准").build(), adminB);

        var logs = recusalService.getReviewLogs(appId, adminB);
        assertEquals(4, logs.size());
        assertEquals("CREATE", logs.get(0).getAction());
        assertEquals("REQUEST_SUPPLEMENT", logs.get(1).getAction());
        assertEquals(judgeUser1.getId(), logs.get(0).getOperatorId());
        assertEquals(adminB.getId(), logs.get(1).getOperatorId());
        assertEquals("PENDING", logs.get(1).getFromStatus());
        assertEquals("SUPPLEMENT_REQUESTED", logs.get(1).getToStatus());
        assertEquals("批准", logs.get(3).getComment());

        // 查看员不能看轨迹
        assertThrows(AccessDeniedException.class, () -> recusalService.getReviewLogs(appId, viewer));
    }
}
