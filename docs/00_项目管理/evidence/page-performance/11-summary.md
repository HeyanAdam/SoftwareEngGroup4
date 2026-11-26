# 11 · 性能优化证据汇总（P1 优化前 vs P2 优化后）

## 一、测试环境与条件

| 项 | 内容 |
|---|---|
| 项目 | 考研 AI 导学系统（SoftwareEngGroup4） |
| 前端代码 | main HEAD `ec0b1ab`（任务 A 已合并），P1/P2 复用**同一份** `frontend/dist` |
| P1 提供方 | Vite Preview，`http://localhost:4173` |
| P2 提供方 | 原生 Nginx 1.26.2，`http://localhost:8088`，托管同一 dist |
| 后端 | `http://172.20.10.7:8080`（队友机器，同 WiFi），MySQL/Redis/RabbitMQ/Mailpit 由队友 Docker 提供 |
| 浏览器 | 内置受控 Chromium；Lighthouse headless 单次模拟 |
| 网络 | 局域网手机热点网段；未做固定限速 |
| 采集时间 | 2026-11-26 21:08–21:28（Asia/Shanghai） |

### 条件偏差（如实记录）
- 未执行无痕窗口、DevTools 禁缓存、固定限速、≥5 次取中位数；运行时指标为单次加载实测，受浏览器内存缓存影响。
- Lighthouse 为单次 headless 模拟，分数与 LCP/TBT 存在波动，两次不宜做显著性结论。
- 本机 Docker 引擎不可用，P2 用 Windows 原生 Nginx 验证；容器构建/启动需在有 Docker 环境补测。

## 二、证据文件清单

| 编号 | 文件 | 内容 |
|---|---|---|
| 01 | `01-before-lighthouse.html` / `.json` | P1 登录页 Lighthouse 报告 |
| 02 | `02-after-lighthouse.html` / `.json` | P2 登录页 Lighthouse 报告 |
| 03 | `03-before-network.png` | P1 登录页 Network/页面截图 |
| 04 | `04-after-network.png` | P2 登录页 Network/页面截图 |
| 05 | `05-before-performance-dashboard.png` | P1 学生首屏 Performance 截图 |
| 05 | `05-before-performance-chat.png` | P1 学生聊天页 Performance 截图 |
| 05 | `05-before-performance-dashboard-admin.png` | P1 管理员大屏 Performance 截图 |
| 06 | `06-after-performance-chat.png` | P2 学生聊天页 Performance 截图 |
| 06 | `06-after-performance-dashboard-admin.png` | P2 管理员大屏 Performance 截图 |
| 07 | `07-build-before.txt` | 生产构建完整日志（chunk 体积） |
| 08 | `08-build-after.txt` | P2 复用同一份 dist 的构建说明 |
| 09 | `09-nginx-headers-before.txt` | P1 静态响应头基线（no-cache） |
| 10 | `10-nginx-headers-after.txt` | P2 Nginx 响应头（gzip + immutable + 深链接 200） |
| 11 | `11-summary.md` | 本汇总 |
| — | `_p1-raw-data.md` / `_p2-raw-data.md` | 两阶段逐页浏览器实测原始记录 |

> 证据中不含 Cookie、Authorization Token、API Key、真实邮箱或聊天隐私；临时验证码图片（`_captcha*.png`）不纳入正式提交。

## 三、优化前后对比表

### 3.1 构建产物体积（同一份 dist，Nginx 不改变包体积）

| 指标 | P1 优化前 | P2 优化后 | 变化 |
|---|---:|---:|---|
| 主 JS 原始大小 | 874.69 KB | 874.69 KB | 0（同一份产物） |
| 主 JS gzip 大小 | 277.83 KB | 274.99 KB（响应传输 274997 B） | 0（同一份 `.gz`） |
| 全局 CSS 原始大小 | 362.16 KB | 362.16 KB | 0 |
| 全局 CSS gzip 大小 | 48.25 KB | 48.25 KB | 0 |
| 大屏路由 JS 原始大小 | 503.68 KB | 503.68 KB | 0 |
| 大屏路由 JS gzip 大小 | 170.98 KB | 170.98 KB | 0 |
| 聊天路由 JS gzip | 10.60 KB | 10.60 KB | 0 |
| 文件路由 JS gzip | 10.62 KB | 10.62 KB | 0 |

### 3.2 浏览器实测（单次采样，受缓存/波动影响）

| 指标 | 页面 | P1 Preview | P2 Nginx | 备注 |
|---|---|---:|---:|---|
| FCP（实测） | 登录页 | 512 ms | 504 ms | 接近，单次采样 |
| FCP（Lighthouse） | 登录页 | 1.1 s | 1.1 s | 模拟值 |
| LCP（Lighthouse） | 登录页 | 1.1 s | 3.7 s | 单次模拟波动，不下结论 |
| TBT（Lighthouse） | 登录页 | 100 ms | 130 ms | 单次模拟波动 |
| CLS（Lighthouse） | 登录页 | 0 | 0.011 | 接近 |
| Lighthouse 分数 | 登录页 | 99 | 88 | 单次模拟波动 |
| FCP（实测） | 学生 /chat | 408 ms | 236 ms | 单次采样 |
| FCP（实测） | 管理员大屏 | 184 ms | 208 ms | 单次采样 |
| 大屏 summary 接口 | /admin/dashboard | 232 ms | 94 ms | 同后端，接口耗时波动，非 Nginx 收益 |

### 3.3 Nginx 动静分离能力验证（P2 响应头实测）

| 验证项 | 结果 |
|---|---|
| `index.html` 缓存 | `Cache-Control: no-cache`（不长期缓存） |
| 哈希 JS/CSS 缓存 | `Cache-Control: public, immutable`、`max-age=31536000`、`Expires` 1 年 |
| 静态压缩 | `Content-Encoding: gzip`，`gzip_static on` 返回构建期 `.gz` |
| 深链接刷新 | `/study-plans`、`/chat`、`/admin/dashboard` 均 200 回退 `index.html`，无 404 |
| `/api/` 反代 | `auth/captcha` 200，JSON 正常 |
| `/ws/` 反代 | 转发到达后端；假 ticket 返回 `401 UNAUTHORIZED`（`X-Realtime-Error`），Upgrade 链路正常 |

## 四、结论口径

1. **包体积不变**：P1/P2 为同一份构建产物，Nginx 不减少原始 JS/CSS 体积；瓶颈仍在前端大包（主包 874.69 KB、大屏包 503.68 KB、Element Plus 全量注册）。
2. **Nginx 可量化收益**：静态资源长期不可变缓存（二次访问命中本地缓存）、`gzip_static` 直接下发预压缩 `.gz`、响应头合规、SPA 深链接回退正确、`/api` 与 `/ws` 反代隔离且不套用静态缓存。
3. **首屏时间**：两次实测 FCP/DCL 接近，受单次采样与缓存影响，不能据此宣称 Nginx 带来大幅提速；显著提速需在固定条件（无痕、禁缓存、限速、≥5 次中位数）下复测。
4. **待联调/补测**：WS 真实 101 握手与消息收发需 DevTools→WS 面板复核；Docker Compose 构建启动需有 Docker 环境验证；文件下载受 MinIO 预签名地址影响暂不可用。
