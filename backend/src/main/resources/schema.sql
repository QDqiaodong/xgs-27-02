SET NAMES utf8mb4;
SET character_set_client = utf8mb4;
SET character_set_connection = utf8mb4;
SET character_set_results = utf8mb4;

DROP TABLE IF EXISTS desensitization_audit;
DROP TABLE IF EXISTS identity_clue;
DROP TABLE IF EXISTS identity_group;
DROP TABLE IF EXISTS recusal_review_log;
DROP TABLE IF EXISTS recusal_application;
DROP TABLE IF EXISTS audit_log;
DROP TABLE IF EXISTS script_assignment;
DROP TABLE IF EXISTS score;
DROP TABLE IF EXISTS judge;
DROP TABLE IF EXISTS dimension_weight;
DROP TABLE IF EXISTS script;
DROP TABLE IF EXISTS `user`;

CREATE TABLE `user` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    role VARCHAR(20) DEFAULT 'VIEWER' COMMENT 'ADMIN/JUDGE/VIEWER',
    email VARCHAR(200),
    phone VARCHAR(50),
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_login_at DATETIME
);

CREATE TABLE IF NOT EXISTS script (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    genre VARCHAR(50) NOT NULL,
    duration INT NOT NULL COMMENT '时长(秒)',
    target_audience VARCHAR(100) NOT NULL,
    description TEXT,
    author VARCHAR(100),
    source VARCHAR(200),
    contact_info TEXT COMMENT '投稿联系方式：电话/邮箱/社交账号等（原稿，仅管理员受控可见）',
    attachment_note TEXT COMMENT '附件说明（原稿，仅管理员受控可见）',
    mask_salt VARCHAR(16) NOT NULL COMMENT '本脚本脱敏代号随机盐：保证跨脚本代号不可横向关联',
    analyze_version VARCHAR(64) NULL COMMENT '最近一次身份识别对应的原稿指纹；原稿变更后自动重新识别',
    status VARCHAR(30) DEFAULT 'PENDING' COMMENT 'PENDING/REVIEWING/PENDING_SUPPLEMENT/GRADED',
    grade VARCHAR(10),
    total_score DOUBLE,
    creator_id BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (creator_id) REFERENCES `user`(id)
);

-- 身份分组：同一脚本内同一投稿身份的所有线索归入同一分组并共用代号；
-- 分组仅隶属于单个脚本（script_id + cluster_key 唯一），跨脚本不复用、不可横向关联。
CREATE TABLE IF NOT EXISTS identity_group (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id BIGINT NOT NULL,
    cluster_key VARCHAR(120) NOT NULL COMMENT '脚本内稳定归并键：类型:归一化原文',
    clue_type VARCHAR(30) NOT NULL COMMENT 'NAME/PHONE/EMAIL/ACCOUNT/ORGANIZATION',
    label_letter VARCHAR(8) NOT NULL COMMENT '脚本内稳定代号字母：A/B/C…（按首次识别顺序）',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待核对/CONFIRMED_IDENTITY确认投稿身份/CONFIRMED_CHARACTER确认剧情人物',
    representative VARCHAR(255) NOT NULL COMMENT '该身份的代表性原文（姓名/机构全称/号码等）',
    decided_by BIGINT NULL COMMENT '最近一次核对管理员',
    decided_by_name VARCHAR(100) NULL,
    decided_at DATETIME NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_group_script_key (script_id, cluster_key),
    KEY idx_group_script (script_id)
);

-- 身份线索：系统在元数据/标题/正文/附件说明/联系方式中识别到的每一处可定位投稿人的片段。
CREATE TABLE IF NOT EXISTS identity_clue (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    field_name VARCHAR(30) NOT NULL COMMENT 'AUTHOR/SOURCE/CONTACT_INFO/TITLE/DESCRIPTION/ATTACHMENT_NOTE',
    start_offset INT NOT NULL COMMENT '所属文本字段中的起始下标（含）',
    end_offset INT NOT NULL COMMENT '所属文本字段中的结束下标（不含）',
    matched_text VARCHAR(255) NOT NULL COMMENT '识别到的原文片段',
    detect_reason VARCHAR(60) NOT NULL COMMENT '识别依据：METADATA/PATTERN/NAME_CONTEXT/NAME_BARE',
    ordinal INT NOT NULL COMMENT '全脚本扫描顺序，用于稳定分配代号字母',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_clue_span (script_id, field_name, start_offset, end_offset),
    KEY idx_clue_group (group_id),
    KEY idx_clue_script (script_id)
);

