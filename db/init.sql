-- ============================================================
-- 智慧社团Web系统（club_web）数据库初始化脚本
-- 技术栈：MySQL8 / utf8mb4
-- 账号: root / 123456
-- 种子账号（密码均为 123456，MD5: e10adc3949ba59abbe56e057f20f883e）:
--   admin     团委老师   学校管理员
--   20230001  张明       社团发起负责人（计算机协会）
--   20230002  李婷       社团参与负责人（摄影协会）
--   20230003  王强       社团发起负责人（青年志愿者协会）
--   20230101  陈晨       普通学生
--   20230102  刘洋       普通学生
-- ============================================================
DROP DATABASE IF EXISTS club_web;
CREATE DATABASE club_web DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE club_web;

-- ---------------- 1. 用户表 ----------------
CREATE TABLE sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
  student_no VARCHAR(30) NOT NULL UNIQUE COMMENT '学号(统一登录账号)',
  name VARCHAR(50) NOT NULL COMMENT '姓名',
  phone VARCHAR(20) COMMENT '手机号',
  password VARCHAR(64) NOT NULL COMMENT '密码(MD5)',
  ai_recommend TINYINT DEFAULT 1 COMMENT '是否开启AI个性化推荐 1开 0关',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) COMMENT='系统用户表';

-- ---------------- 2. 角色表 ----------------
CREATE TABLE sys_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '角色ID',
  role_code VARCHAR(30) NOT NULL UNIQUE COMMENT '角色编码',
  role_name VARCHAR(50) NOT NULL COMMENT '角色名称'
) COMMENT='角色表';

-- ---------------- 3. 用户角色中间表 ----------------
CREATE TABLE sys_user_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL COMMENT '用户ID',
  role_id BIGINT NOT NULL COMMENT '角色ID',
  UNIQUE KEY uk_user_role (user_id, role_id)
) COMMENT='用户角色中间表';

-- ---------------- 4. 社团表 ----------------
CREATE TABLE club (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '社团ID',
  name VARCHAR(100) NOT NULL COMMENT '社团名称',
  intro TEXT COMMENT '社团简介',
  tags VARCHAR(200) COMMENT '标签(逗号分隔)',
  status TINYINT DEFAULT 0 COMMENT '状态 0待审核 1正常 2已驳回',
  reject_reason VARCHAR(500) COMMENT '驳回理由',
  leader_id BIGINT COMMENT '负责人用户ID',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) COMMENT='社团表';

-- ---------------- 5. 社团成员中间表 ----------------
CREATE TABLE club_member (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  club_id BIGINT NOT NULL COMMENT '社团ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  member_role VARCHAR(50) COMMENT '成员角色(会长/部长/会员)',
  status TINYINT DEFAULT 0 COMMENT '状态 0待审核 1正常 2已退出',
  join_time DATETIME COMMENT '入社时间',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_club_user (club_id, user_id)
) COMMENT='社团成员中间表';

-- ---------------- 6. 活动主表 ----------------
CREATE TABLE activity (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '活动ID',
  title VARCHAR(200) NOT NULL COMMENT '活动标题',
  is_joint TINYINT DEFAULT 0 COMMENT '0普通活动 1联合活动',
  club_id BIGINT NOT NULL COMMENT '发起社团ID',
  location VARCHAR(200) COMMENT '活动地点',
  start_time DATETIME COMMENT '开始时间',
  end_time DATETIME COMMENT '结束时间',
  capacity INT DEFAULT 0 COMMENT '名额(0不限)',
  intro TEXT COMMENT '活动介绍/文案',
  safety_officer VARCHAR(50) COMMENT '安全负责人(联合活动必填)',
  status TINYINT DEFAULT 0 COMMENT '状态 0草稿 1待社团确认 2待校方审批 3进行中 4已结束 5已驳回',
  reject_reason VARCHAR(500) COMMENT '驳回理由',
  checkin_code VARCHAR(20) COMMENT '签到码(审批通过后生成)',
  summary TEXT COMMENT '活动总结(AI生成经用户确认后回填)',
  creator_id BIGINT COMMENT '创建人ID',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME ON UPDATE CURRENT_TIMESTAMP
) COMMENT='活动主表';

-- ---------------- 7. 联合活动参与社团中间表 ----------------
CREATE TABLE joint_activity_join (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  activity_id BIGINT NOT NULL COMMENT '活动ID',
  club_id BIGINT NOT NULL COMMENT '受邀社团ID',
  join_status TINYINT DEFAULT 0 COMMENT '确认状态 0待确认 1同意 2拒绝',
  refuse_reason VARCHAR(500) COMMENT '拒绝理由(拒绝时必填)',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_act_club (activity_id, club_id)
) COMMENT='联合活动参与社团中间表';

