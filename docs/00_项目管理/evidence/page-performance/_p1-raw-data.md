# P1 优化前基线 · 浏览器实测原始记录（干净 main，Vite Preview）

- 采集时间：2026-11-26 21:17–21:20（Asia/Shanghai）
- 运行方式：`npm run preview`，地址 `http://localhost:4173`（生产构建 dist，非 dev server）
- 后端：`http://172.20.10.7:8080`（队友机器，同 WiFi「好吃！好吃！」）
- 前端代码：main HEAD `ec0b1ab`（任务 A 已合并后的最终 A+B 代码），同一份 dist 用于 P1/P2 对比
- 浏览器：内置 Aha 受控 Chromium；口径为单次加载实测；字节以 `transferSize||encodedBodySize` 统计，二次加载受内存缓存影响，报告中需标注
- 固定条件偏差：未做无痕/禁缓存/限速/≥5 次中位数（本机环境限制，如实记录）；Lighthouse 为 headless 单次模拟

## 1. 登录页 `/login`（公开侧，未登录）

| 指标 | 数值 |
|---|---:|
| DCL | 212 ms |
| Load | 219 ms |
| TTFB | 8 ms |
| FCP | 512 ms |
| 资源总数 | 13（JS 10 / CSS 2 / API 1） |
| 总传输 | 5665 B |
| 最大资源 | captcha 2065 B |
| 接口耗时 | auth/captcha 118 ms |

### Lighthouse（01-before-lighthouse）
| 项 | 数值 |
|---|---:|
| Performance 分数 | 99 |
| FCP | 1.1 s |
| LCP | 1.1 s |
| TBT | 100 ms |
| CLS | 0 |
| Speed Index | 1.8 s |
| TTI | 3.7 s |

## 2. 学生端 `/dashboard`（登录后首屏）

| 指标 | 数值 |
|---|---:|
| DCL | 374 ms |
| Load | 379 ms |
| TTFB | 317 ms |
| FCP | 456 ms |
| 资源总数 | 19（JS 12 / CSS 3 / API 3） |
| 总传输 | 9580 B |
| 接口 | auth/captcha 136 ms；auth/login 398 ms；health/ready 43 ms |

## 3. 学生端 `/study-plans`（占位页）

| 指标 | 数值 |
|---|---:|
| DCL | 363 ms |
| Load | 366 ms |
| TTFB | 311 ms |
| FCP | 600 ms |
| 资源总数 | 12（JS 9 / CSS 2 / API 1） |
| 总传输 | 3948 B |
| 接口 | auth/me 53 ms |

## 4. 学生端 `/files`

| 指标 | 数值 |
|---|---:|
| DCL | 36 ms |
| Load | 38 ms |
| TTFB | 10 ms |
| FCP | 204 ms |
| 资源总数 | 14（JS 9 / CSS 3 / API 2） |
| 总传输 | 15910 B |
| 最大 JS | FilePage-BxA7DU3H.js 10893 B |
| 接口 | auth/me 50 ms；files?page=1&size=10 105 ms |

## 5. 学生端 `/chat`（AI 答疑，含 WebSocket）

| 指标 | 数值 |
|---|---:|
| DCL | 64 ms |
| Load | 67 ms |
| TTFB | 9 ms |
| FCP | 408 ms |
| 资源总数 | 17（JS 9 / CSS 3 / API 5） |
| 总传输 | 22362 B |
| 最大 JS | ChatPage-Cs_TQsAI.js 10825 B |
| 接口 | auth/me 132 ms；chat/ws-ticket 55 ms；conversations 88 ms；conversations/14/messages 72 ms；chat/ws-ticket 62 ms |
| WS 状态 | ticket 正常获取（55/62 ms）；WS 握手帧需在 Network→WS 面板复核，本轮未抓到握手记录 |

## 6. 管理员端 `/admin/dashboard`（运营概览大屏）

| 指标 | 数值 |
|---|---:|
| DCL | 35 ms |
| Load | 54 ms |
| TTFB | 4 ms |
| FCP | 184 ms |
| 资源总数 | 19（JS 12 / CSS 4 / API 3） |
| 总传输 | 186715 B |
| 最大 JS | AdminDashboardPage-DEhwdsHq.js 169858 B |
| 接口 | auth/captcha 154 ms；auth/login 290 ms；admin/dashboard/summary?days=7 232 ms |
| 页面数据 | 注册用户 4、文件 3、知识库文档 3、聊天消息 27；登录趋势折线图已渲染 |

## 7. 生产构建产物基线（详见 07-build-before.txt）

| Chunk | 原始大小 | gzip 后 |
|---|---:|---:|
| 主 JS index-*.js | 874.69 KB | 277.83 KB |
| 全局 CSS index-*.css | 362.16 KB | 48.25 KB |
| 后台大屏 AdminDashboardPage-*.js | 503.68 KB | 170.98 KB |
| AI 聊天 ChatPage-*.js | 29.21 KB | 10.60 KB |
| 文件页 FilePage-*.js | 30.20 KB | 10.62 KB |

## 8. P1 静态响应头基线（详见 09-nginx-headers-before.txt）

- 主 JS / CSS：`Cache-Control: no-cache`，无长期缓存 / immutable。
- 压缩由 preview 按 Accept-Encoding 协商；构建期已产出 `.gz` / `.br`。

## 待复核 / 环境限制
- WS 握手是否 101、聊天消息能否收发，需在浏览器 Network→WS 面板确认。
- 文件下载受 MinIO 预签名指向 127.0.0.1:9000 影响，暂不可用（环境问题，非 Nginx 范畴）。
- 标准固定条件（无痕、禁缓存、限速、≥5 次中位数）本轮未执行，P2 复测时尽量对齐，无法对齐处如实标注。
