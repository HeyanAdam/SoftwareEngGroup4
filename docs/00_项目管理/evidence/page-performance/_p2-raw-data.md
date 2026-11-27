# P2 优化后复测 · 浏览器实测原始记录（Nginx 动静分离，同一份 dist）

- 采集时间：2026-11-26 21:24–21:28（Asia/Shanghai）
- 运行方式：本机原生 Nginx 1.26.2，`http://localhost:8088`，托管与 P1 **同一份** `frontend/dist`（期间未重新 build）
- 后端：`http://172.20.10.7:8080`（队友机器，同 WiFi）；Nginx `/api/`、`/ws/` 反代到该后端
- 与 P1 唯一主要差异：静态资源由 Nginx 直接提供（长期缓存 + gzip_static），P1 由 Vite Preview 提供
- 口径说明：单次加载实测；运行时传输字节受内存缓存影响，主对比以构建产物 gzip 大小与响应头为准

## 1. 登录页 `/login`（公开侧）

| 指标 | 数值 |
|---|---:|
| DCL | 403 ms |
| Load | 436 ms |
| TTFB | 306 ms |
| FCP | 504 ms |
| 资源总数 | 14（JS 10 / CSS 2 / API 1） |
| 总传输 | 415893 B |
| 最大 JS | index-DUVuG52v.js 274997 B（gzip 后传输） |
| 接口耗时 | auth/captcha 102 ms |

### Lighthouse（02-after-lighthouse）
| 项 | 数值 |
|---|---:|
| Performance 分数 | 88 |
| FCP | 1.1 s |
| LCP | 3.7 s |
| TBT | 130 ms |
| CLS | 0.011 |
| Speed Index | 1.8 s |
| TTI | 3.7 s |

## 2. 学生端 `/dashboard`

| 指标 | 数值 |
|---|---:|
| DCL | 403 ms |
| Load | 436 ms |
| TTFB | 306 ms |
| FCP | 504 ms |
| 资源总数 | 19（JS 12 / CSS 3 / API 3） |
| 总传输 | 419128 B |
| 最大 JS | index-DUVuG52v.js 274997 B（gzip） |
| 接口 | auth/captcha 102 ms；auth/login 658 ms；health/ready 41 ms |

## 3. 学生端 `/study-plans`（占位页）

| 指标 | 数值 |
|---|---:|
| DCL | 37 ms |
| Load | 41 ms |
| TTFB | 9 ms |
| FCP | 200 ms |
| 资源总数 | 12（JS 9 / CSS 2 / API 1） |
| 总传输 | 409303 B |
| 最大 JS | index-DUVuG52v.js 274997 B（gzip） |
| 接口 | auth/me 91 ms |

## 4. 学生端 `/files`

| 指标 | 数值 |
|---|---:|
| DCL | 31 ms |
| Load | 35 ms |
| TTFB | 4 ms |
| FCP | 288 ms |
| 资源总数 | 14（JS 9 / CSS 3 / API 2） |
| 总传输 | 420640 B |
| 最大 JS | index-DUVuG52v.js 274997 B（gzip） |
| 接口 | auth/me 148 ms；files?page=1&size=10 62 ms |

## 5. 学生端 `/chat`（AI 答疑，含 WebSocket）

| 指标 | 数值 |
|---|---:|
| DCL | 32 ms |
| Load | 35 ms |
| TTFB | 4 ms |
| FCP | 236 ms |
| 资源总数 | 17（JS 9 / CSS 3 / API 5） |
| 总传输 | 427098 B |
| 最大 JS | index-DUVuG52v.js 274997 B（gzip） |
| 接口 | auth/me 113 ms；chat/ws-ticket 50 ms；conversations 46 ms；conversations/14/messages 50 ms；chat/ws-ticket 150 ms |
| WS 链路 | `/ws/chat` 经 Nginx 转发到达后端；假 ticket 握手返回 `401 UNAUTHORIZED`（响应头 `X-Realtime-Error: UNAUTHORIZED`、`X-Request-Id`），证明代理路由与 Upgrade 转发正常；真实 ticket 握手帧需在 DevTools Network→WS 面板复核 |

## 6. 管理员端 `/admin/dashboard`（运营概览大屏）

| 指标 | 数值 |
|---|---:|
| DCL | 34 ms |
| Load | 38 ms |
| TTFB | 4 ms |
| FCP | 208 ms |
| 资源总数 | 19（JS 12 / CSS 4 / API 3） |
| 总传输 | 588524 B |
| 最大 JS | index-DUVuG52v.js 274997 B（gzip） |
| 接口 | auth/captcha 65 ms；auth/login 405 ms；admin/dashboard/summary?days=7 94 ms |
| 页面数据 | 注册用户 4、文件 3、知识库文档 3、聊天消息 27；登录趋势折线图已渲染 |

## 7. P2 静态响应头（详见 10-nginx-headers-after.txt）

- `index.html`：`Cache-Control: no-cache`。
- 哈希 JS/CSS：`Content-Encoding: gzip`、`Cache-Control: public, immutable`、`max-age=31536000`、`Expires` 1 年。
- 深链接 `/study-plans`、`/chat`、`/admin/dashboard`：200 回退 `index.html`，无 404。
- `/api/v1/auth/captcha`：200，JSON 正常返回。

## 8. 与 P1 对比要点（同版本、同 dist）

- 静态资源传输：P2 由 gzip_static 直接返回预压缩 `.gz`（主 JS 传输 274997 B ≈ 268.5 KB）；响应头确认 `Content-Encoding: gzip` 与长期不可变缓存。
- 接口耗时：大屏 summary P1 232 ms / P2 94 ms（同后端，单次采样波动，不构成 Nginx 收益结论）。
- 运行时 FCP/DCL 受浏览器缓存与单次采样影响，两次数值接近；Nginx 可量化收益主要体现在静态压缩响应头、长期缓存命中后二次访问，以及深链接回退正确性。

## 待复核 / 环境限制
- 标准固定条件（无痕、禁缓存、限速、≥5 次中位数）本轮未执行；Lighthouse 为 headless 单次模拟，分数与 LCP/TBT 存在波动。
- WS 真实握手 101 与消息收发需浏览器 DevTools→WS 面板复核；本机 Docker 不可用，P2 为原生 Nginx 验证，容器构建/启动需在有 Docker 环境补测。
- 文件下载受 MinIO 预签名指向 127.0.0.1:9000 影响，暂不可用（环境问题，非 Nginx 范畴）。
