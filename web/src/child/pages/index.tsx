import { useState } from 'react';
import { Link, history } from 'umi';
import { getClient } from '@shared/api';
import { useAuth } from '@shared/auth';
import { App, Button, Card, Col, Empty, Input, Modal, Result, Row, Segmented, Space, Spin, Tag, Tooltip, Typography } from 'antd';
const { TextArea } = Input;
import { useApi, useFormField } from '@shared/hooks/useApi';
import { useLowPerformance, useOnline, useReducedMotion } from '@shared/theme';

export interface ChildAssignment {
  id: number;
  childId: number;
  templateId: number;
  difficultyId: number;
  status: string;
  deadline: string;
  snapshotTemplateName: string;
  snapshotDifficultyReward: number;
  snapshotTemplateDescription?: string;
  snapshotTemplateTaskType?: string;
  overdue: boolean;
  rejectionReason?: string;
  version?: number;
  canSubmit: boolean;
  cancelled: boolean;
  submissionBlockReason: 'MAX_REACHED' | 'POINTS_CAP_REACHED' | null;
  snapshotTemplateAllowResubmit: boolean | null;
  snapshotTemplateMaxSubmissions: number | null;
  snapshotTemplatePointsCap: number | null;
}

interface Prize {
  id: number;
  name: string;
  description: string;
  pointsCost: number;
  stock: number;
}

interface PageResult<T> {
  content: T[];
  page: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
}

/** 与家长端 usePaginatedData 等价的分页消费包装：从 {content,...} 中读取 items。 */
function usePaginatedData<T>(path: string) {
  const [page] = useState(1);
  const [pageSize] = useState(20);
  const separator = path.includes('?') ? '&' : '?';
  const { data, loading, error, refetch } = useApi<PageResult<T>>(
    `${path}${separator}page=${page}&pageSize=${pageSize}`,
  );
  return {
    items: data?.content ?? [],
    total: data?.totalElements ?? 0,
    loading,
    error,
    refetch,
  };
}

interface BlindBox {
  id: number;
  name: string;
  cost: number;
  availabilityVersion: string;
}

interface BlindBoxCandidate {
  prizeId: number;
  prizeName: string;
  probability: number;
}

interface Exchange {
  id: number;
  childId: number;
  familyId: number;
  type: 'DIRECT' | 'BLIND_BOX';
  status: string;
  costPoints: number;
  idempotencyKey: string;
  prizeId: number | null;
  poolId: number | null;
  resultPrizeId: number | null;
  fulfilledAt: string | null;
  fulfilledBy: number | null;
  cancelledAt: string | null;
  cancelledBy: number | null;
  createdAt: string;
  updatedAt: string;
  targetName: string;
}

interface BlindBoxResult {
  prizeId: number;
  prizeName: string;
}

function generateIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

/** Map API status values to Chinese labels */
function statusLabel(s: string): string {
  const map: Record<string, string> = {
    completed: '已完成', approved: '已通过', rejected: '已驳回',
    cancelled: '已取消', active: '启用', disabled: '停用',
    pending: '待处理', locked: '已锁定', success: '成功', failed: '失败',
    submitted: '已提交',
  };
  return map[s?.toLowerCase()] ?? s;
}

/** 兑换状态 → 中文标签 + Tag 颜色（与家长端保持一致） */
const EXCHANGE_STATUS_META: Record<string, { label: string; color: string }> = {
  PENDING_FULFILLMENT: { label: '待核销', color: 'orange' },
  FULFILLED: { label: '已核销', color: 'green' },
  CANCELLED: { label: '已取消', color: 'default' },
};

function exchangeStatusLabel(status: string): string {
  return EXCHANGE_STATUS_META[status]?.label ?? status;
}

function exchangeStatusColor(status: string): string | undefined {
  return EXCHANGE_STATUS_META[status]?.color;
}

function useChildId(): number | undefined {
  const { account } = useAuth();
  const id = account?.childId ?? account?.accountId;
  return typeof id === 'number' ? id : undefined;
}

function StateHandler({
  children,
  loading,
  error,
  onRetry,

}: {
  loading: boolean;
  error?: { message?: string };
  onRetry?: () => void;
  children: React.ReactNode;
}) {
  const online = useOnline();
  if (!online) return <Result status="warning" title="当前处于离线状态" subTitle="请检查网络连接，恢复后重试" extra={onRetry ? <Button onClick={onRetry}>重试</Button> : undefined} />;
  if (loading) return <div style={{ display: 'flex', justifyContent: 'center', padding: '48px 0' }}><Spin /></div>;
  if (error) return <Result status="error" title="加载失败" subTitle={error.message} extra={onRetry ? <Button onClick={onRetry}>重试</Button> : undefined} />;
  return <>{children}</>;
}

