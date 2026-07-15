import { useState } from 'react';
import { Link, history } from 'umi';
import { getClient } from '@shared/api';
import { useAuth } from '@shared/auth';
import { App, Button, Card, Empty, Input, Modal, Result, Spin } from 'antd';
import {
  ConfirmModal,
  FormField,
  Layout,
  PageHeader,
  StatusBadge,
} from '@shared/components';
const { TextArea } = Input;
import { useApi, useFormField } from '@shared/hooks/useApi';
import { useLowPerformance, useOnline, useReducedMotion } from '@shared/theme';

interface ChildAssignment {
  id: number;
  templateTitle: string;
  description?: string;
  status: string;
  deadline: string;
  points: number;
  isOverdue: boolean;
  rejectionReason?: string;
}

interface Prize {
  id: number;
  name: string;
  description: string;
  pointsCost: number;
  availableStock: number;
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
  type: 'PRIZE' | 'BLIND_BOX';
  targetName: string;
  pointsCost: number;
  status: string;
  createdAt: string;
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

function PageShell({
  title,
  children,
  actions,
}: {
  title: string;
  children: React.ReactNode;
  actions?: React.ReactNode;
}) {
  return (
    <Layout>
      <PageHeader title={title} actions={actions} />
      {children}
    </Layout>
  );
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
  if (loading) return <Spin className="flex justify-center py-12" />;
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
  } = useApi<{ items: ChildAssignment[] }>(childId ? `/task-assignments?childId=${childId}` : '');
  const {
    data: balance,
    loading: balanceLoading,
    error: balanceError,
    refetch: refetchBalance,
  } = useApi<{ balance: number }>(childId ? `/points/balance/${childId}` : '');

  const today = new Date().toISOString().split('T')[0];
  const todayTasks = (assignments?.items ?? []).filter((a) => a.deadline.startsWith(today));

