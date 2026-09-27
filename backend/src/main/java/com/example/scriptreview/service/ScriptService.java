package com.example.scriptreview.service;

import com.example.scriptreview.dto.request.CreateScriptRequest;
import com.example.scriptreview.dto.response.ScriptResponse;
import com.example.scriptreview.entity.Script;
import com.example.scriptreview.repository.ScoreRepository;
import com.example.scriptreview.repository.ScriptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ScriptService {

    private final ScriptRepository scriptRepository;
    private final ScoreRepository scoreRepository;
    private final DesensitizationService desensitizationService;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 列表固定排序：创建时间升序，主键升序作为相同创建时间的唯一决胜条件。
     * 排序键确定且唯一，分页顺序稳定；新录入的脚本只会追加到末尾页，
     * 浏览过程中他人新增数据不会导致已存在记录在相邻页面间重复或遗漏。
     */
    private static final Sort LIST_SORT = Sort.by(Sort.Direction.ASC, "createdAt")
            .and(Sort.by(Sort.Direction.ASC, "id"));

    private static final int DEFAULT_PAGE_SIZE = 10;
    /** 上限需兼容现有调用方（看板等一次取 200 条），仅拦截异常大的分页请求 */
    private static final int MAX_PAGE_SIZE = 500;

    @Transactional
    public ScriptResponse createScript(CreateScriptRequest request) {
        Script script = Script.builder()
                .title(request.getTitle())
                .genre(request.getGenre())
                .duration(request.getDuration())
                .targetAudience(request.getTargetAudience())
                .description(request.getDescription())
                .author(request.getAuthor())
                .source(request.getSource())
                .contactInfo(request.getContactInfo())
                .attachmentNote(request.getAttachmentNote())
                // 每脚本独立随机盐：同一投稿人在不同脚本中的代号不可横向关联
                .maskSalt(desensitizationService.generateSalt())
                .status("PENDING")
                .build();
        Script saved = scriptRepository.save(script);
        return toMaskedResponse(saved);
    }

    /**
     * 脚本列表统一入口：状态、等级、题材任意组合筛选（空值条件不参与过滤）。
     * 内容、总数、总页数来自同一条查询；越界页回退到最后一个有效页，
     * 响应中的 number 始终是实际返回的页码，前端可直接据此校正。
     * 列表对所有角色只返回脱敏视图（原始身份字段不出现在响应结构中）。
     */
    public Page<ScriptResponse> searchScripts(String status, String grade, String genre, int page, int size) {
        String normalizedStatus = normalizeFilter(status);
        String normalizedGrade = normalizeFilter(grade);
        String normalizedGenre = normalizeFilter(genre);
        int pageSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int pageIndex = Math.max(page, 0);

        Page<Script> result = scriptRepository.search(normalizedStatus, normalizedGrade, normalizedGenre,
                PageRequest.of(pageIndex, pageSize, LIST_SORT));
        if (result.getTotalPages() > 0 && pageIndex >= result.getTotalPages()) {
            result = scriptRepository.search(normalizedStatus, normalizedGrade, normalizedGenre,
                    PageRequest.of(result.getTotalPages() - 1, pageSize, LIST_SORT));
        }
        return result.map(this::toMaskedResponse);
    }

    private static String normalizeFilter(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /** 脚本详情：任何角色拿到的都是脱敏视图；原稿只能通过管理员受控入口查看。 */
    public Optional<ScriptResponse> getScriptById(Long id) {
        return scriptRepository.findById(id).map(this::toMaskedResponse);
    }

    @Transactional
    public Optional<ScriptResponse> updateScript(Long id, CreateScriptRequest request) {
        return scriptRepository.findById(id)
                .map(script -> {
                    script.setTitle(request.getTitle());
                    script.setGenre(request.getGenre());
                    script.setDuration(request.getDuration());
                    script.setTargetAudience(request.getTargetAudience());
                    script.setDescription(request.getDescription());
                    script.setAuthor(request.getAuthor());
                    script.setSource(request.getSource());
                    script.setContactInfo(request.getContactInfo());
                    script.setAttachmentNote(request.getAttachmentNote());
                    // 原稿字段发生变化：analyzeVersion 与现稿不一致，下次读取时自动重新识别
                    Script saved = scriptRepository.save(script);
                    return toMaskedResponse(saved);
                });
    }

    @Transactional
    public void deleteScript(Long id) {
        scriptRepository.deleteById(id);
    }

    @Transactional
    public void updateScriptGrade(Long scriptId, String grade, Double totalScore) {
        scriptRepository.findById(scriptId).ifPresent(script -> {
            script.setGrade(grade);
            script.setTotalScore(totalScore);
            script.setStatus("GRADED");
            scriptRepository.save(script);
        });
    }

    @Transactional
    public void updateScriptStatus(Long scriptId, String status) {
        scriptRepository.findById(scriptId).ifPresent(script -> {
            script.setStatus(status);
            scriptRepository.save(script);
        });
    }

    /**
     * 统一脱敏视图出口：先确保身份识别与当前原稿一致（首次访问自动识别、原稿变更自动重识别），
     * 再按映射把标题/正文/附件说明等整体替换；联系方式等原稿字段根本不放入响应。
     */
    @Transactional
    public ScriptResponse toMaskedResponse(Script script) {
        DesensitizationService.MaskModel model = desensitizationService.ensureAnalyzed(script);
        return ScriptResponse.builder()
                .id(script.getId())
                .title(desensitizationService.maskField(model,
                        DesensitizationService.FIELD_TITLE, script.getTitle()).orElse(script.getTitle()))
                .genre(script.getGenre())
                .duration(script.getDuration())
                .targetAudience(script.getTargetAudience())
                .description(desensitizationService.maskField(model,
                        DesensitizationService.FIELD_DESCRIPTION, script.getDescription()).orElse(null))
                .attachmentNote(desensitizationService.maskField(model,
                        DesensitizationService.FIELD_ATTACHMENT_NOTE, script.getAttachmentNote()).orElse(null))
                .status(script.getStatus())
                .grade(script.getGrade())
                .totalScore(script.getTotalScore())
                .validScoreCount(scoreRepository.countByScriptIdAndValidTrue(script.getId()))
                .masked(true)
                .pendingCount(model.pendingCount)
                .createdAt(script.getCreatedAt() != null ? script.getCreatedAt().format(FORMATTER) : null)
                .updatedAt(script.getUpdatedAt() != null ? script.getUpdatedAt().format(FORMATTER) : null)
                .build();
    }
}
