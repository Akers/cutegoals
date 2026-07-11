# =============================================================================
# CuteGoals 2.0 — Nginx 反向代理 Docker 镜像
# 镜像标签: mit-modelide-core-nginx:<version>
# 基于 nginx:1.25-alpine，提供静态文件 + API 反向代理
# =============================================================================

FROM nginx:1.25-alpine

# 安装 curl 用于健康检查
RUN apk add --no-cache curl

# 复制 Nginx 配置
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf

# 创建非 root 用户运行（nginx 镜像默认已有 nginx 用户）
# 确保目录权限正确
RUN chown -R nginx:nginx /var/cache/nginx /var/log/nginx /etc/nginx && \
    chmod -R 755 /var/cache/nginx /var/log/nginx /etc/nginx && \
    touch /var/run/nginx.pid && \
    chown nginx:nginx /var/run/nginx.pid

# 前端静态文件由 web 容器提供，此容器仅为反向代理
# 因此不需要 COPY 前端产物

USER nginx

EXPOSE 80 443

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=15s \
    CMD curl -f http://localhost:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
