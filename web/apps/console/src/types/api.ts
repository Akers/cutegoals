/**
 * Domain types for the CuteGoals console, ported from the old React console
 * (web/apps/console/src/parent/pages/index.tsx + src/admin/pages/index.tsx)
 * and verified against the backend controllers in server/**.
 *
 * IDs are typed as string | number because the old app serializes them via
 * String(id); backend returns numeric IDs.
 */

// ===== Common =====

/** Backend response envelope: server/common/.../dto/ApiResponse.java */
export interface ApiEnvelope<T = unknown> {
  code: string;
  message: string;
  data: T;
  request_id: string | null;
}

export interface PageResult<T> {
  content: T[];
  page: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
}

export type Id = string | number;

// ===== Auth =====

/** Roles exactly as returned by the backend (no ROLE_ prefix). */
export type BackendRole = 'PARENT' | 'CHILD' | 'INSTANCE_ADMIN';

export interface LoginParams {
  phone: string;
  password: string;
}

export interface LoginResult {
  accountId: string;
  phone: string;
  roles: string[];
  familyId: string | null;
  expiresIn: number;
}

export interface MeResult {
  accountId: string;
  roles: string[];
  childId?: string | null;
  familyId?: string | null;
}

export interface AdminInitParams {
  token: string;
  phone: string;
  password: string;
}

// ===== Instance / Admin =====

export interface InstanceStatus {
  instanceStatus?: string;
  initialized?: boolean;
  version?: string;
  lastBackupAt?: string | null;
  lastBackupStatus?: string | null;
  recoveryDrill?: {
    ranAt: string;
    success: boolean;
    rpo: number;
    rto: number;
  } | null;
}

export interface ConfigEntry {
  key: string;
  type: string;
  description: string;
  masked: boolean;
  value: string | null;
  configured: boolean;
}

/** PUT /api/admin/config: only changed entries, { key: value } */
export type ConfigUpdatePayload = Record<string, string | null>;

export interface Account {
  id: string;
  phone: string;
  status: string;
  roles: string[];
  createdAt: string;
  updatedAt: string;
}

export interface AuditLog {
  id: string;
  actorId: string;
  actorType: string;
  eventType: string;
  result: string;
  objectType: string;
  objectId: string;
  summary: string;
  requestId: string;
  createdAt: string;
}

export interface AccountListParams {
  /** 1-based page (backend default 1) */
  page?: number;
  pageSize?: number;
}

export interface AuditLogParams {
  /** 1-based page (backend default 1) */
  page?: number;
  pageSize?: number;
  actorId?: string;
  eventType?: string;
  result?: string;
  startDate?: string;
  endDate?: string;
}

export interface HealthData {
  status: string;
  initialized?: boolean;
  version?: string;
  buildTime?: string;
  buildCommit?: string;
  database?: { status: string; type?: string } | null;
  backup?: {
    lastBackupTime?: string | null;
    lastBackupStatus?: string | null;
    nextScheduledBackup?: string | null;
  } | null;
  recoveryDrill?: {
    lastRecoveryDrillTime?: string | null;
    lastRecoveryDrillStatus?: string | null;
    rpoSeconds?: number | null;
    rtoSeconds?: number | null;
  } | null;
  rpoWarning?: boolean | null;
  rpoWarningMessage?: string | null;
}

// ===== Family =====

export interface FamilyMember {
  id: string;
  accountId: string;
  nickname?: string;
  role: 'PARENT' | 'CHILD';
  phone?: string;
}

export interface ChildProfile {
  id: string;
  nickname: string;
  pin?: string;
  birthday?: string;
  birthYear?: number | null;
  avatar?: string;
  status?: string;
}

export interface DeviceBinding {
  id: string;
  deviceId: string;
  status: string;
  boundBy: string;
  credential?: string;
  createdAt: string;
}

export interface Family {
  id: string;
  name: string;
  members: FamilyMember[];
  children: ChildProfile[];
  devices?: DeviceBinding[];
}

export interface Invitation {
  id: string;
  inviteePhone: string;
  status: string;
  createdAt: string;
}

export interface FamilyUpdateParams {
  name: string;
}

export interface CreateInvitationParams {
  inviteePhone: string;
}

export interface CreateChildParams {
  nickname: string;
  pin?: string;
  birthday?: string;
}

export interface UpdateChildParams {
  nickname?: string;
  pin?: string;
  birthday?: string;
  status?: string;
}

export interface PagedParams {
  page: number;
  pageSize: number;
}

// ===== Task =====

export type TaskType = 'LIMITED' | 'REPEAT' | 'STANDING';

export interface Difficulty {
  id: string;
  name: string;
  rewardPoints: number;
  displayOrder: number;
  enabled: boolean;
}

export interface TaskTemplate {
  id: string;
  name: string;
  description: string;
  category: string;
  difficulties: Difficulty[];
  enabled: boolean;
  version: number;
  recurrenceRule?: string | null;
  taskType: TaskType;
  typeConfig?: Record<string, unknown> | null;
  allowResubmit: boolean;
  maxSubmissions: number | null;
  pointsCap: number | null;
}

