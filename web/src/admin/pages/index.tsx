import { useEffect, useState } from 'react';
import { getClient } from '@shared/api';
import { Button, Card, Col, Empty, Input, Row, Space, Table, Tag, Typography } from 'antd';
import { Result, Spin } from '@shared/components';
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

  if (!online) return <Result status="warning" title="当前处于离线状态" subTitle="请检查网络连接，恢复后重试" extra={<Button onClick={refetch}>重试</Button>} />;
  if (loading) return <Spin />;
  if (error) return <Result status="error" title="加载失败" subTitle={error.message ?? '未知错误'} extra={<Button onClick={refetch}>重试</Button>} />;
  if (!data) return <Empty description="暂无配置" />;

  return (
    <>
      <Typography.Title level={3}>系统配置</Typography.Title>
      <Card>
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          {Object.entries(data).map(([key]) => (
            <Space key={key} direction="vertical" style={{ width: '100%' }}>
              <Typography.Text strong>{key}</Typography.Text>
              <Input
                type={key.toLowerCase().includes('secret') || key.toLowerCase().includes('password') ? 'password' : 'text'}
                value={values[key] ?? ''}
                onChange={(e) => setValues((prev) => ({ ...prev, [key]: e.target.value }))}
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

  if (!online) return <Result status="warning" title="当前处于离线状态" subTitle="请检查网络连接，恢复后重试" extra={<Button onClick={refetch}>重试</Button>} />;
  if (loading) return <Spin />;
  if (error) return <Result status="error" title="加载失败" subTitle={error.message ?? '未知错误'} extra={<Button onClick={refetch}>重试</Button>} />;
  if (!data || data.content.length === 0) return <Empty description="暂无账号" />;

  const accountColumns = [
    { title: '手机号', dataIndex: 'phone', key: 'phone', render: (p: string) => mask(p) },
    { title: '角色', dataIndex: 'roles', key: 'roles', render: (r: string[]) => r.join(', ') },
    { title: '状态', dataIndex: 'status', key: 'status',
      render: (s: string) => <Tag color={s === 'ACTIVE' ? 'success' : 'error'}>{s}</Tag> },
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
        pagination={data.totalPages > 1 ? { current: data.page + 1, total: data.totalElements, pageSize: data.pageSize } : false}
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
  const { data, loading, error, refetch } = useApi<PageResult<AuditLog>>('/admin/audit-logs');
  const online = useOnline();

  if (!online) return <Result status="warning" title="当前处于离线状态" subTitle="请检查网络连接，恢复后重试" extra={<Button onClick={refetch}>重试</Button>} />;
  if (loading) return <Spin />;
  if (error) return <Result status="error" title="加载失败" subTitle={error.message ?? '未知错误'} extra={<Button onClick={refetch}>重试</Button>} />;
  if (!data || data.content.length === 0) return <Empty description="暂无审计日志" />;

  const auditColumns = [
    { title: '时间', dataIndex: 'createdAt', key: 'createdAt' },
    { title: '操作者', key: 'actor', render: (_: unknown, r: AuditLog) => `${r.actorType}#${r.actorId}` },
    { title: '动作', dataIndex: 'eventType', key: 'eventType' },
    { title: '结果', dataIndex: 'result', key: 'result' },
    { title: '对象', key: 'object', render: (_: unknown, r: AuditLog) => `${r.objectType}#${r.objectId}` },
  ];

  return (
    <>
      <Typography.Title level={3}>审计日志</Typography.Title>
      <Table
        columns={auditColumns}
        dataSource={data.content}
        rowKey="id"
        pagination={data.totalPages > 1 ? { current: data.page + 1, total: data.totalElements, pageSize: data.pageSize } : false}
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
