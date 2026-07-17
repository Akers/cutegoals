import { useEffect, useState } from 'react';
import { getClient } from '@shared/api';
import { Button, Card, Col, Empty, Input, Row, Space, Table, Tag, Typography, message } from 'antd';
import { Result, Spin } from '@shared/components';
import { useOnline } from '@shared/theme';
import { useApi } from '@shared/hooks/useApi';

/** Map API status/result values to Chinese labels (match StatusBadge conventions) */
function statusLabel(s: string): string {
  const map: Record<string, string> = {
    active: '启用',
    disabled: '停用',
    success: '成功',
    failed: '失败',
    approved: '已通过',
    rejected: '已驳回',
    pending: '待处理',
    submitted: '已提交',
    completed: '已完成',
    cancelled: '已取消',
  };
  return map[s.toLowerCase()] ?? s;
}

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

  if (!online) return <Result status="warning" title="当前处于离线状态" subTitle="请检查网络连接，恢复后重试" extra={<Button onClick={refetch}>重试</Button>} />;
  if (loading) return <Spin />;
  if (error) return <Result status="error" title="加载失败" subTitle={error.message ?? '未知错误'} extra={<Button onClick={refetch}>重试</Button>} />;
  if (!data) return <Empty description="暂无数据" />;

  return (
    <>
      <Typography.Title level={3}>实例概览</Typography.Title>
      <Row gutter={[16, 16]}>
        <Col span={24} md={12}>
          <Card title="初始化状态">
            <Space>
              <Tag color={(data.initialized ?? data.instanceStatus === 'INITIALIZED') ? 'success' : 'default'}>
                {(data.initialized ?? data.instanceStatus === 'INITIALIZED') ? '已初始化' : '未初始化'}
              </Tag>
              <Typography.Text type="secondary">{data.version ?? '—'}</Typography.Text>
            </Space>
          </Card>
        </Col>
        <Col span={24} md={12}>
          <Card title="备份状态">
            {data.lastBackupAt ? (
              <Space>
                <Tag color={data.lastBackupStatus === 'SUCCESS' ? 'success' : 'error'}>
                  {data.lastBackupStatus === 'SUCCESS' ? '成功' : '失败'}
                </Tag>
                <Typography.Text type="secondary">{data.lastBackupAt}</Typography.Text>
              </Space>
            ) : (
              <Typography.Text type="secondary">暂无备份记录</Typography.Text>
            )}
          </Card>
        </Col>
        <Col span={24} md={12}>
          <Card title="恢复演练">
            {data.recoveryDrill ? (
              <Space direction="vertical" size="small">
                <Space>
                  <Typography.Text>结果</Typography.Text>
                  <Tag color={data.recoveryDrill.success ? 'success' : 'error'}>
                    {data.recoveryDrill.success ? '成功' : '失败'}
                  </Tag>
                </Space>
                <Typography.Text type="secondary">RPO: {data.recoveryDrill.rpo}</Typography.Text>
                <Typography.Text type="secondary">RTO: {data.recoveryDrill.rto}</Typography.Text>
                <Typography.Text type="secondary">时间: {data.recoveryDrill.ranAt}</Typography.Text>
              </Space>
            ) : (
              <Typography.Text type="secondary">尚未运行恢复演练</Typography.Text>
            )}
          </Card>
        </Col>
      </Row>
    </>
  );
}

/** Backend GET /admin/config contract: one entry per whitelisted config key */
interface ConfigEntry {
  key: string;
  type: string;
  description: string;
  masked: boolean;
  value: string | null;
  configured: boolean;
}

