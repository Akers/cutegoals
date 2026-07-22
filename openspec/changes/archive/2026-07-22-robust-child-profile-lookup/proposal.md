# 使用 findActiveById 替代 selectById 进行孩子档案验证

## 问题描述

前两次修复（添加 JWT 会话 + ATTR_CHILD_ID 检查）后，孩子端首页仍报告 "No child profile found for session"。为提升可靠性，将 `selectById` + 手动状态检查替换为专用的 `findActiveById` SQL 查询。

## 根因分析

`baseMapper.selectById(id)` 依赖 MyBatis-Plus 的实体映射，可能存在行为不确定性。改用显式 SQL 查询 `SELECT * FROM child_profile WHERE id = ? AND status = 'ACTIVE'` 更加可靠。

## 修复目标

1. TaskChildMapper 新增 `findActiveById` 方法
2. PointsController、ExchangeController、TaskReviewController 使用新方法
