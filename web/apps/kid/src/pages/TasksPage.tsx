import { useState } from 'react';
import { App } from 'antd';
import { getClient } from '@shared/api';
import { useApi, useFormField } from '@shared/hooks/useApi';
import {
  KidPage, KidCard, KidButton, KidTag, ChipGroup, EmptyState, KidModal, StateView,
} from '@/design/components';
import {
  ChildAssignment, generateIdempotencyKey, statusLabel, useChildId,
} from './types';

function getBlockReasonText(reason: 'MAX_REACHED' | 'POINTS_CAP_REACHED' | null | undefined): string | undefined {
  if (reason === 'MAX_REACHED') return '已达到最大提交次数';
  if (reason === 'POINTS_CAP_REACHED') return '已达到积分上限';
  return undefined;
}

type TaskFilter = '进行中' | '已逾期' | '已提交' | '已完成' | '已取消';

const FILTERS: readonly TaskFilter[] = ['进行中', '已逾期', '已提交', '已完成', '已取消'];

function isFutureTask(deadline: string): boolean {
  const today = new Date().toISOString().split('T')[0];
  return deadline.slice(0, 10) > today;
}

export default function TasksPage() {
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

  /** 五分类：优先级从高到低（行为契约不变） */
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

  /** 渲染单个任务卡片（童趣呈现，判定逻辑不变） */
  const renderTaskCard = (task: ChildAssignment) => {
    const future = isFutureTask(task.deadline);
    const isBlockedMax = !task.canSubmit && (task.submissionBlockReason === 'MAX_REACHED' || task.submissionBlockReason === 'POINTS_CAP_REACHED');
    const showSubmitButton = (task.status === 'PENDING' || task.status === 'REJECTED') && !isBlockedMax;
    const submitDisabled = future ? true : !task.canSubmit;

    return (
      <KidCard
        key={task.id}
        accent={task.overdue ? 'warning' : !task.canSubmit ? 'muted' : undefined}
      >
        <div className="kid-row">
          <div className="kid-row-main">
            <span className="kid-row-title">{task.snapshotTemplateName}</span>
            <span className="kid-row-sub">截止 {task.deadline}</span>
            {future && filter === '进行中' && (
              <KidTag color="muted">未开始</KidTag>
            )}
            <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
              <KidTag color="sky">{statusLabel(task.status.toLowerCase())}</KidTag>
              <span className="kid-row-sub">+{task.snapshotDifficultyReward} 积分</span>
            </div>
            {task.rejectionReason && (
              <div className="kid-reject-note">
                驳回原因：{task.rejectionReason}
              </div>
            )}
          </div>
          <div className="kid-row-side">
            {isBlockedMax ? (
              <span className="kid-blocked-note">该任务已达最大提交次数</span>
            ) : showSubmitButton ? (
              <>
                {task.status === 'PENDING' && (
                  <span title={!task.canSubmit ? getBlockReasonText(task.submissionBlockReason) : undefined}>
                    <KidButton
                      size="sm"
                      onClick={() => openSubmit(task)}
                      loading={submittingId === task.id}
                      disabled={submitDisabled}
                    >
                      提交
                    </KidButton>
                  </span>
                )}
                {task.status === 'REJECTED' && (
                  <span title={!task.canSubmit ? getBlockReasonText(task.submissionBlockReason) : undefined}>
                    <KidButton
                      size="sm"
                      variant="peach"
                      onClick={() => openSubmit(task)}
                      loading={submittingId === task.id}
                      disabled={submitDisabled}
                    >
                      重新提交
                    </KidButton>
                  </span>
                )}
              </>
            ) : null}
          </div>
        </div>
      </KidCard>
    );
  };

  return (
    <KidPage>
      <h2 className="kid-page-title">我的任务 ✅</h2>

      <ChipGroup
        ariaLabel="任务状态筛选"
        options={FILTERS}
        value={filter}
        onChange={setFilter}
      />

      <StateView loading={loading} error={error} onRetry={refetch}>
        {filteredTasks.length === 0 ? (
          <EmptyState emoji="🎈" text="暂无任务" />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {filteredTasks.map(renderTaskCard)}
          </div>
        )}
      </StateView>

      <KidModal
        open={!!active}
        onClose={() => {
          setActive(null);
          notes.reset();
        }}
        title={active?.status === 'REJECTED' ? '重新提交任务' : '提交任务'}
        emoji={active?.status === 'REJECTED' ? '🔄' : '🚀'}
        footer={
          <>
            <KidButton
              variant="ghost"
              onClick={() => {
                setActive(null);
                notes.reset();
              }}
              disabled={submitting}
            >
              取消
            </KidButton>
            <KidButton onClick={handleSubmit} loading={submitting} disabled={!notes.value.trim()}>
              {active?.status === 'REJECTED' ? '重新提交' : '提交'}
            </KidButton>
          </>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <span className="kid-row-title">{active?.snapshotTemplateName}</span>
          <div>
            <label htmlFor="submit-notes" style={{ display: 'block', fontWeight: 700, marginBottom: 6, fontSize: 14 }}>
              完成情况说明
            </label>
            <textarea
              id="submit-notes"
              placeholder="说说你是怎么完成任务的"
              value={notes.value}
              onChange={notes.inputProps.onChange}
              rows={4}
              style={{
                width: '100%',
                borderRadius: 16,
                border: '2px solid var(--kid-border)',
                padding: 12,
                fontFamily: 'var(--kid-font)',
                fontSize: 15,
                resize: 'vertical',
                boxSizing: 'border-box',
              }}
            />
          </div>
        </div>
      </KidModal>
    </KidPage>
  );
}
