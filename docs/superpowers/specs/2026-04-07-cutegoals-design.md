# CuteGoals — 小孩激励管理系统 设计文档

> 日期：2026-04-07
> 状态：已被 2026-07-10 的 core-features 需求重基线取代
>
> 本文仅保留为历史设计记录，不再作为实施或验收依据。当前规范事实源为 openspec/changes/core-features/；新版深度技术设计将在该 change 通过 open 阶段审视后生成。

## 1. 项目概述

CuteGoals 是一个家庭激励管理系统，帮助家长通过任务积分机制激励孩子养成良好的日常习惯。孩子完成每日任务获取小红花积分，使用积分兑换家长预设的激励奖品，支持明牌兑换和盲盒兑换两种模式。

### 1.1 用户角色

| 角色 | 说明 |
|---|---|
| **家长（Parent）** | 管理家庭激励奖品、创建任务模板、分配每日任务、审核孩子任务完成情况、查看积分余额 |
| **孩子（Child）** | 查看积分和任务、提交任务完成申报、浏览和兑换奖品（含盲盒） |
| **系统管理员（Admin）** | 系统管理及维护，具备所有家庭的管理权限 |

### 1.2 核心特性

- 多孩家庭支持，每个孩子独立的任务和积分
- 任务模板 + 日历分配模式
- 分级积分体系（任务按难度等级对应不同积分）
- 孩子申报 + 家长审核的双重确认流程
- 积分累计不清零
- 明牌兑换 + 盲盒随机兑换
- 三端统一架构，移动端优先设计

---

## 2. 架构设计

### 2.1 整体架构：三端统一路由

一个 React SPA 通过路由前缀区分三端：

- `/admin/*` — 管理后台（PC，简洁后台风格）
- `/parent/*` — 家长端（响应式，温馨风格）
- `/child/*` — 孩子端（响应式，童趣风格）

共享 API 层、认证逻辑、类型定义，一次部署，统一域名。

### 2.2 项目结构

```
cutegoals/
├── server/                    # Spring Boot 后端
│   ├── src/main/java/
│   │   └── com.cutegoals/
│   │       ├── auth/           # 认证模块（手机号+PIN码）
│   │       ├── family/         # 家庭管理
│   │       ├── task/           # 任务模板与分配
│   │       ├── points/         # 积分体系
│   │       ├── prize/          # 奖品与兑换
│   │       ├── admin/          # 系统管理（用户/角色/权限）
│   │       └── common/         # 通用工具
│   └── src/main/resources/
├── web/                       # React 前端（单应用）
│   ├── src/
│   │   ├── app/               # 路由入口
│   │   │   ├── admin/         # 管理后台页面
│   │   │   ├── parent/        # 家长端页面
│   │   │   └── child/         # 孩子端页面
│   │   ├── shared/            # 三端共享组件、hooks、API 层
│   │   ├── themes/            # 主题系统（admin/parent/child）
│   │   └── auth/              # 认证相关
│   └── ...
└── docs/                      # 文档
```

### 2.3 技术栈

| 层 | 选型 | 说明 |
|---|---|---|
| 后端框架 | Spring Boot 3.x + MyBatis-Plus | 沿用 1.0 技术线 |
| 数据库 | MySQL 8.x | 成熟稳定 |
| 缓存 | Redis | Token 管理、热点数据 |
| 前端框架 | React 18 + TypeScript | |
| 前端构建 | Vite | 快速开发体验 |
| UI 组件库 | Ant Design 5（管理端）+ 自定义主题（家长/孩子端） | |
| 移动端适配 | Tailwind CSS + 响应式设计 | 家长端和孩子端 |
| 状态管理 | Zustand | 轻量级 |
| 路由 | React Router v6 | 支持路由级权限 |

### 2.4 主题策略

三端共用一套 React 应用，通过 CSS 变量 + 主题 Provider 实现切换，路由级别自动加载对应主题。

---

## 3. 数据模型

### 3.1 核心实体关系

```
家庭(Family) 1──N 家庭成员(FamilyMember)
家庭成员 1──N 孩子档案(ChildProfile)          [角色: parent/child]
家庭 1──N 任务模板(TaskTemplate)
任务模板 N──M 孩子档案                       [通过 TaskAssignment 关联]
任务模板 1──N 难度等级(TaskDifficulty)       [每个等级对应不同积分]
任务分配(TaskAssignment) 1──N 任务记录(TaskRecord)
孩子档案 1──N 积分账户(PointsAccount)
积分账户 1──N 积分流水(PointsTransaction)
家庭 1──N 奖品(Prize)
奖品 N──M 盲盒池(BlindBoxPool)
盲盒池 1──N 盲盒奖品项(BlindBoxItem)
积分账户 1──N 兑换记录(ExchangeRecord)
```

### 3.2 关键表设计

**TaskTemplate（任务模板）**
- `id`, `family_id`, `name`, `icon`, `description`
- `category`（学习/生活/运动/其他）
- `recurrence_rule`（JSON：每天/工作日/自定义星期几）

