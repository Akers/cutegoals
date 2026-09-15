# 目标

移除家长端（console）的空闲锁屏功能：空闲 1 小时后不再自动弹出锁屏覆盖层，相关组件、store、hooks 与手动"锁屏"入口一并删除。孩子端（kid）经调查不存在空闲锁屏实现，无需改动。

# 范围

- 删除 `web/apps/console/src/components/Lockscreen/` 整个目录（Lockscreen.vue、Recharge.vue、index.ts）。
- 删除 `web/apps/console/src/store/modules/screenLock.ts`，并清理 `store/types.ts`、`store/mutation-types.ts` 中的关联定义（IS-SCREENLOCKED、IStore.screenLock）。
- 删除仅被 Lockscreen.vue 使用的 hooks：`src/hooks/useBattery.ts`、`useOnline.ts`、`useTime.ts`。
- 修改 `web/apps/console/src/App.vue`：移除 idle 计时（`timekeeping`、mousedown 监听）、`v-if="!isLock"` 卸载逻辑、Lockscreen 挂载与相关 import/computed。
- 修改 `web/apps/console/src/layout/components/Header/index.vue` 与 `components.ts`：移除"锁屏"图标项、LockOutlined 注册与 setLock 调用。
- console 应用构建（type-check/build）通过，现有其他功能不受影响。

# 非目标

- 不改动 kid 端（无空闲锁屏实现；其 PIN 暴力破解锁定 `lockedUntil` 属于登录限流，保留）。
- 不改动后端 server/、web/packages/shared（无锁屏相关接口）。
- 不清理老用户浏览器 localStorage 中残留的 `IS-SCREENLOCKED` key（无害，随浏览器清理自然消失）。
- 不调整登录/会话过期逻辑（Cookie 会话行为保持现状）。

# 验收示例

- A1: console 端用户保持空闲超过原锁屏阈值（1 小时）后，页面不弹出任何锁屏覆盖层，可直接继续操作。
- A2: console 端顶栏（Header）不再显示"锁屏"图标项；仓库中不再存在 Lockscreen 组件、screenLock store 及 useBattery/useOnline/useTime hooks 的源文件。
- A3: console 应用构建（含 type-check）通过，且现有登录、家长端、管理端页面行为不变。

# 约束与不变量

- Cookie 会话 + CSRF 认证行为不变；不新增/修改任何后端接口。
- kid 端与 shared 包零改动。
- 删除后不得残留对已删模块的 import 或类型引用（编译期保证）。

# 决策

- 手动"锁屏"入口（Header 图标）随空闲锁屏一并移除：锁屏 UI 组件是同一实现，且其"输密码解锁"路径在 Vue 迁移后已引用不存在的 `userStore.login`，属已损坏的死代码；仅剩合规选项（整体移除），无需用户选择。
- kid 端声明为"无此功能、零改动"，基于仓库调查事实：kid 端仅有 PIN 暴力破解锁定（服务端限流），与空闲锁屏无关。

# 待解决问题

- （已确认）CONFIRM: 用户于 2026-09-15 确认目标、范围、验收与非目标，进入实现。

# 验证预期

- `pnpm --filter @cutegoals/console run build`（或等价 type-check + build）成功。
- grep 验证 `Lockscreen`、`screenLock`、`IS-SCREENLOCKED`、`useBattery`、`useOnline`、`useTime` 在 web/apps/console 中无残留引用。
