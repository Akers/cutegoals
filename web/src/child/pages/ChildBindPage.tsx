import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { getClient } from '@shared/api';
import { Button, LoadingState, ErrorState, PageHeader } from '@shared/components';
import { useOnline } from '@shared/theme';

interface ChildProfile {
  id: number;
  nickname: string;
  avatarUrl?: string;
}

function getDeviceId(): string {
  const key = 'cg.deviceId';
  if (typeof localStorage === 'undefined') {
    return `device-${Date.now()}`;
  }
  let id = localStorage.getItem(key);
  if (!id) {
    id = `device-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    localStorage.setItem(key, id);
  }
  return id;
}

export function ChildBindPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [deviceId] = useState(() => searchParams.get('deviceId') ?? getDeviceId());
  const [children, setChildren] = useState<ChildProfile[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [polling, setPolling] = useState(false);
  const online = useOnline();

  const fetchChildren = async () => {
    if (!online) return;
    setLoading(true);
    const response = await getClient().get<ChildProfile[]>(`/family/devices/children?deviceId=${encodeURIComponent(deviceId)}`);
    setLoading(false);
    if (response.error) {
      setError(response.error.message ?? '无法查询设备状态');
    } else {
      setError(null);
      setChildren(response.data ?? []);
    }
  };

  useEffect(() => {
    fetchChildren();
    // Poll every 5 seconds until at least one child is available.
    const interval = window.setInterval(() => {
      setPolling(true);
      getClient().get<ChildProfile[]>(`/family/devices/children?deviceId=${encodeURIComponent(deviceId)}`).then((response) => {
        if (!response.error) {
          const data = response.data ?? [];
          setChildren(data);
          if (data.length > 0) {
            window.clearInterval(interval);
          }
        }
      });
    }, 5000);
    return () => window.clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [deviceId, online]);

  if (loading && !polling) return <LoadingState message="正在检查设备授权..." />;
  if (!online) return <ErrorState title="离线" message="请连接网络后重试" onRetry={fetchChildren} />;
  if (error) return <ErrorState title="查询失败" message={error} onRetry={fetchChildren} />;

  if (children.length === 0) {
    return (
      <div className="cg-page flex min-h-screen flex-col items-center justify-center">
        <div className="w-full max-w-md cg-card p-6 text-center">
          <PageHeader title="设备绑定" subtitle="请让家长授权此设备" />
          <div className="mb-6 rounded-cg-md bg-cg-surface-raised p-4">
            <p className="text-sm text-cg-text-muted">设备标识</p>
            <p className="break-all font-mono text-sm text-cg-text" aria-label="设备标识">{deviceId}</p>
          </div>
          <p className="mb-4 text-sm text-cg-text-muted">
            在家长端「家庭设置」中点击「授权设备」，并输入上方设备标识。
          </p>
          <Button onClick={fetchChildren} isLoading={loading}>
            我已授权，继续
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="cg-page flex min-h-screen flex-col items-center justify-center">
      <div className="w-full max-w-md cg-card p-6">
        <PageHeader title="选择档案" subtitle="你是谁？" />
        <div className="grid grid-cols-1 gap-3">
          {children.map((child) => (
            <button
              key={child.id}
              onClick={() => navigate(`/child/login?childId=${child.id}&deviceId=${encodeURIComponent(deviceId)}`)}
              className="flex items-center gap-4 rounded-cg-lg border border-cg-border bg-cg-surface p-4 text-left transition-shadow hover:shadow-cg-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cg-focus min-h-touch"
            >
              <div className="flex h-12 w-12 items-center justify-center rounded-full bg-cg-primary text-lg text-cg-primary-text">
                {child.nickname.charAt(0)}
              </div>
              <div className="text-lg font-medium text-cg-text">{child.nickname}</div>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

export default ChildBindPage;
