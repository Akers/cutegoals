# 归档说明 — frontend-split-child-mobile-first（手动归档）

## 状态

- **归档方式**：手动归档（用户批准，绕过 Comet Native 自动流程）
- **归档日期**：2026-08-19
- **Comet 状态**：build / verification_result=fail（记录于 comet-state.yaml）

## 为什么手动归档

Comet Native 工作流对单次 change 的 scope 明细有硬性预算：
`MAX_NATIVE_DETAILED_SCOPE_CHANGES = 128` 且文档预算 1MB（见 `@rpamis/comet/dist/domains/comet-native/native-verification-scope.js`）。
本次重构为 web/src 单工程 → `apps/console` + `apps/kid` + `packages/shared` 双前端 monorepo 迁移，
变化条目约 220+（文件迁移 + 新增孩子端 + 部署改造），超出预算 → scope 进入 `scope-detail-overflow`
→ 内置 `scoped-text-safety` check 必产生 `scan-limit` → verification 无法 pass →
`comet native archive` 被硬性阻塞（`native-archive.js` 要求 `verification_result === "pass"`）。
该限制是工作流工具对单次 change 变更量的结构性约束，与实施正确性无关。

## 证据完备性

归档目录内保留完整验证证据：

- `verification.md`：完整验收报告（39/39 项验收均有 receipt）
- `runtime/evidence/receipts/`：11 张 evidence receipt（7 automated + 4 manual，全部 status=passed，sourceRevision=15）
- `runtime/evidence/`：scope / allowance / snapshot 等运行时证据
- `specs/`：frontend-split 与 child-mobile-first-ui 两份完整目标规格

实际验证覆盖（receipt 命令真实执行）：

| 维度 | 结果 |
|---|---|
| 单元测试（shared 53 + console 124 + kid 28 = 205） | 全通过 |
| lint（tsc + CSS 引用 + API 白名单一致性） | 通过 |
| 双 app umi build + bundle 双向物理隔离 | 通过 |
| docker compose config（prod + dev） | 通过 |
| Playwright 三视口浏览器实测（375/834/1280，31 断言） | 通过 |
| podman 真实 nginx 容器白名单实测（17 断言） | 通过 |
| podman 真实网关容器分流实测（13 断言） | 通过 |
| podman 双前端镜像净室构建 | 成功 |

## 后续建议

- 本次实施已可生产部署：`web/apps/console/Dockerfile`、`web/apps/kid/Dockerfile`、`deploy/docker-compose.yml`、`deploy/nginx.conf`。
- 若未来希望纳入 Comet 自动归档，可将该重构拆分为多个 ≤128 变更的独立 change 逐步提交。
