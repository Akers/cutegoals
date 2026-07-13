import { useEffect, useState } from 'react';
import { getClient } from '@shared/api';
import {
  Button,
  CardSection,
  EmptyState,
  ErrorState,
  Layout,
  LoadingState,
  OfflineState,
  PageHeader,
  StatusBadge,
} from '@shared/components';
import { useOnline } from '@shared/theme';
import { useApi } from '@shared/hooks/useApi';

interface PageResult<T> {
  content: T[];
  page: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
}

interface OverviewData {
  instanceStatus?: string;
  initialized?: boolean;
  version?: string;
  lastBackupAt?: string;
  lastBackupStatus?: string;
  recoveryDrill?: {
    ranAt: string;
    success: boolean;
    rpo: string;
    rto: string;
  };
}

export function AdminOverviewPage() {
  const { data, loading, error, refetch } = useApi<OverviewData>('/instance/status');
  const online = useOnline();

  if (!online) return <OfflineState onRetry={refetch} />;
  if (loading) return <LoadingState />;
  if (error) return <ErrorState title="加载失败" message={error.message ?? '未知错误'} onRetry={refetch} />;
  if (!data) return <EmptyState title="暂无数据" />;

  return (
    <Layout>
      <PageHeader title="实例概览" />
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <CardSection title="初始化状态">
          <div className="flex items-center gap-2">
            <StatusBadge
              status={
                (data.initialized ?? data.instanceStatus === 'INITIALIZED')
                  ? 'completed'
                  : 'pending'
              }
            />
            <span className="text-cg-text-muted">{data.version ?? '—'}</span>
          </div>
        </CardSection>
        <CardSection title="备份状态">
          {data.lastBackupAt ? (
            <div className="flex items-center gap-2">
              <StatusBadge status={data.lastBackupStatus === 'SUCCESS' ? 'completed' : 'cancelled'} />
              <span className="text-cg-text-muted">{data.lastBackupAt}</span>
            </div>
          ) : (
            <p className="text-cg-text-muted">暂无备份记录</p>
          )}
        </CardSection>
        <CardSection title="恢复演练">
          {data.recoveryDrill ? (
            <div className="space-y-1 text-sm">
              <div className="flex items-center gap-2">
                结果
                <StatusBadge status={data.recoveryDrill.success ? 'completed' : 'rejected'} />
              </div>
              <div>RPO: {data.recoveryDrill.rpo}</div>
              <div>RTO: {data.recoveryDrill.rto}</div>
              <div>时间: {data.recoveryDrill.ranAt}</div>
            </div>
          ) : (
            <p className="text-cg-text-muted">尚未运行恢复演练</p>
          )}
        </CardSection>
      </div>
    </Layout>
  );
}

export function AdminConfigPage() {
  const { data, loading, error, refetch } = useApi<Record<string, string>>('/admin/config');
  const [saving, setSaving] = useState(false);
  const [values, setValues] = useState<Record<string, string>>({});
  const online = useOnline();

  useEffect(() => {
    if (data) setValues(data);
  }, [data]);

  const handleSave = async () => {
    setSaving(true);
    await getClient().put('/admin/config', values);
    setSaving(false);
    await refetch();
  };

  if (!online) return <OfflineState onRetry={refetch} />;
  if (loading) return <LoadingState />;
  if (error) return <ErrorState title="加载失败" message={error.message ?? '未知错误'} onRetry={refetch} />;
  if (!data) return <EmptyState title="暂无配置" />;

  return (
    <Layout>
      <PageHeader title="系统配置" />
      <div className="cg-card p-4">
        <div className="flex flex-col gap-4">
          {Object.entries(data).map(([key]) => (
            <div key={key} className="flex flex-col gap-1">
              <label className="text-sm font-medium text-cg-text">{key}</label>
              <input
                type={key.toLowerCase().includes('secret') || key.toLowerCase().includes('password') ? 'password' : 'text'}
                value={values[key] ?? ''}
                onChange={(e) => setValues((prev) => ({ ...prev, [key]: e.target.value }))}
                className="w-full rounded-cg-md border border-cg-border bg-cg-surface px-3 py-2 text-cg-text min-h-touch"
              />
            </div>
          ))}
          <Button onClick={handleSave} isLoading={saving}>保存配置</Button>
        </div>
      </div>
    </Layout>
  );
}

interface Account {
  id: number;
  phone: string;
  status: string;
  roles: string[];
  createdAt: string;
  updatedAt: string;
}

