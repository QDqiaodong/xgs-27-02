package com.example.scriptreview.service;

import com.example.scriptreview.dto.request.CreateScriptRequest;
import com.example.scriptreview.dto.response.MaskingMapResponse;
import com.example.scriptreview.dto.response.ScriptResponse;
import com.example.scriptreview.entity.IdentityClue;
import com.example.scriptreview.entity.Script;
import com.example.scriptreview.entity.User;
import com.example.scriptreview.repository.AuditLogRepository;
import com.example.scriptreview.repository.IdentityClueRepository;
import com.example.scriptreview.repository.RecusalApplicationRepository;
import com.example.scriptreview.repository.ScoreRepository;
import com.example.scriptreview.repository.ScriptAssignmentRepository;
import com.example.scriptreview.repository.ScriptIdentityRepository;
import com.example.scriptreview.repository.ScriptRepository;
import com.example.scriptreview.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 脚本双盲脱敏验收：元数据与正文线索识别、同一身份稳定代号、跨脚本不可横向关联、
 * 剧情人物保留、模糊线索保守隐藏与管理员核对、审计追溯、原稿不被改写。
 */
@SpringBootTest
class MaskingIntegrationTest {

    @Autowired private ScriptService scriptService;
    @Autowired private MaskingService maskingService;
    @Autowired private ScriptRepository scriptRepository;
    @Autowired private ScriptIdentityRepository identityRepository;
    @Autowired private IdentityClueRepository clueRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private ScoreRepository scoreRepository;
    @Autowired private ScriptAssignmentRepository assignmentRepository;
    @Autowired private RecusalApplicationRepository recusalRepository;
    @Autowired private UserRepository userRepository;

    private User admin;
    private User judge;
    private User viewer;
    private Long scriptId;

    private static final String AUTHOR = "张三";
    private static final String ORG = "星光文化传媒有限公司";
    private static final String PHONE = "13812345678";
    private static final String EMAIL = "zhangsan@food.com";
    private static final String SOCIAL = "zs_food2024";
    private static final String DESCRIPTION =
            "店主张三每天深夜接待食客。联系人：张三，电话 " + PHONE + "，邮箱 " + EMAIL
                    + "，微信号 " + SOCIAL + "。剧情人物李小花是常客。海风渔业公司提供食材。"
                    + "张三丰是店主口中的武侠传说。";
    private static final String ATTACHMENT = "分镜表见附件 storyboard.pdf，补充联系 " + PHONE + "。";

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        clueRepository.deleteAll();
        identityRepository.deleteAll();
        recusalRepository.deleteAll();
        scoreRepository.deleteAll();
        assignmentRepository.deleteAll();
        scriptRepository.deleteAll();
        userRepository.deleteAll();

        admin = userRepository.save(User.builder().username("mAdmin").password("x")
                .name("管理员甲").role("ADMIN").status("ACTIVE").build());
        judge = userRepository.save(User.builder().username("mJudge").password("x")
                .name("评委甲").role("JUDGE").status("ACTIVE").build());
        viewer = userRepository.save(User.builder().username("mViewer").password("x")
                .name("查看员").role("VIEWER").status("ACTIVE").build());

        loginAs(admin);
        ScriptResponse created = scriptService.createScript(CreateScriptRequest.builder()
                .title("深夜食堂：张三的小店").genre("美食").duration(90).targetAudience("青年")
                .description(DESCRIPTION)
                .attachmentNote(ATTACHMENT)
                .author(AUTHOR).source(ORG)
                .build());
        scriptId = created.getId();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private List<IdentityClue> clues() {
        return clueRepository.findByScriptId(scriptId);
    }

    private IdentityClue clueOf(String fieldOrigin, String clueType, String text) {
        return clues().stream()
                .filter(c -> c.getFieldOrigin().equals(fieldOrigin) && c.getClueType().equals(clueType)
                        && c.getOriginalText().equals(text))
                .findFirst().orElse(null);
    }

    // ========== 1. 元数据与正文线索识别 ==========

