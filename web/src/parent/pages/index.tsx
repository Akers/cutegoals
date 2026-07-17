import { useEffect, useMemo, useState } from 'react';
import { history } from 'umi';
import { getClient } from '@shared/api';
import type { TaskTypeValue } from '@shared/api/types';
import { Alert, Button, Card, Empty, Input, Modal, Result, Row, Select, Space, Spin, Table, Tag, Typography, message } from 'antd';
const { TextArea } = Input;
import { useAuth } from '@shared/auth';
import { useApi, useFormField, useIdempotencyKey } from '@shared/hooks/useApi';
import { useOnline } from '@shared/theme';
import { TaskTypeConfigForms, type TypeConfigValue } from '@parent/components/TaskTypeConfigForms';
import { TaskTypeFilter } from '@parent/components/TaskTypeFilter';

/** Map API status values to Chinese labels */
function statusLabel(s: string): string {
  const map: Record<string, string> = {
    completed: '已完成', approved: '已通过', rejected: '已驳回',
    cancelled: '已取消', active: '启用', disabled: '停用',
    pending: '待处理', locked: '已锁定', success: '成功', failed: '失败',
  };
  return map[s?.toLowerCase()] ?? s;
}

// 后端分页响应统一契约：{content,page,pageSize,totalElements,totalPages}
interface PageResult<T> {
  content: T[];
  page: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
}

// Domain types
interface Family {
  id: number;
  name: string;
  members: FamilyMember[];
  children: ChildProfile[];
}

interface FamilyMember {
  id: number;
  accountId: number;
  nickname?: string;
  role: 'PARENT' | 'CHILD';
  phone?: string;
}

interface Invitation {
  id: number;
  inviteePhone: string;
  status: string;
  createdAt: string;
}

interface ChildProfile {
  id: number;
  nickname: string;
  pin?: string;
  birthday?: string;
  birthYear?: number;
  avatar?: string;
  status?: string;
}

interface Difficulty {
  id: number;
  name: string;
  rewardPoints: number;
  displayOrder: number;
  enabled: boolean;
}

interface TaskTemplate {
  id: number;
  name: string;
  description: string;
  category: string;
  difficulties: Difficulty[];
  enabled: boolean;
  version: number;
  recurrenceRule?: { ruleType: string };
  taskType: TaskTypeValue;
  typeConfig: string; // JSON string
}

interface TaskAssignment {
  id: number;
  childId: number;
  childNickname: string;
  templateTitle: string;
  status: string;
  deadline: string;
  points: number;
  isOverdue: boolean;
}

interface ReviewItem {
  attemptId: number;
  assignmentId: number;
  childNickname: string;
  templateTitle: string;
  submittedAt: string;
  notes?: string;
  isOverdue: boolean;
}

interface Prize {
  id: number;
  name: string;
  description: string;
  pointsCost: number;
  availableStock: number;
  enabled: boolean;
}

interface BlindBox {
  id: number;
  name: string;
  cost: number;
  enabled: boolean;
  availabilityVersion: string;
}

interface BlindBoxCandidate {
  prizeId: number;
  prizeName: string;
  probability: number;
}

interface Exchange {
  id: number;
  type: 'PRIZE' | 'BLIND_BOX';
  childNickname: string;
  targetName: string;
  pointsCost: number;
  status: string;
  createdAt: string;
}

// Generic helpers
function usePaginatedData<T>(path: string, filters?: Record<string, string>) {
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const filterString = filters
    ? '&' +
      Object.entries(filters)
        .map(([k, v]) => `${k}=${encodeURIComponent(v)}`)
        .join('&')
    : '';
  const { data, loading, error, refetch } = useApi<PageResult<T>>(
    `${path}?page=${page}&pageSize=${pageSize}${filterString}`,
  );
  return {
    items: data?.content ?? [],
    total: data?.totalElements ?? 0,
    page,
    pageSize,
    setPage,
    setPageSize,
    loading,
    error,
    refetch,
  };
}

/** 手机号脱敏：138****1234 */
function maskPhone(phone: string): string {
  if (!phone || phone.length < 7) return phone ?? '';
  return phone.slice(0, 3) + '****' + phone.slice(-4);
}

export function ParentHomePage() {
  const { data, loading, error, refetch } = useApi<Family>('/family');
  const online = useOnline();

  if (!online)
    return (
      <Result status="warning" title="当前处于离线状态" subTitle="请检查网络连接，恢复后重试" extra={<Button onClick={refetch}>重试</Button>} />
    );
  if (loading)
    return <Spin />;
  if (error)
    return (
      <Result status="error" title="加载失败" subTitle={error.message} extra={<Button onClick={refetch}>重试</Button>} />
    );
  if (!data)
    return <Empty description="暂无数据" />;

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Row justify="space-between" align="middle">
        <Typography.Title level={4} style={{ margin: 0 }}>家庭</Typography.Title>
        <Space>
          <Button onClick={() => history.push('/parent/family')}>管理家庭</Button>
          <Button onClick={() => history.push('/parent/templates')}>任务模板</Button>
        </Space>
      </Row>
      <Card title="家庭成员">
        <Space direction="vertical" size="small" style={{ width: '100%' }}>
          {(data.members ?? []).map((member) => (
            <Row key={member.id} justify="space-between" align="middle">
              <Typography.Text strong>
                {member.nickname ?? maskPhone(member.phone ?? '')}
              </Typography.Text>
              <Tag>{statusLabel(member.role === 'PARENT' ? 'approved' : 'pending')}</Tag>
            </Row>
          ))}
        </Space>
      </Card>
    </Space>
  );
}

