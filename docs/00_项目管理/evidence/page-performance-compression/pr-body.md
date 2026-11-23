# PR: perf(frontend): 增加 Vite 静态资源预压缩

> 分支：`feat/perf-static-compression` → `main`
>
> 提交：`46d9807`（构建压缩）、`aba58dc`（体积证据）、本文件所在提交（浏览器验收与 PR 正文）
>
> 对应 Issue：未创建（本机 gh CLI 不可用，且按协作口径只推送分支、不建 PR/不合并）
> 协作跟踪：待补

## 目标

为前端 JavaScript、CSS 等文本构建产物生成 Gzip 与 Brotli 预压缩文件，减少部署后传输体积；原始文件保留，构建产物仍可直接使用。本 PR 只做 Vite 构建期压缩，Nginx 静态托管、缓存与响应头验收留给下一阶段。

## 改动内容

- `frontend/package.json` / `package-lock.json`：新增 devDependency `vite-plugin-compression2@^2.5.4`（插件开发依赖基于 Vite 8，安装无 peer 告警）。
- `frontend/vite.config.ts`：新增 `compression({ algorithms: ['gzip','brotliCompress'], threshold: 1024, deleteOriginalAssets: false })`；`/api`、`/ws` 开发代理未改动。
- `docs/00_项目管理/静态资源压缩验收记录.md` 与 `evidence/page-performance-compression/`：修改前后体积、构建日志与浏览器验收证据。

## 数据

| 形态 | js + css 体积 | 相对未压缩 |
| --- | --- | --- |
| 未压缩（基线 7611400） | 2125978 B | — |
| Gzip `.gz` | 635430 B | 压缩率 70.1% |
| Brotli `.br` | 530602 B | 压缩率 75.0% |

- js 32 个 / css 13 个，原文件数量与基线一致；`.gz` 36 个、`.br` 36 个。
- 11 个小于 1024 B 的 js/css 产物未压缩，符合阈值规则。
- 构建耗时 1.73s → 3.06s（单次采样）。

## 当前边界

- 本 PR 不修改 `deploy/nginx.conf` 与 `deploy/docker-compose.yml`。
- 浏览器 `Content-Encoding`、Nginx `gzip_static`、静态缓存头、生产环境提速测量均未完成，留待下一阶段 Nginx 任务。
- 不声称已完成生产环境压缩。

## 验证

- [x] `npm run test`：6 文件 / 80 用例通过
- [x] `npm run build`：通过（`vue-tsc -b && vite build`，vite 8.3.8）
- [x] `npm run preview`：`localhost:4173` 首页与 `/assets/*` 全部 200
- [x] 真实浏览器打开 `/login`：页面正常渲染，12 个 `/assets/*` 请求全 200，0 个 404
- [x] `.gz` 产物存在
- [x] Brotli 产物存在
- [x] 原文件仍存在
- [x] 前后大小对比已记录（`before-build-size.txt` / `after-build-size.txt` / 构建日志 txt+png）

说明：浏览器控制台唯一一条 error 是 `/api/v1/auth/captcha` 返回 502（预览环境无后端），与静态资源无关。

## 风险

- 插件兼容：`vite-plugin-compression2@2.5.4` 对 Vite 8 未声明 peer 约束，本次以真实构建验证通过；后续 vite 大版本升级需重新核验。
- 构建时间：增加约 1.3s，Brotli 高压缩等级对 CPU 更敏感，产物增长后耗时会继续上升。
- 部署包体积：`.gz` + `.br` 合计约 1.17 MB，需确认镜像/制品体积上限。
- Brotli 依赖服务端支持（`ngx_brotli` 或等价方案）；若下一阶段 Nginx 不支持，`.br` 文件为冗余而非故障。

## 提交拆分说明

文档建议拆为 4 个提交（gzip / brotli / test / docs）。实际为 2 个：gzip 与 brotli 由同一处配置同时启用，拆分会导致一次配置被回退再重写，故合并为 `46d9807`；未新增压缩产物的自动化校验脚本，`test(perf)` 提交据实未做，产物检查以 PowerShell 与浏览器验收证据替代。
