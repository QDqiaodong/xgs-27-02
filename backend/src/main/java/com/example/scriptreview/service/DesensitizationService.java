package com.example.scriptreview.service;

import com.example.scriptreview.entity.IdentityClue;
import com.example.scriptreview.entity.IdentityGroup;
import com.example.scriptreview.entity.Script;
import com.example.scriptreview.repository.IdentityClueRepository;
import com.example.scriptreview.repository.IdentityGroupRepository;
import com.example.scriptreview.repository.ScriptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 脚本双盲脱敏引擎。
 *
 * 设计要点：
 * 1) 识别覆盖元数据（作者/来源机构/联系方式）与自由文本（标题/正文/附件说明），
 *    同一套规则在所有字段生效，而不是只替换固定字段；
 * 2) 身份按“类型 + 归一化原文”在脚本内归并：同脚本同身份永远同代号，
 *    相似姓名不会被合并；代号字母带每脚本随机盐，跨脚本不可横向关联；
 * 3) 姓名若带有剧情语境线索（剧中人物/角色/饰演…）按剧情人物保留原文，
 *    无法确定的片段进入 PENDING，评委侧先按保守结果隐藏，等待管理员核对；
 * 4) 原稿指纹未变化时复用映射；原稿更新后自动重新识别，管理员的核对结论在
 *    身份仍然出现时继续生效，核对动作只调整脱敏视图与映射，不改写原稿。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DesensitizationService {

    public static final String FIELD_AUTHOR = "AUTHOR";
    public static final String FIELD_SOURCE = "SOURCE";
    public static final String FIELD_CONTACT_INFO = "CONTACT_INFO";
    public static final String FIELD_TITLE = "TITLE";
    public static final String FIELD_DESCRIPTION = "DESCRIPTION";
    public static final String FIELD_ATTACHMENT_NOTE = "ATTACHMENT_NOTE";

    public static final String TYPE_NAME = "NAME";
    public static final String TYPE_PHONE = "PHONE";
    public static final String TYPE_EMAIL = "EMAIL";
    public static final String TYPE_ACCOUNT = "ACCOUNT";
    public static final String TYPE_ORGANIZATION = "ORGANIZATION";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_IDENTITY = "CONFIRMED_IDENTITY";
    public static final String STATUS_CHARACTER = "CONFIRMED_CHARACTER";

    public static final String REASON_METADATA = "METADATA";
    public static final String REASON_PATTERN = "PATTERN";
    public static final String REASON_NAME_CONTEXT = "NAME_CONTEXT";
    public static final String REASON_NAME_BARE = "NAME_BARE";

    private static final String[] TEXT_FIELDS = {
            FIELD_TITLE, FIELD_DESCRIPTION, FIELD_ATTACHMENT_NOTE
    };

    // 元数据中“来源”为这些泛化值时不构成机构线索
    private static final Pattern GENERIC_SOURCE =
            Pattern.compile("^(内部投稿|外部投稿|个人投稿|自主投稿|平台征稿|公开征集)$");

    private static final Pattern ORG_IN_TEXT = Pattern.compile(
            "([\\u4e00-\\u9fa5A-Za-z0-9（）()·]{2,30}?" +
            "(?:文化传媒|文化传播|影视传媒|传媒科技|网络科技|科技)(?:股份有限|有限责任|有限)?公司" +
            "|[\\u4e00-\\u9fa5A-Za-z0-9（）()·]{2,30}?(?:有限责任公司|股份有限公司|有限公司)" +
            "|[\\u4e00-\\u9fa5A-Za-z0-9（）()·]{2,30}?(?:MCN机构|工作室|经纪公司|广告公司))");

    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");

    // 手机号（可选 +86 前缀）；整体匹配，不允许只遮一半
    private static final Pattern MOBILE = Pattern.compile(
            "(?:\\+?86[\\- ]?)?1[3-9]\\d{9}");
    // 固话 / 400 号（区号-号码 整体）
    private static final Pattern LANDLINE = Pattern.compile(
            "(?:0\\d{2,3}[\\- ]?)?\\d{7,8}(?:[\\- ]?\\d{1,5})?");
    private static final Pattern SERVICE_LINE = Pattern.compile("400[\\- ]?\\d{3}[\\- ]?\\d{4}");

    private static final List<Pattern> ACCOUNT_PATTERNS = List.of(
            Pattern.compile("(?:微信号|微信(?:号)?[：:]?|抖音号|快手号|小红书号|B站(?:账号|号|UID)?|微博(?:号|账号)?" +
                    "|知乎(?:号|账号)?|QQ号|TikTok\\s*(?:ID|账号|号)?|Instagram\\s*(?:ID|账号)?|视频号|公众号" +
                    "|账号|帐号|ID)[:：\\s]*([A-Za-z0-9_.\\-]{4,30})"),
            Pattern.compile("((?:@|[Vv][Xx][：: ]?)[A-Za-z0-9_.\\-]{4,30})"),
            // 素材/网盘链接中可能带有投稿人账号名，整体视为账号线索
            Pattern.compile("(https?://[\\w./?=&%#\\-]+|(?:pan|cloud|drive)\\.[\\w./?=&%#\\-]+)"));

    private static final String COMMON_SURNAMES =
            "赵钱孙李周吴郑王冯陈褚卫蒋沈韩杨朱秦尤许何吕施张孔曹严华金魏陶姜" +
            "戚谢邹喻柏水窦章云苏潘葛奚范彭郎鲁韦昌马苗凤花方俞任袁柳酆鲍史唐" +
            "费廉岑薛雷贺倪汤滕殷罗毕郝邬安常乐于时傅皮卞齐康伍余元卜顾孟黄穆" +
            "萧尹姚邵湛汪祁毛禹狄米贝明臧计伏成戴宋茅庞熊纪舒屈项祝董梁杜阮蓝" +
            "闵席季麻强贾路娄危江童颜郭梅盛林刁钟徐邱骆高夏蔡田樊胡凌霍虞万支" +
            "柯昝管卢莫经房裘缪干解应宗丁宣贲邓郁单杭洪包诸左石崔吉钮龚程嵇邢" +
            "滑裴陆荣翁荀羊於惠甄曲家封芮羿储靳汲邴糜松井段富巫乌焦巴弓牧隗山" +
            "谷车侯宓蓬全郗班仰秋仲伊宫宁仇栾暴甘钭厉戎祖武符刘景詹束龙叶幸司" +
            "韶郜黎蓟薄蒲台从鄂索咸籍赖卓蔺屠蒙池乔阴鬱胥能苍双闻莘党翟谭贡劳" +
            "逄姬申扶堵冉宰郦雍卻璩桑桂濮牛寿通边扈燕冀郏浦尚农温别庄晏柴瞿阎" +
            "充慕连茹习宦艾鱼容向古易慎戈廖庾终暨居衡步都耿满弘匡国文寇广禄阙" +
            "东欧殳沃利蔚越夔隆师巩厍聂晁勾敖融冷訾辛阚那简饶空曾毋沙乜养鞠须" +
            "丰巢关蒯相查后荆红游竺权逯盖益桓公";

    /** 带前缀的昵称式人物称呼：老李、小王、阿飞、阿杰…（候选在扫描时向后续吃到停用字） */
    private static final Pattern NICK_PREFIX = Pattern.compile("[老小大阿][\\u4e00-\\u9fa5]");

    // 姓名前若出现这些词，基本可以判定为剧中角色而非投稿身份
    private static final Pattern DRAMA_PREFIX = Pattern.compile(
            "(?:剧中(?:的)?人物|剧里(?:的)?人物|剧中角色|剧里角色|角色(?:是|为|叫|：|:)?|" +
            "人物(?:是|为|叫|：|:|表)?|顾客|摊主|室友|同事|邻居|店主|老板|店长|主角|主人公|旁白|" +
            "饰演|扮演|出演|主演(?:是|为|叫)?|由)(?:[^，。；;]{0,6})$");
    // 姓名后若出现这些词，是剧中角色
    private static final Pattern DRAMA_SUFFIX = Pattern.compile(
            "^(?:\\s*)(?:饰演|扮演|出演|主演|扮演的角色|这个角色|这一角色|一角|是个|是一)");

    // 姓名前出现这些词，基本可以判定指向投稿人身份
    private static final Pattern IDENTITY_PREFIX = Pattern.compile(
            "(?:编剧|作者|导演|投稿人|撰稿|笔者|本人|联系人|负责人)(?:是|为|叫|：|:)?(?:[^，。；;]{0,4})$");
    private static final Pattern IDENTITY_SUFFIX = Pattern.compile(
            "^(?:\\s*)(?:编剧|作者|导演|本人|供稿|撰稿|根据亲身|(?:的?作品)|(?:的?投稿))");

    /** 姓名候选向后续扫到这些字即截断，避免把“刘雨桐在出租屋”整段当姓名 */
    private static final String NAME_STOP_CHARS =
            "的了是在和与跟也就都很还把被对向从给到让会要说问答笑看想听走跑叫喊哭闹唱做吃喝买卖住坐" +
            "地得着过等吗呢吧啊呀哦嗯嗯着提拿打推拉开关接送遇见发现找帮陪带约劝她他它" +
            "，。；;：:、！？\\s“”\"'（）()【】《》<>";
    private static final Pattern NAME_STOP = Pattern.compile("[" + NAME_STOP_CHARS + "]");

    /** 两字候选恰好是高频普通词时不按“待核对姓名”处理，避免误伤可读性（作者本人不受此限）。 */
    private static final java.util.Set<String> NAME_BLACKLIST = java.util.Set.of(
            "回家", "回来", "回去", "高峰", "高兴", "马上", "王子", "国王", "女王",
            "长江", "黄色", "金钱", "金币", "周到", "周围", "周边", "罗列", "张罗");

    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final IdentityGroupRepository groupRepository;
    private final IdentityClueRepository clueRepository;
    private final ScriptRepository scriptRepository;

    // ============================ 代号与盐 ============================

    /** 每个脚本一个随机盐：代号在脚本内稳定，跨脚本互不相同、无法横向关联。 */
    public String generateSalt() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)]);
        }
        return sb.toString();
    }

    /**
     * 身份代号：类型标签 + 脚本内字母 + 脚本盐。
     * 例如【作者A·7K2QXQ】、【电话B·7K2QXQ】；同一脚本同一分组永远一致，
     * 不同脚本盐值不同，同一投稿人也拿不到相同代号。
     */
    public String codeToken(IdentityGroup group, String salt) {
        return "【" + typeLabel(group.getClueType()) + group.getLabelLetter() + "·" + salt + "】";
    }

    /** 待核对片段代号：不暴露线索类型（避免“这是个姓名”本身成为提示），保守隐藏。 */
    public String pendingToken(IdentityGroup group, String salt) {
        return "【待核对" + group.getLabelLetter() + "·" + salt + "】";
    }

    public static String typeLabel(String type) {
        return switch (type) {
            case TYPE_NAME -> "作者";
            case TYPE_PHONE -> "电话";
            case TYPE_EMAIL -> "邮箱";
            case TYPE_ACCOUNT -> "账号";
            case TYPE_ORGANIZATION -> "机构";
            default -> "身份";
        };
    }

    // ============================ 识别（检测） ============================

    /**
     * 单处识别到的线索片段。
     * clusterText 为归并原文：多数情况下等于片段原文；作者别名（如全名“刘雨桐”的两字名“雨桐”）
     * 通过归并原文并入作者身份分组，沿用同一代号。
     */
    public record Span(String field, int start, int end, String text, String type, String reason,
                       String clusterText) {
        public Span(String field, int start, int end, String text, String type, String reason) {
            this(field, start, end, text, type, reason, text);
        }

        /** 归并到另一个身份（用于作者别名）。 */
        Span withCluster(String cluster) {
            return new Span(field, start, end, text, type, reason, cluster);
        }
    }

    /**
     * 扫描全部字段得到有序线索（按字段顺序 + 偏移）。
     * 检测规则对元数据和正文一致生效；剧情人物姓名作为线索保留记录但归入 CHARACTER 分组，
     * 脱敏时保留原文；不确定的姓名进入 PENDING。
     */
    public List<Span> detect(Script script) {
        List<Span> spans = new ArrayList<>();
        String author = trimToNull(script.getAuthor());
        String source = trimToNull(script.getSource());

        // 1. 元数据：作者
        if (author != null) {
            spans.add(new Span(FIELD_AUTHOR, 0, author.length(), author, TYPE_NAME, REASON_METADATA));
        }
        // 2. 元数据：来源机构（泛化值不算机构线索）
        if (source != null && !GENERIC_SOURCE.matcher(source).matches()) {
            spans.add(new Span(FIELD_SOURCE, 0, source.length(), source, TYPE_ORGANIZATION, REASON_METADATA));
        }
        // 3. 联系方式自由文本：邮箱 / 电话 / 平台账号 / 联系人姓名
        scanContactInfo(script.getContactInfo(), spans);

        // 4. 标题 / 正文 / 附件说明：模式 + 机构 + 姓名，同一规则覆盖全部文本位置
        for (String field : TEXT_FIELDS) {
            String text = fieldText(script, field);
            if (text != null) {
                scanTextField(field, text, spans, author);
            }
        }

        // 去掉同字段重叠区间（优先保留先加入的高置信线索），再稳定排序
        List<Span> deduped = removeOverlaps(spans);
        deduped.sort(Comparator.comparingInt((Span s) -> fieldOrder(s.field()))
                .thenComparingInt(Span::start)
                .thenComparingInt(Span::end));
        return deduped;
    }

    private void scanContactInfo(String contact, List<Span> spans) {
        if (trimToNull(contact) == null) {
            return;
        }
        List<Span> patterned = new ArrayList<>();
        addPatterns(FIELD_CONTACT_INFO, contact, patterned);
        // 联系人：XXX / 联系人XXX
        Matcher label = Pattern.compile("(?:联系人|姓名|投稿人)[:：\\s]*([\\u4e00-\\u9fa5]{2,4})").matcher(contact);
        while (label.find()) {
            patterned.add(new Span(FIELD_CONTACT_INFO, label.start(1), label.end(1),
                    label.group(1), TYPE_NAME, REASON_NAME_CONTEXT));
        }
        for (Span s : removeOverlaps(patterned)) {
            spans.add(s);
        }
    }

    private void scanTextField(String field, String text, List<Span> spans, String author) {
        List<Span> local = new ArrayList<>();
        addPatterns(field, text, local);
        addOrganizations(field, text, local);
        addNames(field, text, local, author);
        for (Span s : removeOverlaps(local)) {
            spans.add(s);
        }
    }

    /** 邮箱 / 电话 / 社交账号等强模式，整体识别。 */
    private void addPatterns(String field, String text, List<Span> out) {
        List<Span> patterned = new ArrayList<>();

        Matcher mail = EMAIL.matcher(text);
        while (mail.find()) {
            patterned.add(new Span(field, mail.start(), mail.end(), mail.group(), TYPE_EMAIL, REASON_PATTERN));
        }
        Matcher mobile = MOBILE.matcher(text);
        while (mobile.find()) {
            patterned.add(new Span(field, mobile.start(), mobile.end(), mobile.group(), TYPE_PHONE, REASON_PATTERN));
        }
        Matcher service = SERVICE_LINE.matcher(text);
        while (service.find()) {
            patterned.add(new Span(field, service.start(), service.end(), service.group(), TYPE_PHONE, REASON_PATTERN));
        }
        Matcher line = LANDLINE.matcher(text);
        while (line.find()) {
            String raw = line.group();
            String digits = raw.replaceAll("\\D", "");
            // 纯 7~12 位且不像年份/时长等普通数字
            if (digits.length() >= 7 && digits.length() <= 12 && !looksLikeOrdinalNumber(text, line.start(), line.end())) {
                patterned.add(new Span(field, line.start(), line.end(), raw, TYPE_PHONE, REASON_PATTERN));
            }
        }
        for (Pattern p : ACCOUNT_PATTERNS) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                // 优先取捕获组（平台账号名），没有捕获组时整体命中（如 URL）
                int g = m.groupCount() >= 1 && m.group(1) != null && m.group(1).length() >= 4 ? 1 : 0;
                String value = m.group(g);
                String handle = value.startsWith("@") ? value.substring(1) : value;
                patterned.add(new Span(field, m.start(g), m.end(g), handle, TYPE_ACCOUNT, REASON_PATTERN));
            }
        }

        // 邮箱优先、手机优先；去掉与强模式重叠的弱模式（如被邮箱包含的账号）
        patterned.sort(Comparator.comparingInt((Span s) -> s.type().equals(TYPE_EMAIL) ? 0 : 1)
                .thenComparingInt(Span::start));
        out.addAll(removeOverlaps(patterned));
    }

    private boolean looksLikeOrdinalNumber(String text, int start, int end) {
        // 前后紧贴数字或日期分隔符（年/月/日/秒/分）时视为普通数字，不是电话
        boolean gluedDigit = (start > 0 && Character.isDigit(text.charAt(start - 1)))
                || (end < text.length() && Character.isDigit(text.charAt(end)));
        if (gluedDigit) {
            return true;
        }
        String after = end < text.length() ? text.substring(end, Math.min(end + 1, text.length())) : "";
        return after.matches("[年月日秒分%]");
    }

    private void addOrganizations(String field, String text, List<Span> out) {
        Matcher m = ORG_IN_TEXT.matcher(text);
        while (m.find()) {
            out.add(new Span(field, m.start(), m.end(), m.group(), TYPE_ORGANIZATION, REASON_PATTERN));
        }
    }

    /**
     * 姓名识别：
     * - 作者本名在文本中出现：身份姓名（与元数据归并，沿用同一代号）；
     *   作者全名的两字名（“刘雨桐”→“雨桐”）作为别名并入同一身份，避免只遮全名仍可猜；
     * - 其他 2~3 字中文姓名：带身份语境 → 身份姓名；带剧情语境 → 剧情人物（保留）；
     *   两者都无 → 待管理员核对（评委侧先保守隐藏）；
     * - “老李/阿杰”式昵称：同样按语境判定，无语境则待核对。
     */
    private void addNames(String field, String text, List<Span> out, String author) {
        List<int[]> occupied = new ArrayList<>();
        for (Span s : out) {
            occupied.add(new int[]{s.start(), s.end()});
        }

        // 作者本名逐字精确匹配；两字名别名同样并入作者身份
        List<String> authorAliases = new ArrayList<>();
        if (author != null && author.length() >= 2) {
            authorAliases.add(author);
            if (author.length() == 3 && isAllChinese(author)) {
                String alias = author.substring(1);
                if (!authorAliases.contains(alias)) {
                    authorAliases.add(alias);
                }
            }
        }
        for (String alias : authorAliases) {
            int idx = 0;
            while ((idx = text.indexOf(alias, idx)) >= 0) {
                int end = idx + alias.length();
                if (!overlapsAny(idx, end, occupied)) {
                    Span span = new Span(field, idx, end, alias, TYPE_NAME, REASON_METADATA);
                    if (!alias.equals(author)) {
                        span = span.withCluster(author);
                    }
                    out.add(span);
                    occupied.add(new int[]{idx, end});
                }
                idx = end;
            }
        }

        // 昵称式人物（老李/小芳/阿杰）：固定“前缀 + 一个汉字”，不贪心外扩
        List<int[]> nickBounds = new ArrayList<>();
        Matcher nick = NICK_PREFIX.matcher(text);
        while (nick.find()) {
            nickBounds.add(new int[]{nick.start(), nick.end()});
        }

        // 正式 2~3 字姓名：以常见姓氏开头，向后吃到第一个停用字
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (COMMON_SURNAMES.indexOf(c) < 0) {
                continue;
            }
            int maxEnd = Math.min(text.length(), i + 3);
            int end = i + 2;
            while (end < maxEnd) {
                if (NAME_STOP.matcher(String.valueOf(text.charAt(end))).find()) {
                    break;
                }
                end++;
            }
            if (end - i < 2 || overlapsAny(i, end, occupied)) {
                continue;
            }
            // 与昵称前缀重叠（如“老李”）时跳过正式姓名扫描，交给昵称判定
            if (overlapsAny(i, end, nickBounds)) {
                continue;
            }
            String candidate = text.substring(i, end);
            // 两字候选若为“回家/高峰”这类高频普通词，不误判为待核对姓名
            if (candidate.length() == 2 && NAME_BLACKLIST.contains(candidate)) {
                continue;
            }
            // 无语境姓名要求自然边界：候选后若紧跟另一个汉字（如“周宁提…”边界外扩），
            // 说明这只是更长词语的片段，放弃该候选，宁可少遮也不误伤可读性
            if (!hasNameEndBoundary(text, end)) {
                continue;
            }
            String reason = reasonFor(text, i, end, candidate, author);
            if (reason != null) {
                out.add(new Span(field, i, end, candidate, TYPE_NAME, reason));
                occupied.add(new int[]{i, end});
                i = end - 1;
            }
        }

        // 昵称判定（独立于正式姓名扫描）
        for (int[] b : nickBounds) {
            if (overlapsAny(b[0], b[1], occupied)) {
                continue;
            }
            String candidate = text.substring(b[0], b[1]);
            String reason = reasonFor(text, b[0], b[1], candidate, author);
            if (reason != null) {
                out.add(new Span(field, b[0], b[1], candidate, TYPE_NAME, reason));
                occupied.add(b);
            }
        }
    }

    private boolean isAllChinese(String v) {
        for (int i = 0; i < v.length(); i++) {
            if (v.charAt(i) < '一' || v.charAt(i) > '龥') {
                return false;
            }
        }
        return true;
    }

    /**
     * 姓名候选的结束边界：文本结尾、停用字（标点/助词/常见动词），或非汉字字符。
     * 若候选后紧跟另一个未被停用的汉字，视为更长普通词的片段（如“回家就…”），放弃候选。
     */
    private boolean hasNameEndBoundary(String text, int end) {
        if (end >= text.length()) {
            return true;
        }
        char next = text.charAt(end);
        if (next < '一' || next > '龥') {
            return true;
        }
        return NAME_STOP.matcher(String.valueOf(next)).find();
    }

    /**
     * 依据上下文返回线索识别依据：
     * CHARACTER 表示剧情人物（保留原文）；NAME_CONTEXT/NAME_BARE 表示指向投稿身份或存疑；
     * null 表示放弃该候选（不乱删）。作者别名在外层已直接归并，不会走到这里。
     */
    private String reasonFor(String text, int start, int end, String candidate, String author) {
        if (author != null && candidate.equals(author)) {
            return REASON_METADATA;
        }
        String before = text.substring(Math.max(0, start - 12), start);
        String after = text.substring(end, Math.min(text.length(), end + 12));

        boolean drama = DRAMA_PREFIX.matcher(before).find() || DRAMA_SUFFIX.matcher(after).find();
        boolean identity = IDENTITY_PREFIX.matcher(before).find() || IDENTITY_SUFFIX.matcher(after).find();

        if (drama && !identity) {
            return "CHARACTER";
        }
        if (identity) {
            return REASON_NAME_CONTEXT;
        }
        // 无任何语境：可能是投稿人也可能是角色 —— 待管理员核对，评委侧先保守隐藏
        return REASON_NAME_BARE;
    }

    private boolean overlapsAny(int start, int end, List<int[]> bounds) {
        for (int[] b : bounds) {
            if (start < b[1] && end > b[0]) {
                return true;
            }
        }
        return false;
    }

    /** 同字段重叠时保留先加入（高置信）的一条；不同字段互不影响。 */
    private List<Span> removeOverlaps(List<Span> spans) {
        Map<String, List<Span>> byField = new LinkedHashMap<>();
        for (Span s : spans) {
            byField.computeIfAbsent(s.field(), k -> new ArrayList<>()).add(s);
        }
        List<Span> result = new ArrayList<>();
        for (List<Span> list : byField.values()) {
            list.sort(Comparator.comparingInt(Span::start).thenComparingInt(s -> -s.end()));
            List<Span> kept = new ArrayList<>();
            for (Span s : list) {
                boolean overlap = false;
                for (Span k : kept) {
                    if (s.start() < k.end() && s.end() > k.start()) {
                        overlap = true;
                        break;
                    }
                }
                if (!overlap) {
                    kept.add(s);
                }
            }
            result.addAll(kept);
        }
        return result;
    }

    private int fieldOrder(String field) {
        return switch (field) {
            case FIELD_AUTHOR -> 0;
            case FIELD_SOURCE -> 1;
            case FIELD_CONTACT_INFO -> 2;
            case FIELD_TITLE -> 3;
            case FIELD_DESCRIPTION -> 4;
            case FIELD_ATTACHMENT_NOTE -> 5;
            default -> 9;
        };
    }

    private String fieldText(Script script, String field) {
        return switch (field) {
            case FIELD_TITLE -> script.getTitle();
            case FIELD_DESCRIPTION -> script.getDescription();
            case FIELD_ATTACHMENT_NOTE -> script.getAttachmentNote();
            default -> null;
        };
    }

    // ============================ 归并键 / 指纹 ============================

    public String clusterKey(String type, String raw) {
        return type + ":" + normalize(type, raw);
    }

    /** 归一化只用于“同一身份归并”：精确相等才归并，相似姓名不会被合并。 */
    public String normalize(String type, String raw) {
        String v = raw == null ? "" : raw.trim();
        return switch (type) {
            case TYPE_EMAIL -> v.toLowerCase();
            case TYPE_PHONE -> v.replaceAll("[^0-9]", "");
            case TYPE_ACCOUNT -> v.replaceAll("^@", "").toLowerCase();
            case TYPE_NAME -> v.replaceAll("[\\s·]", "").toLowerCase();
            default -> v;
        };
    }

    /** 原稿指纹：任一原稿字段变化都会触发重新识别；核对结论不参与指纹（不改写原稿）。 */
    public String fingerprint(Script script) {
        String raw = join(script.getTitle(), script.getAuthor(), script.getSource(),
                script.getContactInfo(), script.getDescription(), script.getAttachmentNote());
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("无法计算原稿指纹", e);
        }
    }

    private String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            sb.append(p == null ? "" : p).append('');
        }
        return sb.toString();
    }

    private String trimToNull(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        return v.trim();
    }

    // ============================ 同步映射 + 生成脱敏视图 ============================

    /** 一份脚本的脱敏上下文：分组、线索与各字段替换区间。 */
    public static class MaskModel {
        public Map<Long, IdentityGroup> groups = new HashMap<>();
        /** field -> 该字段中需要替换的区间（按起点倒序，供替换时从后往前） */
        public Map<String, List<Replacement>> replacements = new LinkedHashMap<>();
        public int pendingCount;

        public record Replacement(int start, int end, String token, Long groupId) {
        }
    }

    /**
     * 确保脚本已按当前原稿完成识别；原稿指纹变化时重新扫描并同步映射。
     * 管理员的核对结论（身份/剧情人物）在身份仍然出现时沿用；已消失且从未核对的
     * PENDING 分组自动清理，已核对的历史分组保留以维持审计可追溯。
     *
     * REQUIRES_NEW：调用方可能是只读事务（如回避/分配/统计的查询），
     * 首次识别需要落库分组/线索/指纹，使用独立写事务保证在只读上下文中也能完成。
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public MaskModel ensureAnalyzed(Script script) {
        String fingerprint = fingerprint(script);
        if (!fingerprint.equals(script.getAnalyzeVersion())) {
            syncGroups(script, fingerprint);
        }
        return buildModel(script);
    }

    private synchronized void syncGroups(Script script, String fingerprint) {
        // 双重检查，避免并发请求重复识别
        if (fingerprint.equals(script.getAnalyzeVersion())) {
            return;
        }
        List<IdentityGroup> existing = groupRepository.findByScriptIdOrderByLabelLetterAscIdAsc(script.getId());
        Map<String, IdentityGroup> byKey = new LinkedHashMap<>();
        for (IdentityGroup g : existing) {
            byKey.put(g.getClusterKey(), g);
        }
        List<Span> spans = detect(script);

        // 按归并键聚合本次扫描结果；LinkedHashMap 保持检测到的首次出现顺序，
        // 新分组的代号字母因此按身份在脚本中首次出现的位置稳定分配。
        // 归并键使用 clusterText：作者别名（两字名）并入作者全名分组，沿用同一代号。
        Map<String, List<Span>> spansByKey = new LinkedHashMap<>();
        for (Span span : spans) {
            spansByKey.computeIfAbsent(clusterKey(span.type(), span.clusterText()), k -> new ArrayList<>()).add(span);
        }

        List<IdentityGroup> resolved = new ArrayList<>();
        Map<String, IdentityGroup> resolvedByKey = new HashMap<>();
        for (Map.Entry<String, List<Span>> entry : spansByKey.entrySet()) {
            List<Span> same = entry.getValue();
            Span first = same.get(0);
            IdentityGroup group = byKey.get(entry.getKey());
            if (group == null) {
                group = IdentityGroup.builder()
                        .scriptId(script.getId())
                        .clusterKey(entry.getKey())
                        .clueType(first.type())
                        .labelLetter("")
                        .status(defaultStatus(same))
                        .representative(truncate(first.clusterText(), 250))
                        .build();
            } else {
                // 身份仍出现：沿用核对结论；仅在尚未核对时按本次检测刷新初始状态与代表原文
                if (STATUS_PENDING.equals(group.getStatus())) {
                    group.setStatus(defaultStatus(same));
                }
                group.setRepresentative(truncate(first.clusterText(), 250));
            }
            group = groupRepository.save(group);
            resolved.add(group);
            resolvedByKey.put(entry.getKey(), group);
        }

        // 代号字母按“本次仍出现的分组”稳定分配：已有字母不动，新分组按首次出现顺序追加
        assignLetters(resolved, existing, spansByKey.keySet());
        for (IdentityGroup g : resolved) {
            groupRepository.save(g);
        }

        // 重建线索（区间位置随原稿变化，必须整体重算）
        clueRepository.deleteByScriptId(script.getId());
        clueRepository.flush();
        int ordinal = 0;
        for (Span span : spans) {
            IdentityGroup group = resolvedByKey.get(clusterKey(span.type(), span.clusterText()));
            clueRepository.save(IdentityClue.builder()
                    .scriptId(script.getId())
                    .groupId(group.getId())
                    .fieldName(span.field())
                    .startOffset(span.start())
                    .endOffset(span.end())
                    .matchedText(truncate(span.text(), 250))
                    .detectReason(span.reason())
                    .ordinal(ordinal++)
                    .build());
        }

        // 清理本次不再出现、且从未核对的 PENDING 分组（确认过的保留，保证审计可追溯）
        List<Long> resolvedIds = resolved.stream().map(IdentityGroup::getId).toList();
        for (IdentityGroup g : existing) {
            if (!resolvedIds.contains(g.getId()) && STATUS_PENDING.equals(g.getStatus()) && g.getDecidedBy() == null) {
                groupRepository.delete(g);
            }
        }

        script.setAnalyzeVersion(fingerprint);
        scriptRepository.save(script);
        log.debug("脚本{}完成身份识别：{}个分组、{}条线索", script.getId(), resolved.size(), spans.size());
    }

    /** 新分组的初始状态：剧情语境线索直接记为剧情人物；无语境姓名保守待核对；其余按身份确认。 */
    private String defaultStatus(List<Span> all) {
        boolean character = all.stream().anyMatch(s -> "CHARACTER".equals(s.reason()));
        boolean identity = all.stream().anyMatch(s -> REASON_METADATA.equals(s.reason())
                || REASON_PATTERN.equals(s.reason()) || REASON_NAME_CONTEXT.equals(s.reason()));
        boolean uncertain = all.stream().anyMatch(s -> REASON_NAME_BARE.equals(s.reason()));
        if (character && !identity && !uncertain) {
            return STATUS_CHARACTER;
        }
        // 只要存在“无法确定是否身份线索”的片段，就进入待管理员核对（评委侧先隐藏）
        if (uncertain || (character && identity)) {
            return STATUS_PENDING;
        }
        if (character) {
            return STATUS_CHARACTER;
        }
        return STATUS_IDENTITY;
    }

    /**
     * 代号字母稳定分配：已持有字母的分组保持不变（刷新/重新登录/重新识别后一致），
     * 新分组按其在脚本中首次出现的顺序（keySet 即检测顺序）追加未使用字母。
     * 字母仅在脚本内有意义，跨脚本不复用。
     */
    private void assignLetters(List<IdentityGroup> resolved, List<IdentityGroup> existing,
                               java.util.Set<String> orderedKeys) {
        java.util.Set<String> used = new java.util.TreeSet<>();
        for (IdentityGroup g : existing) {
            if (g.getLabelLetter() != null && !g.getLabelLetter().isBlank()) {
                used.add(g.getLabelLetter());
            }
        }
        Map<String, IdentityGroup> resolvedByKey = new HashMap<>();
        for (IdentityGroup g : resolved) {
            resolvedByKey.put(g.getClusterKey(), g);
        }
        for (String key : orderedKeys) {
            IdentityGroup g = resolvedByKey.get(key);
            if (g != null && (g.getLabelLetter() == null || g.getLabelLetter().isBlank())) {
                String letter = nextLetter(used);
                g.setLabelLetter(letter);
                used.add(letter);
            }
        }
    }

    private String nextLetter(java.util.Set<String> used) {
        int n = 1;
        while (true) {
            String letter = toLetters(n);
            if (!used.contains(letter)) {
                return letter;
            }
            n++;
        }
    }

    /** 1->A … 26->Z 27->AA */
    private String toLetters(int n) {
        StringBuilder sb = new StringBuilder();
        int v = n;
        while (v > 0) {
            v--;
            sb.insert(0, (char) ('A' + v % 26));
            v /= 26;
        }
        return sb.toString();
    }

    private MaskModel buildModel(Script script) {
        MaskModel model = new MaskModel();
        List<IdentityGroup> groups = groupRepository.findByScriptIdOrderByLabelLetterAscIdAsc(script.getId());
        Map<Long, IdentityGroup> groupById = new LinkedHashMap<>();
        for (IdentityGroup g : groups) {
            groupById.put(g.getId(), g);
            model.groups.put(g.getId(), g);
        }

        for (IdentityClue clue : clueRepository.findByScriptIdOrderByOrdinalAsc(script.getId())) {
            IdentityGroup group = groupById.get(clue.getGroupId());
            if (group == null) {
                continue;
            }
            String token;
            if (STATUS_CHARACTER.equals(group.getStatus())) {
                // 剧情人物：保留原文，不产生替换
                continue;
            } else if (STATUS_PENDING.equals(group.getStatus())) {
                token = pendingToken(group, script.getMaskSalt());
                model.pendingCount++;
            } else {
                token = codeToken(group, script.getMaskSalt());
            }
            model.replacements.computeIfAbsent(clue.getFieldName(), k -> new ArrayList<>())
                    .add(new MaskModel.Replacement(clue.getStartOffset(), clue.getEndOffset(), token, group.getId()));
        }
        for (List<MaskModel.Replacement> list : model.replacements.values()) {
            list.sort(Comparator.comparingInt(MaskModel.Replacement::start).reversed());
        }
        return model;
    }

    /** 对单个文本字段应用替换区间；剧情人物区间不在其中，因此姓名原样保留。 */
    public Optional<String> maskField(MaskModel model, String field, String original) {
        if (original == null) {
            return Optional.empty();
        }
        List<MaskModel.Replacement> list = model.replacements.getOrDefault(field, List.of());
        if (list.isEmpty()) {
            return Optional.of(original);
        }
        String result = original;
        for (MaskModel.Replacement r : list) {
            int start = Math.max(0, r.start());
            int end = Math.min(result.length(), r.end());
            if (start < end) {
                result = result.substring(0, start) + r.token() + result.substring(end);
            }
        }
        return Optional.of(result);
    }

    private String truncate(String v, int max) {
        if (v == null) {
            return "";
        }
        return v.length() <= max ? v : v.substring(0, max);
    }

    /**
     * 跨出口统一的标题掩码：回避、分配、统计等页面中的脚本标题与脚本详情遵守同一规则。
     * 管理员看原标题（管控页面），评委/查看员拿到的标题永远是脱敏版本，
     * 避免“详情遮住了、列表/回避页标题却带着投稿人姓名”的旁路泄露。
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public String maskTitleFor(Script script, com.example.scriptreview.entity.User viewer) {
        if (viewer != null && "ADMIN".equals(viewer.getRole())) {
            return script.getTitle();
        }
        MaskModel model = ensureAnalyzed(script);
        return maskField(model, FIELD_TITLE, script.getTitle()).orElse(script.getTitle());
    }
}