export function ParentFamilyPage() {
  const { data, loading, error, refetch } = useApi<Family>('/family');
  const { items: invitations, refetch: refetchInvitations } =
    usePaginatedData<Invitation>('/family/invitations');
  const [showInvite, setShowInvite] = useState(false);
  const [showChildModal, setShowChildModal] = useState(false);
  const [confirm, setConfirm] = useState<{
    type: 'remove' | 'leave' | 'removeChild';
    member?: FamilyMember;
    child?: ChildProfile;
  } | null>(null);
  const [actionLoading, setActionLoading] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const phone = useFormField();
  const childNickname = useFormField();
  const childPin = useFormField();
  const childBirthday = useFormField();
  const online = useOnline();
  const { account } = useAuth();
  const [sending, setSending] = useState(false);
  const [childSaving, setChildSaving] = useState(false);
  const [childSaveError, setChildSaveError] = useState<string | null>(null);

  const resetChildForm = () => {
    childNickname.reset();
    childPin.reset();
    childBirthday.reset();
  };

  const openNewChild = () => {
    resetChildForm();
    setChildSaveError(null);
    setShowChildModal(true);
  };

  const handleInvite = async () => {
    setSending(true);
    await getClient().post('/family/invitations', { inviteePhone: phone.value });
    setSending(false);
    setShowInvite(false);
    phone.reset();
    await refetchInvitations();
  };

  const handleSaveChild = async () => {
    setChildSaving(true);
    setChildSaveError(null);
    const res = await getClient().post('/family/children', {
      nickname: childNickname.value,
      pin: childPin.value || undefined,
      birthday: childBirthday.value || undefined,
    });
    setChildSaving(false);
    if (res.error) {
      setChildSaveError(res.error.message ?? '添加孩子失败');
      return;
    }
    // 刷新家庭概览即可同步成员与孩子（单一数据源）。
    await refetch();
    setShowChildModal(false);
    resetChildForm();
    message.success('保存成功');
  };

  const handleRemove = async (member: FamilyMember) => {
    setActionLoading(true);
    setActionError(null);
    try {
      await getClient().delete(`/family/members/${member.id}`);
      await refetch();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : '移除失败');
    } finally {
      setActionLoading(false);
      setConfirm(null);
    }
  };

  const handleRemoveChild = async (child: ChildProfile) => {
    setActionLoading(true);
    setActionError(null);
    try {
      const res = await getClient().delete(`/family/children/${child.id}`);
      if (res.error) {
        setActionError(res.error.message ?? '移除孩子失败');
        return;
      }
      await refetch();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : '移除孩子失败');
    } finally {
      setActionLoading(false);
      setConfirm(null);
    }
  };

  const handleLeave = async () => {
    setActionLoading(true);
    setActionError(null);
    try {
      await getClient().post('/family/members/me/leave');
      await refetch();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : '退出失败');
    } finally {
      setActionLoading(false);
      setConfirm(null);
    }
  };

  if (!online)
    return (
      <Result status="warning" title="当前处于离线状态" subTitle="请检查网络连接，恢复后重试" extra={<Button onClick={refetch}>重试</Button>} />
    );
  if (loading)
    return <Spin />;
  if (error)
    return (
      <Result status="error" title="加载失败" subTitle={error.message} extra={<Button onClick={refetch}>重试</Button>} />
    );
  if (!data)
    return <Empty description="暂无数据" />;

  const confirmTitle =
    confirm?.type === 'leave'
      ? '退出家庭'
      : confirm?.type === 'removeChild'
        ? '移除孩子'
        : '移除成员';
  const confirmMessage =
    confirm?.type === 'leave'
      ? '退出后你将无法管理该家庭，是否继续？'
      : confirm?.type === 'removeChild'
        ? '移除后该孩子将无法继续使用家庭功能，是否继续？'
        : '移除后该家长将无法管理此家庭，是否继续？';
  const confirmButtonText =
    confirm?.type === 'leave' ? '退出' : confirm?.type === 'removeChild' ? '移除' : '移除';

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Row justify="space-between" align="middle">
        <Typography.Title level={4} style={{ margin: 0 }}>家庭</Typography.Title>
        <Space>
          <Button onClick={() => setShowInvite(true)}>邀请家长</Button>
          <Button onClick={openNewChild}>添加孩子</Button>
        </Space>
      </Row>

      <Card title="家庭成员">
        <Space direction="vertical" size="small" style={{ width: '100%' }}>
          {(data.members ?? []).map((member) => {
            const isSelf = account != null && member.accountId === Number(account.accountId);
            const canRemove = member.role === 'PARENT' && !isSelf;
            return (
              <Row key={member.id} justify="space-between" align="middle">
                <Space direction="vertical" size={0}>
                  <Typography.Text strong>
                    {member.nickname ?? maskPhone(member.phone ?? '')}
                  </Typography.Text>
                  {member.phone && (
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>{maskPhone(member.phone)}</Typography.Text>
                  )}
                </Space>
                <Space>
                  <Tag>{statusLabel(member.role === 'PARENT' ? 'approved' : 'pending')}</Tag>
                  {isSelf && (
                    <Button danger size="small" onClick={() => setConfirm({ type: 'leave' })} loading={actionLoading}>
                      退出家庭
                    </Button>
                  )}
                  {canRemove && (
                    <Button danger size="small" onClick={() => setConfirm({ type: 'remove', member })} loading={actionLoading}>
                      移除
                    </Button>
                  )}
                </Space>
              </Row>
            );
          })}
        </Space>
      </Card>

      <Card title="孩子">
        {(data.children ?? []).length === 0 ? (
          <Typography.Text type="secondary">暂无孩子，点击上方「添加孩子」创建档案。</Typography.Text>
        ) : (
          <Space direction="vertical" size="small" style={{ width: '100%' }}>
            {(data.children ?? []).map((child) => (
              <Row key={child.id} justify="space-between" align="middle">
                <Space direction="vertical" size={0}>
                  <Typography.Text strong>{child.nickname}</Typography.Text>
                  {child.birthday && (
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>生日 {child.birthday}</Typography.Text>
                  )}
                </Space>
                <Button danger size="small" onClick={() => setConfirm({ type: 'removeChild', child })} loading={actionLoading}>
                  移除
                </Button>
              </Row>
            ))}
          </Space>
        )}
      </Card>

      <Card title="待处理邀请">
        {invitations.length === 0 ? (
          <Typography.Text type="secondary">暂无邀请</Typography.Text>
        ) : (
          <Space direction="vertical" size="small" style={{ width: '100%' }}>
            {invitations.map((inv) => (
              <Row key={inv.id} justify="space-between" align="middle">
                <Space direction="vertical" size={0}>
                  <Typography.Text strong>{maskPhone(inv.inviteePhone)}</Typography.Text>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>{inv.createdAt}</Typography.Text>
                </Space>
                <Tag>{statusLabel(inv.status.toLowerCase())}</Tag>
              </Row>
            ))}
          </Space>
        )}
      </Card>

      {actionError && (
        <Alert message={actionError} type="error" closable onClose={() => setActionError(null)} />
      )}

      <Modal open={showInvite} onCancel={() => setShowInvite(false)} title="邀请家长">
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <div>
            <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>被邀请人手机号</Typography.Text>
            <Input id="invite-phone" type="tel" placeholder="11 位手机号" {...phone.inputProps} />
          </div>
          <Button onClick={handleInvite} loading={sending} htmlType="button" style={{ width: '100%' }}>
            发送邀请
          </Button>
        </Space>
      </Modal>

      <Modal
        open={showChildModal}
        onCancel={() => setShowChildModal(false)}
        title="添加孩子"
        okText="保存"
        onOk={handleSaveChild}
        confirmLoading={childSaving}
      >
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          {childSaveError && <Alert message={childSaveError} type="error" showIcon />}
          <div>
            <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>昵称</Typography.Text>
            <Input id="child-nickname" {...childNickname.inputProps} />
          </div>
          <div>
            <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>PIN</Typography.Text>
            <Input id="child-pin" type="password" {...childPin.inputProps} />
          </div>
          <div>
            <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>生日</Typography.Text>
            <Input id="child-birthday" type="date" {...childBirthday.inputProps} />
          </div>
        </Space>
      </Modal>

      <Modal
        open={confirm != null}
        onCancel={() => setConfirm(null)}
        title={confirmTitle}
        footer={[
          <Button key="cancel" onClick={() => setConfirm(null)} disabled={actionLoading}>取消</Button>,
          <Button key="confirm" danger onClick={() => {
            if (confirm?.type === 'leave') handleLeave();
            else if (confirm?.type === 'remove' && confirm.member) handleRemove(confirm.member);
            else if (confirm?.type === 'removeChild' && confirm.child) handleRemoveChild(confirm.child);
          }} loading={actionLoading}>{confirmButtonText}</Button>,
        ]}
      >
        <Typography.Text>{confirmMessage}</Typography.Text>
      </Modal>
    </Space>
  );
}

