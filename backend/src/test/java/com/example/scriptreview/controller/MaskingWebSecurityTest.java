package com.example.scriptreview.controller;

import com.example.scriptreview.config.JwtTokenProvider;
import com.example.scriptreview.dto.request.CreateScriptRequest;
import com.example.scriptreview.entity.User;
import com.example.scriptreview.repository.AuditLogRepository;
import com.example.scriptreview.repository.IdentityClueRepository;
import com.example.scriptreview.repository.RecusalApplicationRepository;
import com.example.scriptreview.repository.ScoreRepository;
import com.example.scriptreview.repository.ScriptAssignmentRepository;
import com.example.scriptreview.repository.ScriptIdentityRepository;
import com.example.scriptreview.repository.ScriptRepository;
import com.example.scriptreview.repository.UserRepository;
import com.example.scriptreview.service.ScriptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 双盲脱敏的 Web 层验收：评委/查看员可到达的任何入口（列表、详情、篡改编号、
 * 直接访问映射地址）都拿不到原始身份字段；管理员可见原稿且查看映射留痕。
 */
@SpringBootTest
@AutoConfigureMockMvc
class MaskingWebSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private ScriptRepository scriptRepository;
    @Autowired private ScriptIdentityRepository identityRepository;
    @Autowired private IdentityClueRepository clueRepository;
    @Autowired private ScoreRepository scoreRepository;
    @Autowired private ScriptAssignmentRepository assignmentRepository;
    @Autowired private RecusalApplicationRepository recusalRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private ScriptService scriptService;

    private String adminToken;
    private String judgeToken;
    private String viewerToken;
    private Long scriptId;

    private static final String AUTHOR = "张三";
    private static final String ORG = "星光文化传媒有限公司";
    private static final String PHONE = "13812345678";
    private static final String EMAIL = "zhangsan@food.com";

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

        User admin = userRepository.save(User.builder().username("wAdmin").password("x")
                .name("管理员").role("ADMIN").status("ACTIVE").build());
        User judge = userRepository.save(User.builder().username("wJudge").password("x")
                .name("评委").role("JUDGE").status("ACTIVE").build());
        User viewer = userRepository.save(User.builder().username("wViewer").password("x")
                .name("查看员").role("VIEWER").status("ACTIVE").build());
        adminToken = jwtTokenProvider.generateToken(admin.getId(), admin.getUsername(), "ADMIN");
        judgeToken = jwtTokenProvider.generateToken(judge.getId(), judge.getUsername(), "JUDGE");
        viewerToken = jwtTokenProvider.generateToken(viewer.getId(), viewer.getUsername(), "VIEWER");

        scriptId = scriptService.createScript(CreateScriptRequest.builder()
                .title("深夜食堂：张三的小店").genre("美食").duration(90).targetAudience("青年")
                .description("联系人：张三，电话 " + PHONE + "，邮箱 " + EMAIL + "。剧情人物李小花是常客。")
                .attachmentNote("分镜表见附件，联系电话 " + PHONE)
                .author(AUTHOR).source(ORG)
                .build()).getId();
    }

    // ========== 评委详情：响应不携带任何原始身份字段 ==========

    @Test
    void judgeDetailResponseCarriesNoOriginalIdentity() throws Exception {
        mockMvc.perform(get("/api/scripts/" + scriptId)
                        .header("Authorization", "Bearer " + judgeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.masked").value(true))
                .andExpect(content().string(not(containsString(AUTHOR))))
                .andExpect(content().string(not(containsString(ORG))))
                .andExpect(content().string(not(containsString(PHONE))))
                .andExpect(content().string(not(containsString(EMAIL))))
                .andExpect(content().string(containsString("投稿人·")));
    }

    // ========== 查看员与评委看到完全一致的脱敏结果 ==========

    @Test
    void viewerGetsIdenticalMaskedViewAsJudge() throws Exception {
        MvcResult judgeResult = mockMvc.perform(get("/api/scripts/" + scriptId)
                        .header("Authorization", "Bearer " + judgeToken))
                .andExpect(status().isOk()).andReturn();
        MvcResult viewerResult = mockMvc.perform(get("/api/scripts/" + scriptId)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk()).andReturn();
        assertTrue(viewerResult.getResponse().getContentAsString()
                .equals(judgeResult.getResponse().getContentAsString()),
                "查看员不得获得比评委更多的信息");
    }

    // ========== 列表同样脱敏 ==========

    @Test
    void listIsMaskedForJudgeAndViewer() throws Exception {
        for (String token : new String[]{judgeToken, viewerToken}) {
            mockMvc.perform(get("/api/scripts")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString(AUTHOR))))
                    .andExpect(content().string(not(containsString(ORG))))
                    .andExpect(content().string(not(containsString(PHONE))));
        }
    }

    // ========== 篡改脚本编号/直接访问详情地址：依然只有脱敏结果 ==========

    @Test
    void craftedScriptIdStillReturnsMaskedData() throws Exception {
        Long otherId = scriptService.createScript(CreateScriptRequest.builder()
                .title("另一个投稿").genre("生活").duration(30).targetAudience("青年")
                .description("作者李四亲自出镜，手机 13900002222。")
                .author("李四").source("另一家机构")
                .build()).getId();
        // 评委篡改编号访问他人脚本详情：拿到的仍是脱敏版本
        mockMvc.perform(get("/api/scripts/" + otherId)
                        .header("Authorization", "Bearer " + judgeToken))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("李四"))))
                .andExpect(content().string(not(containsString("13900002222"))))
                .andExpect(content().string(not(containsString("另一家机构"))));
    }

    // ========== 管理员受控详情可见原稿 ==========

    @Test
    void adminKeepsOriginalView() throws Exception {
        mockMvc.perform(get("/api/scripts/" + scriptId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.masked").value(false))
                .andExpect(jsonPath("$.author").value(AUTHOR))
                .andExpect(jsonPath("$.source").value(ORG))
                .andExpect(content().string(containsString(PHONE)));
    }

    // ========== 映射入口仅管理员可达 ==========

    @Test
    void maskingEndpointsAreAdminOnly() throws Exception {
        mockMvc.perform(get("/api/scripts/" + scriptId + "/masking")
                        .header("Authorization", "Bearer " + judgeToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/scripts/" + scriptId + "/masking")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/scripts/" + scriptId + "/masking/clues/1/confirm")
                        .header("Authorization", "Bearer " + judgeToken)
                        .contentType("application/json")
                        .content("{\"decision\":\"CHARACTER\"}"))
                .andExpect(status().isForbidden());
        // 未登录一律拒绝
        mockMvc.perform(get("/api/scripts/" + scriptId + "/masking"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/scripts/" + scriptId))
                .andExpect(status().is4xxClientError());
    }

    // ========== 管理员查看映射：三分区完整且留痕 ==========

    @Test
    void adminMappingViewIsAudited() throws Exception {
        mockMvc.perform(get("/api/scripts/" + scriptId + "/masking")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.original.author").value(AUTHOR))
                .andExpect(jsonPath("$.masked.author").value(containsString("投稿人·")))
                .andExpect(jsonPath("$.clues").isArray())
                .andExpect(jsonPath("$.identities").isArray());

        assertTrue(auditLogRepository.findAll().stream().anyMatch(
                l -> "VIEW_MASKING_MAPPING".equals(l.getAction())
                        && scriptId.equals(l.getTargetId())
                        && "管理员".equals(l.getUserName())),
                "查看映射必须写入审计记录");
    }
}
