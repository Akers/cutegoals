import { Alova } from '@/utils/http/alova';
import type {
  BatchAssignmentParams,
  AssignmentListParams,
  CreateAssignmentParams,
  Id,
  PageResult,
  PagedParams,
  TaskAssignment,
  TaskTemplate,
  TaskTemplatePayload,
} from '@/types/api';

// ===== 任务模板 =====
// Source: server/task/.../controller/TaskTemplateController.java

/**
 * GET /api/task-templates — 模板列表（分页；支持 enabled/keyword/category/taskType/includeDeleted）
 */
export function listTemplates(
  params?: Partial<PagedParams> & {
    enabled?: boolean;
    keyword?: string;
    category?: string;
    taskType?: string;
    includeDeleted?: boolean;
  }
) {
  return Alova.Get<PageResult<TaskTemplate>>('/task-templates', {
    params,
  });
}

/** GET /api/task-templates/{id} — 模板详情 */
export function getTemplate(id: Id) {
  return Alova.Get<TaskTemplate>(`/task-templates/${id}`);
}

/** POST /api/task-templates — 创建模板 */
export function createTemplate(payload: TaskTemplatePayload) {
  return Alova.Post<TaskTemplate>('/task-templates', payload);
}

/** PUT /task-templates/{id} — 更新模板（需携带最新 version） */
export function updateTemplate(id: Id, payload: TaskTemplatePayload) {
  return Alova.Put<TaskTemplate>(`/task-templates/${id}`, payload);
}

/** PUT /task-templates/{id}/enabled — 启用/停用模板 { enabled } */
export function setTemplateEnabled(id: Id, enabled: boolean) {
  return Alova.Put<TaskTemplate>(`/task-templates/${id}/enabled`, { enabled });
}

/** DELETE /task-templates/{id} — 软删除模板 */
export function deleteTemplate(id: Id) {
  return Alova.Delete<void>(`/task-templates/${id}`);
}

// ===== 任务分配 =====
// Source: server/task/.../controller/TaskAssignmentController.java

/** POST /api/task-assignments — 为单个孩子创建任务分配 */
export function createAssignment(params: CreateAssignmentParams) {
  return Alova.Post<TaskAssignment>('/task-assignments', params);
}

/** POST /api/task-assignments/batch — 按日期范围批量创建分配 */
export function createAssignmentsBatch(params: BatchAssignmentParams) {
  return Alova.Post<TaskAssignment[]>('/task-assignments/batch', params);
}

/** GET /api/task-assignments — 分配列表（分页） */
export function listAssignments(params?: Partial<AssignmentListParams>) {
  return Alova.Get<PageResult<TaskAssignment>>('/task-assignments', {
    params,
  });
}

/** GET /api/task-assignments/{id} — 分配详情 */
export function getAssignment(id: Id) {
  return Alova.Get<TaskAssignment>(`/task-assignments/${id}`);
}

/** 单日任务聚合数据（GET /api/task-assignments/calendar 返回项） */
export interface CalendarDayData {
  total: number;
  pending: number;
  submitted: number;
  approved: number;
  rejected: number;
  cancelled: number;
  overdue: number;
  taskTypes: { LIMITED: number; REPEAT: number; STANDING: number };
}

/** GET /api/task-assignments/calendar — 按日聚合的任务数（单月面板） */
export interface TaskCalendarData {
  year: number;
  month: number;
  days: Record<string, CalendarDayData>;
}

/** GET /api/task-assignments/calendar?year=&month= — 家长任务日历 */
export function getTaskCalendar(year: number, month: number) {
  return Alova.Get<TaskCalendarData>('/task-assignments/calendar', {
    params: { year, month },
  });
}