export function ParentChildrenPage() {
  const { items, loading, error, refetch, page, pageSize, setPage, total } = usePaginatedData<ChildProfile>('/family/children');
  const [showModal, setShowModal] = useState(false);
  const [editing, setEditing] = useState<ChildProfile | null>(null);
  const nickname = useFormField();
  const pin = useFormField();
  const birthday = useFormField();
  const online = useOnline();
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  const openNew = () => {
    setEditing(null);
    nickname.reset();
    pin.reset();
    birthday.reset();
    setSaveError(null);
    setShowModal(true);
  };

  const openEdit = (child: ChildProfile) => {
    setEditing(child);
    nickname.setValue(child.nickname);
    pin.reset();
    birthday.setValue(child.birthday ?? '');
    setSaveError(null);
    setShowModal(true);
  };

  const handleSave = async () => {
    setSaving(true);
    setSaveError(null);
    const payload = {
      nickname: nickname.value,
      pin: pin.value || undefined,
      birthday: birthday.value || undefined,
    };
    const res = editing
      ? await getClient().put(`/family/children/${editing.id}`, payload)
      : await getClient().post('/family/children', payload);
    setSaving(false);
    if (res.error) {
      setSaveError(res.error.message ?? '保存失败');
      return;
    }
    setShowModal(false);
    message.success('保存成功');
    await refetch();
  };

  const handleDelete = async (id: number) => {
    await getClient().delete(`/family/children/${id}`);
    await refetch();
  };

  if (!online)
    return (
      <Result status="warning" title="当前处于离线状态" subTitle="请检查网络连接，恢复后重试" extra={<Button onClick={refetch}>重试</Button>} />
    );
  if (loading)
    return <Spin />;
  if (error)
    return (
      <Result status="error" title="加载失败" subTitle={error.message} extra={<Button onClick={refetch}>重试</Button>} />
    );

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Row justify="space-between" align="middle">
        <Typography.Title level={4} style={{ margin: 0 }}>孩子档案</Typography.Title>
        <Button onClick={openNew}>新增档案</Button>
      </Row>

      <Table
        dataSource={items}
        rowKey="id"
        loading={loading}
        pagination={{
          current: page + 1,
          total,
          pageSize,
          onChange: (p) => setPage(p - 1),
        }}
        columns={[
          { title: '名称', dataIndex: 'nickname', key: 'nickname' },
          { title: '年龄', dataIndex: 'birthday', key: 'birthday', render: (v: string | undefined) => v ?? '-' },
          {
            title: '操作',
            key: 'actions',
            render: (_: unknown, record: ChildProfile) => (
              <Space>
                <Button type="text" size="small" onClick={() => openEdit(record)}>编辑</Button>
                <Button danger size="small" onClick={() => handleDelete(record.id)}>删除</Button>
              </Space>
            ),
          },
        ]}
      />

      <Modal
        open={showModal}
        onCancel={() => setShowModal(false)}
        title={editing ? '编辑档案' : '新增档案'}
        okText="保存"
        onOk={handleSave}
        confirmLoading={saving}
      >
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          {saveError && <Alert message={saveError} type="error" showIcon />}
          <div>
            <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>昵称</Typography.Text>
            <Input id="child-nickname" {...nickname.inputProps} />
          </div>
          <div>
            <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>
              {editing ? '新 PIN（留空不修改）' : 'PIN'}
            </Typography.Text>
            <Input id="child-pin" type="password" {...pin.inputProps} />
          </div>
          <div>
            <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>生日</Typography.Text>
            <Input id="child-birthday" type="date" {...birthday.inputProps} />
          </div>
        </Space>
      </Modal>
    </Space>
  );
}