-- ---------------- 8. 活动报名表 ----------------
CREATE TABLE activity_signup (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  activity_id BIGINT NOT NULL COMMENT '活动ID',
  user_id BIGINT NOT NULL COMMENT '学生用户ID',
  checkin_time DATETIME COMMENT '签到时间(签到码校验通过后写入)',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_act_user (activity_id, user_id)
) COMMENT='活动报名表';

-- ---------------- 9. 站内消息表 ----------------
CREATE TABLE sys_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL COMMENT '接收人用户ID',
  title VARCHAR(200) COMMENT '消息标题',
  content TEXT COMMENT '消息内容',
  msg_type TINYINT DEFAULT 1 COMMENT '类型 0待办任务 1普通通知',
  is_read TINYINT DEFAULT 0 COMMENT '是否已读 0未读 1已读',
  biz_id BIGINT COMMENT '关联业务ID(如活动ID)',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) COMMENT='站内消息表';

-- ---------------- 10. AI调用记录表 ----------------
CREATE TABLE ai_generate_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL COMMENT '调用人ID',
  skill_name VARCHAR(50) COMMENT '技能名称(ACTIVITY_PLAN/ACTIVITY_SUMMARY/CLUB_ANALYSE/ACTIVITY_RECOMMEND)',
  agent_prompt TEXT COMMENT '发送给大模型的提示词',
  context_param JSON COMMENT '上下文参数(JSON)',
  result_content TEXT COMMENT '生成结果内容',
  call_status TINYINT DEFAULT 1 COMMENT '调用状态 1成功 0失败 2降级',
  error_msg VARCHAR(1000) COMMENT '失败/降级原因',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) COMMENT='AI调用记录表';

-- ============================================================
-- 种子数据
-- ============================================================

-- 角色
INSERT INTO sys_role (id, role_code, role_name) VALUES
(1, 'ADMIN', '学校管理员'),
(2, 'CLUB_LEADER', '社团发起负责人'),
(3, 'CLUB_PARTICIPANT', '社团参与负责人'),
(4, 'STUDENT', '普通学生');

-- 用户（密码统一 123456）
INSERT INTO sys_user (id, student_no, name, phone, password, ai_recommend) VALUES
(1, 'admin',    '团委老师', '13900000001', 'e10adc3949ba59abbe56e057f20f883e', 0),
(2, '20230001', '张明', '13911111101', 'e10adc3949ba59abbe56e057f20f883e', 1),
(3, '20230002', '李婷', '13911111102', 'e10adc3949ba59abbe56e057f20f883e', 1),
(4, '20230003', '王强', '13911111103', 'e10adc3949ba59abbe56e057f20f883e', 1),
(5, '20230101', '陈晨', '13922222201', 'e10adc3949ba59abbe56e057f20f883e', 1),
(6, '20230102', '刘洋', '13922222202', 'e10adc3949ba59abbe56e057f20f883e', 1),
(7, '20230103', '孙浩', '13922222203', 'e10adc3949ba59abbe56e057f20f883e', 1);

-- 用户角色分配
INSERT INTO sys_user_role (user_id, role_id) VALUES
(1, 1),            -- 团委老师 -> 学校管理员
(2, 2),            -- 张明 -> 社团发起负责人
(3, 3),            -- 李婷 -> 社团参与负责人
(4, 2),            -- 王强 -> 社团发起负责人
(5, 4), (6, 4), (7, 4);  -- 学生

-- 社团
INSERT INTO club (id, name, intro, tags, status, leader_id) VALUES
(1, '计算机协会', '以代码会友，定期开展编程工作坊、算法训练营和企业参观，助力同学提升技术实力。', '科技,编程', 1, 2),
(2, '摄影协会', '用镜头记录校园，组织外拍采风、摄影展和后期修图课堂，欢迎热爱生活的你。', '文艺,摄影', 1, 3),
(3, '青年志愿者协会', '立足校园服务社会，组织敬老助残、社区支教、校园公益日等志愿活动。', '公益,志愿', 1, 4);

-- 社团成员
INSERT INTO club_member (club_id, user_id, member_role, status, join_time) VALUES
(1, 2, '会长', 1, '2026-09-01 10:00:00'),
(1, 5, '技术部部长', 1, '2026-09-02 10:00:00'),
(1, 6, '会员', 1, '2026-09-03 10:00:00'),
(2, 3, '会长', 1, '2026-09-01 10:00:00'),
(2, 6, '会员', 1, '2026-09-04 10:00:00'),
(2, 7, '会员', 1, '2026-09-05 10:00:00'),
(3, 4, '会长', 1, '2026-09-01 10:00:00'),
(3, 5, '会员', 1, '2026-09-06 10:00:00');

-- 活动样例（覆盖多种状态）
INSERT INTO activity (id, title, is_joint, club_id, location, start_time, end_time, capacity, intro, safety_officer, status, checkin_code, creator_id) VALUES
(1, 'Python零基础入门工作坊', 0, 1, '教学楼B302机房', '2026-09-10 14:00:00', '2026-09-10 16:30:00', 40,
 '面向零基础同学的Python入门课程，涵盖环境搭建、基础语法与小游戏实战。', NULL, 3, '8001', 2),