-- 脱敏查看审计：谁在何时查看过原稿/代号映射/核对结果。
CREATE TABLE IF NOT EXISTS desensitization_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id BIGINT NOT NULL,
    action VARCHAR(40) NOT NULL COMMENT 'VIEW_RAW 查看原稿 / VIEW_MAP 查看代号映射 / DECIDE_GROUP 核对身份',
    viewer_id BIGINT,
    viewer_name VARCHAR(100),
    ip_address VARCHAR(50),
    detail TEXT COMMENT '动作详情（如核对的分组ID与结论）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_mask_audit_script (script_id),
    KEY idx_mask_audit_created (created_at)
);

CREATE TABLE IF NOT EXISTS judge (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    role VARCHAR(20) DEFAULT 'JUDGE',
    user_id BIGINT UNIQUE,
    expertise VARCHAR(200),
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES `user`(id)
);

CREATE TABLE IF NOT EXISTS score (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id BIGINT NOT NULL,
    judge_id BIGINT NOT NULL,
    creativity_score INT NOT NULL CHECK (creativity_score BETWEEN 1 AND 10),
    commercial_score INT NOT NULL CHECK (commercial_score BETWEEN 1 AND 10),
    difficulty_score INT NOT NULL CHECK (difficulty_score BETWEEN 1 AND 10),
    logic_score INT NOT NULL CHECK (logic_score BETWEEN 1 AND 10),
    audience_score INT NOT NULL CHECK (audience_score BETWEEN 1 AND 10),
    comment TEXT,
    is_extreme BOOLEAN DEFAULT FALSE,
    valid BOOLEAN DEFAULT TRUE COMMENT '评分是否有效；回避批准后旧评分置为无效但保留原文',
    invalidated_at DATETIME NULL COMMENT '评分失效时间',
    invalidated_reason VARCHAR(255) NULL COMMENT '评分失效原因(如回避申请ID)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (script_id) REFERENCES script(id) ON DELETE CASCADE,
    FOREIGN KEY (judge_id) REFERENCES judge(id),
    UNIQUE KEY uk_script_judge (script_id, judge_id)
);

DROP TABLE IF EXISTS dimension_weight;
CREATE TABLE dimension_weight (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dimension_name VARCHAR(50) NOT NULL UNIQUE,
    weight DOUBLE NOT NULL CHECK (weight BETWEEN 0 AND 1),
    description VARCHAR(200),
    sort_order INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS script_assignment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id BIGINT NOT NULL,
    judge_id BIGINT NOT NULL,
    assigned_by BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'ASSIGNED' COMMENT 'ASSIGNED/COMPLETED/RECUSED',
    deadline DATETIME,
    assigned_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    recused_at DATETIME NULL COMMENT '因回避终止的时间',
    recusal_application_id BIGINT NULL COMMENT '终止该分配的回避申请ID',
    replaces_assignment_id BIGINT NULL COMMENT '替补分配所接管的原分配ID',
    FOREIGN KEY (script_id) REFERENCES script(id) ON DELETE CASCADE,
    FOREIGN KEY (judge_id) REFERENCES judge(id),
    FOREIGN KEY (assigned_by) REFERENCES `user`(id),
    UNIQUE KEY uk_assignment (script_id, judge_id),
    UNIQUE KEY uk_replaces_assignment (replaces_assignment_id)
);

CREATE TABLE IF NOT EXISTS recusal_application (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id BIGINT NOT NULL,
    judge_id BIGINT NOT NULL COMMENT '申报回避的评委',
    assignment_id BIGINT NOT NULL COMMENT '对应的脚本分配记录',
    type VARCHAR(30) NOT NULL COMMENT '回避类型: AUTHOR/ORGANIZATION/TOPIC/OTHER',
    reason TEXT NOT NULL COMMENT '回避说明(敏感)',
    evidence TEXT NOT NULL COMMENT '可核验依据(敏感)',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUPPLEMENT_REQUESTED/APPROVED/REJECTED',
    submitted_by BIGINT NOT NULL COMMENT '提交人: 评委本人或代为提交的管理员',
    supplement_info TEXT NULL COMMENT '评委补充材料(敏感)',
    decided_by BIGINT NULL COMMENT '最终处理管理员',
    decided_by_name VARCHAR(100) NULL,
    decision_comment TEXT NULL COMMENT '审批理由/补充要求',
    decided_at DATETIME NULL,
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁, 保证并发审批唯一决定',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_recusal_script (script_id),
    KEY idx_recusal_judge (judge_id),
    KEY idx_recusal_status (status)
);