export function ParentTemplatesPage() {
  const [showModal, setShowModal] = useState(false);
  const [editing, setEditing] = useState<TaskTemplate | null>(null);
  const title = useFormField();
  const description = useFormField();
  const category = useFormField();
  const basePoints = useFormField('10');
  const [taskType, setTaskType] = useState<TaskTypeValue | ''>('');
  const [typeConfig, setTypeConfig] = useState<TypeConfigValue>({});
  const [selectedTypes, setSelectedTypes] = useState<TaskTypeValue[]>([]);
  const online = useOnline();
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  const filterParams = selectedTypes.length > 0 ? { taskType: selectedTypes.join(',') } : undefined;
  const { items, loading, error, refetch, page, pageSize, setPage, total } = usePaginatedData<TaskTemplate>(
    '/task-templates',
    filterParams,
  );

  const openNew = () => {
    setEditing(null);
    title.reset();
    description.reset();
    category.reset();
    basePoints.setValue('10');
    setTaskType('');
    setTypeConfig({});
    setSaveError(null);
    setShowModal(true);
  };

  const openEdit = (t: TaskTemplate) => {
    setEditing(t);
    title.setValue(t.name);
    description.setValue(t.description ?? '');
    category.setValue(t.category ?? '');
    basePoints.setValue(String(t.difficulties?.[0]?.rewardPoints ?? 10));
    setTaskType(t.taskType ?? '');
    try {
      const parsed = t.typeConfig ? JSON.parse(t.typeConfig) : {};
      setTypeConfig(parsed);
    } catch {
      setTypeConfig({});
    }
    setSaveError(null);
    setShowModal(true);
  };

  const handleSave = async () => {
    setSaving(true);
    setSaveError(null);
    const payload: Record<string, unknown> = {
      name: title.value,
      description: description.value,
      category: category.value,
      difficulties: [
        {
          name: '标准',
          displayOrder: 1,
          rewardPoints: Number(basePoints.value) || 1,
          enabled: true,
        },
      ],
    };
    // 添加任务类型和配置
    if (taskType) {
      payload.taskType = taskType;
      payload.typeConfig = JSON.stringify(typeConfig);
    }
    if (editing) {
      payload.version = editing.version;
      const res = await getClient().put(`/task-templates/${editing.id}`, payload);
      if (res.error) {
        setSaving(false);
        setSaveError(res.error.message ?? '保存失败');
        return;
      }
    } else {
      const res = await getClient().post('/task-templates', payload);
      if (res.error) {
        setSaving(false);
        setSaveError(res.error.message ?? '保存失败');
        return;
      }
    }
    setSaving(false);
    setShowModal(false);
    message.success('保存成功');
    await refetch();
  };

  const toggleEnabled = async (t: TaskTemplate) => {
    await getClient().put(`/task-templates/${t.id}/enabled`, { enabled: !t.enabled });
    await refetch();
  };

  if (!online)
    return (
      <Result status="warning" title="当前处于离线状态" subTitle="请检查网络连接，恢复后重试" extra={<Button onClick={refetch}>重试</Button>} />
    );
  if (loading)
    return <Spin />;
  if (error)
    return (
      <Result status="error" title="加载失败" subTitle={error.message} extra={<Button onClick={refetch}>重试</Button>} />
    );

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Row justify="space-between" align="middle">
        <Typography.Title level={4} style={{ margin: 0 }}>任务模板</Typography.Title>
        <Button onClick={openNew}>新建模板</Button>
      </Row>

      {/* 任务类型筛选器 */}
      <TaskTypeFilter selected={selectedTypes} onChange={setSelectedTypes} />

      <Table
        dataSource={items}
        rowKey="id"
        loading={loading}
        pagination={{
          current: page + 1,
          total,
          pageSize,
          onChange: (p) => setPage(p - 1),
        }}
        columns={[
          { title: '模板名称', dataIndex: 'name', key: 'name' },
          { title: '分类', dataIndex: 'category', key: 'category' },
          {
            title: '状态',
            key: 'enabled',
            render: (_: unknown, record: TaskTemplate) => (
              <Tag color={record.enabled ? 'success' : 'default'}>{record.enabled ? '已启用' : '已停用'}</Tag>
            ),
          },
          {
            title: '操作',
            key: 'actions',
            render: (_: unknown, record: TaskTemplate) => (
              <Space>
                <Button type="text" size="small" onClick={() => openEdit(record)}>编辑</Button>
                <Button size="small" onClick={() => toggleEnabled(record)}>
                  {record.enabled ? '停用' : '启用'}
                </Button>
              </Space>
            ),
          },
        ]}
      />

      <Modal
        open={showModal}
        onCancel={() => setShowModal(false)}
        title={editing ? '编辑模板' : '新建模板'}
        okText="保存"
        onOk={handleSave}
        confirmLoading={saving}
      >
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          {saveError && <Alert message={saveError} type="error" showIcon />}
          <div>
            <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>标题</Typography.Text>
            <Input id="tpl-title" {...title.inputProps} />
          </div>
          <div>
            <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>描述</Typography.Text>
            <TextArea id="tpl-desc" {...description.inputProps} />
          </div>
          <div>
            <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>分类</Typography.Text>
            <Input id="tpl-category" {...category.inputProps} />
          </div>
          <div>
            <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>基础积分</Typography.Text>
            <Input id="tpl-points" type="number" {...basePoints.inputProps} />
          </div>

          {/* 任务类型选择器和配置表单 */}
          <TaskTypeConfigForms
            taskType={taskType}
            onTaskTypeChange={setTaskType}
            typeConfig={typeConfig}
            onTypeConfigChange={setTypeConfig}
          />
        </Space>
      </Modal>
    </Space>
  );
}

