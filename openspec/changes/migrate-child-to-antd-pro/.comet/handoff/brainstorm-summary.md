# Brainstorm Summary

- Change: migrate-child-to-antd-pro
- Date: 2026-07-15

## 确认的技术方案

复用 Admin/Parent 已验证的组件映射模式，将 Child 端 5 页面迁移到 antd。

## 关键取舍与风险

- 5 页面 ~618 行，最小变更
- 页面: HomePage, TasksPage, PrizesPage, BlindBoxesPage, ExchangesPage

## 测试策略

- 保持 94 tests 通过

## Spec Patch

无
