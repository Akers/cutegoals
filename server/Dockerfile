# =============================================================================
# CuteGoals 2.0 — 后端 Docker 镜像（多阶段构建）
# 镜像标签: cutegoals-core-server:<version>
# 基于 Eclipse Temurin 21 (JDK -> JRE)
# =============================================================================

# ---- 构建阶段 ----
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# 先复制 POM 与依赖元数据，利用 Docker 缓存层
COPY server/pom.xml server/
COPY server/common/pom.xml server/common/
COPY server/auth/pom.xml server/auth/
COPY server/family/pom.xml server/family/
COPY server/task/pom.xml server/task/
COPY server/task-review/pom.xml server/task-review/
COPY server/points/pom.xml server/points/
COPY server/prize/pom.xml server/prize/
COPY server/exchange/pom.xml server/exchange/
COPY server/instance-management/pom.xml server/instance-management/
COPY server/web/pom.xml server/web/

# 复制所有源码
COPY server/ server/

# 编译打包（Maven 缓存挂载到 /root/.m2，跨构建复用依赖下载）
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f server/pom.xml package -pl web -am -DskipTests -B -q

# ---- 运行阶段 ----
FROM eclipse-temurin:21-jre-alpine

# 创建非 root 用户
RUN addgroup -S cutegoals && adduser -S cutegoals -G cutegoals

WORKDIR /app

# 从构建阶段复制 JAR
COPY --from=builder /build/server/web/target/*.jar app.jar

# 切换到非 root 用户
USER cutegoals

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
    CMD wget -qO- http://localhost:8080/api/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