/**
 * Create/update payload for task templates. Note: the old console sends
 * allow_resubmit / max_submissions / points_cap in snake_case on the wire
 * (see old console parent/pages/index.tsx template save flow) — payloads are
 * constructed by the page layer, this type stays permissive.
 */
export type TaskTemplatePayload = Partial<Omit<TaskTemplate, 'id'>> & {
  name: string;
  version: number;
} & Record<string, unknown>;

export interface TaskAssignment {
  id: string;
  childId: string;
  templateId: string;
  difficultyId: string;
  status: string;
  deadline: string | null;
  snapshotTemplateName: string;
  snapshotDifficultyName: string;
  snapshotDifficultyReward: number;
  snapshotTemplateDescription?: string;
  snapshotTemplateCategory?: string;
  snapshotTemplateTaskType?: TaskType;
  snapshotTemplateTypeConfig?: Record<string, unknown> | null;
  overdue: boolean;
  version?: number;
  cancelled?: boolean;
  cancelledReason?: string | null;
  snapshotTemplateAllowResubmit: boolean;
  snapshotTemplateMaxSubmissions: number | null;
  snapshotTemplatePointsCap: number | null;
  approvedSubmissionCount?: number;
  earnedPoints?: number;
  canSubmit?: boolean;
  submissionBlockReason?: string | null;
}

export interface CreateAssignmentParams {
  templateId: Id;
  childId: Id;
  difficultyId: Id;
  /** e.g. "YYYY-MM-DDT23:59:59" */
  deadline?: string;
}

export interface BatchAssignmentParams {
  templateId: Id;
  difficultyId: Id;
  startDate: string;
  endDate: string;
  childIds?: Id[];
  idempotencyKey?: string;
}

export interface AssignmentListParams extends PagedParams {
  childId?: Id;
  status?: string;
  startDate?: string;
  endDate?: string;
  taskType?: string;
}

// ===== Review =====

export interface ReviewItem {
  attemptId: string;
  assignmentId: string;
  childNickname: string;
  templateTitle: string;
  submittedAt: string;
  notes?: string;
  isOverdue: boolean;
}

export interface ReviewDecisionParams {
  reason?: string;
}

// ===== Points =====

export interface PointsLedgerEntry {
  id: string;
  amount: number;
  type: string;
  createdAt: string;
  reason?: string;
}

export interface PointsLedger {
  currentBalance: number;
  content: PointsLedgerEntry[];
}

export interface PointsAdjustmentParams {
  childId: Id;
  amount: number;
  reason: string;
  businessRef: string;
}

/** GET /api/points/balance/{childId} */
export interface PointsBalance {
  childId: string;
  balance: number;
  totalEarned: number;
  version: number;
}

/** POST /api/points/adjustments response (ledger row) */
export interface PointsAdjustmentResult {
  id: string;
  childId: string;
  type: string;
  amount: number;
  balanceAfter: number;
  businessRef: string;
  reason: string;
  createdAt: string;
}

// ===== Prize / Blind box =====

export interface Prize {
  id: string;
  name: string;
  description?: string;
  pointsCost: number;
  availableStock: number;
  enabled: boolean;
  prizeType?: string;
  prizeCategory?: string;
  titleImage?: string;
  detailImage?: string;
  validFrom?: string;
  validTo?: string;
  typeConfig?: Record<string, unknown> | null;
}

export interface PrizePayload {
  name: string;
  description?: string;
  pointsCost: number;
  availableStock: number;
  enabled: boolean;
  prizeType?: string;
  prizeCategory?: string;
  titleImage?: string;
  detailImage?: string;
  validFrom?: string;
  validTo?: string;
  typeConfig?: Record<string, unknown> | null;
}

export interface BlindBox {
  id: string;
  name: string;
  cost: number;
  enabled: boolean;
  availabilityVersion?: number;
}

export interface BlindBoxPayload {
  name: string;
  cost: number;
  enabled?: boolean;
}

export interface BlindBoxCandidate {
  prizeId: string;
  prizeName: string;
  probability: number;
}

/** POST /api/blind-boxes/{id}/items */
export interface BlindBoxItemParams {
  prizeId: Id;
  weight?: number;
}

export interface PrizeUploadResult {
  url: string;
}

// ===== Exchange =====

export type ExchangeStatus = 'PENDING_FULFILLMENT' | 'FULFILLED' | 'CANCELLED';

export interface Exchange {
  id: string;
  childId: string;
  familyId: string;
  type: 'DIRECT' | 'BLIND_BOX';
  status: ExchangeStatus;
  costPoints: number;
  idempotencyKey: string;
  prizeId: string;
  poolId: string;
  resultPrizeId: string;
  fulfilledAt: string | null;
  fulfilledBy: string | null;
  cancelledAt: string | null;
  cancelledBy: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ExchangeListParams extends PagedParams {
  status?: string;
  childId?: Id;
}

export interface ExchangeCancelParams {
  reason: string;
}
