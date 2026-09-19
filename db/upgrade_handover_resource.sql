-- ============================================================
-- club-web 增量升级脚本：移植旧版 zhituan-system 独有功能
--   1. 社团换届管理（club_handover）
--   2. 社团资料库（resource_file）
-- 适用于已执行过 init.sql 的存量数据库；全新环境直接执行 init.sql 即可
-- ============================================================

USE club_web;

-- ---------------- 11. 社团换届记录表 ----------------
CREATE TABLE IF NOT EXISTS club_handover (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  club_id BIGINT NOT NULL COMMENT '社团ID',
  old_leader_id BIGINT NOT NULL COMMENT '原负责人用户ID',
  new_leader_id BIGINT NOT NULL COMMENT '新负责人用户ID',
  note VARCHAR(500) COMMENT '交接说明',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '换届时间'
) COMMENT='社团换届记录表';

-- ---------------- 12. 社团资料库文件表 ----------------
CREATE TABLE IF NOT EXISTS resource_file (
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

-- ============================================================
-- 种子数据（增量）
-- ============================================================

-- 换届记录演示：社团1（计算机协会）2026春季换届
INSERT INTO club_handover (id, club_id, old_leader_id, new_leader_id, note, create_time) VALUES
(1, 1, 6, 2, '2026春季学期换届，代理会长刘洋同学因交换学习移交职务。', '2026-08-25 10:00:00');
