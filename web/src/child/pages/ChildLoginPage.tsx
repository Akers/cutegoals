import { useEffect, useState } from 'react';
import { history, useSearchParams } from 'umi';
import { getClient } from '@shared/api';
import { useAuth } from '@shared/auth';
import { Result, Spin } from 'antd';
import { PageHeader } from '@shared/components';
import { useOnline } from '@shared/theme';

const LOCK_DURATION_MS = 15 * 60 * 1000;
const MAX_ATTEMPTS = 5;

export function ChildLoginPage() {
  const [searchParams] = useSearchParams();
  const childId = searchParams.get('childId');
  const deviceId = searchParams.get('deviceId') ?? '';
  const { login } = useAuth();
  const [pin, setPin] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [attempts, setAttempts] = useState(0);
  const [lockedUntil, setLockedUntil] = useState<number | null>(null);
  const [now, setNow] = useState(Date.now());
  const online = useOnline();

  useEffect(() => {
    if (!childId) {
      history.push('/child/bind');
    }
  }, [childId]);

  useEffect(() => {
    if (!lockedUntil) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    if (Date.now() >= lockedUntil) {
      setLockedUntil(null);
      setAttempts(0);
      setError(null);
    }
    return () => window.clearInterval(timer);
  }, [lockedUntil]);

  const remainingMs = lockedUntil ? Math.max(0, lockedUntil - now) : 0;
  const isLocked = remainingMs > 0;
  const minutes = Math.floor(remainingMs / 60000);
  const seconds = Math.floor((remainingMs % 60000) / 1000);

  const appendDigit = (digit: string) => {
    if (isLocked || loading || pin.length >= 6) return;
    setPin((prev) => prev + digit);
  };

  const backspace = () => {
    if (isLocked || loading) return;
    setPin((prev) => prev.slice(0, -1));
  };

  const handleLogin = async () => {
    if (isLocked || !childId || pin.length < 4) return;
    if (!online) {
      setError('当前处于离线状态，请连接网络后重试');
      return;
    }
    setLoading(true);
    setError(null);
    const response = await getClient().post('/auth/child/login', {
      deviceId,
      childId: Number(childId),
      pin,
    });
    setLoading(false);
    if (response.error) {
      const nextAttempts = attempts + 1;
      setAttempts(nextAttempts);
      if (nextAttempts >= MAX_ATTEMPTS) {
        const until = Date.now() + LOCK_DURATION_MS;
        setLockedUntil(until);
        setError(`PIN 已锁定，请 ${minutes} 分 ${seconds} 秒后重试`);
      } else {
        setError(response.error.message ?? 'PIN 错误');
      }
      setPin('');
    } else {
      const data = response.data as {
        childId?: number;
        nickname?: string;
        familyId?: number;
        expiresIn?: number;
      };
      login({
        accountId: data.childId ?? Number(childId),
        nickname: data.nickname,
        roles: ['CHILD'],
        familyId: data.familyId,
        childId: data.childId ?? Number(childId),
        expiresIn: data.expiresIn,
      });
      history.push('/child');
    }
  };

  useEffect(() => {
    if (pin.length === 6) {
      handleLogin();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pin]);

  if (!childId) return <Spin className="flex justify-center py-12" />;
  if (!online) return <Result status="warning" title="离线" subTitle="请连接网络后重试" />;

  return (
    <div className="cg-page flex min-h-screen flex-col items-center justify-center">
      <div className="w-full max-w-sm cg-card p-6">
        <PageHeader title="输入 PIN" subtitle="输入你的 4-6 位家庭 PIN" />
        <div
          className="mb-6 flex h-14 items-center justify-center rounded-cg-lg bg-cg-surface-raised text-2xl font-mono tracking-widest text-cg-text"
          aria-label="PIN 输入"
          aria-live="polite"
        >
          {pin.replace(/./g, '●') || '—'}
        </div>

        {error && (
          <div className="mb-4 rounded-cg-md bg-cg-warning-bg px-4 py-3 text-sm text-cg-warning" role="alert">
            {error}
            {isLocked && (
              <div className="mt-1 font-mono">
                剩余 {minutes.toString().padStart(2, '0')}:{seconds.toString().padStart(2, '0')}
              </div>
            )}
          </div>
        )}

        <div className="grid grid-cols-3 gap-3">
          {['1', '2', '3', '4', '5', '6', '7', '8', '9'].map((digit) => (
            <button
              key={digit}
              type="button"
              onClick={() => appendDigit(digit)}
              disabled={isLocked || loading}
              className="min-h-touch rounded-cg-md bg-cg-surface-raised text-2xl font-semibold text-cg-text hover:bg-cg-border focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cg-focus disabled:opacity-50"
              aria-label={digit}
            >
              {digit}
            </button>
          ))}
          <button
            type="button"
            onClick={backspace}
            disabled={isLocked || loading || pin.length === 0}
            className="min-h-touch rounded-cg-md bg-cg-surface-raised text-cg-text hover:bg-cg-border focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cg-focus disabled:opacity-50"
            aria-label="退格"
          >
            ⌫
          </button>
          <button
            type="button"
            onClick={() => appendDigit('0')}
            disabled={isLocked || loading}
            className="min-h-touch rounded-cg-md bg-cg-surface-raised text-2xl font-semibold text-cg-text hover:bg-cg-border focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cg-focus disabled:opacity-50"
            aria-label="0"
          >
            0
          </button>
          <button
            type="button"
            onClick={() => {
              setPin('');
              history.push('/child/bind');
            }}
            disabled={loading}
            className="min-h-touch rounded-cg-md bg-cg-surface-raised text-sm text-cg-text hover:bg-cg-border focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cg-focus disabled:opacity-50"
          >
            切换
          </button>
        </div>
      </div>
    </div>
  );
}

export default ChildLoginPage;