    @Test
    void metadataAndContentCluesAreDetected() {
        // 元数据：作者、来源机构直接确认为投稿身份
        assertEquals("CONFIRMED", clueOf("AUTHOR", "NAME", AUTHOR).getStatus());
        assertEquals("CONFIRMED", clueOf("SOURCE", "ORG", ORG).getStatus());
        // 正文：电话、邮箱、社交账号整体识别并确认
        assertEquals("CONFIRMED", clueOf("DESCRIPTION", "PHONE", PHONE).getStatus());
        assertEquals("CONFIRMED", clueOf("DESCRIPTION", "EMAIL", EMAIL).getStatus());
        assertEquals("CONFIRMED", clueOf("DESCRIPTION", "SOCIAL", SOCIAL).getStatus());
        // 标题与附件说明遵守同一规则
        assertEquals("CONFIRMED", clueOf("TITLE", "NAME", AUTHOR).getStatus());
        assertEquals("CONFIRMED", clueOf("ATTACHMENT", "PHONE", PHONE).getStatus());
        // 正文中与作者一致的姓名再次出现 -> 同一身份
        IdentityClue nameInBody = clueOf("DESCRIPTION", "NAME", AUTHOR);
        assertEquals("CONFIRMED", nameInBody.getStatus());
        assertEquals(clueOf("AUTHOR", "NAME", AUTHOR).getIdentityId(), nameInBody.getIdentityId());
        // 无法确定的机构 -> 待核对
        assertEquals("PENDING", clueOf("DESCRIPTION", "ORG", "海风渔业公司").getStatus());
        // 与作者姓名相似的长名 -> 独立待核对线索，不并入作者身份
        IdentityClue similar = clueOf("DESCRIPTION", "NAME", "张三丰");
        assertNotNull(similar, "张三丰必须被识别为独立线索");
        assertEquals("PENDING", similar.getStatus());
        assertNull(similar.getIdentityId());
    }

    // ========== 2. 同一身份稳定替换 + 电话邮箱整体替换 ==========

    @Test
    void judgeGetsStableAliasEverywhere() {
        loginAs(judge);
        ScriptResponse masked = scriptService.getScriptById(scriptId).orElseThrow();
        assertEquals(Boolean.TRUE, masked.getMasked());

        String alias = masked.getAuthor();
        assertNotNull(alias);
        assertTrue(alias.startsWith("投稿人·"), "代号应为投稿人别名: " + alias);
        // 元数据与正文、附件中的同一身份使用一致代号
        assertEquals(alias, masked.getSource());
        assertTrue(masked.getTitle().contains(alias), "标题中的作者名应替换为同一代号");
        long occurrences = masked.getDescription().split(java.util.regex.Pattern.quote(alias), -1).length - 1;
        assertTrue(occurrences >= 5, "正文中的姓名/电话/邮箱/微信号都应替换为同一代号，实际 " + occurrences);
        assertTrue(masked.getAttachmentNote().contains(alias));

        // 任何原始线索都不出现在脱敏结果中；电话整体替换，不留可猜的半截
        for (String raw : new String[]{AUTHOR, ORG, PHONE, EMAIL, SOCIAL}) {
            assertFalse(masked.getTitle().contains(raw), "标题泄露 " + raw);
            assertFalse(masked.getDescription().contains(raw), "正文泄露 " + raw);
            assertFalse(masked.getAttachmentNote().contains(raw), "附件说明泄露 " + raw);
        }
        assertFalse(masked.getDescription().contains("zhangsan"), "邮箱局部也不得泄露");
        assertFalse(masked.getDescription().contains("12345678"), "电话局部也不得泄露");
    }

    // ========== 3. 剧情人物保留 + 相似姓名不合并 ==========

    @Test
    void plotCharactersArePreservedAndSimilarNamesNotMerged() {
        loginAs(judge);
        ScriptResponse masked = scriptService.getScriptById(scriptId).orElseThrow();
        // 与投稿身份无关的剧情人物不误删
        assertTrue(masked.getDescription().contains("李小花"), "剧情人物李小花应保留");
        // 张三丰不是张三：不得被替换成投稿人代号+“丰”，核对前保守隐藏
        assertFalse(masked.getDescription().contains("张三丰"));
        assertFalse(masked.getDescription().contains(masked.getAuthor() + "丰"),
                "相似姓名不得并入投稿人代号");
        assertTrue(masked.getDescription().contains(MaskingService.PENDING_PLACEHOLDER));

        // 管理员核对张三丰为正文角色后，脱敏视图恢复显示
        loginAs(admin);
        IdentityClue similar = clueOf("DESCRIPTION", "NAME", "张三丰");
        maskingService.confirmClue(scriptId, similar.getId(), "CHARACTER", null);
        loginAs(judge);
        ScriptResponse after = scriptService.getScriptById(scriptId).orElseThrow();
        assertTrue(after.getDescription().contains("张三丰"), "核对为正文角色后应恢复显示");
        assertFalse(after.getDescription().contains(AUTHOR + "丰") && !after.getDescription().contains("张三丰"));
    }