export function ChildHomePage() {
  const childId = useChildId();
  const {
    data: assignments,
    loading: assignmentsLoading,
    error: assignmentsError,
    refetch: refetchAssignments,
  } = useApi<{ content: ChildAssignment[] }>(childId ? `/task-assignments?childId=${childId}&pageSize=100` : '');
  const {
    data: balance,
    loading: balanceLoading,
    error: balanceError,
    refetch: refetchBalance,
  } = useApi<{ balance: number }>(childId ? `/points/balance/${childId}` : '');

  const today = new Date().toISOString().split('T')[0];
  const todayTasks = (assignments?.content ?? []).filter((a) => a.deadline.startsWith(today));

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Row justify="space-between" align="middle">
        <Typography.Title level={4} style={{ margin: 0 }}>今日任务</Typography.Title>
        <Button onClick={() => history.push('/child/prizes')}>去商城</Button>
      </Row>

      <Card title="积分余额">
        <StateHandler loading={balanceLoading} error={balanceError} onRetry={refetchBalance}>
          <Typography.Title level={2} style={{ margin: 0 }}>{balance?.balance ?? 0} 积分</Typography.Title>
        </StateHandler>
      </Card>

      <Card title="今日任务">
        <StateHandler
          loading={assignmentsLoading}
          error={assignmentsError}
          onRetry={refetchAssignments}
        >
          {todayTasks.length === 0 ? (
            <Empty description="今天没有任务" />
          ) : (
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              {todayTasks.map((task) => (
                <Card key={task.id} size="small" style={task.overdue ? { borderLeft: '4px solid #faad14' } : {}}>
                  <Row justify="space-between" align="top">
                    <div>
                      <Typography.Text strong>{task.snapshotTemplateName}</Typography.Text>
                      <br />
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>截止 {task.deadline}</Typography.Text>
                      {task.overdue && (
                        <div>
                          <Typography.Text style={{ fontSize: 12, fontWeight: 600, color: '#faad14' }}>已逾期</Typography.Text>
                        </div>
                      )}
                    </div>
                    <Space direction="vertical" size={2} align="end">
                      <Tag>{statusLabel(task.status.toLowerCase())}</Tag>
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>+{task.snapshotDifficultyReward} 积分</Typography.Text>
                    </Space>
                  </Row>
                </Card>
              ))}
            </Space>
          )}
        </StateHandler>
      </Card>

      <Row gutter={[12, 12]}>
        <Col span={12}>
          <Link to="/child/tasks" style={{ display: 'block' }}>
            <Card hoverable>
              <Typography.Text strong style={{ display: 'block', textAlign: 'center' }}>全部任务</Typography.Text>
            </Card>
          </Link>
        </Col>
        <Col span={12}>
          <Link to="/child/exchanges" style={{ display: 'block' }}>
            <Card hoverable>
              <Typography.Text strong style={{ display: 'block', textAlign: 'center' }}>兑换历史</Typography.Text>
            </Card>
          </Link>
        </Col>
      </Row>
    </Space>
  );
}

function getBlockReasonText(reason: 'MAX_REACHED' | 'POINTS_CAP_REACHED' | null | undefined): string | undefined {
  if (reason === 'MAX_REACHED') return '已达到最大提交次数';
  if (reason === 'POINTS_CAP_REACHED') return '已达到积分上限';
  return undefined;
}

type TaskFilter = '进行中' | '已逾期' | '已提交' | '已完成' | '已取消';

function isFutureTask(deadline: string): boolean {
  const today = new Date().toISOString().split('T')[0];
  return deadline.slice(0, 10) > today;
}

/**
 * 仅作用于 ChildTasksPage 的 Segmented 选中态高亮：
 * 主题色背景 (#0284c7) + 白字。通过作用域 className 限制影响范围，
 * 避免全局 childTheme.components.Segmented token 导致的渲染回归。
 */
const childTaskSegmentedCss = `
.child-task-segmented .ant-segmented-item-selected {
  background-color: #0284c7;
  color: #ffffff;
}
.child-task-segmented .ant-segmented-item-selected .ant-segmented-item-label {
  color: #ffffff;
}
`;

