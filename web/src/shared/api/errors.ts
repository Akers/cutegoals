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
  [ErrorCodes.POINTS_REFUND_SOURCE_INVALID]: '退款来源不合法',
  [ErrorCodes.POINTS_INVALID_QUERY]: '积分查询参数不合法',

  // EXCHANGE
  [ErrorCodes.EXCHANGE_IDEMPOTENCY_KEY_REQUIRED]: '兑换请求缺少幂等键',
  [ErrorCodes.EXCHANGE_IDEMPOTENCY_CONFLICT]: '兑换幂等键与请求参数冲突',
  [ErrorCodes.EXCHANGE_INVALID_STATE]: '兑换状态不允许当前操作',
  [ErrorCodes.EXCHANGE_TRANSACTION_FAILED]: '兑换事务失败，请重试',
  [ErrorCodes.EXCHANGE_CANCELLATION_FAILED]: '取消失败，请重试',
  [ErrorCodes.EXCHANGE_NOT_FOUND]: '兑换记录不存在',
  [ErrorCodes.EXCHANGE_INVALID_QUERY]: '兑换查询参数不合法',

  // TASK_TEMPLATE
  [ErrorCodes.TASK_TEMPLATE_FORBIDDEN]: '无权访问该任务模板',
  [ErrorCodes.TASK_TEMPLATE_NOT_FOUND]: '任务模板不存在',
  [ErrorCodes.TASK_TEMPLATE_VALIDATION_FAILED]: '任务模板参数不合法',
  [ErrorCodes.TASK_TEMPLATE_REQUIRES_ACTIVE_DIFFICULTY]: '任务模板至少需要一个启用的难度',
  [ErrorCodes.TASK_TEMPLATE_INVALID_RECURRENCE]: '任务模板周期规则不合法',
  [ErrorCodes.TASK_TEMPLATE_VERSION_CONFLICT]: '任务模板版本冲突，请刷新后重试',
  [ErrorCodes.TASK_TEMPLATE_INACTIVE]: '任务模板已停用',
  [ErrorCodes.TASK_TEMPLATE_INVALID_QUERY]: '任务模板查询参数不合法',

  // TASK_ASSIGNMENT
  [ErrorCodes.TASK_ASSIGNMENT_FORBIDDEN]: '无权访问该任务分配',
  [ErrorCodes.TASK_ASSIGNMENT_NOT_FOUND]: '任务分配不存在',
  [ErrorCodes.TASK_ASSIGNMENT_DIFFICULTY_INACTIVE]: '所选难度已停用',
  [ErrorCodes.TASK_ASSIGNMENT_TEMPLATE_INACTIVE]: '任务模板已停用或已删除',
  [ErrorCodes.TASK_ASSIGNMENT_CHILD_NOT_FOUND]: '孩子不存在或不属于当前家庭',
  [ErrorCodes.TASK_ASSIGNMENT_INVALID_DEADLINE]: '截止时间不合法',
  [ErrorCodes.TASK_ASSIGNMENT_IDEMPOTENCY_CONFLICT]: '分配幂等键与请求参数冲突',
  [ErrorCodes.TASK_ASSIGNMENT_INVALID_DATE_RANGE]: '日期范围不合法',
  [ErrorCodes.TASK_ASSIGNMENT_RECURRENCE_NOT_CONFIGURED]: '该模板未配置周期规则',
  [ErrorCodes.TASK_ASSIGNMENT_VERSION_CONFLICT]: '分配版本冲突，请刷新后重试',
  [ErrorCodes.TASK_ASSIGNMENT_NOT_EDITABLE]: '当前状态不允许修改该分配',
  [ErrorCodes.TASK_ASSIGNMENT_ALREADY_APPROVED]: '该分配已被批准，无法操作',
  [ErrorCodes.TASK_ASSIGNMENT_INVALID_QUERY]: '任务分配查询参数不合法',

  // TASK_REVIEW
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
  [ErrorCodes.BLIND_BOX_INVALID_COST]: '盲盒兑换成本不合法',
  [ErrorCodes.BLIND_BOX_EMPTY_POOL]: '盲盒奖池至少需要一个奖品项',
  [ErrorCodes.BLIND_BOX_INVALID_WEIGHT]: '盲盒奖品权重不合法',
  [ErrorCodes.BLIND_BOX_DUPLICATE_PRIZE]: '盲盒奖池不可重复添加同一奖品',
  [ErrorCodes.BLIND_BOX_UNAVAILABLE]: '盲盒奖池暂时无可用奖品',

  // AUTH
  [ErrorCodes.AUTH_UNAUTHENTICATED]: '未登录或会话已过期',
  [ErrorCodes.AUTH_INVALID_TOKEN]: '登录令牌无效',
  [ErrorCodes.AUTH_EXPIRED_TOKEN]: '登录令牌已过期',
  [ErrorCodes.AUTH_INSUFFICIENT_PERMISSIONS]: '权限不足',
  [ErrorCodes.AUTHENTICATION_FAILED]: '手机号或密码错误',
  [ErrorCodes.SMS_LOGIN_NOT_CONFIGURED]: '短信登录未配置，请使用密码登录',
  [ErrorCodes.CHILD_AUTHENTICATION_FAILED]: 'PIN 验证失败',
  [ErrorCodes.PIN_LOCKED]: 'PIN 已锁定，请 15 分钟后重试',
  [ErrorCodes.REFRESH_TOKEN_REUSED]: '刷新令牌已被使用，请重新登录',
  [ErrorCodes.REFRESH_TOKEN_INVALID]: '刷新令牌无效或已过期',
  [ErrorCodes.SESSION_REVOKED]: '会话已撤销，请重新登录',
  [ErrorCodes.DEVICE_NOT_AUTHORIZED]: '设备未授权',
  [ErrorCodes.RESOURCE_NOT_FOUND]: '请求的资源不存在',

  // FAMILY
  [ErrorCodes.SINGLE_FAMILY_ONLY]: '实例只支持一个家庭',
  [ErrorCodes.VALIDATION_FAILED]: '请求参数校验失败',
  [ErrorCodes.INVITATION_NOT_AVAILABLE]: '邀请不可用',
  [ErrorCodes.LAST_ACTIVE_PARENT]: '至少需要一位有效家长',
  [ErrorCodes.PIN_CONFLICT]: '该 PIN 已被其他孩子使用',
  [ErrorCodes.DEVICE_BINDING_NOT_AVAILABLE]: '设备绑定凭据不可用',
  [ErrorCodes.AUDIT_UNAVAILABLE]: '审计写入失败，操作已回滚',

  // INSTANCE
  [ErrorCodes.INSTANCE_ADMIN_REQUIRED]: '需要实例管理员权限',
  [ErrorCodes.LAST_INSTANCE_ADMIN]: '不能移除最后一位实例管理员',
  [ErrorCodes.AUDIT_QUERY_LIMIT_EXCEEDED]: '审计查询超出限制',
  [ErrorCodes.AUDIT_IMMUTABLE]: '审计记录不可修改',
  [ErrorCodes.CONFIGURATION_INVALID]: '系统配置不合法',
  [ErrorCodes.RECOVERY_NOT_AVAILABLE]: '恢复凭据不可用',
  [ErrorCodes.RPO_EXCEEDED]: '恢复点目标已超过 24 小时',
  [ErrorCodes.INITIALIZATION_NOT_ALLOWED]: '实例已初始化，不允许此操作',

  // DEPLOYMENT
  [ErrorCodes.BUILD_DEPENDENCY_UNAVAILABLE]: '构建依赖不可用，请检查网络连接',
  [ErrorCodes.UNSUPPORTED_PLATFORM]: '不支持的目标平台',
  [ErrorCodes.DEPENDENCY_UNHEALTHY]: '依赖服务不健康',
  [ErrorCodes.TLS_REQUIRED]: '生产环境必须启用 HTTPS',
  [ErrorCodes.CONFIG_INVALID]: '配置项不合法',
  [ErrorCodes.BACKUP_FAILED]: '备份失败',
  [ErrorCodes.RESTORE_BACKUP_INVALID]: '备份数据校验失败，无法恢复',
  [ErrorCodes.RESTORE_FAILED]: '恢复流程失败',
  [ErrorCodes.PRE_UPGRADE_BACKUP_FAILED]: '升级前备份失败，已中止升级',
  [ErrorCodes.MIGRATION_FAILED]: '数据迁移失败',
  [ErrorCodes.UPGRADE_HEALTHCHECK_FAILED]: '升级后健康检查失败',
  [ErrorCodes.DOWNGRADE_NOT_SUPPORTED]: '不支持原地降级',

  // WEB_APP (UI-level states)
  [ErrorCodes.UNAUTHENTICATED]: '未登录或会话已过期',
  [ErrorCodes.INSUFFICIENT_POINTS]: '积分不足',

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
