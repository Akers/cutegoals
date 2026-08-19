import { useState } from 'react';
import { App } from 'antd';
import { getClient } from '@shared/api';
import { useApi } from '@shared/hooks/useApi';
import { useLowPerformance, useReducedMotion } from '@shared/theme';
import {
  KidPage, KidCard, KidButton, EmptyState, KidModal, PointsPill, StateView,
} from '@/design/components';
import {
  BlindBox, BlindBoxCandidate, BlindBoxResult, generateIdempotencyKey, useChildId,
} from './types';

export default function BlindBoxesPage() {
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
    <KidPage>
      <h2 className="kid-page-title">惊喜盲盒 🎉</h2>

      <KidCard>
        <div className="kid-row" style={{ alignItems: 'center' }}>
          <span className="kid-row-sub">当前积分</span>
          <PointsPill points={balance?.balance ?? 0} />
        </div>
      </KidCard>

      <StateView loading={boxesLoading || balanceLoading} error={boxesError ?? balanceError} onRetry={refetchBoxes}>
        {(boxes?.items ?? []).length === 0 ? (
          <EmptyState emoji="📦" text="暂无盲盒" />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {(boxes?.items ?? []).map((box) => (
              <KidCard
                key={box.id}
                selected={selected?.id === box.id}
                onClick={() => {
                  setSelected(box);
                  setReconfirm(false);
                }}
              >
                <div className="kid-row" style={{ alignItems: 'center' }}>
                  <div className="kid-row-main">
                    <span className="kid-row-title">
                      <span aria-hidden="true">🎁 </span>{box.name}
                    </span>
                    <span className="kid-row-sub">{box.cost} 积分</span>
                  </div>
                  {selected?.id === box.id && (
                    <span style={{ fontSize: 24 }} aria-hidden="true">👆</span>
                  )}
                </div>
              </KidCard>
            ))}
          </div>
        )}
      </StateView>

      {selected && (
        <KidCard title="候选概率" emoji="🔮">
          <StateView loading={candidatesLoading} error={candidatesError} onRetry={refetchCandidates}>
            {(candidates ?? []).length === 0 ? (
              <span className="kid-row-sub">暂无候选奖品</span>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {(candidates ?? []).map((c) => (
                  <div
                    key={c.prizeId}
                    className="kid-row"
                    style={{
                      alignItems: 'center',
                      background: 'var(--kid-grape-soft)',
                      borderRadius: 'var(--kid-radius-md)',
                      padding: '10px 14px',
                    }}
                  >
                    <span style={{ fontWeight: 700, fontSize: 14 }}>{c.prizeName}</span>
                    <span style={{ fontWeight: 800, fontSize: 14, color: 'var(--kid-grape)' }}>
                      {(c.probability * 100).toFixed(1)}%
                    </span>
                  </div>
                ))}
              </div>
            )}
            <div style={{ marginTop: 12 }}>
              <span className="kid-row-sub">
                版本：{selected.availabilityVersion.slice(0, 8)}…
                {reconfirm && (
                  <span style={{ color: 'var(--kid-warning)', marginLeft: 8, fontWeight: 700 }}>
                    奖池已更新，请重新确认
                  </span>
                )}
              </span>
            </div>
            <KidButton
              block
              onClick={() => setConfirming(true)}
              disabled={(balance?.balance ?? 0) < selected.cost}
            >
              🎊 开启盲盒（{selected.cost} 积分）
            </KidButton>
          </StateView>
        </KidCard>
      )}

      <KidModal
        open={confirming}
        onClose={() => setConfirming(false)}
        title="确认开启盲盒"
        emoji="🎉"
        footer={
          <>
            <KidButton variant="ghost" onClick={() => setConfirming(false)} disabled={opening}>
              取消
            </KidButton>
            <KidButton onClick={openBox} loading={opening}>
              确认开启
            </KidButton>
          </>
        }
      >
        <span style={{ fontSize: 15, fontWeight: 600 }}>
          确定要花费 {selected?.cost} 积分开启「{selected?.name}」吗？结果由概率随机产生。
        </span>
      </KidModal>

      <KidModal
        open={!!result}
        onClose={closeResult}
        title="盲盒结果"
        emoji="🎊"
        footer={
          <KidButton block variant="mint" onClick={closeResult}>
            收下奖品
          </KidButton>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 12, padding: '16px 0' }}>
          <span style={{ fontSize: 72 }} role="img" aria-hidden="true" className="kid-pop">🎁</span>
          <span style={{ fontSize: 20, fontWeight: 800 }}>{result?.prizeName}</span>
          <span className="kid-row-sub">已记录到兑换历史</span>
        </div>
      </KidModal>

      <KidModal open={opening} title="开启中…" emoji="🎁">
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 12, padding: '24px 0' }}>
          <span style={{ fontSize: 64 }} aria-hidden="true" className="kid-shake">🎁</span>
          <span className="kid-row-sub">正在为你摇一摇…</span>
        </div>
      </KidModal>
    </KidPage>
  );
}
