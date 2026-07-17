## 决策

### 决策 1：后端做部分掩码，前端移除二次脱敏

**选择**：方案 B（后端返回部分掩码，前端直接渲染）。

**备选**：
- 方案 A：后端继续返回全掩码 `***MASKED***`，前端移除 `mask` 函数直接渲染——可恢复可读性（统一显示 `***MASKED***`），但管理员仍无法区分不同账号。
- 方案 B：后端返回部分掩码（`136*****249`），前端直接渲染——既满足 spec「脱敏」要求，又恢复可识别性。**✅ 选定**
- 方案 C：后端返回真实手机号，前端做部分掩码——违反 spec「脱敏手机号」要求，API 明文传输敏感字段，安全风险高。❌

**理由**：spec 仅要求「脱敏」，未规定粒度。账号管理场景下管理员需要识别账号，部分掩码是行业惯例（银行/电商账号管理）。日志场景的 `maskPhone` 全掩码保持不变，避免日志泄露可识别度更高的前缀+后缀组合。

### 决策 2：新增 `maskPhonePartial` 方法，不改 `maskPhone`

**选择**：在 `MaskUtil` 新增独立方法 `maskPhonePartial`，不修改原有 `maskPhone` 语义。

**理由**：
- `maskPhone` 当前在 `InvitationService.maskPhone`（日志/审计场景）、`MaskUtilTest`、`StructuredLoggingTest` 中使用，全掩码语义为日志深度防御而设计。
- 修改 `maskPhone` 语义会触发 `MaskUtilTest.maskPhoneReturnsMasked`、`maskPhoneContainsNoOriginalValue` 等用例失败，且语义飘移会引发日志中可识别度上升的安全顾虑。
- 新增独立方法 `maskPhonePartial` 仅在账号列表场景使用，单一职责清晰，向后兼容。

### 决策 3：部分掩码的掩码规则

**规则**：
- 长度 ≥ 7：`前 3 位 + '*' × (len - 6) + 后 3 位`（11 位手机号即 `136*****249`，5 个星号；7 位即 `123*789`，1 个星号）
- 长度 1–6：回退全掩码 `***MASKED***`（避免短号暴露过多原始字符，如 4 位 PIN 不应返回 `1**4`）
- null/空：原样透传（与 `maskPhone` 一致）

**理由**：长度阈值 7 保证至少有 1 个掩码字符且前/后缀只暴露有限信息；回退到全掩码确保短号不被逆向。

## 风险与回滚

- **风险**：极低。
  - `maskPhonePartial` 为新增方法，零调用方影响。
  - `AccountManagementService` 改动仅影响 `getAccounts` 的 `phone` 字段值，API 字段结构不变。
  - 前端 `mask` 函数移除是行为修正，不会影响其他页面（`mask` 是组件内闭包，非导出）。
- **回滚**：如线上发现新格式不符合合规要求，恢复 `AccountManagementService` line 53 的 `maskPhonePartial` → `maskPhone`，前端恢复 `mask` 函数（但需要改为渲染全掩码值）即可回到原状（即 `***MASKED***`，不会再出现 `*******ED***`）。
