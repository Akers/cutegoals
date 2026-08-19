import { useApi } from '@shared/hooks/useApi';
import {
  KidPage, KidCard, KidTag, EmptyState, StateView,
} from '@/design/components';
import {
  Exchange, EXCHANGE_STATUS_META, PageResult, useChildId,
} from './types';

export default function ExchangesPage() {
  const childId = useChildId();
  const {
    data,
    loading,
    error,
    refetch,
  } = useApi<PageResult<Exchange>>(childId ? `/exchanges?childId=${childId}&page=1&pageSize=20` : '');
  const exchanges = data?.content ?? [];

  return (
    <KidPage>
      <h2 className="kid-page-title">兑换历史 📜</h2>
      <StateView loading={loading} error={error} onRetry={refetch}>
        {exchanges.length === 0 ? (
          <EmptyState emoji="🗂️" text="还没有兑换记录" />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {exchanges.map((ex) => {
              const meta = EXCHANGE_STATUS_META[ex.status];
              return (
                <KidCard key={ex.id}>
                  <div className="kid-row">
                    <div className="kid-row-main">
                      <span className="kid-row-title">
                        <span aria-hidden="true">{ex.type === 'BLIND_BOX' ? '🎉' : '🎁'} </span>
                        {ex.targetName}
                      </span>
                      <span className="kid-row-sub">
                        {ex.type === 'BLIND_BOX' ? '盲盒' : '奖品'} · {ex.costPoints} 积分 · {ex.createdAt}
                      </span>
                      <div>
                        <KidTag color={meta?.color ?? 'muted'}>{meta?.label ?? ex.status}</KidTag>
                      </div>
                    </div>
                  </div>
                </KidCard>
              );
            })}
          </div>
        )}
      </StateView>
    </KidPage>
  );
}
