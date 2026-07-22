/**
 * 全局 dayjs 插件注册（side-effect 模块）。
 *
 * 该模块必须在 antd `DatePicker` / `Calendar` 等基于 rc-picker 的组件首次
 * 渲染之前被加载。推荐入口：`src/app.tsx` 顶部的 `import '@/shared/dayjs'`。
 *
 * 背景：dayjs 默认不加载任何插件；rc-picker 内部会调用 dayjs 实例上的
 * `.weekday(...)`（见 `getWeekFirstDate`），未注册 `weekday` 插件时会抛出
 * `TypeError: ... weekday is not a function`，从而触发「Something went wrong」
 * 错误边界。此处集中注册前端运行时所需的全部 dayjs 插件。
 *
 * 注册清单：
 *  - weekday         —— rc-picker / antd DatePicker 内部依赖（必修）
 *  - weekOfYear      —— TaskCalendar 周编号计算依赖
 *  - customParseFormat —— 支持以指定解析字符串构造 dayjs（如 `dayjs(s, 'YYYY-MM-DD')`）
 *  - localizedFormat —— antd DatePicker 面板本地化格式
 *  - advancedFormat  —— antd 高级格式化 token
 */
import dayjs from 'dayjs';
import weekday from 'dayjs/plugin/weekday';
import weekOfYear from 'dayjs/plugin/weekOfYear';
import customParseFormat from 'dayjs/plugin/customParseFormat';
import localizedFormat from 'dayjs/plugin/localizedFormat';
import advancedFormat from 'dayjs/plugin/advancedFormat';

dayjs.extend(weekday);
dayjs.extend(weekOfYear);
dayjs.extend(customParseFormat);
dayjs.extend(localizedFormat);
dayjs.extend(advancedFormat);

export default dayjs;
