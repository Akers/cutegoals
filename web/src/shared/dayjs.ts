/**
 * 全局 dayjs 插件注册（side-effect 模块）。
 *
 * 该模块必须在 antd `DatePicker` / `Calendar` 等基于 rc-picker 的组件首次
 * 渲染之前被加载。推荐入口：`src/app.tsx` 顶部的 `import '@/shared/dayjs'`。
 *
 * 背景：dayjs 默认不加载任何插件；rc-picker 内部会调用 dayjs 实例上的
 * `.weekday(...)` / `.localeData()` / `.week()` / `.weekYear()` 等方法
 * （见 `@rc-component/picker/src/generate/dayjs.ts`），未注册对应插件时
 * 抛 `TypeError: clone.xxx is not a function`，触发「Something went wrong」
 * 错误边界。此处集中注册前端运行时所需的全部 dayjs 插件。
 *
 * 注册清单（对齐 rc-picker 官方 6 插件集）：
 *  - weekday         —— rc-picker getWeekDay 调用 `.weekday(0)`
 *  - localeData      —— rc-picker getWeekDay / getShortWeekDays / getShortMonths
 *                       / getWeekFirstDay 调用 `.localeData().firstDayOfWeek()`
 *                       / `.weekdaysMin()` / `.monthsShort()`
 *  - weekOfYear      —— rc-picker getWeek / WeekPicker 调用 `.week()`
 *  - weekYear        —— rc-picker `YYYY-wo` 解析调用 `.weekYear()`
 *  - customParseFormat —— 支持以指定解析字符串构造 dayjs（如 `dayjs(s, 'YYYY-MM-DD')`）
 *  - advancedFormat  —— rc-picker `wo` 等 token；antd 高级格式化
 *
 * 另保留 localizedFormat 供业务侧本地化格式使用（非 rc-picker 必需）。
 */
import dayjs from 'dayjs';
import weekday from 'dayjs/plugin/weekday';
import localeData from 'dayjs/plugin/localeData';
import weekOfYear from 'dayjs/plugin/weekOfYear';
import weekYear from 'dayjs/plugin/weekYear';
import customParseFormat from 'dayjs/plugin/customParseFormat';
import localizedFormat from 'dayjs/plugin/localizedFormat';
import advancedFormat from 'dayjs/plugin/advancedFormat';

dayjs.extend(weekday);
dayjs.extend(localeData);
dayjs.extend(weekOfYear);
dayjs.extend(weekYear);
dayjs.extend(customParseFormat);
dayjs.extend(localizedFormat);
dayjs.extend(advancedFormat);

export default dayjs;