    // ========== 4. 模糊线索待核对：先保守隐藏，核对后按结论呈现 ==========

    @Test
    void ambiguousCluesStayHiddenUntilAdminReviews() {
        loginAs(judge);
        ScriptResponse before = scriptService.getScriptById(scriptId).orElseThrow();
        assertFalse(before.getDescription().contains("海风渔业公司"), "核对前不得泄露可疑片段");
        assertTrue(before.getPendingClueCount() > 0);

        // 管理员核对为正文角色 -> 仅调整脱敏视图，原稿不变
        loginAs(admin);
        IdentityClue org = clueOf("DESCRIPTION", "ORG", "海风渔业公司");
        maskingService.confirmClue(scriptId, org.getId(), "CHARACTER", null);
        Script original = scriptRepository.findById(scriptId).orElseThrow();
        assertTrue(original.getDescription().contains("海风渔业公司"), "核对不得改写原稿");

        loginAs(judge);
        ScriptResponse after = scriptService.getScriptById(scriptId).orElseThrow();
        assertTrue(after.getDescription().contains("海风渔业公司"));
    }

    // ========== 5. 管理员映射核对：区分原稿/线索/脱敏结果 ==========

    @Test
    void adminMappingSeparatesOriginalCluesAndMasked() {
        loginAs(admin);
        MaskingMapResponse mapping = maskingService.getMapping(scriptId, true);
        // 原稿完整
        assertEquals(AUTHOR, mapping.getOriginal().getAuthor());
        assertTrue(mapping.getOriginal().getDescription().contains(PHONE));
        // 脱敏结果不含原文
        assertFalse(mapping.getMasked().getDescription().contains(PHONE));
        assertFalse(mapping.getMasked().getAuthor().contains(AUTHOR));
        // 线索清单区分状态
        assertTrue(mapping.getClues().stream().anyMatch(c -> "CONFIRMED".equals(c.getStatus())));
        assertTrue(mapping.getClues().stream().anyMatch(c -> "PENDING".equals(c.getStatus())));
        assertTrue(mapping.getPendingCount() > 0);
        // 确认线索为投稿身份（新建独立身份）后使用新代号
        IdentityClue org = clueOf("DESCRIPTION", "ORG", "海风渔业公司");
        MaskingMapResponse updated = maskingService.confirmClue(scriptId, org.getId(), "IDENTITY", -1L);
        assertEquals(2, updated.getIdentities().size(), "应新建一个独立身份");
        String relatedAlias = updated.getIdentities().stream()
                .filter(i -> "RELATED".equals(i.getKind())).findFirst().orElseThrow().getAliasCode();
        assertNotEquals(updated.getMasked().getAuthor(), relatedAlias, "不同身份代号不得相同");
        assertTrue(updated.getMasked().getDescription().contains(relatedAlias));
    }

    // ========== 6. 跨脚本不可横向关联 ==========

    @Test
    void sameSubmitterGetsDifferentAliasesAcrossScripts() {
        loginAs(admin);
        ScriptResponse second = scriptService.createScript(CreateScriptRequest.builder()
                .title("张三的早餐铺").genre("生活").duration(60).targetAudience("青年")
                .description("张三又开了一家早餐铺。")
                .author(AUTHOR).source(ORG)
                .build());

        loginAs(judge);
        String aliasA = scriptService.getScriptById(scriptId).orElseThrow().getAuthor();
        String aliasB = scriptService.getScriptById(second.getId()).orElseThrow().getAuthor();
        assertNotEquals(aliasA, aliasB, "同一投稿人在不同脚本不得使用可横向关联的固定代号");
        assertFalse(aliasA.contains(AUTHOR));
        assertFalse(aliasB.contains(AUTHOR));
    }

    // ========== 7. 刷新/重新登录后结果一致 ==========

