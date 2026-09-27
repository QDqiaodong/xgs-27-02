package com.example.scriptreview.controller;

import com.example.scriptreview.config.JwtTokenProvider;
import com.example.scriptreview.entity.Script;
import com.example.scriptreview.entity.User;
import com.example.scriptreview.repository.AuditLogRepository;
import com.example.scriptreview.repository.RecusalApplicationRepository;
import com.example.scriptreview.repository.ScoreRepository;
import com.example.scriptreview.repository.ScriptAssignmentRepository;
import com.example.scriptreview.repository.ScriptRepository;
import com.example.scriptreview.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 脚本列表接口的 Web 层验收：
 * 组合筛选参数透传、越界页回退结果明确、以及可见范围保持不变——
 * 未认证请求即使篡改查询参数也看不到列表内容，管理员/评委/查看员均可正常访问。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ScriptListSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private ScriptRepository scriptRepository;
    @Autowired private ScoreRepository scoreRepository;
    @Autowired private ScriptAssignmentRepository assignmentRepository;
    @Autowired private RecusalApplicationRepository recusalRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 1, 1, 10, 0, 0);

    private String adminToken;
    private String judgeToken;
    private String viewerToken;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        recusalRepository.deleteAll();
        scoreRepository.deleteAll();
        assignmentRepository.deleteAll();
        scriptRepository.deleteAll();
        userRepository.deleteAll();

        User admin = userRepository.save(User.builder().username("secAdmin").password("x")
                .name("管理员").role("ADMIN").status("ACTIVE").build());
        User judge = userRepository.save(User.builder().username("secJudge").password("x")
                .name("评委").role("JUDGE").status("ACTIVE").build());
        User viewer = userRepository.save(User.builder().username("secViewer").password("x")
                .name("查看员").role("VIEWER").status("ACTIVE").build());
        adminToken = jwtTokenProvider.generateToken(admin.getId(), admin.getUsername(), "ADMIN");
        judgeToken = jwtTokenProvider.generateToken(judge.getId(), judge.getUsername(), "JUDGE");
        viewerToken = jwtTokenProvider.generateToken(viewer.getId(), viewer.getUsername(), "VIEWER");

        saveScript("情感剧本甲", "情感", "GRADED", "S", BASE.plusMinutes(1));
        saveScript("美食剧本乙", "美食", "GRADED", "A", BASE.plusMinutes(2));
        saveScript("情感剧本丙", "情感", "PENDING", null, BASE.plusMinutes(3));
    }

    private void saveScript(String title, String genre, String status, String grade, LocalDateTime createdAt) {
        scriptRepository.save(Script.builder()
                .title(title).genre(genre).duration(60).targetAudience("青年")
                .maskSalt("SECSALT" + title.hashCode())
                .status(status).grade(grade)
                .createdAt(createdAt).updatedAt(createdAt)
                .build());
    }

    // ========== 可见范围：未认证用户不能通过查询参数看到列表内容 ==========

    @Test
    void unauthenticatedRequestIsRejectedEvenWithCraftedParams() throws Exception {
        mockMvc.perform(get("/api/scripts")
                        .param("status", "GRADED")
                        .param("grade", "S")
                        .param("genre", "情感")
                        .param("page", "0")
                        .param("size", "100"))
                .andExpect(status().is4xxClientError())
                .andExpect(content().string(not(containsString("情感剧本甲"))))
                .andExpect(content().string(not(containsString("totalElements"))));
    }

    @Test
    void invalidTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/scripts")
                        .header("Authorization", "Bearer not.a.valid.token"))
                .andExpect(status().is4xxClientError())
                .andExpect(content().string(not(containsString("情感剧本甲"))));
    }

    // ========== 可见范围：管理员/评委/查看员保持原有访问能力 ==========

    @Test
    void allRolesKeepListAccess() throws Exception {
        for (String token : new String[]{adminToken, judgeToken, viewerToken}) {
            mockMvc.perform(get("/api/scripts")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.content.length()").value(3));
        }
    }

    // ========== 组合筛选通过 HTTP 参数同时生效 ==========

    @Test
    void combinedFiltersApplyTogetherOverHttp() throws Exception {
        mockMvc.perform(get("/api/scripts")
                        .header("Authorization", "Bearer " + viewerToken)
                        .param("status", "GRADED")
                        .param("grade", "S")
                        .param("genre", "情感"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content[0].title").value("情感剧本甲"));

        // 任意两项组合
        mockMvc.perform(get("/api/scripts")
                        .header("Authorization", "Bearer " + viewerToken)
                        .param("status", "GRADED")
                        .param("genre", "情感"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // 条件不匹配 -> 明确的空结果而不是错误
        mockMvc.perform(get("/api/scripts")
                        .header("Authorization", "Bearer " + viewerToken)
                        .param("status", "PENDING")
                        .param("grade", "S"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    // ========== 越界页：回退到最后一个有效页，number 为实际页码 ==========

    @Test
    void outOfRangePageReturnsLastValidPage() throws Exception {
        // 3 条 / 每页 2 条 -> 共 2 页，请求第 6 页（下标 5）
        mockMvc.perform(get("/api/scripts")
                        .header("Authorization", "Bearer " + viewerToken)
                        .param("page", "5")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("情感剧本丙"));
    }

    // ========== 分页顺序稳定：创建时间升序 + 主键决胜 ==========

    @Test
    void pagingOrderIsStableAcrossRequests() throws Exception {
        // 第一页
        mockMvc.perform(get("/api/scripts")
                        .header("Authorization", "Bearer " + viewerToken)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("情感剧本甲"))
                .andExpect(jsonPath("$.content[1].title").value("美食剧本乙"));
        // 第二页与第一页无交集
        mockMvc.perform(get("/api/scripts")
                        .header("Authorization", "Bearer " + viewerToken)
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("情感剧本丙"));
    }
}
