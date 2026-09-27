package com.example.scriptreview.service;

import com.example.scriptreview.config.JwtTokenProvider;
import com.example.scriptreview.dto.request.DecideIdentityGroupRequest;
import com.example.scriptreview.dto.response.IdentityMapResponse;
import com.example.scriptreview.dto.response.ScriptRawResponse;
import com.example.scriptreview.dto.response.ScriptResponse;
import com.example.scriptreview.entity.DesensitizationAudit;
import com.example.scriptreview.entity.IdentityGroup;
import com.example.scriptreview.entity.Script;
import com.example.scriptreview.entity.User;
import com.example.scriptreview.repository.DesensitizationAuditRepository;
import com.example.scriptreview.repository.IdentityClueRepository;
import com.example.scriptreview.repository.IdentityGroupRepository;
import com.example.scriptreview.repository.ScriptRepository;
import com.example.scriptreview.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 脚本双盲脱敏视图验收测试，覆盖：
 * 1) 元数据与正文线索识别（姓名/电话/邮箱/账号/机构，整体替换而非半遮）；
 * 2) 同一身份在脚本内稳定替换（正文重现沿用同代号、别名并入同代号）；
 * 3) 相似姓名不合并；
 * 4) 同一投稿人跨脚本代号不可横向关联（每脚本独立盐）；
 * 5) 剧情人物姓名保留、脚本保持可读；
 * 6) 模糊线索先保守隐藏（PENDING），管理员核对后只改脱敏视图不改原稿；
 * 7) 原稿/映射/审计三区与审计追溯；
 * 8) 评委/查看员从任何 HTTP 入口都拿不到原始身份字段（含改编号直接访问详情）；
 * 9) 刷新/重新登录结果一致。
 */
@SpringBootTest
@AutoConfigureMockMvc
class DesensitizationIntegrationTest {

    @Autowired private DesensitizationService desensitizationService;
    @Autowired private IdentityReviewService identityReviewService;
    @Autowired private ScriptService scriptService;
    @Autowired private ScriptRepository scriptRepository;
    @Autowired private IdentityGroupRepository groupRepository;
    @Autowired private IdentityClueRepository clueRepository;
    @Autowired private DesensitizationAuditRepository auditRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private MockMvc mockMvc;

    private User admin;
    private User judge;
    private User viewer;
    private String adminToken;
    private String judgeToken;
    private String viewerToken;

    private static final String BODY =
            "编剧刘雨桐根据亲身经历创作。刘雨桐在出租屋接到妈妈电话：“雨桐啊，不行就回家。”" +
            "剧中人物老李是卖煎饼的摊主，每天和顾客小芳开玩笑；室友阿杰劝她转行。" +
            "合作联系微信 liuyutong_vlog，电话 13912345678，备用 010-86551234，" +
            "邮箱 yutong.liu@example.com，机构成都微光文化传媒有限公司。";
    private static final String CONTACT =
            "联系人：刘雨桐 手机：13912345678 邮箱：yutong.liu@example.com 抖音号：liuyutong_vlog";
    private static final String ATTACHMENT = "授权书：成都微光文化传媒有限公司盖章。下载 pan.example.com/u/liutong2026";

    @BeforeEach
    void setUp() {
        auditRepository.deleteAll();
        clueRepository.deleteAll();
        groupRepository.deleteAll();
        scriptRepository.deleteAll();
        userRepository.deleteAll();

        admin = userRepository.save(User.builder().username("maskAdmin").password("x")
                .name("管理员").role("ADMIN").status("ACTIVE").build());
        judge = userRepository.save(User.builder().username("maskJudge").password("x")
                .name("张评委").role("JUDGE").status("ACTIVE").build());
        viewer = userRepository.save(User.builder().username("maskViewer").password("x")
                .name("查看员").role("VIEWER").status("ACTIVE").build());
        adminToken = jwtTokenProvider.generateToken(admin.getId(), admin.getUsername(), "ADMIN");
        judgeToken = jwtTokenProvider.generateToken(judge.getId(), judge.getUsername(), "JUDGE");
        viewerToken = jwtTokenProvider.generateToken(viewer.getId(), viewer.getUsername(), "VIEWER");
    }

    private Script richScript(String title, String author, String salt) {
        return scriptRepository.save(Script.builder()
                .title(title).genre("生活").duration(90).targetAudience("青年")
                .author(author).source("成都微光文化传媒有限公司")
                .contactInfo(CONTACT).description(BODY).attachmentNote(ATTACHMENT)
                .maskSalt(salt).status("REVIEWING").build());
    }

