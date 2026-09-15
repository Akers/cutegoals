# =============================================================================
# CuteGoals 2.0 — Console 前端镜像（家长端 + 管理端，Vue 3 + Vite + naive-ui）
# 镜像标签: cutegoals-core-console:<version>
# 多阶段：Node 22 + pnpm workspace 构建 → nginx 静态服务（容器内 8080，非 root）
# 构建上下文：web/ 目录
# =============================================================================

# ---- 构建阶段 ----
FROM node:22-alpine AS builder

WORKDIR /build
RUN corepack enable pnpm && corepack prepare pnpm@11.15.1 --activate

# 依赖元数据（利用 Docker 缓存层；workspace 需全部 package.json）
COPY pnpm-workspace.yaml package.json pnpm-lock.yaml ./
COPY packages/shared/package.json packages/shared/
COPY apps/console/package.json apps/console/
COPY apps/kid/package.json apps/kid/
RUN pnpm install --frozen-lockfile --ignore-scripts

# 源码与构建（Vite 产物输出至 apps/console/dist）
COPY packages/shared packages/shared
COPY apps/console apps/console
RUN pnpm --filter @cutegoals/console run build

# ---- 运行阶段 ----
FROM nginx:1.25-alpine

RUN apk add --no-cache curl

COPY apps/console/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /build/apps/console/dist /usr/share/nginx/html

RUN chown -R nginx:nginx /usr/share/nginx/html /var/cache/nginx /var/log/nginx /etc/nginx &&     chmod -R 755 /usr/share/nginx/html &&     touch /var/run/nginx.pid && chown nginx:nginx /var/run/nginx.pid

USER nginx
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=15s     CMD curl -f http://localhost:8080/healthz || exit 1

CMD ["nginx", "-g", "daemon off;"]
