# =============================================================================
# CuteGoals 2.0 — 前端 Docker 镜像（多阶段构建）
# 镜像标签: mit-modelide-core-web:<version>
# 基于 Node 20 构建，产物拷贝至 nginx:alpine
# =============================================================================

# ---- 构建阶段 ----
FROM node:20-alpine AS builder

WORKDIR /build

# 先复制依赖元数据，利用 Docker 缓存层
COPY web/package.json web/package-lock.json* ./
RUN npm ci

# 复制所有源码并构建
COPY web/ ./
RUN npm run build

# ---- 运行阶段 ----
FROM nginx:1.25-alpine

# 复制 Nginx 配置
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf

# 复制前端构建产物
COPY --from=builder /build/dist /usr/share/nginx/html

# 创建非 root 用户（nginx 镜像默认已有 nginx 用户）
# 确保 nginx 能读取静态文件
RUN chown -R nginx:nginx /usr/share/nginx/html && \
    chmod -R 755 /usr/share/nginx/html

# 切换到非 root 用户运行
USER nginx

EXPOSE 80 443

CMD ["nginx", "-g", "daemon off;"]