    // ========== 1. 元数据与正文线索识别 + 整体替换 ==========

    @Test
    void metadataAndBodyCluesAreDetectedAndFullyReplaced() {
        Script script = richScript("刘雨桐的北漂日记", "刘雨桐", "AAAA0001");

        ScriptResponse masked = scriptService.getScriptById(script.getId()).orElseThrow();

        // 响应中任何位置都不出现原始姓名/电话/邮箱/账号/机构
        String joined = String.join("|", masked.getTitle(), nullToEmpty(masked.getDescription()),
                nullToEmpty(masked.getAttachmentNote()));
        assertDoesNotContainIdentity(joined);
        // 电话/邮箱整体替换，而不是遮住一半：不得残留任何 11 位手机号段
        assertFalse(joined.contains("13912"), "手机号只能整体替换，不得残留前段数字");
        assertFalse(joined.contains("example.com"), "邮箱必须整体替换");
        assertFalse(joined.contains("86551"), "固话必须整体替换");

        // 识别结果包含全部预期类型
        List<IdentityGroup> groups = groupRepository.findByScriptIdOrderByLabelLetterAscIdAsc(script.getId());
        assertTrue(groups.stream().anyMatch(g -> g.getClueType().equals("NAME")));
        assertTrue(groups.stream().anyMatch(g -> g.getClueType().equals("PHONE")));
        assertTrue(groups.stream().anyMatch(g -> g.getClueType().equals("EMAIL")));
        assertTrue(groups.stream().anyMatch(g -> g.getClueType().equals("ACCOUNT")));
        assertTrue(groups.stream().anyMatch(g -> g.getClueType().equals("ORGANIZATION")));

        // 原稿身份字段不在脱敏响应结构中（序列化后也不会出现）
        assertNull(readField(masked, "author"));
        assertNull(readField(masked, "source"));
        assertNull(readField(masked, "contactInfo"));
        assertTrue((Boolean) readField(masked, "masked"));
    }

    // ========== 2. 同一身份稳定替换 + 别名并入 ==========

    @Test
    void sameIdentityUsesOneStableCodeAcrossAllOccurrences() {
        Script script = richScript("编剧刘雨桐作品：刘雨桐", "刘雨桐", "BBBB0002");
        ScriptResponse first = scriptService.getScriptById(script.getId()).orElseThrow();
        ScriptResponse second = scriptService.getScriptById(script.getId()).orElseThrow();

        IdentityGroup nameGroup = groupRepository.findByScriptIdOrderByLabelLetterAscIdAsc(script.getId())
                .stream().filter(g -> g.getClueType().equals("NAME")).findFirst().orElseThrow();
        String expectedCode = desensitizationService.codeToken(nameGroup, script.getMaskSalt());

        // 标题中“刘雨桐”出现两次，正文中全名与两字名“雨桐”出现多次，全部使用同一代号
        long titleCount = countOccurrences(first.getTitle(), expectedCode);
        assertTrue(titleCount >= 2, "标题中重复姓名应沿用同一代号");
        assertTrue(first.getDescription().contains(expectedCode), "正文姓名应使用同一代号");
        // 两字别名“雨桐”也被同一代号替换，不能残留可猜测的片段
        assertFalse(first.getDescription().contains("雨桐"), "两字名别名必须并入作者代号");
        // 刷新后代号一致
        assertEquals(first.getTitle(), second.getTitle());
        assertEquals(first.getDescription(), second.getDescription());
    }

    // ========== 3. 相似姓名不合并 ==========

    @Test
    void similarNamesAreNotMerged() {
        Script script = scriptRepository.save(Script.builder()
                .title("测试").genre("生活").duration(60).targetAudience("青年")
                .author("张磊").source("内部投稿").maskSalt("CCCC0003")
                .description("编剧张磊投稿。联系人说明里提到张某是制片，张蕾是另一位编剧（署名见尾）。")
                .status("PENDING").build());
        // 触发识别
        scriptService.getScriptById(script.getId());

        List<IdentityGroup> nameGroups = groupRepository
                .findByScriptIdOrderByLabelLetterAscIdAsc(script.getId()).stream()
                .filter(g -> g.getClueType().equals("NAME")).toList();
        // 张磊（作者）/张某/张蕾 是三个不同名字，归一化精确相等才合并，不能因同姓/形近而合并
        assertEquals(3, nameGroups.size(), "相似姓名必须分成不同身份，实际：" + nameGroups.size());
    }