export function ChildTasksPage() {
  const childId = useChildId();
  const { message } = App.useApp();
  const {
    data: assignments,
    loading,
    error,
    refetch,
  } = useApi<{ content: ChildAssignment[] }>(childId ? `/task-assignments?childId=${childId}&pageSize=100` : '');
  const [submittingId, setSubmittingId] = useState<number | null>(null);
  const [active, setActive] = useState<ChildAssignment | null>(null);
  const notes = useFormField();
  const [submitting, setSubmitting] = useState(false);
  const [filter, setFilter] = useState<TaskFilter>('进行中');

  const allTasks = assignments?.content ?? [];

  /** 五分类：优先级从高到低 */
  const cancelledTasks = allTasks.filter((a) => a.cancelled);
  const completedTasks = allTasks.filter((a) => !a.cancelled && (a.status === 'APPROVED' || a.status === 'COMPLETED'));
  const submittedTasks = allTasks.filter((a) => !a.cancelled && a.status === 'SUBMITTED');
  const overdueTasks = allTasks.filter((a) =>
    !a.cancelled && a.status !== 'APPROVED' && a.status !== 'COMPLETED' && a.status !== 'SUBMITTED' && a.overdue && a.snapshotTemplateTaskType !== 'REPEAT',
  );
  const activeTasks = allTasks.filter((a) => {
    if (a.cancelled) return false;
    if (a.status === 'APPROVED' || a.status === 'COMPLETED') return false;
    if (a.status === 'SUBMITTED') return false;
    // 非 REPEAT 任务的逾期归入「已逾期」，REPEAT 任务留在进行中
    if (a.overdue && a.snapshotTemplateTaskType !== 'REPEAT') return false;
    return true;
  });

  /** 进行中分类：截止日期升序，未来任务排末尾 */
  const sortedActiveTasks = [...activeTasks].sort((a, b) => {
    const aFuture = isFutureTask(a.deadline);
    const bFuture = isFutureTask(b.deadline);
    if (aFuture !== bFuture) return aFuture ? 1 : -1;
    return a.deadline.localeCompare(b.deadline);
  });

  const getFilteredTasks = (f: TaskFilter) => {
    switch (f) {
      case '已取消': return cancelledTasks;
      case '已完成': return completedTasks;
      case '已提交': return submittedTasks;
      case '已逾期': return overdueTasks;
      case '进行中': return sortedActiveTasks;
    }
  };

  const filteredTasks = getFilteredTasks(filter);

  const openSubmit = (task: ChildAssignment) => {
    setActive(task);
    notes.reset();
  };

  const handleSubmit = async () => {
    if (!active || !childId) return;
    setSubmitting(true);
    setSubmittingId(active.id);
    const response = await getClient().post('/task-review/submissions', {
      assignmentId: active.id,
      notes: notes.value,
      idempotencyKey: generateIdempotencyKey(),
    });
    setSubmitting(false);
    setSubmittingId(null);
    if (response.error) {
      message.error(response.error.message ?? '提交失败');
    } else {
      message.success('提交成功，等待家长审核');
      setActive(null);
      await refetch();
    }
  };

  /** 渲染单个任务卡片 */
  const renderTaskCard = (task: ChildAssignment) => {
    const future = isFutureTask(task.deadline);
    const isBlockedMax = !task.canSubmit && (task.submissionBlockReason === 'MAX_REACHED' || task.submissionBlockReason === 'POINTS_CAP_REACHED');
    const showSubmitButton = (task.status === 'PENDING' || task.status === 'REJECTED') && !isBlockedMax;
    const submitDisabled = future ? true : !task.canSubmit;

    return (
      <Card key={task.id} size="small" style={
        task.overdue
          ? { borderLeft: '4px solid #faad14' }
          : !task.canSubmit
            ? { borderLeft: '4px solid #d9d9d9' }
            : {}
      }>
        <Row justify="space-between" align="top">
          <div style={{ flex: 1 }}>
            <Typography.Text strong>{task.snapshotTemplateName}</Typography.Text>
            <br />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>截止 {task.deadline}</Typography.Text>
            {future && filter === '进行中' && (
              <div style={{ marginTop: 2 }}>
                <Typography.Text style={{ fontSize: 12, color: '#999' }}>未开始</Typography.Text>
              </div>
            )}
            <div style={{ marginTop: 4 }}>
              <Space size={8}>
                <Tag>{statusLabel(task.status.toLowerCase())}</Tag>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>+{task.snapshotDifficultyReward} 积分</Typography.Text>
              </Space>
            </div>
            {task.rejectionReason && (
              <div style={{ marginTop: 8, padding: 8, background: '#fffbe6', borderRadius: 6, fontSize: 12, color: '#ad6800' }}>
                驳回原因：{task.rejectionReason}
              </div>
            )}
          </div>
          <div style={{ marginLeft: 8 }}>
            {isBlockedMax ? (
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>该任务已达最大提交次数</Typography.Text>
            ) : showSubmitButton ? (
              <Space direction="vertical" size="small">
                {task.status === 'PENDING' && (
                  <Tooltip title={!task.canSubmit ? getBlockReasonText(task.submissionBlockReason) : undefined}>
                    <Button size="small" onClick={() => openSubmit(task)} loading={submittingId === task.id}
                      disabled={submitDisabled}>
                      提交
                    </Button>
                  </Tooltip>
                )}
                {task.status === 'REJECTED' && (
                  <Tooltip title={!task.canSubmit ? getBlockReasonText(task.submissionBlockReason) : undefined}>
                    <Button size="small" onClick={() => openSubmit(task)} loading={submittingId === task.id}
                      disabled={submitDisabled}>
                      重新提交
                    </Button>
                  </Tooltip>
                )}
              </Space>
            ) : null}
          </div>
        </Row>
      </Card>
    );
  };

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Typography.Title level={4} style={{ margin: 0 }}>我的任务</Typography.Title>
      <style>{childTaskSegmentedCss}</style>
      <Segmented
        className="child-task-segmented"
        options={['进行中', '已逾期', '已提交', '已完成', '已取消']}
        value={filter}
        onChange={(v) => setFilter(v as TaskFilter)}
        block
      />
      <StateHandler loading={loading} error={error} onRetry={refetch}>
        {filteredTasks.length === 0 ? (
          <Empty description="暂无任务" />
        ) : (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            {filteredTasks.map(renderTaskCard)}
          </Space>
        )}
      </StateHandler>

      <Modal
        open={!!active}
        onCancel={() => {
          setActive(null);
          notes.reset();
        }}
        title={active?.status === 'REJECTED' ? '重新提交任务' : '提交任务'}
        okText={active?.status === 'REJECTED' ? '重新提交' : '提交'}
        cancelText="取消"
        okButtonProps={{ loading: submitting, disabled: !notes.value.trim() }}
        onOk={handleSubmit}
      >
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Typography.Text>{active?.snapshotTemplateName}</Typography.Text>
          <div>
            <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>完成情况说明</Typography.Text>
            <TextArea
              id="submit-notes"
              placeholder="说说你是怎么完成任务的"
              {...notes.inputProps}
            />
          </div>
        </Space>
      </Modal>
    </Space>
  );
}