**TaskDifficulty（难度等级）**
- `id`, `template_id`, `level`（1-5星）, `name`（简单/普通/困难等）, `points`

**TaskAssignment（任务分配）**
- `id`, `template_id`, `child_id`, `difficulty_id`, `assign_date`, `status`（pending/submitted/approved/rejected）

**TaskRecord（任务记录）**
- `id`, `assignment_id`, `child_id`, `complete_time`, `submit_time`, `review_time`, `reviewer_id`, `status`, `actual_difficulty_id`, `awarded_points`

**PointsAccount（积分账户）**
- `id`, `child_id`, `total_points`, `available_points`, `used_points`

**Prize（奖品）**
- `id`, `family_id`, `name`, `description`, `image`, `points_cost`, `stock`, `exchange_type`（direct/blind_box）

**BlindBoxPool（盲盒池）**
- `id`, `family_id`, `name`, `cost_points`, `description`, `status`

**BlindBoxItem（盲盒奖品项）**
- `id`, `pool_id`, `prize_id`, `probability`（权重）

---

## 4. 核心业务流程

### 4.1 任务分配流程

1. 家长创建任务模板 → 设置难度等级和对应积分
2. 家长在日历上给孩子分配任务（选择模板+难度+日期）
3. 系统按 recurrence_rule 可自动生成后续日期的任务分配

### 4.2 任务完成流程

1. 孩子在任务列表看到今日任务 → 点击"完成"提交申报
2. 家长收到待审核任务 → 查看并审核（通过/驳回）
3. 审核通过 → 系统自动发放对应难度等级的积分 → 记录流水

### 4.3 积分兑换流程

- **明牌兑换**：孩子浏览奖品列表 → 选择心仪奖品 → 扣减积分 → 生成兑换记录 → 奖品库存-1
- **盲盒兑换**：孩子选择盲盒池 → 扣减积分 → 系统按权重随机抽取 → 揭示结果 → 生成兑换记录

---

## 5. 功能模块

### 5.1 管理端（`/admin/*`）— PC

| 模块 | 功能 |
|---|---|
| 系统概览 | 家庭数量、用户数量、活跃统计仪表盘 |
| 家庭管理 | 查看/搜索/管理所有家庭，查看家庭成员 |
| 用户管理 | 管理所有用户账号（家长+孩子+管理员） |
| 角色权限 | 管理角色和权限配置 |
| 系统配置 | 积分规则配置、全局参数 |
| 操作日志 | 查看系统操作记录 |

布局：左侧菜单 + 顶部导航 + 主内容区。

### 5.2 家长端（`/parent/*`）— 移动端为主

| 页面 | 功能 |
|---|---|
| 首页/仪表盘 | 今日待审核任务数、孩子积分概览、快捷操作入口 |
| 任务管理 | 任务模板 CRUD → 按日历给孩子分配任务 → 批量操作 |
| 任务审核 | 待审核列表 → 逐个审核（通过/驳回）→ 历史记录 |
| 积分概览 | 每个孩子的积分余额、积分流水、趋势图 |
| 奖品管理 | 创建/编辑奖品（名称、图片、积分价格、库存） |
| 盲盒管理 | 创建盲盒池 → 添加奖品项和权重 → 管理盲盒 |
| 兑换记录 | 查看孩子的兑换历史、待兑现奖品 |
| 家庭设置 | 管理家庭成员、邀请/移除 |

布局：底部 Tab 导航（首页、任务、积分、奖品、我的）。

### 5.3 孩子端（`/child/*`）— 移动端为主

| 页面 | 功能 |
|---|---|
| 首页 | 今日任务卡片列表 + 积分余额展示 + 进度动画 |
| 我的任务 | 按日期查看任务 → 点击完成 → 查看审核状态 |
| 积分乐园 | 积分余额 + 积分获取记录 + 成就展示 |
| 兑换商店 | 奖品展示（明牌区 + 盲盒区）→ 选择兑换 → 开箱动画 |
| 兑换记录 | 历史兑换列表 + 兑换状态 |

布局：底部 Tab 导航（首页、任务、积分、商店、我的），大按钮、大图标、动画反馈。

### 5.4 认证流程

| 角色 | 登录方式 | 首页跳转 |
|---|---|---|
| 管理员 | 手机号 + 密码 | `/admin/dashboard` |
| 家长 | 手机号 + 短信验证码 | `/parent/home` |
| 孩子 | 4位 PIN 码（家长设置） | `/child/home` |

---

## 6. API 设计

### 6.1 路由规划