export function AdminConfigPage() {
  const { data, loading, error, refetch } = useApi<ConfigEntry[]>('/admin/config');
  const [saving, setSaving] = useState(false);
  const [values, setValues] = useState<Record<string, string>>({});
  const [original, setOriginal] = useState<Record<string, string>>({});
  const online = useOnline();

  useEffect(() => {
    if (data) {
      const initial = Object.fromEntries(data.map((entry) => [entry.key, entry.value ?? '']));
      setValues(initial);
      setOriginal(initial);
    }
  }, [data]);

  const handleSave = async () => {
    // Submit only changed keys; unchanged masked secrets keep the mask value
    // and are naturally excluded, so the mask is never written back as a real secret.
    const payload = Object.fromEntries(
      Object.keys(values)
        .filter((k) => values[k] !== original[k])
        .map((k) => [k, values[k]]),
    );
    if (Object.keys(payload).length === 0) {
      message.info('没有需要保存的变更');
      return;
    }
    setSaving(true);
    const { error: saveError } = await getClient().put('/admin/config', payload);
    setSaving(false);
    if (saveError) {
      message.error(saveError.message ?? '保存失败');
      return;
    }
    message.success('保存成功');
    await refetch();
  };

  if (!online) return <Result status="warning" title="当前处于离线状态" subTitle="请检查网络连接，恢复后重试" extra={<Button onClick={refetch}>重试</Button>} />;
  if (loading) return <Spin />;
  if (error) return <Result status="error" title="加载失败" subTitle={error.message ?? '未知错误'} extra={<Button onClick={refetch}>重试</Button>} />;
  if (!data) return <Empty description="暂无配置" />;

  return (
    <>
      <Typography.Title level={3}>系统配置</Typography.Title>
      <Card>
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          {data.map((entry) => (
            <Space key={entry.key} direction="vertical" style={{ width: '100%' }}>
              <Typography.Text strong>{entry.key}</Typography.Text>
              <Typography.Text type="secondary">{entry.description}</Typography.Text>
              <Input
                type={entry.masked ? 'password' : 'text'}
                value={values[entry.key] ?? ''}
                onChange={(e) => setValues((prev) => ({ ...prev, [entry.key]: e.target.value }))}
              />
            </Space>
          ))}
          <Button type="primary" onClick={handleSave} loading={saving}>保存配置</Button>
        </Space>
      </Card>
    </>
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
  const [page, setPage] = useState(0);
  const { data, loading, error, refetch } = useApi<PageResult<Account>>(`/admin/accounts?page=${page + 1}`);
  const online = useOnline();
  const [acting, setActing] = useState<number | null>(null);

  const toggle = async (id: number, enable: boolean) => {
    setActing(id);
    await getClient().post(`/admin/accounts/${id}/${enable ? 'enable' : 'disable'}`);
    await refetch();
    setActing(null);
  };

  if (!online) return <Result status="warning" title="当前处于离线状态" subTitle="请检查网络连接，恢复后重试" extra={<Button onClick={refetch}>重试</Button>} />;
  if (loading) return <Spin />;
  if (error) return <Result status="error" title="加载失败" subTitle={error.message ?? '未知错误'} extra={<Button onClick={refetch}>重试</Button>} />;
  if (!data || data.content.length === 0) return <Empty description="暂无账号" />;

  const accountColumns = [
    { title: '手机号', dataIndex: 'phone', key: 'phone' },
    { title: '角色', dataIndex: 'roles', key: 'roles', render: (r: string[]) => r.join(', ') },
    { title: '状态', dataIndex: 'status', key: 'status',
      render: (s: string) => <Tag color={s === 'ACTIVE' ? 'success' : 'error'}>{statusLabel(s)}</Tag> },
    { title: '操作', key: 'action',
      render: (_: unknown, record: Account) => (
        <Button
          type={record.status === 'ACTIVE' ? 'default' : 'primary'}
          size="small"
          loading={acting === record.id}
          onClick={() => toggle(record.id, record.status !== 'ACTIVE')}
        >
          {record.status === 'ACTIVE' ? '停用' : '启用'}
        </Button>
      )},
  ];

  return (
    <>
      <Typography.Title level={3}>账号管理</Typography.Title>
      <Table
        columns={accountColumns}
        dataSource={data.content}
        rowKey="id"
        pagination={data.totalPages > 1 ? { current: data.page, total: data.totalElements, pageSize: data.pageSize } : false}
        onChange={(pagination: any) => setPage(pagination.current! - 1)}
      />
    </>
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
  const [page, setPage] = useState(0);
  const { data, loading, error, refetch } = useApi<PageResult<AuditLog>>(`/admin/audit-logs?page=${page + 1}`);
  const online = useOnline();

  if (!online) return <Result status="warning" title="当前处于离线状态" subTitle="请检查网络连接，恢复后重试" extra={<Button onClick={refetch}>重试</Button>} />;
  if (loading) return <Spin />;
  if (error) return <Result status="error" title="加载失败" subTitle={error.message ?? '未知错误'} extra={<Button onClick={refetch}>重试</Button>} />;
  if (!data || data.content.length === 0) return <Empty description="暂无审计日志" />;

  const auditColumns = [
    { title: '时间', dataIndex: 'createdAt', key: 'createdAt' },
    { title: '操作者', key: 'actor', render: (_: unknown, r: AuditLog) => `${r.actorType}#${r.actorId}` },
    { title: '动作', dataIndex: 'eventType', key: 'eventType' },
    { title: '结果', dataIndex: 'result', key: 'result', render: (s: string) => <Tag>{statusLabel(s)}</Tag> },
    { title: '对象', key: 'object', render: (_: unknown, r: AuditLog) => `${r.objectType}#${r.objectId}` },
  ];

  return (
    <>
      <Typography.Title level={3}>审计日志</Typography.Title>
      <Table
        columns={auditColumns}
        dataSource={data.content}
        rowKey="id"
        pagination={data.totalPages > 1 ? { current: data.page, total: data.totalElements, pageSize: data.pageSize } : false}
        onChange={(pagination: any) => setPage(pagination.current! - 1)}
      />
    </>
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

  if (!online) return <Result status="warning" title="当前处于离线状态" subTitle="请检查网络连接，恢复后重试" extra={<Button onClick={refetch}>重试</Button>} />;
  if (loading) return <Spin />;
  if (error) return <Result status="error" title="加载失败" subTitle={error.message ?? '未知错误'} extra={<Button onClick={refetch}>重试</Button>} />;
  if (!data) return <Empty description="暂无健康数据" />;

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
    <>
      <Typography.Title level={3}>健康面板</Typography.Title>
      <Space style={{ marginBottom: 16 }}>
        <Typography.Text>整体状态</Typography.Text>
        <Tag color={data.status === 'UP' ? 'success' : 'error'}>
          {data.status === 'UP' ? '正常' : '异常'}
        </Tag>
      </Space>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        {checks.map((check) => (
          <Card key={check.name} size="small">
            <Space style={{ justifyContent: 'space-between', width: '100%' }}>
              <Typography.Text strong>{check.name}</Typography.Text>
              <Tag color={check.healthy ? 'success' : 'error'}>
                {check.healthy ? '正常' : '异常'}
              </Tag>
            </Space>
            <Typography.Text type="secondary" style={{ display: 'block', marginTop: 4 }}>{check.message}</Typography.Text>
          </Card>
        ))}
      </Space>
    </>
  );
}

export default AdminOverviewPage;