CREATE TABLE IF NOT EXISTS recusal_review_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL COMMENT 'CREATE/SUPPLEMENT/APPROVE/REJECT/REQUEST_SUPPLEMENT/SUBSTITUTE',
    operator_id BIGINT,
    operator_name VARCHAR(100),
    from_status VARCHAR(30),
    to_status VARCHAR(30),
    comment TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_review_log_app (application_id)
);

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    action VARCHAR(100) NOT NULL,
    module VARCHAR(50) NOT NULL,
    target_id BIGINT,
    user_id BIGINT,
    user_name VARCHAR(100),
    ip_address VARCHAR(50),
    request_params TEXT,
    response_result TEXT,
    success BOOLEAN DEFAULT TRUE,
    error_message TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

INSERT IGNORE INTO dimension_weight (id, dimension_name, weight, description, sort_order) VALUES
(1, 'creativity', 0.25, '脚本创意独特性、新颖程度', 1),
(2, 'commercial', 0.20, '变现能力、市场价值', 2),
(3, 'difficulty', 0.15, '拍摄制作难度、可行性', 3),
(4, 'logic', 0.20, '故事结构、叙事逻辑', 4),
(5, 'audience', 0.20, '与目标受众匹配程度', 5);

INSERT IGNORE INTO `user` (id, username, password, name, role, email, phone, status) VALUES
(1, 'admin', '$2a$10$LxEwd.JBss1SB4A9JLR0Ge1.07jzkPK1E1PZickZkULviMH0Zih9q', '管理员', 'ADMIN', 'admin@example.com', '13800138000', 'ACTIVE'),
(2, 'judge1', '$2a$10$LxEwd.JBss1SB4A9JLR0Ge1.07jzkPK1E1PZickZkULviMH0Zih9q', '张评委', 'JUDGE', 'judge1@example.com', '13800138001', 'ACTIVE'),
(3, 'judge2', '$2a$10$LxEwd.JBss1SB4A9JLR0Ge1.07jzkPK1E1PZickZkULviMH0Zih9q', '李评委', 'JUDGE', 'judge2@example.com', '13800138002', 'ACTIVE'),
(4, 'judge3', '$2a$10$LxEwd.JBss1SB4A9JLR0Ge1.07jzkPK1E1PZickZkULviMH0Zih9q', '王评委', 'JUDGE', 'judge3@example.com', '13800138003', 'ACTIVE'),
(5, 'viewer', '$2a$10$LxEwd.JBss1SB4A9JLR0Ge1.07jzkPK1E1PZickZkULviMH0Zih9q', '查看员', 'VIEWER', 'viewer@example.com', '13800138004', 'ACTIVE'),
(6, 'admin2', '$2a$10$LxEwd.JBss1SB4A9JLR0Ge1.07jzkPK1E1PZickZkULviMH0Zih9q', '管理员二号', 'ADMIN', 'admin2@example.com', '13800138005', 'ACTIVE'),
(7, 'judge4', '$2a$10$LxEwd.JBss1SB4A9JLR0Ge1.07jzkPK1E1PZickZkULviMH0Zih9q', '赵评委', 'JUDGE', 'judge4@example.com', '13800138006', 'ACTIVE'),
(8, 'judge5', '$2a$10$LxEwd.JBss1SB4A9JLR0Ge1.07jzkPK1E1PZickZkULviMH0Zih9q', '孙评委', 'JUDGE', 'judge5@example.com', '13800138007', 'ACTIVE');

INSERT IGNORE INTO judge (id, name, role, user_id, expertise, status) VALUES
(1, '张评委', 'JUDGE', 2, '情感、生活类脚本', 'ACTIVE'),
(2, '李评委', 'JUDGE', 3, '美食、旅行类脚本', 'ACTIVE'),
(3, '王评委', 'JUDGE', 4, '科技、科普类脚本', 'ACTIVE'),
(4, '赵评委', 'JUDGE', 7, '旅游、搞笑、美食类脚本', 'ACTIVE'),
(5, '孙评委', 'JUDGE', 8, '情感、搞笑、生活类脚本', 'ACTIVE');