export function ChildPrizesPage() {
  const childId = useChildId();
  const { message } = App.useApp();
  const {
    data: balance,
    loading: balanceLoading,
    error: balanceError,
    refetch: refetchBalance,
  } = useApi<{ balance: number }>(childId ? `/points/balance/${childId}` : '');
  const {
    items: prizes,
    loading: prizesLoading,
    error: prizesError,
    refetch: refetchPrizes,
  } = usePaginatedData<Prize>('/prizes/available');
  const [selected, setSelected] = useState<Prize | null>(null);
  const [exchanging, setExchanging] = useState(false);

  const exchange = async () => {
    if (!selected || !childId) return;
    if ((balance?.balance ?? 0) < selected.pointsCost) {
      message.warning('积分不足');
      return;
    }
    setExchanging(true);
    const response = await getClient().post('/exchanges/direct', {
      prizeId: selected.id,
      childId,
      idempotencyKey: generateIdempotencyKey(),
    });
    setExchanging(false);
    setSelected(null);
    if (response.error) {
      message.error(response.error.message ?? '兑换失败');
    } else {
      message.success('兑换成功，请等待家长兑现');
      await refetchBalance();
      await refetchPrizes();
      history.push('/child/exchanges');
    }
  };

  const canAfford = (prize: Prize) => (balance?.balance ?? 0) >= prize.pointsCost;

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Typography.Title level={4} style={{ margin: 0 }}>积分商城</Typography.Title>
      <Card>
        <Row justify="space-between" align="middle">
          <Typography.Text type="secondary">当前积分</Typography.Text>
          <Typography.Title level={4} style={{ margin: 0 }}>{balance?.balance ?? 0}</Typography.Title>
        </Row>
      </Card>
      <StateHandler loading={prizesLoading || balanceLoading} error={prizesError ?? balanceError} onRetry={refetchPrizes}>
        {prizes.length === 0 ? (
          <Empty description="商城暂无奖品" />
        ) : (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            {prizes.map((prize) => (
              <Card key={prize.id} size="small">
                <Row justify="space-between" align="top">
                  <div>
                    <Typography.Text strong>{prize.name}</Typography.Text>
                    <br />
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>{prize.description}</Typography.Text>
                    <br />
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      {prize.pointsCost} 积分 · 库存 {prize.stock}
                    </Typography.Text>
                  </div>
                  <Button
                    size="small"
                    disabled={!canAfford(prize) || prize.stock <= 0}
                    onClick={() => setSelected(prize)}
                  >
                    兑换
                  </Button>
                </Row>
              </Card>
            ))}
          </Space>
        )}
      </StateHandler>

      <Modal
        open={!!selected}
        onCancel={() => setSelected(null)}
        title="确认兑换"
        footer={
          <Space>
            <Button onClick={() => setSelected(null)} disabled={exchanging}>取消</Button>
            <Button type="primary" onClick={exchange} loading={exchanging}>确认兑换</Button>
          </Space>
        }
      >
        <Typography.Text>
          确定要用 {selected?.pointsCost} 积分兑换「{selected?.name}」吗？
        </Typography.Text>
      </Modal>
    </Space>
  );
}