(2, '校园秋日外拍·银杏大道采风', 0, 2, '校园银杏大道', '2026-10-18 09:00:00', '2026-10-18 11:30:00', 30,
 '秋日限定外拍活动，协会提供构图与用光指导，优秀作品将在年底摄影展展出。', NULL, 2, NULL, 3),
(3, '校园文化节·科技与光影联合展', 1, 1, '大学生活动中心一楼展厅', '2026-10-25 10:00:00', '2026-10-25 17:00:00', 200,
 '计算机协会与摄影协会联合承办文化节主题展：AI互动体验区+校园光影画廊，展现科技与艺术融合。', '张明', 1, NULL, 2),
(4, '校园公益日·科技助老联合行动', 1, 3, '南门广场', '2026-11-08 09:00:00', '2026-11-08 16:00:00', 100,
 '志愿者协会牵头、摄影协会合作，为社区老人提供智能手机使用教学与防诈骗宣传。', '王强', 2, NULL, 4),
(5, '新生编程之夜·代码启蒙专场', 0, 1, '图书馆报告厅', '2026-09-05 19:00:00', '2026-09-05 21:00:00', 60,
 '面向新生的编程启蒙之夜，学长学姐分享学习路线并现场答疑。', NULL, 4, '8002', 2);

-- 联合活动参与社团
INSERT INTO joint_activity_join (activity_id, club_id, join_status) VALUES
(3, 2, 0),   -- 文化节联合展：摄影协会待确认（演示待办流程）
(4, 1, 1),   -- 公益日联合行动：计算机协会已同意
(4, 2, 1);   -- 公益日联合行动：摄影协会已同意（可提交审批）

-- 活动报名
INSERT INTO activity_signup (activity_id, user_id, checkin_time) VALUES
(1, 5, '2026-09-10 14:02:00'),
(1, 6, NULL),
(1, 7, '2026-09-10 14:05:00'),
(5, 5, '2026-09-05 19:01:00'),
(5, 6, '2026-09-05 19:03:00'),
(5, 7, NULL);

-- 站内消息样例
INSERT INTO sys_message (user_id, title, content, msg_type, is_read, biz_id) VALUES
(3, '联合活动确认邀请', '《校园文化节·科技与光影联合展》联合活动邀请贵社团参与，请前往联合活动管理页处理。', 0, 0, 3),
(1, '活动审批待办', '活动《校园秋日外拍·银杏大道采风》已提交，等待学校审批。', 0, 0, 2),
(5, '报名成功通知', '您已成功报名活动《Python零基础入门工作坊》，请准时参加。', 1, 1, 1),
(2, '活动审批通过', '活动《Python零基础入门工作坊》审批通过，签到码：8001', 1, 0, 1);

-- AI调用记录样例（降级示例）
INSERT INTO ai_generate_record (user_id, skill_name, agent_prompt, context_param, result_content, call_status, error_msg) VALUES
(2, 'ACTIVITY_PLAN', '{"skill":"ACTIVITY_PLAN","extraInput":"Python零基础入门工作坊"}', '{"skill":"ACTIVITY_PLAN","userId":2}',
 '【活动主题】Python零基础入门工作坊（种子示例）', 2, '未配置 spring.ai.openai.api-key，已降级为本地模板生成');

-- ---------------- 11. 社团换届记录表（移植自旧版 zhituan-system） ----------------
CREATE TABLE club_handover (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  club_id BIGINT NOT NULL COMMENT '社团ID',
  old_leader_id BIGINT NOT NULL COMMENT '原负责人用户ID',
  new_leader_id BIGINT NOT NULL COMMENT '新负责人用户ID',
  note VARCHAR(500) COMMENT '交接说明',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '换届时间'
) COMMENT='社团换届记录表';

-- ---------------- 12. 社团资料库文件表（移植自旧版 zhituan-system） ----------------
CREATE TABLE resource_file (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  club_id BIGINT NOT NULL COMMENT '所属社团ID',
  category VARCHAR(20) NOT NULL COMMENT '分类：策划/总结/照片/预算/其他',
  title VARCHAR(200) COMMENT '资料标题(默认取原始文件名)',
  file_name VARCHAR(255) COMMENT '原始文件名',
  file_path VARCHAR(500) COMMENT '存储相对路径 clubId/category/uuid.ext',
  uploader_id BIGINT NOT NULL COMMENT '上传人用户ID',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  KEY idx_club (club_id)
) COMMENT='社团资料库文件表';

-- 换届记录演示：社团1（计算机协会）2026春季换届
INSERT INTO club_handover (id, club_id, old_leader_id, new_leader_id, note, create_time) VALUES
(1, 1, 6, 2, '2026春季学期换届，代理会长刘洋同学因交换学习移交职务。', '2026-08-25 10:00:00');
