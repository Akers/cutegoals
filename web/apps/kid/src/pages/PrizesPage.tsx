import { useState } from 'react';
import { App } from 'antd';
import { history } from 'umi';
import { getClient } from '@shared/api';
import { useApi } from '@shared/hooks/useApi';
import {
  KidPage, KidCard, KidButton, EmptyState, KidModal, PointsPill, StateView,
} from '@/design/components';
import {
  PageResult, Prize, generateIdempotencyKey, useChildId,
} from './types';

export default function PrizesPage() {
  const childId = useChildId();
  const { message } = App.useApp();
  const {
    data: balance,
    loading: balanceLoading,
    error: balanceError,
    refetch: refetchBalance,
  } = useApi<{ balance: number }>(childId ? `/points/balance/${childId}` : '');
  const {
    data: prizesData,
    loading: prizesLoading,
    error: prizesError,
    refetch: refetchPrizes,
  } = useApi<PageResult<Prize>>('/prizes/available?page=1&pageSize=20');
  const prizes = prizesData?.content ?? [];
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
    <KidPage>
      <h2 className="kid-page-title">积分商城 🎁</h2>

      <KidCard>
        <div className="kid-row" style={{ alignItems: 'center' }}>
          <span className="kid-row-sub">当前积分</span>
          <PointsPill points={balance?.balance ?? 0} />
        </div>
      </KidCard>

      <StateView loading={prizesLoading || balanceLoading} error={prizesError ?? balanceError} onRetry={refetchPrizes}>
        {prizes.length === 0 ? (
          <EmptyState emoji="🛍️" text="商城暂无奖品" />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {prizes.map((prize) => (
              <KidCard key={prize.id}>
                <div className="kid-row">
                  <div className="kid-row-main">
                    <span className="kid-row-title">
                      <span aria-hidden="true">🎁 </span>{prize.name}
                    </span>
                    <span className="kid-row-sub">{prize.description}</span>
                    <span className="kid-row-sub">
                      {prize.pointsCost} 积分 · 库存 {prize.stock}
                    </span>
                  </div>
                  <div className="kid-row-side">
                    <KidButton
                      size="sm"
                      variant="peach"
                      disabled={!canAfford(prize) || prize.stock <= 0}
                      onClick={() => setSelected(prize)}
                    >
                      兑换
                    </KidButton>
                  </div>
                </div>
              </KidCard>
            ))}
          </div>
        )}
      </StateView>

      <KidModal
        open={!!selected}
        onClose={() => setSelected(null)}
        title="确认兑换"
        emoji="🎁"
        footer={
          <>
            <KidButton variant="ghost" onClick={() => setSelected(null)} disabled={exchanging}>
              取消
            </KidButton>
            <KidButton variant="peach" onClick={exchange} loading={exchanging}>
              确认兑换
            </KidButton>
          </>
        }
      >
        <span style={{ fontSize: 15, fontWeight: 600 }}>
          确定要用 {selected?.pointsCost} 积分兑换「{selected?.name}」吗？
        </span>
      </KidModal>
    </KidPage>
  );
}
