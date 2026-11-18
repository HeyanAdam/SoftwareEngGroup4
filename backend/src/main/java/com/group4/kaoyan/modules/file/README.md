# 文件模块
负责人：孙晔
接口前缀：/api/v1/files
计划接口：
- POST /api/v1/files/batch
- POST /api/v1/files/multipart
- PUT  /api/v1/files/multipart/{uploadId}/parts/{partNo}
- GET  /api/v1/files/multipart/{uploadId}/progress
- POST /api/v1/files/multipart/{uploadId}/complete
- GET  /api/v1/files/{fileId}/download
- DELETE /api/v1/files/{fileId}
  文件本体：MinIO Bucket kaoyan-files
  文件元数据：MySQL
  依赖：认证模块提供当前用户，Redis 提供上传会话状态
