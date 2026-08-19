import { useEffect, useState } from 'react';
import { history, useSearchParams } from 'umi';
import { getClient } from '@shared/api';
import { useOnline } from '@shared/theme';
import { KidSpinner, EmptyState, KidButton } from '@/design/components';

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

export default function BindPage() {
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
      // DEVICE_NOT_AUTHORIZED is expected for unbound devices — show binding instructions
      if (response.error.error_code === 'DEVICE_NOT_AUTHORIZED') {
        setError(null);
        setChildren([]);
      } else {
        setError(response.error.message ?? '无法查询设备状态');
      }
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

  if (loading && !polling) return <KidSpinner />;
  if (!online) {
    return (
      <div className="kid-login-wrap">
        <div className="kid-login-card">
          <EmptyState emoji="📡" text="离线，请连接网络后重试" />
          <KidButton variant="ghost" onClick={fetchChildren}>重试</KidButton>
        </div>
      </div>
    );
  }
  if (error) {
    return (
      <div className="kid-login-wrap">
        <div className="kid-login-card">
          <EmptyState emoji="😵" text={`查询失败：${error}`} />
          <KidButton variant="ghost" onClick={fetchChildren}>重试</KidButton>
        </div>
      </div>
    );
  }

  if (children.length === 0) {
    return (
      <div className="kid-login-wrap">
        <div className="kid-login-card">
          <div className="kid-login-logo" aria-hidden="true">🔗</div>
          <h1 className="kid-login-title">设备绑定</h1>
          <p className="kid-login-subtitle">请让家长授权此设备</p>
          <div>
            <p className="kid-row-sub" style={{ marginBottom: 6 }}>设备标识</p>
            <p className="kid-device-id" aria-label="设备标识">{deviceId}</p>
          </div>
          <p className="kid-row-sub" style={{ textAlign: 'center' }}>
            在家长端「家庭设置」中点击「授权设备」，并输入上方设备标识。
          </p>
          <KidButton onClick={fetchChildren} loading={loading} block>
            我已授权，继续
          </KidButton>
        </div>
      </div>
    );
  }

  return (
    <div className="kid-login-wrap">
      <div className="kid-login-card">
        <div className="kid-login-logo" aria-hidden="true">👋</div>
        <h1 className="kid-login-title">选择档案</h1>
        <p className="kid-login-subtitle">你是谁？</p>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {children.map((child) => (
            <button
              key={child.id}
              type="button"
              onClick={() => history.push(`/child/login?childId=${child.id}&deviceId=${encodeURIComponent(deviceId)}`)}
              className="kid-profile-btn"
            >
              <span className="kid-profile-avatar" aria-hidden="true">
                {child.nickname.charAt(0)}
              </span>
              {child.nickname}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
