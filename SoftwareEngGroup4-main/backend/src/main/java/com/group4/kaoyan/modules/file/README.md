# 文件模块
负责人：孙晔
接口前缀：/api/v1/files
计划接口（对齐《接口设计文档 V1.1》第 10 章）：
- POST   /api/v1/files/batch
- POST   /api/v1/files/upload-sessions
- GET    /api/v1/files/upload-sessions/{session_id}
- PUT    /api/v1/files/upload-sessions/{session_id}/parts/{part_number}
- POST   /api/v1/files/upload-sessions/{session_id}/complete
- DELETE /api/v1/files/upload-sessions/{session_id}
- GET    /api/v1/files
- GET    /api/v1/files/{file_id}/content
- DELETE /api/v1/files/{file_id}
  文件本体：MinIO Bucket kaoyan-files
  文件元数据：MySQL（file_object）
  上传会话与分片：MySQL（file_upload_session / file_upload_part，见 V4 迁移）
  依赖：认证模块提供当前用户与 file:* 权限
