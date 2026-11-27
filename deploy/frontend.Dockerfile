# ---------- 构建阶段：Node 编译生产产物 ----------
FROM node:22-alpine AS build
WORKDIR /app

# 先拷贝依赖清单，利用 Docker 层缓存
COPY frontend/package*.json ./
RUN npm ci

# 拷贝前端源码并构建（产出 /app/dist）
COPY frontend/ ./
RUN npm run build

# ---------- 运行阶段：Nginx 托管静态资源 ----------
FROM nginx:1.27-alpine

# 站点配置与后端反向代理
COPY deploy/nginx.conf /etc/nginx/nginx.conf

# 拷贝构建产物到 Nginx 站点根目录
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