export function ParentTasksPage() {
  const [date, setDate] = useState(() => new Date().toISOString().split('T')[0]);
  const { data, loading, error, refetch } = useApi<PageResult<TaskAssignment>>(
    `/task-assignments?page=1&pageSize=100&startDate=${date}&endDate=${date}`,
  );
  const { data: templates } = useApi<PageResult<TaskTemplate>>('/task-templates?enabled=true');
  const { data: children } = useApi<PageResult<ChildProfile>>('/family/children');
  const [showAssign, setShowAssign] = useState(false);
  const templateId = useFormField();
  const difficultyId = useFormField();
  const startDate = useFormField();
  const endDate = useFormField();
  const { setValue: setDifficultyId } = difficultyId;
  const { setValue: setStartDate } = startDate;
  const { setValue: setEndDate } = endDate;
  const [childIds, setChildIds] = useState<number[]>([]);
  const online = useOnline();
  const [assigning, setAssigning] = useState(false);
  const idempotencyKey = useIdempotencyKey();

  const selectedTemplate = useMemo(() => {
    return (templates?.content ?? []).find((t) => String(t.id) === templateId.value);
  }, [templates, templateId.value]);

  const enabledDifficulties = useMemo(() => {
    return (selectedTemplate?.difficulties ?? []).filter((d) => d.enabled);
  }, [selectedTemplate]);

  useEffect(() => {
    if (selectedTemplate && enabledDifficulties.length > 0) {
      const first = enabledDifficulties[0];
      if (String(first.id) !== difficultyId.value) {
        setDifficultyId(String(first.id));
      }
    } else if (!selectedTemplate) {
      setDifficultyId('');
    }
  }, [selectedTemplate, enabledDifficulties, difficultyId.value, setDifficultyId]);

  // 根据所选模板的任务类型自动填入日期
  useEffect(() => {
    if (!selectedTemplate) {
      setStartDate('');
      setEndDate('');
      return;
    }
    const today = new Date().toISOString().split('T')[0];
    if (selectedTemplate.taskType === 'LIMITED') {
      try {
        const config = selectedTemplate.typeConfig ? JSON.parse(selectedTemplate.typeConfig) : {};
        setStartDate((config.start_date as string) || today);
        setEndDate((config.end_date as string) || today);
      } catch {
        setStartDate(today);
        setEndDate(today);
      }
    } else {
      // REPEAT / STANDING：默认只填开始日期，结束日期与开始日期保持一致
      setStartDate(today);
      setEndDate(today);
    }
  }, [selectedTemplate, setStartDate, setEndDate]);

  const resetAssignForm = () => {
    templateId.reset();
    difficultyId.reset();
    startDate.reset();
    endDate.reset();
    setChildIds([]);
  };

  const handleAssign = async () => {
    const tId = Number(templateId.value);
    const dId = Number(difficultyId.value);
    if (!tId || !dId) {
      Modal.error({ title: '错误', content: '请选择模板和难度' });
      return;
    }
    if (childIds.length === 0) {
      Modal.error({ title: '错误', content: '请至少选择一个孩子' });
      return;
    }
    const isRepeat = selectedTemplate?.taskType === 'REPEAT';
    const payloadStartDate = startDate.value;
    const payloadEndDate = isRepeat ? payloadStartDate : endDate.value;
    if (!payloadStartDate || !payloadEndDate) {
      Modal.error({ title: '错误', content: '请填写开始日期和结束日期' });
      return;
    }
    if (payloadStartDate > payloadEndDate) {
      Modal.error({ title: '错误', content: '开始日期不得晚于结束日期' });
      return;
    }

    setAssigning(true);
    const res = await getClient().post('/task-assignments/batch', {
      templateId: tId,
      difficultyId: dId,
      startDate: payloadStartDate,
      endDate: payloadEndDate,
      childIds,
      idempotencyKey,
    });
    setAssigning(false);
    if (res.error) {
      Modal.error({ title: '分配失败', content: res.error.message ?? '分配失败' });
      return;
    }
    setShowAssign(false);
    resetAssignForm();
    await refetch();
  };

  if (!online)
    return (
      <Result status="warning" title="当前处于离线状态" subTitle="请检查网络连接，恢复后重试" extra={<Button onClick={refetch}>重试</Button>} />
    );
  if (loading)
    return <Spin />;
  if (error)
    return (
      <Result status="error" title="加载失败" subTitle={error.message} extra={<Button onClick={refetch}>重试</Button>} />
    );

  const assignments = data?.content ?? [];

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Row justify="space-between" align="middle">
        <Typography.Title level={4} style={{ margin: 0 }}>任务分配</Typography.Title>
        <Button onClick={() => { resetAssignForm(); setShowAssign(true); }}>批量分配</Button>
      </Row>

      <Card title="日历">
        <Space direction="vertical" size="small" style={{ width: '100%' }}>
          <Typography.Text strong>选择日期</Typography.Text>
          <Input id="task-date" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
        </Space>
      </Card>

      <Card title="任务列表">
        <Space direction="vertical" size="small" style={{ width: '100%' }}>
          {assignments.map((a) => (
            <Card key={a.id} size="small" style={a.isOverdue ? { borderLeft: '4px solid #faad14' } : {}}>
              <Row justify="space-between" align="top">
                <Space direction="vertical" size={2}>
                  <Typography.Text strong>{a.templateTitle}</Typography.Text>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    {a.childNickname} · 截止 {a.deadline}
                  </Typography.Text>
                  {a.isOverdue && (
                    <Typography.Text style={{ fontSize: 12, fontWeight: 600, color: '#faad14' }}>已逾期</Typography.Text>
                  )}
                </Space>
                <Space direction="vertical" size={2} align="end">
                  <Tag>{statusLabel(a.status.toLowerCase())}</Tag>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>{a.points} 积分</Typography.Text>
                </Space>
              </Row>
            </Card>
          ))}
          {assignments.length === 0 && <Typography.Text type="secondary">当天暂无任务</Typography.Text>}
        </Space>
      </Card>

      <Modal open={showAssign} onCancel={() => setShowAssign(false)} title="批量分配任务">
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <div>
            <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>模板</Typography.Text>
            <Select
              id="assign-template"
              value={templateId.value || undefined}
              onChange={(v) => templateId.setValue(v)}
              placeholder="请选择模板"
              style={{ width: '100%' }}
            >
              {(templates?.content ?? []).map((t) => (
                <Select.Option key={t.id} value={String(t.id)}>{t.name}</Select.Option>
              ))}
            </Select>
          </div>
          <div>
            <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>难度</Typography.Text>
            <Select
              id="assign-difficulty"
              value={difficultyId.value || undefined}
              onChange={(v) => difficultyId.setValue(v)}
              disabled={!selectedTemplate}
              placeholder="请选择难度"
              style={{ width: '100%' }}
            >
              {enabledDifficulties.map((d) => (
                <Select.Option key={d.id} value={String(d.id)}>{d.name}（{d.rewardPoints} 积分）</Select.Option>
              ))}
            </Select>
          </div>
          <div>
            <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>孩子</Typography.Text>
            <Select
              id="assign-child"
              mode="multiple"
              value={childIds.map(String)}
              onChange={(values: string[]) => setChildIds(values.map(Number))}
              placeholder="请选择孩子"
              style={{ width: '100%' }}
            >
              {(children?.content ?? []).map((c) => (
                <Select.Option key={c.id} value={String(c.id)}>{c.nickname}</Select.Option>
              ))}
            </Select>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>可选择多个孩子</Typography.Text>
          </div>
          <div>
            <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>开始日期</Typography.Text>
            <Input id="assign-start-date" type="date" {...startDate.inputProps} />
          </div>
          {selectedTemplate?.taskType !== 'REPEAT' && (
            <div>
              <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>结束日期</Typography.Text>
              <Input id="assign-end-date" type="date" {...endDate.inputProps} />
            </div>
          )}
          <Button onClick={handleAssign} loading={assigning} htmlType="button" style={{ width: '100%' }}>
            分配
          </Button>
        </Space>
      </Modal>
    </Space>
  );
}