    // ========== 4. 跨脚本不可横向关联 ==========

    @Test
    void sameSubmitterGetsDifferentUnlinkableCodesAcrossScripts() {
        Script s1 = richScript("刘雨桐的北漂日记", "刘雨桐", "SALT0001");
        Script s2 = scriptRepository.save(Script.builder()
                .title("刘雨桐的短片").genre("生活").duration(60).targetAudience("青年")
                .author("刘雨桐").source("内部投稿").description("编剧刘雨桐新作。")
                .maskSalt("SALT9999").status("PENDING").build());

        ScriptResponse m1 = scriptService.getScriptById(s1.getId()).orElseThrow();
        ScriptResponse m2 = scriptService.getScriptById(s2.getId()).orElseThrow();

        String nameCode1 = extractFirstCode(m1.getTitle());
        String nameCode2 = extractFirstCode(m2.getTitle());
        assertNotNull(nameCode1);
        assertNotNull(nameCode2);
        assertNotEquals(nameCode1, nameCode2, "同一投稿人在不同脚本中的代号必须不同，无法横向关联");
        assertTrue(nameCode1.contains("SALT0001"));
        assertTrue(nameCode2.contains("SALT9999"));
    }

    // ========== 5. 剧情人物保留、可读性不破坏 ==========

    @Test
    void dramaCharacterNamesAreKeptAndScriptStaysReadable() {
        Script script = richScript("北漂日记", "刘雨桐", "DDDD0004");
        ScriptResponse masked = scriptService.getScriptById(script.getId()).orElseThrow();

        // 剧情人物姓名原样保留（它们不是投稿身份线索）
        assertTrue(masked.getDescription().contains("老李"), "剧中人物老李应保留原文");
        assertTrue(masked.getDescription().contains("小芳"), "顾客小芳应保留原文");
        assertTrue(masked.getDescription().contains("阿杰"), "室友阿杰应保留原文");
        // 投稿身份不出现
        assertFalse(masked.getDescription().contains("刘雨桐"));
        // 正文长度与结构未被“把所有人名一律删除”破坏
        assertTrue(masked.getDescription().length() > 100);
        assertTrue(masked.getDescription().contains("卖煎饼"));

        List<IdentityGroup> characters = groupRepository
                .findByScriptIdOrderByLabelLetterAscIdAsc(script.getId()).stream()
                .filter(g -> g.getStatus().equals(DesensitizationService.STATUS_CHARACTER)).toList();
        assertTrue(characters.size() >= 3, "老李/小芳/阿杰应被识别为剧情人物分组");
    }

    // ========== 6. 模糊线索先保守隐藏 + 管理员核对只改视图不改原稿 ==========

    @Test
    void uncertainNameIsHiddenBeforeReviewAndDecisionOnlyChangesMaskedView() {
        // “周宁”没有任何语境，无法确定是投稿人还是剧中角色
        Script script = scriptRepository.save(Script.builder()
                .title("测试脚本").genre("生活").duration(60).targetAudience("青年")
                .author("陈明").source("内部投稿").maskSalt("EEEE0005")
                .description("编剧陈明投稿。走廊上周宁提着咖啡走过来，两人相视一笑。")
                .status("PENDING").build());

        ScriptResponse before = scriptService.getScriptById(script.getId()).orElseThrow();
        // 核对完成前必须先隐藏可疑片段，不能先泄露再补救
        assertFalse(before.getDescription().contains("周宁"), "待核对姓名在评委侧必须先隐藏");
        assertTrue(before.getPendingCount() >= 1);

        IdentityGroup pending = groupRepository.findByScriptIdOrderByLabelLetterAscIdAsc(script.getId())
                .stream().filter(g -> g.getRepresentative().equals("周宁")).findFirst().orElseThrow();
        assertEquals(DesensitizationService.STATUS_PENDING, pending.getStatus());

        // 管理员核对为剧情人物 -> 脱敏视图保留原名
        identityReviewService.decideGroup(script.getId(), pending.getId(),
                DecideIdentityGroupRequest.builder().decision(DesensitizationService.STATUS_CHARACTER).build(),
                admin, "127.0.0.1");
        ScriptResponse afterChar = scriptService.getScriptById(script.getId()).orElseThrow();
        assertTrue(afterChar.getDescription().contains("周宁"));

        // 原稿没有被改写
        Script raw = scriptRepository.findById(script.getId()).orElseThrow();
        assertTrue(raw.getDescription().contains("周宁"));
        assertEquals("陈明", raw.getAuthor());

        // 改判为投稿身份 -> 代号替换，原稿仍不变
        identityReviewService.decideGroup(script.getId(), pending.getId(),
                DecideIdentityGroupRequest.builder().decision(DesensitizationService.STATUS_IDENTITY).build(),
                admin, "127.0.0.1");
        ScriptResponse afterIdentity = scriptService.getScriptById(script.getId()).orElseThrow();
        assertFalse(afterIdentity.getDescription().contains("周宁"));
        assertTrue(scriptRepository.findById(script.getId()).orElseThrow().getDescription().contains("周宁"));

        // 审计记录：两次核对各一条 DECIDE_GROUP
        List<DesensitizationAudit> audits = auditRepository.findAll();
        long decides = audits.stream().filter(a -> a.getAction().equals("DECIDE_GROUP")).count();
        assertEquals(2, decides);
        assertTrue(audits.stream().anyMatch(a -> a.getViewerName().equals("管理员")));

        // 再显式查看映射，才追加 VIEW_MAP 审计
        identityReviewService.viewMap(script.getId(), admin, "127.0.0.9");
        assertTrue(auditRepository.findAll().stream().anyMatch(a -> a.getAction().equals("VIEW_MAP")));
    }