    @Test
    void maskedViewIsStableAcrossReadsAndReLogin() {
        loginAs(judge);
        ScriptResponse first = scriptService.getScriptById(scriptId).orElseThrow();
        // 模拟重新登录：清空上下文后重新放入同一用户
        SecurityContextHolder.clearContext();
        loginAs(judge);
        ScriptResponse second = scriptService.getScriptById(scriptId).orElseThrow();
        assertEquals(first.getAuthor(), second.getAuthor());
        assertEquals(first.getTitle(), second.getTitle());
        assertEquals(first.getDescription(), second.getDescription());
        // 评委与查看员看到的脱敏结果一致
        loginAs(viewer);
        ScriptResponse viewerView = scriptService.getScriptById(scriptId).orElseThrow();
        assertEquals(first.getAuthor(), viewerView.getAuthor());
        assertEquals(first.getDescription(), viewerView.getDescription());
    }

    // ========== 8. 审计追溯 ==========

    @Test
    void mappingViewAndConfirmAreAudited() {
        loginAs(admin);
        maskingService.getMapping(scriptId, true);
        IdentityClue org = clueOf("DESCRIPTION", "ORG", "海风渔业公司");
        maskingService.confirmClue(scriptId, org.getId(), "CHARACTER", null);

        var logs = auditLogRepository.findAll();
        assertTrue(logs.stream().anyMatch(l -> "VIEW_MASKING_MAPPING".equals(l.getAction())
                && "Masking".equals(l.getModule())
                && admin.getId().equals(l.getUserId())
                && scriptId.equals(l.getTargetId())), "查看映射必须留痕");
        assertTrue(logs.stream().anyMatch(l -> "CONFIRM_IDENTITY_CLUE".equals(l.getAction())
                && admin.getId().equals(l.getUserId())), "核对动作必须留痕");
    }

    // ========== 9. 非管理员不得访问映射 ==========

    @Test
    void nonAdminCannotAccessMappingOrConfirm() {
        loginAs(judge);
        assertThrows(AccessDeniedException.class, () -> maskingService.getMapping(scriptId, true));
        IdentityClue org = clueOf("DESCRIPTION", "ORG", "海风渔业公司");
        assertThrows(AccessDeniedException.class,
                () -> maskingService.confirmClue(scriptId, org.getId(), "CHARACTER", null));
        loginAs(viewer);
        assertThrows(AccessDeniedException.class, () -> maskingService.getMapping(scriptId, true));
    }

    // ========== 10. 原稿编辑后重扫：已核对结论保留，失效线索清理 ==========

    @Test
    void rescanAfterEditKeepsDecisionsAndDropsStaleClues() {
        loginAs(admin);
        IdentityClue org = clueOf("DESCRIPTION", "ORG", "海风渔业公司");
        maskingService.confirmClue(scriptId, org.getId(), "CHARACTER", null);

        scriptService.updateScript(scriptId, CreateScriptRequest.builder()
                .title("深夜食堂：张三的小店").genre("美食").duration(90).targetAudience("青年")
                .description("店主张三的新故事。海风渔业公司继续提供食材。")
                .attachmentNote(ATTACHMENT)
                .author(AUTHOR).source(ORG)
                .build());

        // 仍在原文中的线索保留核对结论
        assertEquals("CHARACTER", clueOf("DESCRIPTION", "ORG", "海风渔业公司").getStatus());
        // 已从原稿删除的线索被清理
        assertNull(clueOf("DESCRIPTION", "PHONE", PHONE), "原稿删除电话后线索应被清理");
        assertNull(clueOf("DESCRIPTION", "EMAIL", EMAIL));

        loginAs(judge);
        ScriptResponse masked = scriptService.getScriptById(scriptId).orElseThrow();
        assertTrue(masked.getDescription().contains("海风渔业公司"));
        assertFalse(masked.getDescription().contains(AUTHOR));
    }

    // ========== 11. 手工补充线索 ==========

    @Test
    void adminCanAddMissedClueManually() {
        loginAs(admin);
        MaskingMapResponse mapping = maskingService.addManualClue(
                scriptId, "NAME", "DESCRIPTION", "李小花", -1L);
        assertTrue(mapping.getClues().stream().anyMatch(
                c -> "李小花".equals(c.getOriginalText()) && "CONFIRMED".equals(c.getStatus())));

        loginAs(judge);
        ScriptResponse masked = scriptService.getScriptById(scriptId).orElseThrow();
        assertFalse(masked.getDescription().contains("李小花"), "手工补充的线索应立即脱敏");
        // 原文不存在的内容不允许补充
        loginAs(admin);
        assertThrows(IllegalArgumentException.class,
                () -> maskingService.addManualClue(scriptId, "NAME", "DESCRIPTION", "不存在的人", null));
    }
}
