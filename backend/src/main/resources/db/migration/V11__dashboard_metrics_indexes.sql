-- V11：运营大屏统计接口配套索引（只加索引，不改表结构）
-- 对应 docs/00_项目管理/数字大屏_甲_后端统计接口.md 步骤 C8。
-- 查询均为 COUNT / SUM + 左闭右开时间范围，索引按“过滤列在前、时间列在后”组织。

-- 今日登录、失败登录，以及每日成功登录趋势
ALTER TABLE sys_login_log ADD INDEX idx_login_time_success (create_time, success);

-- 文件总数、存储总量与每日上传趋势
ALTER TABLE file_object ADD INDEX idx_file_deleted_time (deleted, create_time);

-- 消息总数、今日消息与每日聊天趋势
ALTER TABLE chat_message ADD INDEX idx_chat_msg_time (create_time);

-- 文档总数、失败文档计数
ALTER TABLE kb_document ADD INDEX idx_doc_status_time (status, create_time);