    // ========== 7. 管理员原稿/映射/脱敏三区 + 查看审计 ==========

    @Test
    void adminRawAndMapAreSeparatedAndEveryViewIsAudited() {
        Script script = richScript("刘雨桐的北漂日记", "刘雨桐", "FFFF0006");
        ScriptRawResponse raw = identityReviewService.viewRaw(script.getId(), admin, "10.0.0.1");
        assertEquals("刘雨桐", raw.getAuthor());
        assertTrue(raw.getContactInfo().contains("13912345678"));
        assertTrue(raw.getDescription().contains("老李"));

        IdentityMapResponse map = identityReviewService.viewMap(script.getId(), admin, "10.0.0.2");
        // 三区分明
        assertEquals("刘雨桐", map.getRaw().getAuthor());
        assertFalse(map.getGroups().isEmpty());
        assertFalse(map.getMasked().getDescription().contains("刘雨桐"));
        assertTrue(map.getMasked().getDescription().contains("老李"));

        List<DesensitizationAudit> audits = auditRepository.findAll();
        assertTrue(audits.stream().anyMatch(a -> a.getAction().equals("VIEW_RAW")
                && a.getIpAddress().equals("10.0.0.1")));
        assertTrue(audits.stream().anyMatch(a -> a.getAction().equals("VIEW_MAP")
                && a.getIpAddress().equals("10.0.0.2")));

        // 评委无权查看原稿/映射
        assertThrows(AccessDeniedException.class,
                () -> identityReviewService.viewRaw(script.getId(), judge, null));
        assertThrows(AccessDeniedException.class,
                () -> identityReviewService.viewMap(script.getId(), viewer, null));
    }

    // ========== 8. HTTP 层：评委/查看员从任何入口都拿不到原文 ==========

