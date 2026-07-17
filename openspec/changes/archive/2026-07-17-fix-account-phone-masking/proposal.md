## Why

`/admin/accounts` 账号管理页表格的「手机号」列显示为乱码串 `*******ED***`，管理员无法区分不同账号，账号管理功能实质不可用。

根因是 **前后端双重脱敏冲突**：

1. 后端 `AccountManagementService.getAccounts()`（`server/instance-management/.../AccountManagementService.java:53`）已经把每个账号的 `phone` 字段用 `MaskUtil.maskPhone(account.getPhone())` 全脱敏为常量 `***MASKED***`（长度 12）。
2. 前端 `web/src/admin/pages/index.tsx:174` 定义 `const mask = (phone: string) => phone.slice(0, 3) + '****' + phone.slice(7);`，又在表格 `phone` 列 `render`（line 182）对该字符串再次切片脱敏。
3. 代入 `"***MASKED***"`：`slice(0, 3)` = `"***"`，`slice(7)` = `"ED***"`（"M=3,A=4,S=5,K=6,E=7,D=8"），拼接结果为 `"***" + "****" + "ED***"` = `"*******ED***"`，与用户报告完全一致。

spec 要求（`openspec/specs/instance-management/spec.md:81`）只说「脱敏手机号」，未规定脱敏粒度。日志场景的 `MaskUtil.maskPhone()` 全掩码语义继续保留（防御深度，避免日志泄露前缀+后缀可识别度），账号列表改用「部分掩码」以恢复可识别性。

## What Changes

- 在 `MaskUtil` 新增 `maskPhonePartial(String phone)` 方法：长度 ≥ 7 时返回 `前3位 + '*' * (len-6) + 后3位`（11 位手机号即 `136*****249`）；长度 < 7 或为 null/空时回退到 null/empty 透传或 `***MASKED***`。
- `AccountManagementService.getAccounts()` 把 `item.put("phone", MaskUtil.maskPhone(...))` 改为 `MaskUtil.maskPhonePartial(...)`，使账号列表 API 返回部分掩码后的手机号。
- 前端 `web/src/admin/pages/index.tsx` 删除组件内 `mask` 闭包与 `render: (p) => mask(p)`，`phone` 列直接渲染后端返回值，避免二次脱敏。
- 不改动 `MaskUtil.maskPhone` 原有全掩码语义，避免影响日志、审计等其他调用点。

## Capabilities

### New Capabilities

无。本次仅修复显示 bug，不引入新能力。

### Modified Capabilities

无。`instance-management` capability 的「当前实例账号启停」Requirement 验收场景（`spec.md:79-82`）要求「返回分页的非秘密账号标识、脱敏手机号、角色、启停状态和必要时间信息」——部分掩码仍属脱敏，验收语义不变，无需 delta spec。

## Impact

- 受影响代码：
  - `server/common/src/main/java/com/cutegoals/common/util/MaskUtil.java`（新增 1 个 `maskPhonePartial` 方法，不动原有 `maskPhone`/`mask`）
  - `server/common/src/test/java/com/cutegoals/common/util/MaskUtilTest.java`（新增部分掩码用例）
  - `server/instance-management/src/main/java/com/cutegoals/instancemanagement/service/AccountManagementService.java`（line 53 改用 `maskPhonePartial`）
  - `web/src/admin/pages/index.tsx`（删除 `mask` 闭包与 `phone` 列的 `render`，line 174、182）
- 无 API 契约字段变更（`phone` 字段仍存在，仅字符串内容从 `***MASKED***` 变为 `136*****249` 格式）。
- 无数据库 schema 变更，无依赖变更，无 breaking change。
- 风险：极低。
  - `maskPhonePartial` 是新增方法，原有 `maskPhone`/`mask` 调用点零影响。
  - 账号列表 API 改动仅影响 `phone` 字段的展示值，spec 仍满足「脱敏」要求。
  - 短号（< 7 位）回退到全掩码 `***MASKED***`，避免异常输入暴露过多原始字符。
