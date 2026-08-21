import { history } from 'umi';
import { useApi } from '@shared/hooks/useApi';
import {
  KidPage, KidCard, PointsHero, EmptyState, KidTag, StateView,
} from '@/design/components';
import { ChildAssignment, statusLabel, useChildId } from './types';

export default function HomePage() {
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
  const todayTasks = (assignments?.content ?? []).filter((a) => {
    // 截止时间今天的任务始终展示
    if (a.deadline.startsWith(today)) return true;
    // 重复任务（REPEAT）未取消且未完成时也应展示（重复任务今天也应做）
    if (a.snapshotTemplateTaskType === 'REPEAT' && !a.cancelled && a.status !== 'APPROVED' && a.status !== 'COMPLETED') return true;
    return false;
  });

  return (
    <KidPage>
      <h2 className="kid-page-title">今日任务 🌞</h2>

      <StateView loading={balanceLoading} error={balanceError} onRetry={refetchBalance}>
        <PointsHero points={balance?.balance ?? 0} />
      </StateView>

      <KidCard title="今日任务" emoji="📋">
        <StateView
          loading={assignmentsLoading}
          error={assignmentsError}
          onRetry={refetchAssignments}
        >
          {todayTasks.length === 0 ? (
            <EmptyState emoji="🎈" text="今天没有任务" />
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {todayTasks.map((task) => (
                <KidCard
                  key={task.id}
                  accent={task.overdue ? 'warning' : undefined}
                  className={undefined}
                >
                  <div className="kid-row">
                    <div className="kid-row-main">
                      <span className="kid-row-title">{task.snapshotTemplateName}</span>
                      <span className="kid-row-sub">截止 {task.deadline}</span>
                      {task.overdue && <KidTag color="lemon">已逾期</KidTag>}
                    </div>
                    <div className="kid-row-side">
                      <KidTag color="sky">{statusLabel(task.status.toLowerCase())}</KidTag>
                      <span className="kid-row-sub">+{task.snapshotDifficultyReward} 积分</span>
                    </div>
                  </div>
                </KidCard>
              ))}
            </div>
          )}
        </StateView>
      </KidCard>

      <div className="kid-shortcut-grid">
        <button type="button" className="kid-shortcut" onClick={() => history.push('/child/tasks')}>
          <span className="kid-shortcut-emoji" aria-hidden="true">✅</span>
          全部任务
        </button>
        <button type="button" className="kid-shortcut" onClick={() => history.push('/child/exchanges')}>
          <span className="kid-shortcut-emoji" aria-hidden="true">📜</span>
          兑换历史
        </button>
      </div>
    </KidPage>
  );
}
