package com.example.scriptreview.service;

import com.example.scriptreview.dto.response.MaskingMapResponse;
import com.example.scriptreview.dto.response.ScriptResponse;
import com.example.scriptreview.entity.AuditLog;
import com.example.scriptreview.entity.IdentityClue;
import com.example.scriptreview.entity.Script;
import com.example.scriptreview.entity.ScriptIdentity;
import com.example.scriptreview.entity.User;
import com.example.scriptreview.exception.ConflictException;
import com.example.scriptreview.repository.AuditLogRepository;
import com.example.scriptreview.repository.IdentityClueRepository;
import com.example.scriptreview.repository.ScriptIdentityRepository;
import com.example.scriptreview.repository.ScriptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 脚本双盲脱敏核心服务。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>从脚本元数据（作者、来源机构）与正文（标题、描述、附件说明）中识别身份线索；</li>
 *   <li>同一脚本内同一身份的线索共用一个稳定代号；代号按（盐 + 脚本ID + 身份ID）哈希生成，
 *       同一投稿人在不同脚本中代号不同，评委无法横向关联；</li>
 *   <li>电话、邮箱、社交账号整体替换；与作者/来源完全一致的姓名、机构自动确认；
 *       无法确定的姓名/机构标为 PENDING，核对完成前以更保守的占位符隐藏；</li>
 *   <li>管理员核对结果只调整脱敏视图与映射，不改写原稿；查看映射与核对动作均写审计记录。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MaskingService {

    private final ScriptRepository scriptRepository;
    private final ScriptIdentityRepository identityRepository;
    private final IdentityClueRepository clueRepository;
    private final AuditLogRepository auditLogRepository;

    @Value("${masking.secret:xgs-27-masking-default-salt}")
    private String maskingSecret;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 待核对线索在脱敏视图中的保守占位符：不泄露任何原始字符 */
    public static final String PENDING_PLACEHOLDER = "【待核对】";

    // ===================== 线索识别规则 =====================

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern MOBILE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern LANDLINE = Pattern.compile("(?<!\\d)0\\d{2,3}-?\\d{7,8}(?!\\d)");
    /** 社交平台标识：平台名 + 账号，只把账号部分作为线索（保留“抖音号：”等上下文可读） */
    private static final Pattern SOCIAL = Pattern.compile(
            "(?:微信|微信号|抖音|抖音号|快手|快手号|小红书|微博|公众号|哔哩哔哩|B站|QQ)\\s*[:：]?\\s*([A-Za-z0-9_-]{2,30})");
    private static final Pattern AT_HANDLE = Pattern.compile("(?<![A-Za-z0-9_.%+-])@([A-Za-z0-9_一-龥]{2,20})");
    /** 机构全称：以标点/空白/文本开头为边界，避免把“故事发生在蓝天医院”误并为机构 */
    private static final Pattern ORG = Pattern.compile(
            "(?<![一-龥])([一-龥]{2,12}?(?:公司|集团|工作室|中心|协会|大学|学院|医院|电视台|基金会|事务所))");
    private static final String SURNAMES =
            "张王李赵刘陈杨黄周吴徐孙马朱胡郭何高林罗郑梁谢宋唐许韩冯邓曹彭曾肖田董潘袁蔡蒋余于杜叶程苏魏吕丁任"
                    + "沈姚卢姜崔钟谭陆汪范金石廖贾夏韦付方白邹孟熊秦邱江尹薛闫段雷侯龙史陶黎贺顾毛郝龚邵万钱严覃武戴莫孔向汤";
    /** 身份语境中出现的姓名：如“联系人：王某”“作者 张三” */
    private static final Pattern NAME_AFTER_LABEL = Pattern.compile(
            "(?:作者|投稿人|投稿者|编剧|编导|导演|主演|出镜人|联系人|负责人|制片|制片人|策划|供稿人)"
                    + "\\s*[:：为是]?\\s*([" + SURNAMES + "][一-龥]{1,2})");
    /** 紧邻联系方式的姓名：如“张三，电话……” */
    private static final Pattern NAME_BEFORE_CONTACT = Pattern.compile(
            "([" + SURNAMES + "][一-龥]{1,2})\\s*(?:，|,|、)?\\s*(?=电话|手机|微信|邮箱|抖音|QQ)");
    /** 高频非姓名词，降低姓名误报 */
    private static final Set<String> NAME_STOPWORDS = Set.of(
            "王子", "公主", "高峰", "阳光", "田野", "方向", "安全", "王朝", "江山", "花生", "黄豆",
            "杨柳", "马力", "钱程", "郑重", "庄严", "夏天", "春节", "汤药", "石林", "沙漠");
    /** 姓名后紧跟这些字时不视为“相似长名”（如“张三的”“张三每天”“张三说”） */
    private static final String NAME_TRAILING_PARTICLES =
            "的了吗呢吧啊哦嘛和与在是说看走来去哭笑问答想要会能有没我你他她它们个上下之也为就都很还又再先往向把被让给对跟从到及并但因所"
                    + "每各这那哪什怎多少好坏新旧老大小高低快慢早晚今明昨年月日天时分秒次回遍些点种样事物人家国城店"
                    + "爱恨情仇亲道喊叫跑跳站坐躺睡醒吃喝穿戴拿放开关进出完成变改学教唱做买卖送接打写画拍录演着过然虽即便却竟仍尚已曾将正刚才";

    private static final String[] CODE_WORDS = {
            "青梧", "白泽", "鹿鸣", "云帆", "星阑", "沐阳", "疏影", "临渊", "望舒", "扶摇",
            "汀兰", "砚秋", "南絮", "北屿", "西洲", "东篱", "栖迟", "既白", "清商", "疏桐",
            "闻笛", "照野", "沧浪", "拾翠", "观澜", "寄梅", "折柳", "问樵", "渡鹤", "眠鸥"};

    private static final Set<String> CLUE_TYPES = Set.of("NAME", "PHONE", "EMAIL", "ORG", "SOCIAL");
    private static final Set<String> FIELD_ORIGINS = Set.of("AUTHOR", "SOURCE", "TITLE", "DESCRIPTION", "ATTACHMENT");

    // ===================== 当前用户 =====================

    private User currentUserOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user;
        }
        return null;
    }

    private boolean isAdminView() {
        User user = currentUserOrNull();
        return user != null && "ADMIN".equals(user.getRole());
    }

    private User requireAdmin() {
        User user = currentUserOrNull();
        if (user == null || !"ADMIN".equals(user.getRole())) {
            throw new AccessDeniedException("只有管理员可以查看或核对身份映射");
        }
        return user;
    }

    // ===================== 线索扫描 =====================

    /** 检测到的线索（尚未落库） */
    private record Detected(String fieldOrigin, String clueType, String text, boolean autoConfirmed) {
        String key() {
            return fieldOrigin + "" + clueType + "" + text;
        }
    }

    /**
     * 重新扫描脚本全部可识别字段，刷新线索表。
     * 已存在的线索（含管理员核对结论）按（字段+类型+原文）保留；
     * 原稿中已不存在的线索被移除；新出现的线索按规则自动确认或进入待核对。
     * 原稿本身绝不被修改。
     */
    @Transactional
    public void refreshScriptClues(Script script) {
        Long scriptId = script.getId();
        List<Detected> detected = detectAll(script);

        Map<String, IdentityClue> existingByKey = new LinkedHashMap<>();
        for (IdentityClue clue : clueRepository.findByScriptId(scriptId)) {
            existingByKey.put(clue.getFieldOrigin() + "" + clue.getClueType() + "" + clue.getOriginalText(), clue);
        }

        Set<String> detectedKeys = new HashSet<>();
        for (Detected d : detected) {
            detectedKeys.add(d.key());
            if (existingByKey.containsKey(d.key())) {
                continue; // 已识别过，保留原状态与核对结论
            }
            IdentityClue clue = IdentityClue.builder()
                    .scriptId(scriptId)
                    .fieldOrigin(d.fieldOrigin())
                    .clueType(d.clueType())
                    .originalText(d.text())
                    .status(d.autoConfirmed() ? "CONFIRMED" : "PENDING")
                    .build();
            if (d.autoConfirmed()) {
                clue.setIdentityId(submitterIdentity(scriptId).getId());
            }
            clueRepository.save(clue);
        }

        // 移除原稿中已不存在的线索；管理员手工补充且原文仍在的线索保留
        Map<String, String> fieldContents = fieldContents(script);
        for (IdentityClue clue : clueRepository.findByScriptId(scriptId)) {
            String key = clue.getFieldOrigin() + "" + clue.getClueType() + "" + clue.getOriginalText();
            if (detectedKeys.contains(key)) {
                continue;
            }
            String content = fieldContents.getOrDefault(clue.getFieldOrigin(), "");
            if (content == null || !content.contains(clue.getOriginalText())) {
                clueRepository.delete(clue);
            }
        }
    }

    /** 懒扫描：历史数据首次被读取时补建线索，保证任何入口都不会漏掉脱敏 */
    @Transactional
    public void ensureScanned(Script script) {
        if (script.getId() == null || clueRepository.existsByScriptId(script.getId())) {
            return;
        }
        try {
            refreshScriptClues(script);
        } catch (org.springframework.dao.DataIntegrityViolationException concurrent) {
            // 两个请求并发首扫同一历史脚本：唯一键约束保证只有一份线索，
            // 本请求放弃写入，后续读取使用已落库的结果
        }
    }

    private Map<String, String> fieldContents(Script script) {
        Map<String, String> map = new HashMap<>();
        map.put("AUTHOR", script.getAuthor());
        map.put("SOURCE", script.getSource());
        map.put("TITLE", script.getTitle());
        map.put("DESCRIPTION", script.getDescription());
        map.put("ATTACHMENT", script.getAttachmentNote());
        return map;
    }

    private List<Detected> detectAll(Script script) {
        Map<String, Detected> result = new LinkedHashMap<>();
        String authorName = extractAuthorName(script.getAuthor());
        String sourceOrg = trimToNull(script.getSource());

        // 1. 元数据：作者、来源机构整体属于投稿身份
        if (authorName != null) {
            put(result, new Detected("AUTHOR", "NAME", authorName, true));
        }
        scanContacts(script.getAuthor(), "AUTHOR", result);
        if (sourceOrg != null) {
            put(result, new Detected("SOURCE", "ORG", sourceOrg, true));
        }
        scanContacts(script.getSource(), "SOURCE", result);

        // 2. 正文类字段：标题、描述、附件说明遵守同一规则
        scanContent(script.getTitle(), "TITLE", authorName, sourceOrg, result);
        scanContent(script.getDescription(), "DESCRIPTION", authorName, sourceOrg, result);
        scanContent(script.getAttachmentNote(), "ATTACHMENT", authorName, sourceOrg, result);
        return new ArrayList<>(result.values());
    }

    private void put(Map<String, Detected> map, Detected d) {
        if (d.text() == null || d.text().isBlank() || d.text().length() > 191) {
            return;
        }
        map.putIfAbsent(d.key(), d);
    }

    /** 联系方式：整体识别、整体替换，确认属于投稿身份 */
    private void scanContacts(String text, String fieldOrigin, Map<String, Detected> result) {
        if (text == null) {
            return;
        }
        String work = text;
        work = collect(work, EMAIL, 0, fieldOrigin, "EMAIL", result);
        work = collect(work, MOBILE, 0, fieldOrigin, "PHONE", result);
        work = collect(work, LANDLINE, 0, fieldOrigin, "PHONE", result);
        work = collect(work, SOCIAL, 1, fieldOrigin, "SOCIAL", result);
        collect(work, AT_HANDLE, 1, fieldOrigin, "SOCIAL", result);
    }

    /** 在 text 中按 pattern 收集线索，命中区段置为 \0 后返回，避免后续规则重复命中 */
    private String collect(String text, Pattern pattern, int group, String fieldOrigin, String type,
                           Map<String, Detected> result) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder sb = new StringBuilder(text);
        while (matcher.find()) {
            String value = matcher.group(group);
            if (value == null || value.isBlank()) {
                continue;
            }
            put(result, new Detected(fieldOrigin, type, value.trim(), true));
            int start = matcher.start(group);
            for (int i = start; i < matcher.end(group); i++) {
                sb.setCharAt(i, '\0');
            }
        }
        return sb.toString();
    }

    private void scanContent(String text, String fieldOrigin, String authorName, String sourceOrg,
                             Map<String, Detected> result) {
        if (text == null || text.isBlank()) {
            return;
        }
        // 联系方式优先识别并占位，避免社交账号规则误吞号码
        scanContacts(text, fieldOrigin, result);
        String work = text;
        work = blankOut(work, EMAIL);
        work = blankOut(work, MOBILE);
        work = blankOut(work, LANDLINE);
        work = blankOut(work, SOCIAL, 1);
        work = blankOut(work, AT_HANDLE, 1);

        // 与作者姓名完全一致的再次出现：确认属于投稿身份，沿用相同代号
        if (authorName != null && work.contains(authorName)) {
            put(result, new Detected(fieldOrigin, "NAME", authorName, true));
        }
        // 相似长名保护：作者“张三”与剧情人物“张三丰”是不同身份，
        // 必须各自成为独立线索（长名待核对），否则替换“张三”时会把“张三丰”一并吞并
        if (authorName != null) {
            collectNameExtensions(work, authorName, fieldOrigin, result);
        }
        // 与来源机构完全一致的再次出现
        if (sourceOrg != null && work.contains(sourceOrg)) {
            put(result, new Detected(fieldOrigin, "ORG", sourceOrg, true));
        }

        // 机构全称：与来源不一致的无法确定 -> 待核对
        Matcher orgMatcher = ORG.matcher(work);
        while (orgMatcher.find()) {
            String org = orgMatcher.group(1);
            if (sourceOrg == null || !org.equals(sourceOrg)) {
                put(result, new Detected(fieldOrigin, "ORG", org, false));
            }
        }

        // 身份语境中的姓名：与作者不一致的无法确定 -> 待核对
        collectNameCandidates(work, NAME_AFTER_LABEL, fieldOrigin, authorName, result);
        collectNameCandidates(work, NAME_BEFORE_CONTACT, fieldOrigin, authorName, result);
    }

    private void collectNameCandidates(String work, Pattern pattern, String fieldOrigin, String authorName,
                                       Map<String, Detected> result) {
        Matcher matcher = pattern.matcher(work);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (name == null || NAME_STOPWORDS.contains(name)) {
                continue;
            }
            boolean isAuthor = authorName != null && name.equals(authorName);
            put(result, new Detected(fieldOrigin, "NAME", name, isAuthor));
        }
    }

    /**
     * 检测作者姓名的“相似长名”（张三 -> 张三丰/张三四），标为待核对线索。
     * 长名线索比作者名更长，替换时优先命中，保证相似姓名不会被并入投稿人代号。
     */
    private void collectNameExtensions(String work, String authorName, String fieldOrigin,
                                       Map<String, Detected> result) {
        Pattern extension = Pattern.compile(Pattern.quote(authorName) + "([一-龥])([一-龥])?");
        Matcher matcher = extension.matcher(work);
        while (matcher.find()) {
            String first = matcher.group(1);
            String second = matcher.group(2);
            if (first == null || NAME_TRAILING_PARTICLES.indexOf(first.charAt(0)) >= 0) {
                continue; // “张三的/张三说”等不是长名
            }
            String longer = (second == null || NAME_TRAILING_PARTICLES.indexOf(second.charAt(0)) >= 0)
                    ? authorName + first
                    : authorName + first + second;
            if (!longer.equals(authorName) && !NAME_STOPWORDS.contains(longer)) {
                put(result, new Detected(fieldOrigin, "NAME", longer, false));
            }
        }
    }

    private String blankOut(String text, Pattern pattern) {
        return blankOut(text, pattern, 0);
    }

    private String blankOut(String text, Pattern pattern, int group) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder sb = new StringBuilder(text);
        while (matcher.find()) {
            int start = matcher.start(group);
            int end = matcher.end(group);
            if (start < 0) {
                continue;
            }
            for (int i = start; i < end; i++) {
                sb.setCharAt(i, '\0');
            }
        }
        return sb.toString();
    }

    /** 作者字段去掉联系方式与标点后的姓名部分 */
    private String extractAuthorName(String author) {
        if (author == null) {
            return null;
        }
        String work = blankOut(author, EMAIL);
        work = blankOut(work, MOBILE);
        work = blankOut(work, LANDLINE);
        work = blankOut(work, SOCIAL, 1);
        String name = work.replaceAll("[\\s\\x00()（）\\[\\]【】,，、。:：;；-]", "");
        return (name.length() >= 2 && name.length() <= 30) ? name : null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // ===================== 身份与代号 =====================

    /** 获取（或惰性创建）脚本的投稿人身份，代号在创建时确定且之后不变 */
    private ScriptIdentity submitterIdentity(Long scriptId) {
        return identityRepository.findByScriptIdAndKind(scriptId, "SUBMITTER")
                .orElseGet(() -> createIdentity(scriptId, "SUBMITTER"));
    }

    private ScriptIdentity createIdentity(Long scriptId, String kind) {
        ScriptIdentity identity = identityRepository.save(ScriptIdentity.builder()
                .scriptId(scriptId).kind(kind).aliasCode("待定").build());
        String prefix = "SUBMITTER".equals(kind) ? "投稿人·" : "相关人·";
        String word = CODE_WORDS[hashIndex(scriptId, identity.getId())];
        String alias = prefix + word;
        // 同一代码词在同一脚本内被撞用时追加序号，保证不同身份代号不同
        Set<String> used = new HashSet<>();
        for (ScriptIdentity existing : identityRepository.findByScriptId(scriptId)) {
            used.add(existing.getAliasCode());
        }
        String candidate = alias;
        int seq = 2;
        while (used.contains(candidate)) {
            candidate = alias + "·" + seq;
            seq++;
        }
        identity.setAliasCode(candidate);
        return identityRepository.save(identity);
    }

    private int hashIndex(Long scriptId, Long identityId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    (maskingSecret + ":" + scriptId + ":" + identityId).getBytes(StandardCharsets.UTF_8));
            return Math.abs(hash[0] | (hash[1] << 8)) % CODE_WORDS.length;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    // ===================== 脱敏应用 =====================

    /**
     * 按当前登录用户角色处理脚本响应：管理员看到原稿（masked=false），
     * 评委/查看员/未登录上下文一律得到脱敏版本（masked=true）。
     */
    public void applyToResponse(Script script, ScriptResponse response) {
        ensureScanned(script);
        long pending = clueRepository.countByScriptIdAndStatus(script.getId(), "PENDING");
        response.setPendingClueCount((int) pending);
        if (isAdminView()) {
            response.setMasked(false);
            return;
        }
        response.setMasked(true);
        response.setTitle(maskText(script.getId(), script.getTitle()));
        response.setDescription(maskText(script.getId(), script.getDescription()));
        response.setAttachmentNote(maskText(script.getId(), script.getAttachmentNote()));
        String alias = submitterAlias(script.getId());
        if (trimToNull(script.getAuthor()) != null) {
            response.setAuthor(alias);
        }
        if (trimToNull(script.getSource()) != null) {
            response.setSource(alias);
        }
    }

    /** 评审页面摘要位置（分配、回避、统计等）统一走这里脱敏标题 */
    public String maskScriptTitle(Script script) {
        if (script == null) {
            return "未知脚本";
        }
        if (isAdminView()) {
            return script.getTitle();
        }
        ensureScanned(script);
        return maskText(script.getId(), script.getTitle());
    }

    /**
     * 对一段文本应用该脚本的全部线索：
     * CONFIRMED -> 所属身份代号；PENDING -> 保守占位符；CHARACTER -> 保留原文。
     * 按位置扫描、同一位置最长线索优先，保证“张三丰”（正文角色）不会被
     * “张三”（投稿人）的替换吞并——相似姓名的不同身份绝不合并。
     */
    public String maskText(Long scriptId, String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        List<IdentityClue> clues = clueRepository.findByScriptId(scriptId);
        if (clues.isEmpty()) {
            return text;
        }
        Map<Long, String> aliasByIdentity = new HashMap<>();
        for (ScriptIdentity identity : identityRepository.findByScriptId(scriptId)) {
            aliasByIdentity.put(identity.getId(), identity.getAliasCode());
        }
        // 每条线索 -> 替换目标；CHARACTER 为 null 表示命中后保留原文（用于占位保护）
        List<String[]> replacements = new ArrayList<>();
        for (IdentityClue clue : clues) {
            switch (clue.getStatus()) {
                case "CONFIRMED" -> {
                    String alias = aliasByIdentity.get(clue.getIdentityId());
                    replacements.add(new String[]{clue.getOriginalText(),
                            alias != null ? alias : PENDING_PLACEHOLDER});
                }
                case "PENDING" -> replacements.add(new String[]{clue.getOriginalText(), PENDING_PLACEHOLDER});
                case "CHARACTER" -> replacements.add(new String[]{clue.getOriginalText(), null});
                default -> { }
            }
        }
        // 长线索优先，避免“张三文化传媒公司”先被“张三”截断
        replacements.sort(Comparator.comparingInt((String[] r) -> r[0].length()).reversed());

        StringBuilder masked = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            String[] hit = null;
            for (String[] r : replacements) {
                if (text.startsWith(r[0], i)) {
                    hit = r; // 已按长度降序，第一个命中即最长
                    break;
                }
            }
            if (hit == null) {
                masked.append(text.charAt(i));
                i++;
            } else {
                masked.append(hit[1] != null ? hit[1] : hit[0]);
                i += hit[0].length();
            }
        }
        return masked.toString();
    }

    private String submitterAlias(Long scriptId) {
        return identityRepository.findByScriptIdAndKind(scriptId, "SUBMITTER")
                .map(ScriptIdentity::getAliasCode)
                .orElse(PENDING_PLACEHOLDER);
    }

    // ===================== 管理员映射与核对 =====================

    /** 管理员查看“原稿 / 识别线索 / 脱敏结果”映射；每次查看都写审计记录 */
    @Transactional
    public MaskingMapResponse getMapping(Long scriptId, boolean auditView) {
        User admin = requireAdmin();
        Script script = scriptRepository.findById(scriptId)
                .orElseThrow(() -> new IllegalArgumentException("脚本不存在"));
        ensureScanned(script);
        if (auditView) {
            writeAudit("VIEW_MASKING_MAPPING", scriptId, admin,
                    "查看脚本原稿/线索/代号映射", null);
        }
        return buildMapping(script);
    }

    private MaskingMapResponse buildMapping(Script script) {
        Long scriptId = script.getId();
        List<ScriptIdentity> identities = identityRepository.findByScriptId(scriptId);
        Map<Long, String> aliasByIdentity = new HashMap<>();
        for (ScriptIdentity identity : identities) {
            aliasByIdentity.put(identity.getId(), identity.getAliasCode());
        }

        List<IdentityClue> clues = clueRepository.findByScriptId(scriptId);
        clues.sort(Comparator.comparing(IdentityClue::getId));

        MaskingMapResponse response = new MaskingMapResponse();
        response.setScriptId(scriptId);
        response.setOriginal(MaskingMapResponse.MaskedFields.builder()
                .title(script.getTitle())
                .author(script.getAuthor())
                .source(script.getSource())
                .description(script.getDescription())
                .attachmentNote(script.getAttachmentNote())
                .build());
        String alias = submitterAlias(scriptId);
        response.setMasked(MaskingMapResponse.MaskedFields.builder()
                .title(maskText(scriptId, script.getTitle()))
                .author(trimToNull(script.getAuthor()) != null ? alias : script.getAuthor())
                .source(trimToNull(script.getSource()) != null ? alias : script.getSource())
                .description(maskText(scriptId, script.getDescription()))
                .attachmentNote(maskText(scriptId, script.getAttachmentNote()))
                .build());

        List<MaskingMapResponse.IdentityItem> identityItems = new ArrayList<>();
        for (ScriptIdentity identity : identities) {
            identityItems.add(MaskingMapResponse.IdentityItem.builder()
                    .id(identity.getId())
                    .aliasCode(identity.getAliasCode())
                    .kind(identity.getKind())
                    .build());
        }
        response.setIdentities(identityItems);

        List<MaskingMapResponse.ClueItem> clueItems = new ArrayList<>();
        for (IdentityClue clue : clues) {
            clueItems.add(MaskingMapResponse.ClueItem.builder()
                    .id(clue.getId())
                    .clueType(clue.getClueType())
                    .fieldOrigin(clue.getFieldOrigin())
                    .originalText(clue.getOriginalText())
                    .status(clue.getStatus())
                    .identityId(clue.getIdentityId())
                    .aliasCode(clue.getIdentityId() != null ? aliasByIdentity.get(clue.getIdentityId()) : null)
                    .confirmedByName(clue.getConfirmedByName())
                    .confirmedAt(clue.getConfirmedAt() != null ? clue.getConfirmedAt().format(FORMATTER) : null)
                    .build());
        }
        response.setClues(clueItems);
        response.setPendingCount((int) clues.stream().filter(c -> "PENDING".equals(c.getStatus())).count());
        return response;
    }

    /**
     * 管理员核对线索：decision=IDENTITY 确认属于投稿身份（可指定身份，默认投稿人），
     * decision=CHARACTER 确认属于正文剧情人物。只调整脱敏视图与映射，不改写原稿。
     */
    @Transactional
    public MaskingMapResponse confirmClue(Long scriptId, Long clueId, String decision, Long identityId) {
        User admin = requireAdmin();
        Script script = scriptRepository.findById(scriptId)
                .orElseThrow(() -> new IllegalArgumentException("脚本不存在"));
        IdentityClue clue = clueRepository.findById(clueId)
                .orElseThrow(() -> new IllegalArgumentException("线索不存在"));
        if (!scriptId.equals(clue.getScriptId())) {
            throw new IllegalArgumentException("线索不属于该脚本");
        }

        if ("CHARACTER".equals(decision)) {
            clue.setStatus("CHARACTER");
            clue.setIdentityId(null);
        } else if ("IDENTITY".equals(decision)) {
            ScriptIdentity identity;
            if (identityId != null && identityId == -1L) {
                // 与投稿人不同的另一个身份：新建独立身份，分配独立代号，不与投稿人合并
                identity = createIdentity(scriptId, "RELATED");
            } else if (identityId != null) {
                identity = identityRepository.findById(identityId)
                        .orElseThrow(() -> new IllegalArgumentException("身份不存在"));
                if (!scriptId.equals(identity.getScriptId())) {
                    throw new IllegalArgumentException("身份不属于该脚本");
                }
            } else {
                identity = submitterIdentity(scriptId);
            }
            clue.setStatus("CONFIRMED");
            clue.setIdentityId(identity.getId());
        } else {
            throw new IllegalArgumentException("decision 只能是 IDENTITY 或 CHARACTER");
        }
        clue.setConfirmedBy(admin.getId());
        clue.setConfirmedByName(admin.getName());
        clue.setConfirmedAt(LocalDateTime.now());
        clueRepository.save(clue);

        writeAudit("CONFIRM_IDENTITY_CLUE", scriptId, admin,
                "线索[" + clue.getOriginalText() + "]核对为" + decision, null);
        return buildMapping(script);
    }

    /** 管理员手工补充一条自动识别遗漏的线索，直接确认为投稿身份 */
    @Transactional
    public MaskingMapResponse addManualClue(Long scriptId, String clueType, String fieldOrigin,
                                            String originalText, Long identityId) {
        User admin = requireAdmin();
        Script script = scriptRepository.findById(scriptId)
                .orElseThrow(() -> new IllegalArgumentException("脚本不存在"));
        if (!CLUE_TYPES.contains(clueType)) {
            throw new IllegalArgumentException("非法的线索类型");
        }
        if (!FIELD_ORIGINS.contains(fieldOrigin)) {
            throw new IllegalArgumentException("非法的字段来源");
        }
        String text = trimToNull(originalText);
        if (text == null || text.length() > 191) {
            throw new IllegalArgumentException("线索原文不能为空且不超过191个字符");
        }
        String content = fieldContents(script).get(fieldOrigin);
        if (content == null || !content.contains(text)) {
            throw new IllegalArgumentException("线索原文必须真实存在于对应字段中");
        }

        Optional<IdentityClue> existing = clueRepository
                .findByScriptIdAndFieldOriginAndClueTypeAndOriginalText(scriptId, fieldOrigin, clueType, text);
        if (existing.isPresent() && !"PENDING".equals(existing.get().getStatus())) {
            throw new ConflictException("该线索已存在且已核对");
        }

        ScriptIdentity identity;
        if (identityId != null && identityId == -1L) {
            // 与投稿人不同的另一个身份：新建独立身份，分配独立代号
            identity = createIdentity(scriptId, "RELATED");
        } else if (identityId != null) {
            identity = identityRepository.findById(identityId)
                    .orElseThrow(() -> new IllegalArgumentException("身份不存在"));
            if (!scriptId.equals(identity.getScriptId())) {
                throw new IllegalArgumentException("身份不属于该脚本");
            }
        } else {
            identity = submitterIdentity(scriptId);
        }

        IdentityClue clue = existing.orElseGet(() -> IdentityClue.builder()
                .scriptId(scriptId).fieldOrigin(fieldOrigin).clueType(clueType).originalText(text).build());
        clue.setStatus("CONFIRMED");
        clue.setIdentityId(identity.getId());
        clue.setConfirmedBy(admin.getId());
        clue.setConfirmedByName(admin.getName());
        clue.setConfirmedAt(LocalDateTime.now());
        clueRepository.save(clue);

        writeAudit("ADD_MANUAL_CLUE", scriptId, admin,
                "手工补充线索[" + text + "]类型" + clueType, null);
        return buildMapping(script);
    }

    /** 删除脚本时同步清理脱敏数据（先删线索再删身份，避免外键约束） */
    @Transactional
    public void removeScriptData(Long scriptId) {
        clueRepository.deleteByScriptId(scriptId);
        identityRepository.deleteByScriptId(scriptId);
    }

    private void writeAudit(String action, Long scriptId, User operator, String detail, String error) {
        auditLogRepository.save(AuditLog.builder()
                .action(action)
                .module("Masking")
                .targetId(scriptId)
                .userId(operator.getId())
                .userName(operator.getName())
                .requestParams(detail)
                .responseResult(error == null ? "成功" : "失败")
                .success(error == null)
                .errorMessage(error)
                .createdAt(LocalDateTime.now())
                .build());
    }
}