export function ParentReviewsPage() {
  const { data, loading, error, refetch } = useApi<PageResult<ReviewItem>>('/task-review/pending');
  const { data: history } = useApi<PageResult<ReviewItem>>('/task-review/history');
  const [reason, setReason] = useState('');
  const [active, setActive] = useState<ReviewItem | null>(null);
  const online = useOnline();
  const [submitting, setSubmitting] = useState(false);

  const decide = async (attemptId: number, approved: boolean) => {
    setSubmitting(true);
    await getClient().post(`/task-review/${attemptId}/${approved ? 'approve' : 'reject'}`, {
      reason: approved ? undefined : reason,
    });
    setSubmitting(false);
    setActive(null);
    setReason('');
    await refetch();
  };

  if (!online)
    return (
      <Result status="warning" title="当前处于离线状态" subTitle="请检查网络连接，恢复后重试" extra={<Button onClick={refetch}>重试</Button>} />
    );
  if (loading)
    return <Spin />;
  if (error)
    return (
      <Result status="error" title="加载失败" subTitle={error.message} extra={<Button onClick={refetch}>重试</Button>} />
    );

  const pending = data?.content ?? [];

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Typography.Title level={4} style={{ margin: 0 }}>任务审核</Typography.Title>

      <Card title="待审核">
        <Space direction="vertical" size="small" style={{ width: '100%' }}>
          {pending.length === 0 ? (
            <Typography.Text type="secondary">暂无待审核任务</Typography.Text>
          ) : (
            pending.map((item) => (
              <Card key={item.attemptId} size="small" style={item.isOverdue ? { borderLeft: '4px solid #faad14' } : {}}>
                <Row justify="space-between" align="top">
                  <Space direction="vertical" size={2}>
                    <Typography.Text strong>{item.templateTitle}</Typography.Text>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      {item.childNickname} · {item.submittedAt}
                    </Typography.Text>
                    {item.notes && <Typography.Text style={{ fontSize: 12 }}>{item.notes}</Typography.Text>}
                    {item.isOverdue && (
                      <Typography.Text style={{ fontSize: 12, fontWeight: 600, color: '#faad14' }}>已逾期</Typography.Text>
                    )}
                  </Space>
                  <Space direction="vertical" size={2}>
                    <Button size="small" onClick={() => decide(item.attemptId, true)} loading={submitting}>通过</Button>
                    <Button danger size="small" onClick={() => setActive(item)} loading={submitting}>驳回</Button>
                  </Space>
                </Row>
              </Card>
            ))
          )}
        </Space>
      </Card>

      <Card title="审核历史">
        <Space direction="vertical" size="small" style={{ width: '100%' }}>
          {(history?.content ?? []).length === 0 ? (
            <Typography.Text type="secondary">暂无历史</Typography.Text>
          ) : (
            (history?.content ?? []).map((item) => (
              <Card key={item.attemptId} size="small">
                <Typography.Text strong>{item.templateTitle}</Typography.Text>
                <Typography.Text type="secondary" style={{ display: 'block', fontSize: 12 }}>
                  {item.childNickname} · {item.submittedAt}
                </Typography.Text>
              </Card>
            ))
          )}
        </Space>
      </Card>

      <Modal
        open={!!active}
        onCancel={() => { setActive(null); setReason(''); }}
        title="驳回原因"
        footer={[
          <Button key="cancel" onClick={() => { setActive(null); setReason(''); }}>取消</Button>,
          <Button key="reject" danger onClick={() => active && decide(active.attemptId, false)} loading={submitting} disabled={!reason.trim()}>确认驳回</Button>,
        ]}
      >
        <TextArea
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder="请输入驳回原因"
          aria-label="驳回原因"
        />
      </Modal>
    </Space>
  );
}

