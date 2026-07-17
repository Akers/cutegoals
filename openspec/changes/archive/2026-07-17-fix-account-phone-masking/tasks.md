## 1. 后端新增部分掩码方法

- [x] 1.1 在 `server/common/src/main/java/com/cutegoals/common/util/MaskUtil.java` 新增 public static 方法 `maskPhonePartial(String phone)`：
  - null 或空字符串：原样返回（与 `maskPhone` 一致）
  - 长度 < 7：返回常量 `MASKED`（即 `***MASKED***`）
  - 长度 ≥ 7：返回 `phone.substring(0, 3) + "*".repeat(phone.length() - 6) + phone.substring(phone.length() - 3)`
  - 在类 Javadoc 与方法 Javadoc 中说明：仅用于账号管理等需可识别性的展示场景，日志脱敏继续使用 `maskPhone`
  - 验证：`cd server && mvn -q -pl common compile`
- [x] 1.2 在 `server/common/src/test/java/com/cutegoals/common/util/MaskUtilTest.java` 新增测试：
  - `maskPhonePartialReturnsPartialMaskFor11Digits`：`maskPhonePartial("13612341249")` == `"136*****249"`（5 个星号）
  - `maskPhonePartialReturnsPartialMaskFor7Digits`：`maskPhonePartial("1234567")` == `"123*567"`（1 个星号）
  - `maskPhonePartialReturnsMaskedForShortInput`：`maskPhonePartial("123456")` == `MASKED`
  - `maskPhonePartialReturnsMaskedFor4DigitPin`：`maskPhonePartial("1234")` == `MASKED`（防御短号逆向）
  - `@NullAndEmptySource` 参数化：null/空原样透传
  - `maskPhonePartialContainsNoFullOriginal`：`maskPhonePartial("13612341249")` 不包含完整原始字符串
  - 验证：`cd server && mvn -q -pl common test -Dtest=MaskUtilTest`

## 2. 后端账号列表改用部分掩码

- [x] 2.1 修改 `server/instance-management/src/main/java/com/cutegoals/instancemanagement/service/AccountManagementService.java` line 53：
  - 将 `item.put("phone", MaskUtil.maskPhone(account.getPhone()));` 改为 `item.put("phone", MaskUtil.maskPhonePartial(account.getPhone()));`
  - 保留 `import com.cutegoals.common.util.MaskUtil;`，类 Javadoc/方法 Javadoc 不需要改
  - 验证：`cd server && mvn -q -pl instance-management -am compile`

## 3. 前端移除二次脱敏

- [x] 3.1 修改 `web/src/admin/pages/index.tsx`：
  - 删除 line 174 的 `const mask = (phone: string) => phone.slice(0, 3) + '****' + phone.slice(7);`
  - 修改 line 182 的 `phone` 列：`{ title: '手机号', dataIndex: 'phone', key: 'phone', render: (p: string) => p }`（或直接去掉 `render`）
  - 验证：`cd web && npx tsc -b --noEmit`

## 4. 全量构建与测试

- [x] 4.1 运行后端测试：`cd server && mvn -q test -Dtest='MaskUtilTest,AccountManagement*Test'` 全绿
  - 实测：`mvn -pl common clean test -Dtest=MaskUtilTest` → Tests run: 26, Failures: 0, Errors: 0
  - 实测：`mvn -pl instance-management -am test` → Tests run: 35, Failures: 0, Errors: 0
- [x] 4.2 运行前端类型检查与构建：`cd web && npx tsc -b --noEmit && npm run build`（如 build 不可用仅跑 tsc）通过
  - 实测：`npx tsc -b` exit 0；`npm run build` ✓ built in 5.73s
- [x] 4.3 静态证据：`grep -n "slice(0, 3)" web/src/admin/pages/index.tsx` 无输出；`grep -n "maskPhonePartial" server/instance-management/src/main/java/com/cutegoals/instancemanagement/service/AccountManagementService.java` 命中 line 53

## 5. 根因消除检查

- [x] 5.1 确认 `web/src/admin/pages/index.tsx` 中无 `slice(0, 3) + '****' + slice(7)` 模式，无 `mask` 闭包（`grep -n "const mask = " web/src/admin/pages/index.tsx` 无输出）
- [x] 5.2 确认 `AccountManagementService.getAccounts` 返回的 `phone` 字段不再经过 `maskPhone`（仅 `maskPhonePartial`），日志场景的 `maskPhone` 调用方（InvitationService、StructuredLoggingTest）保持不变
- [x] 5.3 手工演算：以 11 位手机号 `13612341249` 为例，`maskPhonePartial` 返回 `"136*****249"`；前端直接渲染得到 `"136*****249"`，不再出现 `*******ED***`
