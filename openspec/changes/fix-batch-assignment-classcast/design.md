## 修复方案

### 根因

`TaskAssignmentService.createBatchAssignments` 第 162 行执行 `for (Long childId : childIds)` 时，实际从 `Map<String, Object>` 取出的 `childIds` 是 `List<Integer>`（Jackson 默认将能放入 int 范围的 JSON 数字解析为 `Integer`）。Java 在泛型擦除的增强 for 循环中，调用 `Iterator.next()` 返回 `Integer`，再自动拆箱/装箱转换为 `Long` 时触发 `ClassCastException`。

### 修改点

在 `TaskAssignmentService` 中新增私有方法 `extractLongList(Map<String, Object>, String)`，将 `childIds` 按 `List<?>` 读取，对每个元素调用 `((Number) item).longValue()`，返回 `List<Long>`。用该方法替换第 137-138 行的直接 `(List<Long>) request.get("childIds")` 强转。

### 边界

- `childIds` 为 `null` 或空列表时仍按现有校验抛出 `VALIDATION_FAILED`。
- 单个 `childId` 元素为 `Integer` 或 `Long` 时均可正确转换。
- 不改动 `templateId`/`difficultyId` 的 `extractLong` 处理，因为它们已经是 `Number.longValue()`。
- 不改动 `isSameBatchRequest` 的语义；其当前不比较 `childIds` 是既有的幂等设计，本次修复不扩展。

## 测试

- 在 `TaskAssignmentServiceTest` 中为批量分配补充一个 `childIds` 为整数列表的测试，验证不再抛 `ClassCastException` 且能创建分配。
- 运行 `mvn -pl :task -am test` 确认全部测试通过。