export function ParentPointsPage() {
  const { data: children } = useApi<PageResult<ChildProfile>>('/family/children');
  const [selectedChild, setSelectedChild] = useState('');
  const { data, loading, error, refetch } = useApi<{
    balance: number;
    transactions: {
      id: number;
      amount: number;
      type: string;
      createdAt: string;
      reason?: string;
    }[];
  }>(selectedChild ? `/points/ledger/${selectedChild}` : '', { skip: !selectedChild });
  const online = useOnline();
  const amount = useFormField();
  const reason = useFormField();
  const [adjusting, setAdjusting] = useState(false);

  const handleAdjust = async () => {
    if (!selectedChild) return;
    setAdjusting(true);
    await getClient().post('/points/adjustments', {
      childId: Number(selectedChild),
      amount: Number(amount.value),
      reason: reason.value,
    });
    setAdjusting(false);
    amount.reset();
    reason.reset();
    await refetch();
  };

  if (!online)
    return (
      <Result status="warning" title="当前处于离线状态" subTitle="请检查网络连接，恢复后重试" extra={<Button onClick={refetch}>重试</Button>} />
    );
  if (loading)
    return <Spin />;
  if (error)
    return (
      <Result status="error" title="加载失败" subTitle={error.message} extra={<Button onClick={refetch}>重试</Button>} />
    );

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Typography.Title level={4} style={{ margin: 0 }}>积分</Typography.Title>

      <Card title="选择孩子">
        <Select
          value={selectedChild || undefined}
          onChange={(v) => setSelectedChild(v)}
          placeholder="请选择孩子"
          style={{ width: '100%' }}
        >
          {(children?.content ?? []).map((c) => (
            <Select.Option key={c.id} value={String(c.id)}>{c.nickname}</Select.Option>
          ))}
        </Select>
      </Card>

      {selectedChild && (
        <>
          <Card title="积分余额">
            <Typography.Title level={2} style={{ margin: 0 }}>{data?.balance ?? 0} 积分</Typography.Title>
          </Card>
          <Card title="积分调整">
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              <div>
                <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>调整数量（正数奖励、负数扣除）</Typography.Text>
                <Input id="adjust-amount" type="number" {...amount.inputProps} />
              </div>
              <div>
                <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>原因</Typography.Text>
                <Input id="adjust-reason" {...reason.inputProps} />
              </div>
              <Button onClick={handleAdjust} loading={adjusting}>确认调整</Button>
            </Space>
          </Card>
          <Card title="流水">
            <Space direction="vertical" size="small" style={{ width: '100%' }}>
              {(data?.transactions ?? []).map((tx) => (
                <Row key={tx.id} justify="space-between" align="middle">
                  <Space direction="vertical" size={0}>
                    <Typography.Text>{tx.reason ?? tx.type}</Typography.Text>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>{tx.createdAt}</Typography.Text>
                  </Space>
                  <Typography.Text strong style={{ color: tx.amount >= 0 ? '#52c41a' : '#ff4d4f' }}>
                    {tx.amount > 0 ? '+' : ''}{tx.amount}
                  </Typography.Text>
                </Row>
              ))}
              {(data?.transactions ?? []).length === 0 && (
                <Typography.Text type="secondary">暂无流水</Typography.Text>
              )}
            </Space>
          </Card>
        </>
      )}
    </Space>
  );
}

export function ParentPrizesPage() {
  const { items, loading, error, refetch, page, pageSize, setPage, total } = usePaginatedData<Prize>('/prizes');
  const [showModal, setShowModal] = useState(false);
  const [editing, setEditing] = useState<Prize | null>(null);
  const name = useFormField();
  const description = useFormField();
  const pointsCost = useFormField('0');
  const availableStock = useFormField('0');
  const online = useOnline();
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  const openNew = () => {
    setEditing(null);
    name.reset();
    description.reset();
    pointsCost.setValue('0');
    availableStock.setValue('0');
    setSaveError(null);
    setShowModal(true);
  };

  const openEdit = (p: Prize) => {
    setEditing(p);
    name.setValue(p.name);
    description.setValue(p.description);
    pointsCost.setValue(String(p.pointsCost));
    availableStock.setValue(String(p.availableStock));
    setSaveError(null);
    setShowModal(true);
  };

  const handleSave = async () => {
    setSaving(true);
    setSaveError(null);
    const payload = {
      name: name.value,
      description: description.value,
      pointsCost: Number(pointsCost.value),
      availableStock: Number(availableStock.value),
    };
    const res = editing
      ? await getClient().put(`/prizes/${editing.id}`, payload)
      : await getClient().post('/prizes', payload);
    setSaving(false);
    if (res.error) {
      setSaveError(res.error.message ?? '保存失败');
      return;
    }
    setShowModal(false);
    message.success('保存成功');
    await refetch();
  };

  if (!online)
    return (
      <Result status="warning" title="当前处于离线状态" subTitle="请检查网络连接，恢复后重试" extra={<Button onClick={refetch}>重试</Button>} />
    );
  if (loading)
    return <Spin />;
  if (error)
    return (
      <Result status="error" title="加载失败" subTitle={error.message} extra={<Button onClick={refetch}>重试</Button>} />
    );

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Row justify="space-between" align="middle">
        <Typography.Title level={4} style={{ margin: 0 }}>奖品</Typography.Title>
        <Button onClick={openNew}>新增奖品</Button>
      </Row>

      <Table
        dataSource={items}
        rowKey="id"
        loading={loading}
        pagination={{
          current: page + 1,
          total,
          pageSize,
          onChange: (p) => setPage(p - 1),
        }}
        columns={[
          { title: '奖品名称', dataIndex: 'name', key: 'name' },
          { title: '积分', dataIndex: 'pointsCost', key: 'pointsCost' },
          { title: '库存', dataIndex: 'availableStock', key: 'availableStock' },
          {
            title: '状态',
            key: 'enabled',
            render: (_: unknown, record: Prize) => (
              <Tag color={record.enabled ? 'success' : 'default'}>{record.enabled ? '启用' : '停用'}</Tag>
            ),
          },
          {
            title: '操作',
            key: 'actions',
            render: (_: unknown, record: Prize) => (
              <Button type="text" size="small" onClick={() => openEdit(record)}>编辑</Button>
            ),
          },
        ]}
      />

      <Modal
        open={showModal}
        onCancel={() => setShowModal(false)}
        title={editing ? '编辑奖品' : '新增奖品'}
        okText="保存"
        onOk={handleSave}
        confirmLoading={saving}
      >
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          {saveError && <Alert message={saveError} type="error" showIcon />}
          <div>
            <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>名称</Typography.Text>
            <Input id="prize-name" {...name.inputProps} />
          </div>
          <div>
            <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>描述</Typography.Text>
            <TextArea id="prize-desc" {...description.inputProps} />
          </div>
          <div>
            <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>积分价格</Typography.Text>
            <Input id="prize-cost" type="number" {...pointsCost.inputProps} />
          </div>
          <div>
            <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>库存</Typography.Text>
            <Input id="prize-stock" type="number" {...availableStock.inputProps} />
          </div>
        </Space>
      </Modal>
    </Space>
  );
}

