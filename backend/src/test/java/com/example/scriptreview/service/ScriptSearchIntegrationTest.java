package com.example.scriptreview.service;

import com.example.scriptreview.dto.response.ScriptResponse;
import com.example.scriptreview.entity.Script;
import com.example.scriptreview.repository.RecusalApplicationRepository;
import com.example.scriptreview.repository.ScoreRepository;
import com.example.scriptreview.repository.ScriptAssignmentRepository;
import com.example.scriptreview.repository.ScriptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 脚本列表组合筛选与稳定分页验收测试：
 * 单条件、任意两项组合、三项组合、清空部分条件、零结果、越界页、
 * 相同创建时间、浏览期间新增脚本、快速连续切换筛选。
 */
@SpringBootTest
class ScriptSearchIntegrationTest {

    @Autowired private ScriptService scriptService;
    @Autowired private ScriptRepository scriptRepository;
    @Autowired private ScoreRepository scoreRepository;
    @Autowired private ScriptAssignmentRepository assignmentRepository;
    @Autowired private RecusalApplicationRepository recusalRepository;

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 1, 1, 10, 0, 0);

    /** 全部测试数据，按保存顺序记录 */
    private final List<Script> all = new ArrayList<>();

    @BeforeEach
    void setUp() {
        recusalRepository.deleteAll();
        scoreRepository.deleteAll();
        assignmentRepository.deleteAll();
        scriptRepository.deleteAll();
        all.clear();
    }

    private Script save(String title, String genre, String status, String grade, LocalDateTime createdAt) {
        Script script = scriptRepository.save(Script.builder()
                .title(title).genre(genre).duration(60).targetAudience("青年")
                .maskSalt("SRCH" + Math.abs(title.hashCode()))
                .status(status).grade(grade)
                .createdAt(createdAt).updatedAt(createdAt)
                .build());
        all.add(script);
        return script;
    }

    /** 标准数据矩阵：覆盖状态×等级×题材的各种交集 */
    private void seedMatrix() {
        save("情感-待评审", "情感", "PENDING", null, BASE.plusMinutes(1));
        save("情感-已分级S", "情感", "GRADED", "S", BASE.plusMinutes(2));
        save("美食-已分级S", "美食", "GRADED", "S", BASE.plusMinutes(3));
        save("美食-评审中", "美食", "REVIEWING", null, BASE.plusMinutes(4));
        save("情感-已分级A", "情感", "GRADED", "A", BASE.plusMinutes(5));
        save("科技-待评审", "科技", "PENDING", null, BASE.plusMinutes(6));
    }

    /** 期望结果：内存中按条件过滤后，按列表固定排序（创建时间升序 + 主键升序）排列 */
    private List<Long> expectedIds(String status, String grade, String genre) {
        return all.stream()
                .filter(s -> status == null || status.equals(s.getStatus()))
                .filter(s -> grade == null || grade.equals(s.getGrade()))
                .filter(s -> genre == null || genre.equals(s.getGenre()))
                .sorted(Comparator.comparing(Script::getCreatedAt).thenComparing(Script::getId))
                .map(Script::getId)
                .toList();
    }

    /** 逐页拉取全部结果并拼接，用于校验跨页顺序与完整性 */
    private List<Long> collectAllPages(String status, String grade, String genre, int size) {
        List<Long> ids = new ArrayList<>();
        int pageIndex = 0;
        Page<ScriptResponse> page;
        do {
            page = scriptService.searchScripts(status, grade, genre, pageIndex, size);
            assertTrue(page.getContent().size() <= size, "单页条数不能超过页大小");
            page.getContent().forEach(s -> ids.add(s.getId()));
            pageIndex++;
        } while (pageIndex < page.getTotalPages());
        return ids;
    }

    /** 校验单页响应：每条记录都满足全部条件，且总数/总页数与同一组条件一致 */
    private void assertPageConsistent(Page<ScriptResponse> page, String status, String grade, String genre, int size) {
        for (ScriptResponse item : page.getContent()) {
            if (status != null) assertEquals(status, item.getStatus(), "混入了不满足状态条件的脚本");
            if (grade != null) assertEquals(grade, item.getGrade(), "混入了不满足等级条件的脚本");
            if (genre != null) assertEquals(genre, item.getGenre(), "混入了不满足题材条件的脚本");
        }
        long expectedTotal = expectedIds(status, grade, genre).size();
        assertEquals(expectedTotal, page.getTotalElements(), "总数必须与筛选条件一致");
        assertEquals((int) Math.ceil(expectedTotal / (double) size), page.getTotalPages(),
                "总页数必须与总数、页大小一致");
    }

    // ========== 单条件筛选 ==========

    @Test
    void filterByStatusOnly() {
        seedMatrix();
        Page<ScriptResponse> page = scriptService.searchScripts("GRADED", null, null, 0, 10);
        assertPageConsistent(page, "GRADED", null, null, 10);
        assertEquals(3, page.getTotalElements());
        assertEquals(expectedIds("GRADED", null, null),
                page.getContent().stream().map(ScriptResponse::getId).toList());
    }

    @Test
    void filterByGradeOnly() {
        seedMatrix();
        Page<ScriptResponse> page = scriptService.searchScripts(null, "S", null, 0, 10);
        assertPageConsistent(page, null, "S", null, 10);
        assertEquals(2, page.getTotalElements());
    }

    @Test
    void filterByGenreOnly() {
        seedMatrix();
        Page<ScriptResponse> page = scriptService.searchScripts(null, null, "情感", 0, 10);
        assertPageConsistent(page, null, null, "情感", 10);
        assertEquals(3, page.getTotalElements());
    }

    // ========== 任意两项组合 ==========

    @Test
    void filterByStatusAndGrade() {
        seedMatrix();
        // 修复前该场景只应用 status 或 grade 之一
        Page<ScriptResponse> page = scriptService.searchScripts("GRADED", "S", null, 0, 10);
        assertPageConsistent(page, "GRADED", "S", null, 10);
        assertEquals(2, page.getTotalElements());
        assertEquals(expectedIds("GRADED", "S", null), page.getContent().stream().map(ScriptResponse::getId).toList());
    }

    @Test
    void filterByStatusAndGenre() {
        seedMatrix();
        Page<ScriptResponse> page = scriptService.searchScripts("PENDING", null, "科技", 0, 10);
        assertPageConsistent(page, "PENDING", null, "科技", 10);
        assertEquals(1, page.getTotalElements());
    }

    @Test
    void filterByGradeAndGenre() {
        seedMatrix();
        Page<ScriptResponse> page = scriptService.searchScripts(null, "S", "美食", 0, 10);
        assertPageConsistent(page, null, "S", "美食", 10);
        assertEquals(1, page.getTotalElements());
    }

    // ========== 三项组合 ==========

    @Test
    void filterByStatusGradeAndGenre() {
        seedMatrix();
        Page<ScriptResponse> page = scriptService.searchScripts("GRADED", "S", "情感", 0, 10);
        assertPageConsistent(page, "GRADED", "S", "情感", 10);
        assertEquals(1, page.getTotalElements());
        assertEquals("情感-已分级S", page.getContent().get(0).getTitle());
    }

    // ========== 清空部分条件，保留其余条件 ==========

    @Test
    void clearingOneFilterKeepsTheOthers() {
        seedMatrix();
        // 三项组合 -> 清空题材 -> 再清空等级 -> 再清空状态，每步结果必须只由剩余条件决定
        Page<ScriptResponse> triple = scriptService.searchScripts("GRADED", "S", "情感", 0, 10);
        assertEquals(expectedIds("GRADED", "S", "情感"),
                triple.getContent().stream().map(ScriptResponse::getId).toList());

        Page<ScriptResponse> withoutGenre = scriptService.searchScripts("GRADED", "S", null, 0, 10);
        assertEquals(expectedIds("GRADED", "S", null),
                withoutGenre.getContent().stream().map(ScriptResponse::getId).toList());
        assertEquals(2, withoutGenre.getTotalElements());

        Page<ScriptResponse> withoutGrade = scriptService.searchScripts("GRADED", null, null, 0, 10);
        assertEquals(expectedIds("GRADED", null, null),
                withoutGrade.getContent().stream().map(ScriptResponse::getId).toList());
        assertEquals(3, withoutGrade.getTotalElements());

        Page<ScriptResponse> allCleared = scriptService.searchScripts(null, null, null, 0, 10);
        assertEquals(expectedIds(null, null, null),
                allCleared.getContent().stream().map(ScriptResponse::getId).toList());
        assertEquals(6, allCleared.getTotalElements());
    }

    @Test
    void blankFiltersAreTreatedAsAbsent() {
        seedMatrix();
        // 空串/空白串等价于未设置该条件（前端清空筛选项后可能传空串）
        Page<ScriptResponse> page = scriptService.searchScripts("  ", "", null, 0, 10);
        assertEquals(6, page.getTotalElements());
    }

    // ========== 零结果 ==========

    @Test
    void zeroResultsReturnEmptyPageNotError() {
        seedMatrix();
        Page<ScriptResponse> page = scriptService.searchScripts("PENDING", "S", "美食", 0, 10);
        assertTrue(page.getContent().isEmpty());
        assertEquals(0, page.getTotalElements());
        assertEquals(0, page.getTotalPages());

        // 零结果时请求越界页也不应抛异常，仍是明确的空结果
        Page<ScriptResponse> outOfRange = scriptService.searchScripts("PENDING", "S", "美食", 5, 10);
        assertTrue(outOfRange.getContent().isEmpty());
        assertEquals(0, outOfRange.getTotalElements());
    }

    // ========== 越界页回退到最后一个有效页 ==========

    @Test
    void outOfRangePageFallsBackToLastValidPage() {
        for (int i = 0; i < 7; i++) {
            save("脚本" + i, "情感", "PENDING", null, BASE.plusMinutes(i));
        }
        // 7 条 / 每页 3 条 -> 共 3 页，请求第 10 页（下标 9）
        Page<ScriptResponse> page = scriptService.searchScripts(null, null, null, 9, 3);
        assertEquals(2, page.getNumber(), "越界页必须回退到最后一个有效页（下标 2）");
        assertEquals(7, page.getTotalElements());
        assertEquals(3, page.getTotalPages());
        assertEquals(1, page.getContent().size());
        assertEquals(expectedIds(null, null, null).get(6), page.getContent().get(0).getId());

        // 负页码按第一页处理
        Page<ScriptResponse> negative = scriptService.searchScripts(null, null, null, -3, 3);
        assertEquals(0, negative.getNumber());
        assertEquals(3, negative.getContent().size());
    }

    // ========== 相同创建时间：跨页顺序稳定、不重复不遗漏 ==========

    @Test
    void sameCreatedAtKeepsStableOrderAcrossPages() {
        LocalDateTime same = BASE;
        for (int i = 0; i < 7; i++) {
            save("同时创建" + i, "情感", "PENDING", null, same);
        }
        List<Long> expected = expectedIds(null, null, null);
        // 主键升序决胜，顺序确定
        List<Long> firstRun = collectAllPages(null, null, null, 3);
        List<Long> secondRun = collectAllPages(null, null, null, 3);
        assertEquals(expected, firstRun, "相同创建时间时必须按主键决胜，顺序确定");
        assertEquals(firstRun, secondRun, "重复查询顺序必须一致");
        assertEquals(7, firstRun.stream().distinct().count(), "跨页不得出现重复记录");
    }

    // ========== 浏览期间新增脚本：已存在记录不在相邻页面间重复或漏掉 ==========

    @Test
    void insertDuringBrowsingDoesNotDuplicateOrSkipExistingRecords() {
        for (int i = 0; i < 6; i++) {
            save("存量" + i, "情感", "PENDING", null, BASE.plusMinutes(i));
        }
        List<Long> expected = expectedIds(null, null, null);

        // 用户浏览第一页（每页 4 条）
        Page<ScriptResponse> page1 = scriptService.searchScripts(null, null, null, 0, 4);
        assertEquals(expected.subList(0, 4),
                page1.getContent().stream().map(ScriptResponse::getId).toList());

        // 浏览过程中他人录入新脚本（创建时间最新，追加到末尾）
        Script newcomer = save("新增脚本", "情感", "PENDING", null, BASE.plusDays(30));

        // 用户翻到第二页：存量记录位置不变，不重复（不含第一页最后一条）也不漏掉
        Page<ScriptResponse> page2 = scriptService.searchScripts(null, null, null, 1, 4);
        assertEquals(expected.subList(4, 6),
                page2.getContent().stream().limit(2).map(ScriptResponse::getId).toList(),
                "新增脚本不得把已存在记录挤出或重复进相邻页面");
        assertEquals(List.of(newcomer.getId()),
                page2.getContent().stream().skip(2).map(ScriptResponse::getId).toList(),
                "创建时间最新的脚本应追加在当前最后一页末尾");

        // 当前总数只有 7 条，请求第三页时应回退到最后一个有效页
        Page<ScriptResponse> page3 = scriptService.searchScripts(null, null, null, 2, 4);
        assertEquals(1, page3.getNumber());
        assertEquals(page2.getContent().stream().map(ScriptResponse::getId).toList(),
                page3.getContent().stream().map(ScriptResponse::getId).toList());
    }

    // ========== 快速连续切换筛选：每个响应都只由自己的条件决定 ==========

    @Test
    void rapidFilterSwitchingKeepsEveryResponseConsistent() {
        seedMatrix();
        String[][] switches = {
                {"GRADED", null, null},
                {null, "S", null},
                {null, null, "情感"},
                {"GRADED", "S", null},
                {"PENDING", null, "科技"},
                {null, null, null},
                {"GRADED", "S", "情感"},
        };
        // 模拟用户快速连续切换筛选：每个请求的列表内容、总数、页数必须来自同一组条件
        for (int round = 0; round < 3; round++) {
            for (String[] f : switches) {
                String status = f[0], grade = f[1], genre = f[2];
                Page<ScriptResponse> page = scriptService.searchScripts(status, grade, genre, 0, 2);
                assertPageConsistent(page, status, grade, genre, 2);
                // 逐页拼全集，必须与该条件的期望结果完全一致（不多不少、顺序稳定）
                assertEquals(expectedIds(status, grade, genre),
                        collectAllPages(status, grade, genre, 2));
            }
        }
    }

    // ========== 分页边界：页大小归一化 ==========

    @Test
    void pageSizeIsNormalized() {
        for (int i = 0; i < 3; i++) {
            save("脚本" + i, "情感", "PENDING", null, BASE.plusMinutes(i));
        }
        // 非法页大小回退为默认 10
        Page<ScriptResponse> zeroSize = scriptService.searchScripts(null, null, null, 0, 0);
        assertEquals(10, zeroSize.getSize());
        assertEquals(3, zeroSize.getContent().size());
        // 超大页大小被限制在上限内
        Page<ScriptResponse> hugeSize = scriptService.searchScripts(null, null, null, 0, 100000);
        assertEquals(500, hugeSize.getSize());
        assertEquals(3, hugeSize.getContent().size());
    }
}