INSERT IGNORE INTO script (id, title, genre, duration, target_audience, description, author, source, contact_info, attachment_note, mask_salt, status, grade, total_score, creator_id) VALUES
(1, '职场逆袭记', '情感', 120, '职场人士', '讲述一个普通员工通过努力实现职场逆袭的故事，充满正能量。', '张三', '内部投稿', NULL, NULL, 'S1A0001', 'GRADED', 'A', 82.5, 1),
(2, '美食探秘：深夜食堂', '美食', 90, '青年', '探访城市中的隐秘美食小店，记录美食背后的故事。', '李四', '外部投稿', NULL, NULL, 'S2B0002', 'GRADED', 'S', 94.3, 1),
(3, '科技前沿：AI时代', '科技', 150, '青少年', '介绍人工智能的最新发展和应用，探讨未来科技趋势。', '王五', '内部投稿', NULL, NULL, 'S3C0003', 'GRADED', 'B', 68.2, 1),
(4, '旅行日记：海岛奇遇', '旅游', 180, '青年', '记录海岛旅行的精彩瞬间，展现大自然的美丽。', '赵六', '外部投稿', NULL, NULL, 'S4D0004', 'REVIEWING', NULL, NULL, 1),
(5, '搞笑日常：办公室趣事', '搞笑', 60, '青年', '讲述办公室里发生的搞笑故事，轻松幽默。', '孙七', '内部投稿', NULL, NULL, 'S5E0005', 'PENDING', NULL, NULL, 1),
(6, '刘雨桐的北漂日记', '生活', 90, '青年',
 '编剧刘雨桐根据亲身经历创作。开场刘雨桐在出租屋接到妈妈电话：“雨桐啊，不行就回家。”镜头一转，刘雨桐挤上早高峰地铁。剧中人物老李是卖煎饼的摊主，每天和顾客小芳开玩笑；室友阿杰劝她转行。片尾字幕：合作请联系编剧微信 liuyutong_vlog 或电话 13912345678，邮箱 yutong.liu@example.com。',
 '刘雨桐', '成都微光文化传媒有限公司',
 '联系人：刘雨桐；手机：13912345678；邮箱：yutong.liu@example.com；抖音号：liuyutong_vlog；备用机 010-86551234',
 '附件：分镜表《刘雨桐北漂分镜终稿.pdf》、授权书（成都微光文化传媒有限公司盖章扫描件）。素材下载链接 pan.example.com/u/liuyutong2026',
 'S6F0006', 'REVIEWING', NULL, NULL, 1);

INSERT IGNORE INTO score (id, script_id, judge_id, creativity_score, commercial_score, difficulty_score, logic_score, audience_score, comment, is_extreme, valid) VALUES
(1, 1, 1, 8, 7, 6, 9, 8, '故事结构完整，情感真挚，建议增加更多职场细节。', 0, 1),
(2, 1, 2, 9, 8, 7, 8, 7, '创意不错，但商业潜力有待提升。', 0, 1),
(3, 1, 3, 7, 6, 5, 7, 6, '逻辑清晰，受众定位准确。', 0, 1),
(4, 2, 1, 10, 9, 8, 10, 9, '非常出色！画面感强，故事动人，极具商业价值。', 0, 1),
(5, 2, 2, 9, 10, 7, 9, 10, '选题独特，内容丰富，强烈推荐立项。', 0, 1),
(6, 2, 3, 9, 9, 8, 9, 9, '完美的美食纪录片脚本，期待成片。', 0, 1),
(7, 3, 1, 6, 7, 5, 6, 5, '内容过于理论化，建议增加更多实际案例。', 0, 1),
(8, 3, 2, 7, 6, 6, 7, 6, '科技感强，但故事性不足，需要改进。', 0, 1),
(9, 3, 3, 5, 5, 4, 5, 4, '受众定位不够精准，逻辑不够清晰。', 1, 1),
(10, 4, 1, 8, 7, 6, 8, 7, '旅行路线规划合理，期待看到更多当地文化。', 0, 1),
(11, 4, 2, 7, 8, 5, 7, 8, '画面感不错，建议增加更多互动元素。', 0, 1);

INSERT IGNORE INTO script_assignment (id, script_id, judge_id, assigned_by, status, deadline) VALUES
(1, 1, 1, 1, 'COMPLETED', '2026-07-20 23:59:59'),
(2, 1, 2, 1, 'COMPLETED', '2026-07-20 23:59:59'),
(3, 1, 3, 1, 'COMPLETED', '2026-07-20 23:59:59'),
(4, 2, 1, 1, 'COMPLETED', '2026-07-20 23:59:59'),
(5, 2, 2, 1, 'COMPLETED', '2026-07-20 23:59:59'),
(6, 2, 3, 1, 'COMPLETED', '2026-07-20 23:59:59'),
(7, 3, 1, 1, 'COMPLETED', '2026-07-20 23:59:59'),
(8, 3, 2, 1, 'COMPLETED', '2026-07-20 23:59:59'),
(9, 3, 3, 1, 'COMPLETED', '2026-07-20 23:59:59'),
(10, 4, 1, 1, 'ASSIGNED', '2026-07-25 23:59:59'),
(11, 4, 2, 1, 'ASSIGNED', '2026-07-25 23:59:59'),
(12, 4, 3, 1, 'ASSIGNED', '2026-07-25 23:59:59'),
(13, 5, 1, 1, 'ASSIGNED', '2026-07-25 23:59:59'),
(14, 5, 2, 1, 'ASSIGNED', '2026-07-25 23:59:59'),
(15, 5, 3, 1, 'ASSIGNED', '2026-07-25 23:59:59');