export function ChildBlindBoxesPage() {
  const childId = useChildId();
  const { message } = App.useApp();
  const reducedMotion = useReducedMotion();
  const lowPerf = useLowPerformance();
  const {
    data: balance,
    loading: balanceLoading,
    error: balanceError,
    refetch: refetchBalance,
  } = useApi<{ balance: number }>(childId ? `/points/balance/${childId}` : '');
  const {
    data: boxes,
    loading: boxesLoading,
    error: boxesError,
    refetch: refetchBoxes,
  } = useApi<{ items: BlindBox[] }>('/blind-boxes/available');
  const [selected, setSelected] = useState<BlindBox | null>(null);
  const {
    data: candidates,
    loading: candidatesLoading,
    error: candidatesError,
    refetch: refetchCandidates,
  } = useApi<BlindBoxCandidate[]>(selected ? `/blind-boxes/${selected.id}/candidates` : '');
  const [confirming, setConfirming] = useState(false);
  const [opening, setOpening] = useState(false);
  const [result, setResult] = useState<BlindBoxResult | null>(null);
  const [reconfirm, setReconfirm] = useState(false);

  const openBox = async () => {
    if (!selected || !childId) return;
    if ((balance?.balance ?? 0) < selected.cost) {
      message.warning('积分不足');
      return;
    }
    setConfirming(false);
    setOpening(true);

    const shouldAnimate = !reducedMotion && !lowPerf;
    if (shouldAnimate) {
      await new Promise((resolve) => setTimeout(resolve, 1200));
    }

    const response = await getClient().post('/exchanges/blind-box', {
      blindBoxId: selected.id,
      childId,
      availabilityVersion: selected.availabilityVersion,
      idempotencyKey: generateIdempotencyKey(),
    });
    setOpening(false);
    if (response.error) {
      if (response.error.error_code === 'BLIND_BOX_POOL_CHANGED') {
        message.warning('盲盒奖池已变更，请重新确认');
        setReconfirm(true);
        await refetchCandidates();
        return;
      }
      message.error(response.error.message ?? '开启失败');
      return;
    }
    const data = response.data as BlindBoxResult | undefined;
    setResult(data ?? { prizeId: 0, prizeName: '神秘奖品' });
    await refetchBalance();
  };

  const closeResult = () => {
    setResult(null);
    setSelected(null);
    setReconfirm(false);
  };

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Typography.Title level={4} style={{ margin: 0 }}>惊喜盲盒</Typography.Title>
      <Card>
        <Row justify="space-between" align="middle">
          <Typography.Text type="secondary">当前积分</Typography.Text>
          <Typography.Title level={4} style={{ margin: 0 }}>{balance?.balance ?? 0}</Typography.Title>
        </Row>
      </Card>
      <StateHandler loading={boxesLoading || balanceLoading} error={boxesError ?? balanceError} onRetry={refetchBoxes}>
        {(boxes?.items ?? []).length === 0 ? (
          <Empty description="暂无盲盒" />
        ) : (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            {(boxes?.items ?? []).map((box) => (
              <Card
                key={box.id}
                size="small"
                hoverable
                style={selected?.id === box.id ? { borderColor: '#1677ff' } : {}}
                onClick={() => {
                  setSelected(box);
                  setReconfirm(false);
                }}
              >
                <Typography.Text strong>{box.name}</Typography.Text>
                <br />
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>{box.cost} 积分</Typography.Text>
              </Card>
            ))}
          </Space>
        )}
      </StateHandler>

      {selected && (
        <Card title="候选概率">
          <StateHandler loading={candidatesLoading} error={candidatesError} onRetry={refetchCandidates}>
            {(candidates ?? []).length === 0 ? (
              <Typography.Text type="secondary">暂无候选奖品</Typography.Text>
            ) : (
              <Space direction="vertical" size="small" style={{ width: '100%' }}>
                {(candidates ?? []).map((c) => (
                  <Card key={c.prizeId} size="small" style={{ background: '#f5f5f5' }}>
                    <Row justify="space-between" align="middle">
                      <Typography.Text>{c.prizeName}</Typography.Text>
                      <Typography.Text strong>{(c.probability * 100).toFixed(1)}%</Typography.Text>
                    </Row>
                  </Card>
                ))}
              </Space>
            )}
            <div style={{ marginTop: 12 }}>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                版本：{selected.availabilityVersion.slice(0, 8)}…
                {reconfirm && <Typography.Text style={{ color: '#faad14', marginLeft: 8 }}>奖池已更新，请重新确认</Typography.Text>}
              </Typography.Text>
            </div>
            <Button
              type="primary"
              block
              style={{ marginTop: 16 }}
              onClick={() => setConfirming(true)}
              disabled={(balance?.balance ?? 0) < selected.cost}
            >
              开启盲盒（{selected.cost} 积分）
            </Button>
          </StateHandler>
        </Card>
      )}

      <Modal
        open={confirming}
        onCancel={() => setConfirming(false)}
        title="确认开启盲盒"
        footer={
          <Space>
            <Button onClick={() => setConfirming(false)} disabled={opening}>取消</Button>
            <Button type="primary" onClick={openBox} loading={opening}>确认开启</Button>
          </Space>
        }
      >
        <Typography.Text>
          确定要花费 {selected?.cost} 积分开启「{selected?.name}」吗？结果由概率随机产生。
        </Typography.Text>
      </Modal>

      <Modal
        open={!!result}
        onCancel={closeResult}
        title="盲盒结果"
        footer={<Button type="primary" block onClick={closeResult}>收下奖品</Button>}
      >
        <Space direction="vertical" size="middle" align="center" style={{ width: '100%', padding: '16px 0' }}>
          <span style={{ fontSize: 64 }} role="img" aria-hidden="true">🎁</span>
          <Typography.Title level={4} style={{ margin: 0 }}>{result?.prizeName}</Typography.Title>
          <Typography.Text type="secondary">已记录到兑换历史</Typography.Text>
        </Space>
      </Modal>
    </Space>
  );
}

export function ChildExchangesPage() {
  const childId = useChildId();
  const {
    items: exchanges,
    loading,
    error,
    refetch,
  } = usePaginatedData<Exchange>(childId ? `/exchanges?childId=${childId}` : '');

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Typography.Title level={4} style={{ margin: 0 }}>兑换历史</Typography.Title>
      <StateHandler loading={loading} error={error} onRetry={refetch}>
        {exchanges.length === 0 ? (
          <Empty description="还没有兑换记录" />
        ) : (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            {exchanges.map((ex) => (
              <Card key={ex.id} size="small">
                <Row justify="space-between" align="top">
                  <div>
                    <Typography.Text strong>{ex.targetName}</Typography.Text>
                    <br />
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      {ex.type === 'BLIND_BOX' ? '盲盒' : '奖品'} · {ex.costPoints} 积分 · {ex.createdAt}
                    </Typography.Text>
                    <div style={{ marginTop: 4 }}>
                      <Tag color={exchangeStatusColor(ex.status)}>{exchangeStatusLabel(ex.status)}</Tag>
                    </div>
                  </div>
                </Row>
              </Card>
            ))}
          </Space>
        )}
      </StateHandler>
    </Space>
  );
}

export default ChildHomePage;