export function AdminAccountsPage() {
  const { data, loading, error, refetch } = useApi<PageResult<Account>>('/admin/accounts');
  const online = useOnline();
  const [acting, setActing] = useState<number | null>(null);

  const toggle = async (id: number, enable: boolean) => {
    setActing(id);
    await getClient().post(`/admin/accounts/${id}/${enable ? 'enable' : 'disable'}`);
    await refetch();
    setActing(null);
  };

  const mask = (phone: string) => phone.slice(0, 3) + '****' + phone.slice(7);

  if (!online) return <OfflineState onRetry={refetch} />;
  if (loading) return <LoadingState />;
  if (error) return <ErrorState title="加载失败" message={error.message ?? '未知错误'} onRetry={refetch} />;
  if (!data || data.content.length === 0) return <EmptyState title="暂无账号" />;

  return (
    <Layout>
      <PageHeader title="账号管理" />
      <div className="cg-card overflow-hidden">
        <table className="w-full text-left text-sm">
          <thead className="bg-cg-surface-raised">
            <tr>
              <th className="px-4 py-3 font-medium">手机号</th>
              <th className="px-4 py-3 font-medium">角色</th>
              <th className="px-4 py-3 font-medium">状态</th>
              <th className="px-4 py-3 font-medium">操作</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-cg-border">
            {data.content.map((account) => (
              <tr key={account.id}>
                <td className="px-4 py-3">{mask(account.phone)}</td>
                <td className="px-4 py-3">{account.roles.join(', ')}</td>
                <td className="px-4 py-3">
                  <StatusBadge status={account.status === 'ACTIVE' ? 'approved' : 'cancelled'} />
                </td>
                <td className="px-4 py-3">
                  <Button
                    variant={account.status === 'ACTIVE' ? 'secondary' : 'primary'}
                    size="sm"
                    isLoading={acting === account.id}
                    onClick={() => toggle(account.id, account.status !== 'ACTIVE')}
                  >
                    {account.status === 'ACTIVE' ? '停用' : '启用'}
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Layout>
  );
}

interface AuditLog {
  id: number;
  actorId: number;
  actorType: string;
  eventType: string;
  result: string;
  objectType: string;
  objectId: string;
  summary: string;
  requestId: string;
  createdAt: string;
}

export function AdminAuditPage() {
  const { data, loading, error, refetch } = useApi<PageResult<AuditLog>>('/admin/audit-logs');
  const online = useOnline();

  if (!online) return <OfflineState onRetry={refetch} />;
  if (loading) return <LoadingState />;
  if (error) return <ErrorState title="加载失败" message={error.message ?? '未知错误'} onRetry={refetch} />;
  if (!data || data.content.length === 0) return <EmptyState title="暂无审计日志" />;

  return (
    <Layout>
      <PageHeader title="审计日志" />
      <div className="cg-card overflow-hidden">
        <table className="w-full text-left text-sm">
          <thead className="bg-cg-surface-raised">
            <tr>
              <th className="px-4 py-3 font-medium">时间</th>
              <th className="px-4 py-3 font-medium">操作者</th>
              <th className="px-4 py-3 font-medium">动作</th>
              <th className="px-4 py-3 font-medium">结果</th>
              <th className="px-4 py-3 font-medium">对象</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-cg-border">
            {data.content.map((log) => (
              <tr key={log.id}>
                <td className="px-4 py-3">{log.createdAt}</td>
                <td className="px-4 py-3">{log.actorType}#{log.actorId}</td>
                <td className="px-4 py-3">{log.eventType}</td>
                <td className="px-4 py-3">{log.result}</td>
                <td className="px-4 py-3">{log.objectType}#{log.objectId}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Layout>
  );
}

interface HealthCheck {
  name: string;
  healthy: boolean;
  message: string;
}

interface HealthData {
  status: string;
  initialized?: boolean;
  version?: string;
  buildTime?: string;
  buildCommit?: string;
  database?: { status: string; type: string };
  backup?: {
    lastBackupTime: string | null;
    lastBackupStatus: string;
    nextScheduledBackup: string | null;
  };
  recoveryDrill?: {
    lastRecoveryDrillTime: string | null;
    lastRecoveryDrillStatus: string;
    rpoSeconds: number | null;
    rtoSeconds: number | null;
  };
  rpoWarning?: string;
  rpoWarningMessage?: string;
}

export function AdminHealthPage() {
  const { data, loading, error, refetch } = useApi<HealthData>('/admin/health');
  const online = useOnline();

  if (!online) return <OfflineState onRetry={refetch} />;
  if (loading) return <LoadingState />;
  if (error) return <ErrorState title="加载失败" message={error.message ?? '未知错误'} onRetry={refetch} />;
  if (!data) return <EmptyState title="暂无健康数据" />;

  const checks: HealthCheck[] = [
    {
      name: '数据库',
      healthy: data.database?.status === 'UP',
      message: data.database ? `类型: ${data.database.type}` : '未连接',
    },
    {
      name: '备份',
      healthy: data.backup?.lastBackupStatus === 'SUCCESS',
      message:
        !data.backup?.lastBackupTime || data.backup.lastBackupStatus === 'NEVER'
          ? '从未备份'
          : `上次备份: ${data.backup.lastBackupTime} (${data.backup.lastBackupStatus})`,
    },
    {
      name: '恢复演练',
      healthy: data.recoveryDrill?.lastRecoveryDrillStatus === 'SUCCESS',
      message:
        !data.recoveryDrill?.lastRecoveryDrillTime ||
        data.recoveryDrill.lastRecoveryDrillStatus === 'NEVER'
          ? '从未演练'
          : `上次演练: ${data.recoveryDrill.lastRecoveryDrillTime} (${data.recoveryDrill.lastRecoveryDrillStatus})`,
    },
  ];
  if (data.rpoWarning) {
    checks.push({
      name: 'RPO 警告',
      healthy: false,
      message: data.rpoWarningMessage ?? 'RPO 超出阈值',
    });
  }

  return (
    <Layout>
      <PageHeader title="健康面板" />
      <div className="mb-4 flex items-center gap-2">
        整体状态
        <StatusBadge status={data.status === 'UP' ? 'approved' : 'rejected'} />
      </div>
      <div className="grid grid-cols-1 gap-3">
        {checks.map((check) => (
          <div key={check.name} className="cg-card p-4">
            <div className="flex items-center justify-between">
              <span className="font-medium text-cg-text">{check.name}</span>
              <StatusBadge status={check.healthy ? 'approved' : 'rejected'} />
            </div>
            <p className="mt-1 text-sm text-cg-text-muted">{check.message}</p>
          </div>
        ))}
      </div>
    </Layout>
  );
}

export default AdminOverviewPage;