export function ParentBlindBoxesPage() {
  const { items, loading, error, refetch, page, pageSize, setPage, total } = usePaginatedData<BlindBox>('/blind-boxes');
  const [selected, setSelected] = useState<BlindBox | null>(null);
  const { data: candidates } = useApi<{ candidates: BlindBoxCandidate[] }>(
    selected ? `/blind-boxes/${selected.id}/candidates` : '',
  );
  const online = useOnline();

  if (!online)
    return (
      <Result status="warning" title="当前处于离线状态" subTitle="请检查网络连接，恢复后重试" extra={<Button onClick={refetch}>重试</Button>} />
    );
  if (loading)
    return <Spin />;
  if (error)
    return (
      <Result status="error" title="加载失败" subTitle={error.message} extra={<Button onClick={refetch}>重试</Button>} />
    );

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Typography.Title level={4} style={{ margin: 0 }}>盲盒</Typography.Title>

      <Table
        dataSource={items}
        rowKey="id"
        loading={loading}
        pagination={{
          current: page + 1,
          total,
          pageSize,
          onChange: (p) => setPage(p - 1),
        }}
        columns={[
          { title: '名称', dataIndex: 'name', key: 'name' },
          { title: '价格', dataIndex: 'cost', key: 'cost', render: (v: number) => `${v} 积分` },
          {
            title: '状态',
            key: 'enabled',
            render: (_: unknown, record: BlindBox) => (
              <Tag color={record.enabled ? 'success' : 'default'}>{record.enabled ? '启用' : '停用'}</Tag>
            ),
          },
          {
            title: '操作',
            key: 'actions',
            render: (_: unknown, record: BlindBox) => (
              <Button
                type={selected?.id === record.id ? 'primary' : 'default'}
                size="small"
                onClick={() => setSelected(record)}
              >
                查看概率
              </Button>
            ),
          },
        ]}
      />

      {selected && (
        <Card title="概率预览">
          <Space direction="vertical" size="small" style={{ width: '100%' }}>
            {(candidates?.candidates ?? []).map((c) => (
              <Row key={c.prizeId} justify="space-between" align="middle">
                <Typography.Text>{c.prizeName}</Typography.Text>
                <Typography.Text strong>{(c.probability * 100).toFixed(1)}%</Typography.Text>
              </Row>
            ))}
            {(candidates?.candidates ?? []).length === 0 && (
              <Typography.Text type="secondary">暂无候选</Typography.Text>
            )}
          </Space>
        </Card>
      )}
    </Space>
  );
}

export function ParentExchangesPage() {
  const { items, loading, error, refetch, page, pageSize, setPage, total } = usePaginatedData<Exchange>('/exchanges');
  const [confirmId, setConfirmId] = useState<number | null>(null);
  const online = useOnline();
  const [acting, setActing] = useState(false);

  const fulfill = async (id: number) => {
    setActing(true);
    await getClient().post(`/exchanges/${id}/fulfill`);
    setActing(false);
    setConfirmId(null);
    await refetch();
  };

  const cancel = async (id: number) => {
    setActing(true);
    await getClient().post(`/exchanges/${id}/cancel`);
    setActing(false);
    await refetch();
  };

  if (!online)
    return (
      <Result status="warning" title="当前处于离线状态" subTitle="请检查网络连接，恢复后重试" extra={<Button onClick={refetch}>重试</Button>} />
    );
  if (loading)
    return <Spin />;
  if (error)
    return (
      <Result status="error" title="加载失败" subTitle={error.message} extra={<Button onClick={refetch}>重试</Button>} />
    );

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Typography.Title level={4} style={{ margin: 0 }}>兑换履约</Typography.Title>

      <Table
        dataSource={items}
        rowKey="id"
        loading={loading}
        pagination={{
          current: page + 1,
          total,
          pageSize,
          onChange: (p) => setPage(p - 1),
        }}
        locale={{ emptyText: <Empty description="暂无数据" /> }}
        columns={[
          { title: '孩子', dataIndex: 'childNickname', key: 'childNickname' },
          { title: '奖品', dataIndex: 'targetName', key: 'targetName' },
          {
            title: '状态',
            key: 'status',
            render: (_: unknown, record: Exchange) => <Tag>{statusLabel(record.status.toLowerCase())}</Tag>,
          },
          {
            title: '操作',
            key: 'actions',
            render: (_: unknown, record: Exchange) =>
              record.status === 'PENDING_FULFILLMENT' ? (
                <Space>
                  <Button size="small" onClick={() => setConfirmId(record.id)} loading={acting}>兑现</Button>
                  <Button size="small" onClick={() => cancel(record.id)} loading={acting}>取消</Button>
                </Space>
              ) : null,
          },
        ]}
      />

      <Modal
        open={confirmId !== null}
        onCancel={() => setConfirmId(null)}
        title="确认兑现"
        footer={[
          <Button key="cancel" onClick={() => setConfirmId(null)} disabled={acting}>取消</Button>,
          <Button key="confirm" onClick={() => confirmId !== null && fulfill(confirmId)} loading={acting}>确认兑现</Button>,
        ]}
      >
        <Typography.Text>兑换一旦兑现，积分将从孩子账户扣除。请确认已交付奖品。</Typography.Text>
      </Modal>
    </Space>
  );
}

export default ParentHomePage;