    @Test
    void judgeAndViewerHttpResponsesNeverCarryRawIdentity() throws Exception {
        Script script = richScript("刘雨桐的北漂日记", "刘雨桐", "GGGG0007");

        for (String token : List.of(judgeToken, viewerToken)) {
            // 直接访问详情地址（含篡改编号无效化：未登录/越权 403）
            MvcResult detail = mockMvc.perform(get("/api/scripts/" + script.getId())
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andReturn();
            String body = detail.getResponse().getContentAsString();
            assertFalse(body.contains("刘雨桐"), token + " 详情泄露作者");
            assertFalse(body.contains("13912345678"), token + " 详情泄露电话");
            assertFalse(body.contains("yutong.liu"), token + " 详情泄露邮箱");
            assertFalse(body.contains("成都微光"), token + " 详情泄露机构");
            assertFalse(body.contains("contactInfo"), token + " 响应结构不得包含联系方式字段");
            assertFalse(body.contains("\"author\""), token + " 响应结构不得包含原始作者字段");
            assertFalse(body.contains("\"source\""), token + " 响应结构不得包含原始来源字段");

            // 列表同样干净
            MvcResult list = mockMvc.perform(get("/api/scripts")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andReturn();
            assertFalse(list.getResponse().getContentAsString().contains("刘雨桐"));

            // 受控原稿 / 映射入口一律 403，改路径或直接访问都无效
            mockMvc.perform(get("/api/scripts/" + script.getId() + "/raw")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/scripts/" + script.getId() + "/identity-map")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/scripts/" + script.getId()
                            + "/identity-groups/1/decision")
                            .header("Authorization", "Bearer " + token)
                            .contentType("application/json")
                            .content("{\"decision\":\"CONFIRMED_IDENTITY\"}"))
                    .andExpect(status().isForbidden());
            // 写操作禁止
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .delete("/api/scripts/" + script.getId())
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }

        // 未认证请求同样拿不到任何正文
        mockMvc.perform(get("/api/scripts/" + script.getId()))
                .andExpect(status().is4xxClientError());

        // 管理员可以走受控入口看原稿，且该次查看留下审计
        mockMvc.perform(get("/api/scripts/" + script.getId() + "/raw")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        assertTrue(auditRepository.findAll().stream()
                .anyMatch(a -> a.getAction().equals("VIEW_RAW") && a.getViewerId().equals(admin.getId())));
    }

    // ========== 9. 跨出口：回避/分配/统计页面的标题也脱敏 ==========

    @Test
    void otherOutletsTitlesAreMaskedForJudge() {
        Script script = richScript("刘雨桐的北漂日记", "刘雨桐", "HHHH0008");
        String judgeTitle = desensitizationService.maskTitleFor(
                scriptRepository.findById(script.getId()).orElseThrow(), judge);
        String adminTitle = desensitizationService.maskTitleFor(
                scriptRepository.findById(script.getId()).orElseThrow(), admin);

        assertFalse(judgeTitle.contains("刘雨桐"));
        IdentityGroup nameGroup = groupRepository.findByScriptIdOrderByLabelLetterAscIdAsc(script.getId())
                .stream().filter(g -> g.getClueType().equals("NAME")).findFirst().orElseThrow();
        assertTrue(judgeTitle.contains(desensitizationService.codeToken(nameGroup, "HHHH0008")));
        assertEquals("刘雨桐的北漂日记", adminTitle);
    }

    // ========== 10. 原稿更新后重新识别，核对结论在身份仍存在时沿用 ==========

    @Test
    void editingOriginalTriggersReanalysisAndStableCodesSurvive() {
        Script script = richScript("刘雨桐的北漂日记", "刘雨桐", "IIII0009");
        ScriptResponse first = scriptService.getScriptById(script.getId()).orElseThrow();
        String firstNameCode = extractFirstCode(first.getTitle());

        // 管理员改正文（不改作者），代号保持稳定，新电话被补充识别
        Script entity = scriptRepository.findById(script.getId()).orElseThrow();
        entity.setDescription(BODY + " 新号码 13700001234。");
        scriptRepository.save(entity);
        ScriptResponse second = scriptService.getScriptById(script.getId()).orElseThrow();

        assertEquals(firstNameCode, extractFirstCode(second.getTitle()), "作者代号在原稿更新后保持稳定");
        assertFalse(second.getDescription().contains("13700001234"), "新出现的电话必须被识别并整体替换");
        // 原稿、脱敏视图、身份映射都指向同一脚本
        assertEquals(script.getId(),
                identityReviewService.viewMap(script.getId(), admin, null).getScriptId());
    }

    // ========== helpers ==========

    private void assertDoesNotContainIdentity(String text) {
        assertFalse(text.contains("刘雨桐"), "泄露作者姓名");
        assertFalse(text.contains("13912345678"), "泄露手机号");
        assertFalse(text.contains("yutong.liu@example.com"), "泄露邮箱");
        assertFalse(text.contains("liuyutong_vlog"), "泄露社交账号");
        assertFalse(text.contains("成都微光文化传媒有限公司"), "泄露机构全称");
        assertFalse(text.contains("pan.example.com"), "泄露带账号名的链接");
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private Object readField(ScriptResponse response, String field) {
        try {
            return ScriptResponse.class.getDeclaredField(field).get(response);
        } catch (NoSuchFieldException e) {
            return null; // 字段不存在即满足“响应不携带”
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private long countOccurrences(String text, String token) {
        if (text == null) return 0;
        int idx = 0;
        long count = 0;
        while ((idx = text.indexOf(token, idx)) >= 0) {
            count++;
            idx += token.length();
        }
        return count;
    }

    private static final Pattern CODE = Pattern.compile("【[^】]+】");

    private String extractFirstCode(String text) {
        if (text == null) return null;
        Matcher m = CODE.matcher(text);
        return m.find() ? m.group() : null;
    }
}
