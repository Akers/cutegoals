# =============================================================================
# CuteGoals 2.0 — Kid 前端镜像（孩子端）
# 镜像标签: mit-modelide-core-kid:<version>
# 多阶段：Node 22 + pnpm workspace 构建 → nginx 静态服务 + /child/api 白名单反代
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

# 源码与构建
COPY packages/shared packages/shared
COPY apps/kid apps/kid
RUN pnpm --filter @cutegoals/kid exec umi setup && pnpm --filter @cutegoals/kid run build

# ---- 运行阶段 ----
FROM nginx:1.25-alpine

RUN apk add --no-cache curl

COPY apps/kid/nginx.conf /etc/nginx/conf.d/default.conf
# API 白名单与反代公共头（与 deploy/nginx/ 下文件保持同源，构建时由 deploy 目录复制）
COPY apps/kid/nginx-kid-api-whitelist.conf /etc/nginx/includes/kid-api-whitelist.conf
COPY apps/kid/nginx-api-proxy-headers.conf /etc/nginx/includes/api-proxy-headers.conf
COPY --from=builder /build/apps/kid/dist /usr/share/nginx/html

RUN chown -R nginx:nginx /usr/share/nginx/html /var/cache/nginx /var/log/nginx /etc/nginx &&     chmod -R 755 /usr/share/nginx/html &&     touch /var/run/nginx.pid && chown nginx:nginx /var/run/nginx.pid

USER nginx
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=15s     CMD curl -f http://localhost:8080/healthz || exit 1

CMD ["nginx", "-g", "daemon off;"]
