# Task 11.2: Refactor Recurrence Rule Model to frequency + trigger_day

## 问题

当前 `task_recurrence_rule` 表使用简化 `rule_type`（DAILY/WEEKDAYS/WEEKENDS/CUSTOM_WEEKDAYS），而 spec 和计划要求丰富的 `frequency`（DAILY/WEEKLY/MONTHLY/YEARLY）+ `trigger_day` 结构。数据应作为 JSON 存储在 `task_template.type_config`（已由 11.1 添加）。

## 要求

1. **V11 迁移**：向 `task_template` 添加 `type_config` 字段。
   - 注意：V10 已添加 `task_type`/`type_config`。需要在 V10 基础上创建 **V11**。
   - 如果有现有周期数据，迁移到 `type_config` JSON。若无可忽略。
   - `type_config` JSON 结构：
     ```json
     {
       "frequency": "DAILY|WEEKLY|MONTHLY|YEARLY",
       "trigger_day": {
         "weekday": null,          // WEEKLY: ISO 1-7 (Mon=1, Sun=7)
         "mode": null,             // MONTHLY: "FIRST_DAY"|"LAST_DAY"|"MID_MONTH"
         "month": null,            // YEARLY: 1-12
         "day": null               // MONTHLY/YEARLY: 1-31，月末自适应
       }
     }
     ```
   - REPEAT 模板必须提供 `type_config`；LIMITED/STANDING 时 `type_config` 可以是 `null` 或 `{"frequency":"NONE"}`。

2. **`RecurrenceRule` entity 废弃**：保留旧 entity 和表但标记 `@Deprecated`。新代码直接读 `TaskTemplate.typeConfig` 获取频率信息。

3. **`nextTriggerDate()` 方法**：在 `server/task/src/main/java/com/cutegoals/task/service/` 下创建 `TaskTemplateFrequencyService`（或类似名称），实现：
   - `nextTriggerDate(typeConfig, fromDate)` → `Optional<LocalDate>`
   - **DAILY**: `fromDate.plusDays(1)`
   - **WEEKLY**: 下一个匹配的 ISO weekday（Mon=1, Sun=7）
   - **MONTHLY**: 下一个月的 FIRST_DAY/LAST_DAY/MID_MONTH（含月末自适应：2 月 30 → 28/29）
   - **YEARLY**: 下一年同月同日（含 2 月 29 日只在闰年触发）
   - **NONE/null**: 返回 `Optional.empty()`

4. **测试**：`TaskTemplateFrequencyServiceTest` 覆盖四种 frequency 的合法/非法/月末自适应/闰年边界。

## 依赖

- 依赖 11.1（`task_type`/`type_config` 列和 TaskTemplate 字段已存在）
- `type_config` JSON 结构需要与 TaskTemplate 中的 `taskType` 字段配合验证（REPEAT 时才需要 frequency）

## 文件范围

- `server/common/src/main/resources/db/migration/V11__add_frequency_to_type_config.sql`
- `server/task/src/main/java/com/cutegoals/task/service/TaskTemplateFrequencyService.java`
- `server/task/src/test/java/com/cutegoals/task/service/TaskTemplateFrequencyServiceTest.java`
- `server/common/src/main/java/com/cutegoals/common/entity/task/TaskRecurrenceRule.java`（加 @Deprecated）
- 可能调整 `TaskTemplate.java` 不需要额外字段（typeConfig 已是 String）

## 验收标准

- V11 迁移可重复执行（使用 `DROP VIEW IF EXISTS` 或 `CREATE TABLE IF NOT EXISTS` 风格）
- `nextTriggerDate()` 在合法输入返回正确日期
- 月末自适应：2 月 30 日 → 2 月 28/29 日
- 闰年自适应：闰年 2 月 29 触发，非闰年不触发
- 非法输入返回 `Optional.empty()`
- 旧 `RecurrenceRule` 代码继续工作（标记为 Deprecated 但不移除）