```
/api/v1/
├── auth/
│   ├── POST /login              # 手机号+验证码/密码 登录
│   ├── POST /child-login        # PIN 码登录
│   └── POST /refresh-token      # 刷新 Token
│
├── family/
│   ├── GET    /me               # 获取我的家庭信息
│   ├── POST   /                 # 创建家庭
│   ├── PUT    /{id}             # 更新家庭信息
│   ├── GET    /{id}/members     # 获取家庭成员列表
│   ├── POST   /{id}/members     # 添加家庭成员
│   └── DELETE /{id}/members/{uid} # 移除家庭成员
│
├── task/
│   ├── GET    /templates        # 任务模板列表
│   ├── POST   /templates        # 创建任务模板
│   ├── PUT    /templates/{id}   # 更新任务模板
│   ├── DELETE /templates/{id}   # 删除任务模板
│   ├── GET    /templates/{id}/difficulties
│   ├── POST   /templates/{id}/difficulties
│   ├── GET    /assignments      # 任务分配列表
│   ├── POST   /assignments      # 批量分配任务
│   ├── PUT    /assignments/{id}
│   ├── POST   /assignments/{id}/submit
│   ├── POST   /assignments/{id}/review
│   └── GET    /calendar
│
├── points/
│   ├── GET    /balance
│   ├── GET    /transactions
│   └── GET    /summary
│
├── prize/
│   ├── GET    /
│   ├── POST   /
│   ├── PUT    /{id}
│   ├── DELETE /{id}
│   ├── GET    /blind-boxes
│   ├── POST   /blind-boxes
│   ├── PUT    /blind-boxes/{id}
│   ├── DELETE /blind-boxes/{id}
│   ├── GET    /blind-boxes/{id}/items
│   └── POST   /exchange
│
└── admin/
    ├── GET    /families
    ├── GET    /users
    ├── GET    /statistics
    └── GET    /audit-logs
```

### 6.2 权限模型

| 角色 | 权限范围 |
|---|---|
| ADMIN | 所有家庭的完全管理权限，系统配置 |
| PARENT | 仅限自己家庭的数据 |
| CHILD | 仅限自己的数据 |

后端：Spring Security + JWT + 自定义 `@DataScope` 注解实现数据隔离。
前端：路由级 `AuthGuard` + API 层自动携带 Token。

### 6.3 数据隔离策略

- 管理员：可查看所有家庭数据
- 家长：SQL 层自动注入 `family_id = 当前用户的家庭ID`
- 孩子：SQL 层自动注入 `child_id = 当前用户ID`

---

## 7. UI/UX 设计方向

### 7.1 管理端 — "专业高效"

- 配色：Ant Design 默认蓝白主题
- 布局：经典侧边栏 + 内容区，数据表格为主
- 交互：标准 CRUD 操作

### 7.2 家长端 — "温馨家庭"

- 主色：`#F59E0B`（暖琥珀色），辅色：`#10B981`（清新绿）、`#F97316`（活力橙）
- 背景：`#FFFBF5`（暖白色）
- 布局：卡片式，圆角 16px，柔和阴影
- 导航：底部 Tab（首页、任务、积分、奖品、我的）
- 特色：任务审核左滑通过/右滑驳回，环形进度条展示积分

### 7.3 孩子端 — "童趣乐园"

- 主色：`#6366F1`（梦幻紫）、`#EC4899`（粉红）
- 辅色：`#FBBF24`（阳光黄）、`#34D399`（薄荷绿）
- 背景：渐变 + 星星/云朵装饰元素
- 布局：大卡片、大间距、大图标（放大 1.5 倍）
- 导航：底部 Tab（首页、任务、积分、商店、我的）
- 特色交互：
  - 完成任务：撒花/星星动画 + 积分飞入动画
  - 盲盒兑换：开箱摇一摇动画 + 烟花揭示效果
  - 积分变化：数字跳动动画

### 7.4 响应式断点

| 断点 | 设备 | 布局策略 |
|---|---|---|
| < 640px | 手机 | 单列布局，底部 Tab |
| 640px - 1024px | 平板 | 双列布局，侧边栏可收起 |
| > 1024px | PC | 完整侧边栏（仅管理端） |

家长端和孩子端在 PC 上最大宽度限制在 768px 居中显示。

---

## 8. MVP 范围

### 8.1 第一版包含

- 认证（手机号登录、PIN码登录、JWT）
- 家庭管理（创建家庭、添加成员）
- 任务模板（CRUD、难度和积分设置）
- 任务分配（按日期分配）
- 任务完成（孩子申报 → 家长审核 → 自动发积分）
- 积分体系（余额、流水）
- 奖品管理（创建/编辑奖品）
- 明牌兑换
- 盲盒兑换（盲盒池管理、随机抽取）
- 管理端基础功能

### 8.2 后续迭代

- 通知推送
- 数据统计图表
- 成就系统
- 任务模板共享/社区

### 8.3 非目标

- 社交功能（孩子间互动、排行榜）
- 第三方登录（微信等）
- 积分交易/转赠
- 实物奖品发货管理

### 8.4 非功能性要求

- 接口响应时间 < 200ms
- 支持 1000+ 家庭并发
- 移动端首屏加载 < 2s
- 数据库定期备份