  return (
    <PageShell
      title="今日任务"
      actions={
        <Button onClick={() => history.push('/child/prizes')}>
          去商城
        </Button>
      }
    >
      <div className="grid grid-cols-1 gap-4">
        <Card title="积分余额">
          <StateHandler loading={balanceLoading} error={balanceError} onRetry={refetchBalance}>
            <div className="text-3xl font-bold text-cg-text">{balance?.balance ?? 0} 积分</div>
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
              <div className="grid grid-cols-1 gap-3">
                {todayTasks.map((task) => (
                  <div
                    key={task.id}
                    className={`cg-card p-4 ${task.isOverdue ? 'border-l-4 border-l-cg-warning' : ''}`}
                  >
                    <div className="flex items-start justify-between">
                      <div>
                        <div className="font-medium text-cg-text">{task.templateTitle}</div>
                        <div className="text-sm text-cg-text-muted">截止 {task.deadline}</div>
                        {task.isOverdue && (
                          <div className="mt-1 text-sm font-semibold text-cg-warning">已逾期</div>
                        )}
                      </div>
                      <div className="flex flex-col items-end gap-1">
                        <StatusBadge status={task.status.toLowerCase()} />
                        <span className="text-sm text-cg-text-muted">+{task.points} 积分</span>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </StateHandler>
        </Card>

        <div className="grid grid-cols-2 gap-3">
          <Link
            to="/child/tasks"
            className="cg-card flex min-h-touch items-center justify-center p-4 text-center font-medium text-cg-text hover:bg-cg-surface-raised"
          >
            全部任务
          </Link>
          <Link
            to="/child/exchanges"
            className="cg-card flex min-h-touch items-center justify-center p-4 text-center font-medium text-cg-text hover:bg-cg-surface-raised"
          >
            兑换历史
          </Link>
        </div>
      </div>
    </PageShell>
  );
}

export function ChildTasksPage() {
  const childId = useChildId();
  const { message } = App.useApp();
  const {
    data: assignments,
    loading,
    error,
    refetch,
  } = useApi<{ items: ChildAssignment[] }>(childId ? `/task-assignments?childId=${childId}` : '');
  const [submittingId, setSubmittingId] = useState<number | null>(null);
  const [active, setActive] = useState<ChildAssignment | null>(null);
  const notes = useFormField();
  const [submitting, setSubmitting] = useState(false);

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

  return (
    <PageShell title="我的任务">
      <StateHandler loading={loading} error={error} onRetry={refetch}>
        {(assignments?.items ?? []).length === 0 ? (
          <Empty description="暂无任务" />
        ) : (
          <div className="grid grid-cols-1 gap-3">
            {(assignments?.items ?? []).map((task) => (
              <div
                key={task.id}
                className={`cg-card p-4 ${task.isOverdue ? 'border-l-4 border-l-cg-warning' : ''}`}
              >
                <div className="flex items-start justify-between">
                  <div className="flex-1">
                    <div className="font-medium text-cg-text">{task.templateTitle}</div>
                    <div className="text-sm text-cg-text-muted">截止 {task.deadline}</div>
                    <div className="mt-1 flex items-center gap-2">
                      <StatusBadge status={task.status.toLowerCase()} />
                      <span className="text-sm text-cg-text-muted">+{task.points} 积分</span>
                    </div>
                    {task.rejectionReason && (
                      <div className="mt-2 rounded-cg-md bg-cg-warning-bg p-2 text-sm text-cg-warning">
                        驳回原因：{task.rejectionReason}
                      </div>
                    )}
                  </div>
                  <div className="ml-2 flex flex-col gap-2">
                    {task.status === 'PENDING' && (
                      <Button size="small" onClick={() => openSubmit(task)} loading={submittingId === task.id}>
                        提交
                      </Button>
                    )}
                    {task.status === 'REJECTED' && (
                      <Button size="small" onClick={() => openSubmit(task)} loading={submittingId === task.id}>
                        重新提交
                      </Button>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </StateHandler>

      <Modal
        open={!!active}
        onCancel={() => {
          setActive(null);
          notes.reset();
        }}
        title={active?.status === 'REJECTED' ? '重新提交任务' : '提交任务'}
      >
        <div className="flex flex-col gap-4">
          <p className="text-cg-text">{active?.templateTitle}</p>
          <FormField label="完成情况说明" htmlFor="submit-notes">
            <TextArea
              id="submit-notes"
              placeholder="说说你是怎么完成任务的"
              {...notes.inputProps}
            />
          </FormField>
          <Button onClick={handleSubmit} loading={submitting} disabled={!notes.value.trim()}>
            {active?.status === 'REJECTED' ? '重新提交' : '提交'}
          </Button>
        </div>
      </Modal>
    </PageShell>
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
    data: prizes,
    loading: prizesLoading,
    error: prizesError,
    refetch: refetchPrizes,
  } = useApi<{ items: Prize[] }>('/prizes/available');
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
    <PageShell title="积分商城">
      <div className="mb-4 flex items-center justify-between rounded-cg-md bg-cg-surface-raised p-3">
        <span className="text-sm text-cg-text-muted">当前积分</span>
        <span className="text-lg font-bold text-cg-text">{balance?.balance ?? 0}</span>
      </div>
      <StateHandler loading={prizesLoading || balanceLoading} error={prizesError ?? balanceError} onRetry={refetchPrizes}>
        {(prizes?.items ?? []).length === 0 ? (
          <Empty description="商城暂无奖品" />
        ) : (
          <div className="grid grid-cols-1 gap-3">
            {(prizes?.items ?? []).map((prize) => (
              <div key={prize.id} className="cg-card p-4">
                <div className="flex items-start justify-between">
                  <div>
                    <div className="font-medium text-cg-text">{prize.name}</div>
                    <p className="text-sm text-cg-text-muted">{prize.description}</p>
                    <div className="mt-1 text-sm">
                      {prize.pointsCost} 积分 · 库存 {prize.availableStock}
                    </div>
                  </div>
                  <Button
                    size="small"
                    disabled={!canAfford(prize) || prize.availableStock <= 0}
                    onClick={() => setSelected(prize)}
                  >
                    兑换
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}
      </StateHandler>

      <ConfirmModal
        isOpen={!!selected}
        onClose={() => setSelected(null)}
        title="确认兑换"
        message={`确定要用 ${selected?.pointsCost} 积分兑换「${selected?.name}」吗？`}
        confirmText="确认兑换"
        confirmVariant="primary"
        onConfirm={exchange}
        isConfirming={exchanging}
      />
    </PageShell>
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
    <PageShell title="惊喜盲盒">
      <div className="mb-4 flex items-center justify-between rounded-cg-md bg-cg-surface-raised p-3">
        <span className="text-sm text-cg-text-muted">当前积分</span>
        <span className="text-lg font-bold text-cg-text">{balance?.balance ?? 0}</span>
      </div>
      <StateHandler loading={boxesLoading || balanceLoading} error={boxesError ?? balanceError} onRetry={refetchBoxes}>
        {(boxes?.items ?? []).length === 0 ? (
          <Empty description="暂无盲盒" />
        ) : (
          <div className="grid grid-cols-1 gap-3">
            {(boxes?.items ?? []).map((box) => (
              <div
                key={box.id}
                className={`cg-card p-4 cursor-pointer ${selected?.id === box.id ? 'ring-2 ring-cg-focus' : ''}`}
                onClick={() => {
                  setSelected(box);
                  setReconfirm(false);
                }}
                role="button"
                tabIndex={0}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    setSelected(box);
                    setReconfirm(false);
                  }
                }}
              >
                <div className="font-medium text-cg-text">{box.name}</div>
                <div className="text-sm text-cg-text-muted">{box.cost} 积分</div>
              </div>
            ))}
          </div>
        )}
      </StateHandler>

      {selected && (
        <Card title="候选概率">
          <StateHandler loading={candidatesLoading} error={candidatesError} onRetry={refetchCandidates}>
            {(candidates ?? []).length === 0 ? (
              <p className="text-cg-text-muted">暂无候选奖品</p>
            ) : (
              <div className="grid grid-cols-1 gap-2">
                {(candidates ?? []).map((c) => (
                  <div key={c.prizeId} className="flex items-center justify-between rounded-cg-md bg-cg-surface-raised p-3">
                    <span className="text-cg-text">{c.prizeName}</span>
                    <span className="font-medium text-cg-text">{(c.probability * 100).toFixed(1)}%</span>
                  </div>
                ))}
              </div>
            )}
            <div className="mt-3 text-xs text-cg-text-muted">
              版本：{selected.availabilityVersion.slice(0, 8)}…
              {reconfirm && <span className="ml-2 text-cg-warning">奖池已更新，请重新确认</span>}
            </div>
            <Button
              className="mt-4 w-full"
              onClick={() => setConfirming(true)}
              disabled={(balance?.balance ?? 0) < selected.cost}
            >
              开启盲盒（{selected.cost} 积分）
            </Button>
          </StateHandler>
        </Card>
      )}

      <ConfirmModal
        isOpen={confirming}
        onClose={() => setConfirming(false)}
        title="确认开启盲盒"
        message={`确定要花费 ${selected?.cost} 积分开启「${selected?.name}」吗？结果由概率随机产生。`}
        confirmText="确认开启"
        onConfirm={openBox}
        isConfirming={opening}
      />

      <Modal
        open={!!result}
        onCancel={closeResult}
        title="盲盒结果"
        footer={
          <Button onClick={closeResult} className="w-full">
            收下奖品
          </Button>
        }
      >
        <div className="flex flex-col items-center gap-3 py-4">
          <div className="text-6xl" aria-hidden="true">
            🎁
          </div>
          <div className="text-xl font-bold text-cg-text">{result?.prizeName}</div>
          <p className="text-sm text-cg-text-muted">已记录到兑换历史</p>
        </div>
      </Modal>
    </PageShell>
  );
}

export function ChildExchangesPage() {
  const childId = useChildId();
  const {
    data: exchanges,
    loading,
    error,
    refetch,
  } = useApi<{ items: Exchange[] }>(childId ? `/exchanges?childId=${childId}` : '');

  return (
    <PageShell title="兑换历史">
      <StateHandler loading={loading} error={error} onRetry={refetch}>
        {(exchanges?.items ?? []).length === 0 ? (
          <Empty description="还没有兑换记录" />
        ) : (
          <div className="grid grid-cols-1 gap-3">
            {(exchanges?.items ?? []).map((ex) => (
              <div key={ex.id} className="cg-card p-4">
                <div className="flex items-start justify-between">
                  <div>
                    <div className="font-medium text-cg-text">{ex.targetName}</div>
                    <div className="text-sm text-cg-text-muted">
                      {ex.type === 'BLIND_BOX' ? '盲盒' : '奖品'} · {ex.pointsCost} 积分 · {ex.createdAt}
                    </div>
                    <div className="mt-1">
                      <StatusBadge status={ex.status.toLowerCase()} />
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </StateHandler>
    </PageShell>
  );
}

export default ChildHomePage;
