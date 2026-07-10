import { ErrorCodes } from './types';

/**
 * User-facing message mapping for stable error codes (§5.2).
 * Keys use the ErrorCodes const values; unknown codes fall back to
 * a generic message.
 */
export const ERROR_MESSAGES: Record<string, string> = {
  // POINTS
  [ErrorCodes.POINTS_FORBIDDEN]: '无权访问该积分账户',
  [ErrorCodes.POINTS_ACCOUNT_NOT_FOUND]: '积分账户不存在',
  [ErrorCodes.POINTS_INVALID_TRANSACTION]: '积分交易参数不合法',
  [ErrorCodes.POINTS_LEDGER_IMMUTABLE]: '积分流水不可修改',
  [ErrorCodes.POINTS_REFERENCE_CONFLICT]: '积分业务引用冲突',
  [ErrorCodes.POINTS_INSUFFICIENT_BALANCE]: '积分余额不足',
  [ErrorCodes.POINTS_ACCOUNT_CONFLICT]: '积分账户并发冲突，请重试',
  [ErrorCodes.POINTS_SPEND_SOURCE_INVALID]: '积分支出来源无效',
  [ErrorCodes.POINTS_ALREADY_REFUNDED]: '该笔积分已退款',
  [ErrorCodes.POINTS_ADJUST_REASON_REQUIRED]: '积分调整必须填写原因',

  // EXCHANGE
  [ErrorCodes.EXCHANGE_IDEMPOTENCY_KEY_REQUIRED]: '兑换请求缺少幂等键',
  [ErrorCodes.EXCHANGE_IDEMPOTENCY_CONFLICT]: '兑换幂等键与请求参数冲突',
  [ErrorCodes.EXCHANGE_INVALID_STATE]: '兑换状态不允许当前操作',
  [ErrorCodes.EXCHANGE_TRANSACTION_FAILED]: '兑换事务失败，请重试',
  [ErrorCodes.EXCHANGE_CANCELLATION_FAILED]: '取消失败，请重试',
  [ErrorCodes.EXCHANGE_NOT_FOUND]: '兑换记录不存在',
  [ErrorCodes.EXCHANGE_INVALID_QUERY]: '兑换查询参数不合法',

  // TASK
  [ErrorCodes.TASK_REVIEW_FORBIDDEN]: '无权审核该任务',
  [ErrorCodes.TASK_REVIEW_NOT_FOUND]: '审核记录不存在',
  [ErrorCodes.TASK_SUBMISSION_VALIDATION_FAILED]: '任务提交内容不合法',
  [ErrorCodes.TASK_REVIEW_INVALID_STATE]: '任务状态不允许当前操作',
  [ErrorCodes.TASK_SUBMISSION_IDEMPOTENCY_CONFLICT]: '提交幂等键与请求冲突',
  [ErrorCodes.TASK_SUBMISSION_LATE_NOT_ALLOWED]: '该任务不允许迟交',
  [ErrorCodes.TASK_REVIEW_REASON_REQUIRED]: '驳回必须填写原因',
  [ErrorCodes.TASK_REVIEW_VALIDATION_FAILED]: '审核参数不合法',
  [ErrorCodes.TASK_REVIEW_IDEMPOTENCY_CONFLICT]: '审核幂等键与请求冲突',
  [ErrorCodes.TASK_REVIEW_ALREADY_DECIDED]: '该提交已被审核',
  [ErrorCodes.TASK_REVIEW_STALE_ATTEMPT]: '审核目标不是当前提交',
  [ErrorCodes.TASK_ASSIGNMENT_CANCELLED]: '该任务已取消',
  [ErrorCodes.TASK_REVIEW_INVALID_QUERY]: '审核查询参数不合法',
  [ErrorCodes.TASK_REVIEW_HISTORY_IMMUTABLE]: '审核历史不可修改',

  // PRIZE
  [ErrorCodes.PRIZE_INVALID_POINTS_COST]: '奖品积分价格不合法',
  [ErrorCodes.PRIZE_INVALID_STOCK]: '奖品库存数量不合法',
  [ErrorCodes.PRIZE_NOT_FOUND]: '奖品不存在',
  [ErrorCodes.PRIZE_OUT_OF_STOCK]: '奖品已售罄',

  // BLIND_BOX
  [ErrorCodes.BLIND_BOX_NOT_FOUND]: '盲盒奖池不存在',
  [ErrorCodes.BLIND_BOX_POOL_CHANGED]: '盲盒奖池已变更，请重新确认',

  // AUTH
  [ErrorCodes.AUTH_UNAUTHENTICATED]: '未登录或会话已过期',
  [ErrorCodes.AUTH_INVALID_TOKEN]: '登录令牌无效',
  [ErrorCodes.AUTH_EXPIRED_TOKEN]: '登录令牌已过期',
  [ErrorCodes.AUTH_INSUFFICIENT_PERMISSIONS]: '权限不足',

  // INSTANCE
  [ErrorCodes.INSTANCE_ADMIN_REQUIRED]: '需要实例管理员权限',
  [ErrorCodes.LAST_INSTANCE_ADMIN]: '不能移除最后一位实例管理员',
  [ErrorCodes.AUDIT_QUERY_LIMIT_EXCEEDED]: '审计查询超出限制',

  // General
  [ErrorCodes.CAPABILITY_NOT_SUPPORTED]: '该功能在当前版本中不可用',
  [ErrorCodes.FORBIDDEN]: '没有权限执行此操作',
  [ErrorCodes.RATE_LIMITED]: '请求过于频繁，请稍后重试',
  [ErrorCodes.INTERNAL_ERROR]: '服务器内部错误，请稍后重试',
};

/** Default message when error_code is unknown */
export const UNKNOWN_ERROR_MESSAGE = '发生未知错误，请稍后重试';

/**
 * Resolve a stable error code to a user-facing message.
 */
export function getErrorMessage(errorCode: string): string {
  return ERROR_MESSAGES[errorCode] ?? UNKNOWN_ERROR_MESSAGE;
}
