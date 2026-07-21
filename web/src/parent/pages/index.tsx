import { useEffect, useMemo, useState } from 'react';
import { history } from 'umi';
import { getClient } from '@shared/api';
import type { TaskTypeValue } from '@shared/api/types';
import { Alert, Button, Card, Checkbox, DatePicker, Empty, Input, InputNumber, Modal, Result, Row, Select, Space, Spin, Table, Tag, Typography, message } from 'antd';
const { TextArea } = Input;
import dayjs from 'dayjs';
import { useAuth } from '@shared/auth';
import { useApi, useFormField, useIdempotencyKey } from '@shared/hooks/useApi';
import { useOnline } from '@shared/theme';
import { TaskTypeConfigForms, type TypeConfigValue } from '@parent/components/TaskTypeConfigForms';
import { TaskTypeFilter } from '@parent/components/TaskTypeFilter';
import { PrizeTypeConfigForms, type PrizeTypeConfig } from '@parent/components/PrizeTypeConfigForms';

/** Map API status values to Chinese labels */
function statusLabel(s: string): string {
  const map: Record<string, string> = {
    completed: '已完成', approved: '已通过', rejected: '已驳回',
    cancelled: '已取消', active: '启用', disabled: '停用',
    pending: '待处理', locked: '已锁定', success: '成功', failed: '失败',
  };
  return map[s?.toLowerCase()] ?? s;
}

/**
 * 根据任务类型的快照字段生成显示文本。
 * REPEAT 任务显示"重复任务，每天/每周/每月"；其他任务返回 null（由调用方显示截止日期）。
 */
function repeatTaskLabel(taskType: string | null | undefined, typeConfig: string | null | undefined): string | null {
  if (taskType === 'REPEAT') {
    try {
      const config = typeConfig ? JSON.parse(typeConfig) : {};
      const freq = config.frequency as string;
      if (freq === 'DAILY') return '重复任务，每天';
      if (freq === 'WEEKLY') return '重复任务，每周';
      if (freq === 'MONTHLY') return '重复任务，每月';
      if (freq === 'YEARLY') return '重复任务，每年';
      return '重复任务';
    } catch {
      return '重复任务';
    }
  }
  return null;
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
interface DeviceBinding {
  id: number;
  deviceId: string;
  status: string;
  boundBy: number;
  credential?: string;
  createdAt: string;
}

interface Family {
  id: number;
  name: string;
  members: FamilyMember[];
  children: ChildProfile[];
  devices?: DeviceBinding[];
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
  allowResubmit: boolean;
  maxSubmissions: number;
  pointsCap: number;
}

interface TaskAssignment {
  id: number;
  childId: number;
  templateId: number;
  difficultyId: number;
  status: string;
  deadline: string;
  snapshotTemplateName: string;
  snapshotDifficultyName: string;
  snapshotDifficultyReward: number;
  snapshotTemplateDescription?: string;
  snapshotTemplateCategory?: string;
  snapshotTemplateTaskType?: string;
  overdue: boolean;
  version?: number;
  cancelled?: boolean;
  cancelledReason?: string;
  snapshotTemplateAllowResubmit: boolean | null;
  snapshotTemplateMaxSubmissions: number | null;
  snapshotTemplatePointsCap: number | null;
  canSubmit: boolean;
  submissionBlockReason: 'MAX_REACHED' | 'POINTS_CAP_REACHED' | null;
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
  prizeType?: string;
  prizeCategory?: string;
  titleImage?: string;
  detailImage?: string;
  validFrom?: string;
  validTo?: string;
  typeConfig?: string;
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
  childId: number;
  familyId: number;
  type: 'DIRECT' | 'BLIND_BOX';
  status: 'PENDING_FULFILLMENT' | 'FULFILLED' | 'CANCELLED';
  costPoints: number;
  idempotencyKey: string;
  prizeId: number | null;
  poolId: number | null;
  resultPrizeId: number | null;
  fulfilledAt: string | null;
  fulfilledBy: number | null;
  cancelledAt: string | null;
  cancelledBy: number | null;
  createdAt: string;
  updatedAt: string;
}

/** 兑换状态 → 中文标签 + Tag 颜色 */
const EXCHANGE_STATUS_META: Record<string, { label: string; color: string }> = {
  PENDING_FULFILLMENT: { label: '待核销', color: 'orange' },
  FULFILLED: { label: '已核销', color: 'green' },
  CANCELLED: { label: '已取消', color: 'default' },
};

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
  const [showEditNameModal, setShowEditNameModal] = useState(false);
  const familyName = useFormField();

  const handleEditName = async () => {
    if (!familyName.value.trim()) return;
    const res = await getClient().put('/family', { name: familyName.value.trim() });
    if (res.error) {
      message.error(res.error.message ?? '编辑失败');
      return;
    }
    message.success('家庭名称已更新');
    setShowEditNameModal(false);
    await refetch();
  };

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
        <Typography.Title level={4} style={{ margin: 0 }}>{data.name}</Typography.Title>
        <Space>
          <Button onClick={() => { familyName.setValue(data.name); setShowEditNameModal(true); }}>编辑家庭名称</Button>
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
        open={showEditNameModal}
        onCancel={() => setShowEditNameModal(false)}
        title="编辑家庭名称"
        okText="保存"
        onOk={handleEditName}
      >
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <div>
            <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>家庭名称</Typography.Text>
            <Input id="family-name" {...familyName.inputProps} />
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
  const [allowResubmit, setAllowResubmit] = useState(false);
  const [maxSubmissions, setMaxSubmissions] = useState(0);
  const [pointsCap, setPointsCap] = useState(0);

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
    setAllowResubmit(false);
    setMaxSubmissions(0);
    setPointsCap(0);
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
    setAllowResubmit(t.allowResubmit ?? false);
    setMaxSubmissions(t.maxSubmissions ?? 0);
    setPointsCap(t.pointsCap ?? 0);
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
    payload.allow_resubmit = allowResubmit;
    payload.max_submissions = maxSubmissions;
    payload.points_cap = pointsCap;
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

  const handleDeleteTemplate = async (id: number) => {
    await getClient().delete(`/task-templates/${id}`);
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
                <Button danger size="small" onClick={() => {
                  Modal.confirm({
                    title: '确认删除',
                    content: '删除模板将影响相关任务分配，确认删除？',
                    okText: '确认删除',
                    okType: 'danger',
                    cancelText: '取消',
                    onOk: () => handleDeleteTemplate(record.id),
                  });
                }}>删除</Button>
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

          <div>
            <Checkbox
              checked={allowResubmit}
              onChange={(e) => setAllowResubmit(e.target.checked)}
            >
              允许重复提交
            </Checkbox>
          </div>

          {allowResubmit && (
            <>
              <div>
                <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>最大提交次数</Typography.Text>
                <InputNumber
                  min={0} max={10000} value={maxSubmissions}
                  onChange={(v) => setMaxSubmissions(v ?? 0)}
                  style={{ width: '100%' }}
                  placeholder="0 = 不限制"
                />
              </div>
              <div>
                <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>积分上限</Typography.Text>
                <InputNumber
                  min={0} max={100000000} value={pointsCap}
                  onChange={(v) => setPointsCap(v ?? 0)}
                  style={{ width: '100%' }}
                  placeholder="0 = 不限制"
                />
              </div>
            </>
          )}
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
  const childNameMap = useMemo(() => {
    const map = new Map<number, string>();
    for (const c of children?.content ?? []) {
      map.set(c.id, c.nickname);
    }
    return map;
  }, [children]);
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

  // 单任务分配状态
  const [showSingleAssign, setShowSingleAssign] = useState(false);
  const singleTemplateId = useFormField();
  const singleDifficultyId = useFormField();
  const singleChildId = useFormField();
  const [singleDeadline, setSingleDeadline] = useState('');
  const [singleAssigning, setSingleAssigning] = useState(false);

  const selectedSingleTemplate = useMemo(() => {
    return (templates?.content ?? []).find((t) => String(t.id) === singleTemplateId.value);
  }, [templates, singleTemplateId.value]);

  const singleEnabledDifficulties = useMemo(() => {
    return (selectedSingleTemplate?.difficulties ?? []).filter((d) => d.enabled);
  }, [selectedSingleTemplate]);

  const resetSingleAssignForm = () => {
    singleTemplateId.reset();
    singleDifficultyId.reset();
    singleChildId.reset();
    setSingleDeadline('');
  };

  const handleSingleAssign = async () => {
    const tId = Number(singleTemplateId.value);
    const dId = Number(singleDifficultyId.value);
    const cId = Number(singleChildId.value);
    if (!tId || !dId || !cId) {
      message.error('请选择模板、难度和孩子');
      return;
    }

    const isRepeat = selectedSingleTemplate?.taskType === 'REPEAT';

    if (isRepeat) {
      // 重复任务：不需要任何日期，直接创建分配
      setSingleAssigning(true);
      const res = await getClient().post('/task-assignments', {
        templateId: tId,
        childId: cId,
        difficultyId: dId,
      });
      setSingleAssigning(false);
      if (res.error) {
        message.error(res.error.message ?? '分配失败');
        return;
      }
      setShowSingleAssign(false);
      resetSingleAssignForm();
      message.success('重复任务已分配');
      await refetch();
    } else {
      // 非重复任务：需要截止日期
      if (!singleDeadline) {
        message.error('请选择截止日期');
        return;
      }
      setSingleAssigning(true);
      const res = await getClient().post('/task-assignments', {
        templateId: tId,
        childId: cId,
        difficultyId: dId,
        deadline: `${singleDeadline}T23:59:59`,
      });
      setSingleAssigning(false);
      if (res.error) {
        message.error(res.error.message ?? '分配失败');
        return;
      }
      setShowSingleAssign(false);
      resetSingleAssignForm();
      message.success('任务已分配');
      await refetch();
    }
  };

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
        <Space>
          <Button onClick={() => { resetSingleAssignForm(); setShowSingleAssign(true); }}>分配任务</Button>
          <Button onClick={() => { resetAssignForm(); setShowAssign(true); }}>批量分配</Button>
        </Space>
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
            <Card key={a.id} size="small" style={a.overdue ? { borderLeft: '4px solid #faad14' } : {}}>
              <Row justify="space-between" align="top">
                <Space direction="vertical" size={2}>
                  <Typography.Text strong>{a.snapshotTemplateName}</Typography.Text>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    {(() => {
                      const childName = childNameMap.get(a.childId) ?? `ID ${a.childId}`;
                      const label = repeatTaskLabel(a.snapshotTemplateTaskType, a.snapshotTemplateTypeConfig);
                      return label
                        ? `孩子：${childName} · ${label}`
                        : `孩子：${childName} · 截止 ${a.deadline}`;
                    })()}
                  </Typography.Text>
                  {a.overdue && (
                    <Typography.Text style={{ fontSize: 12, fontWeight: 600, color: '#faad14' }}>已逾期</Typography.Text>
                  )}
                </Space>
                <Space direction="vertical" size={2} align="end">
                  <Tag>{statusLabel(a.status.toLowerCase())}</Tag>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>{a.snapshotDifficultyReward} 积分</Typography.Text>
                </Space>
              </Row>
            </Card>
          ))}
          {assignments.length === 0 && <Typography.Text type="secondary">当天暂无任务</Typography.Text>}
        </Space>
      </Card>

      <Modal open={showAssign} onCancel={() => setShowAssign(false)} title="批量分配任务" onOk={handleAssign} okText="分配" confirmLoading={assigning}>
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
        </Space>
      </Modal>

      {/* 单任务分配弹窗 */}
      <Modal open={showSingleAssign} onCancel={() => { setShowSingleAssign(false); resetSingleAssignForm(); }} title="分配任务" onOk={handleSingleAssign} okText="分配" confirmLoading={singleAssigning}>
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <div>
            <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>模板</Typography.Text>
            <Select
              id="single-assign-template"
              value={singleTemplateId.value || undefined}
              onChange={(v) => { singleTemplateId.setValue(v); singleDifficultyId.reset(); }}
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
              id="single-assign-difficulty"
              value={singleDifficultyId.value || undefined}
              onChange={(v) => singleDifficultyId.setValue(v)}
              disabled={!selectedSingleTemplate}
              placeholder="请选择难度"
              style={{ width: '100%' }}
            >
              {singleEnabledDifficulties.map((d) => (
                <Select.Option key={d.id} value={String(d.id)}>{d.name}（{d.rewardPoints} 积分）</Select.Option>
              ))}
            </Select>
          </div>
          <div>
            <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>孩子</Typography.Text>
            <Select
              id="single-assign-child"
              value={singleChildId.value || undefined}
              onChange={(v) => singleChildId.setValue(v)}
              placeholder="请选择孩子"
              style={{ width: '100%' }}
            >
              {(children?.content ?? []).map((c) => (
                <Select.Option key={c.id} value={String(c.id)}>{c.nickname}</Select.Option>
              ))}
            </Select>
          </div>
          {selectedSingleTemplate?.taskType !== 'REPEAT' && (
            <div>
              <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>截止日期</Typography.Text>
              <DatePicker
                id="single-assign-deadline"
                value={singleDeadline ? dayjs(singleDeadline) : null}
                onChange={(date) => setSingleDeadline(date ? date.format('YYYY-MM-DD') : '')}
                format="YYYY-MM-DD"
                style={{ width: '100%' }}
              />
            </div>
          )}
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
    currentBalance: number;
    content: {
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
    const amt = Number(amount.value);
    if (!amt || !reason.value.trim()) {
      message.error('请填写调整数量和原因');
      return;
    }
    const ref = `${Date.now()}_${Math.random().toString(36).substring(2, 8)}`;
    setAdjusting(true);
    const res = await getClient().post('/points/adjustments', {
      childId: Number(selectedChild),
      amount: amt,
      reason: reason.value,
      businessRef: ref,
    });
    setAdjusting(false);
    if (res.error) {
      message.error(res.error.message ?? '积分调整失败');
      return;
    }
    amount.reset();
    reason.reset();
    message.success('积分已调整');
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
            <Typography.Title level={2} style={{ margin: 0 }}>{data?.currentBalance ?? 0} 积分</Typography.Title>
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
              {(data?.content ?? []).map((tx) => (
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
                {(data?.content ?? []).length === 0 && (
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

  const prizeTypeValue = useFormField('PHYSICAL');
  const prizeCategoryValue = useFormField();
  const titleImage = useFormField();
  const detailImage = useFormField();
  const validFrom = useFormField();
  const validTo = useFormField();
  const typeConfigValue = useFormField();

  const prizeTypeConfig = useMemo((): PrizeTypeConfig => ({
    prizeType: (prizeTypeValue.value as 'VIRTUAL' | 'PHYSICAL') || 'PHYSICAL',
    prizeCategory: (prizeCategoryValue.value as any) || undefined,
    titleImage: titleImage.value || undefined,
    detailImage: detailImage.value || undefined,
    validFrom: validFrom.value || undefined,
    validTo: validTo.value || undefined,
    typeConfig: typeConfigValue.value || undefined,
  }), [prizeTypeValue.value, prizeCategoryValue.value, titleImage.value, detailImage.value, validFrom.value, validTo.value, typeConfigValue.value]);

  const handleUpload = async (file: File): Promise<string> => {
    const formData = new FormData();
    formData.append('file', file);
    const res = await getClient().post<{ url: string }>('/prizes/upload', formData);
    if (res.error) throw new Error(res.error.message);
    if (!res.data?.url) throw new Error('上传返回的 URL 为空');
    return res.data.url;
  };

  const openNew = () => {
    setEditing(null);
    name.reset();
    description.reset();
    pointsCost.setValue('0');
    availableStock.setValue('0');
    prizeTypeValue.setValue('PHYSICAL');
    prizeCategoryValue.reset();
    titleImage.reset();
    detailImage.reset();
    validFrom.reset();
    validTo.reset();
    typeConfigValue.reset();
    setSaveError(null);
    setShowModal(true);
  };

  const openEdit = (p: Prize) => {
    setEditing(p);
    name.setValue(p.name);
    description.setValue(p.description);
    pointsCost.setValue(String(p.pointsCost));
    availableStock.setValue(String(p.availableStock));
    prizeTypeValue.setValue(p.prizeType ?? 'PHYSICAL');
    prizeCategoryValue.setValue(p.prizeCategory ?? '');
    titleImage.setValue(p.titleImage ?? '');
    detailImage.setValue(p.detailImage ?? '');
    validFrom.setValue(p.validFrom ?? '');
    validTo.setValue(p.validTo ?? '');
    typeConfigValue.setValue(p.typeConfig ?? '');
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
      prizeType: prizeTypeValue.value,
      prizeCategory: prizeCategoryValue.value || undefined,
      titleImage: titleImage.value || undefined,
      detailImage: detailImage.value || undefined,
      validFrom: validFrom.value || undefined,
      validTo: validTo.value || undefined,
      typeConfig: typeConfigValue.value || undefined,
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

  const handleDeletePrize = async (id: number) => {
    await getClient().delete(`/prizes/${id}`);
    await refetch();
  };

  const togglePrizeEnabled = async (p: Prize) => {
    const payload = {
      name: p.name,
      description: p.description,
      pointsCost: p.pointsCost,
      availableStock: p.availableStock,
      enabled: !p.enabled,
    };
    await getClient().put(`/prizes/${p.id}`, payload);
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
            title: '类型',
            key: 'prizeType',
            render: (_: unknown, record: Prize) => {
              if (record.prizeType === 'VIRTUAL') {
                const labels: Record<string, string> = { TV_TIME: '电视时长卡', COMPUTER_TIME: '电脑时长卡', PARK_PLAY: '公园游玩卡', GENERAL: '通用', TRAVEL: '旅游卡' };
                return <Tag color="blue">虚拟 · {labels[record.prizeCategory ?? ''] || record.prizeCategory}</Tag>;
              }
              return <Tag>实物</Tag>;
            },
          },
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
              <Space>
                <Button type="text" size="small" onClick={() => openEdit(record)}>编辑</Button>
                <Button size="small" onClick={() => togglePrizeEnabled(record)}>
                  {record.enabled ? '停用' : '启用'}
                </Button>
                <Button danger size="small" onClick={() => {
                  Modal.confirm({
                    title: '确认删除',
                    content: '删除奖品将影响相关兑换记录，确认删除？',
                    okText: '确认删除',
                    okType: 'danger',
                    cancelText: '取消',
                    onOk: () => handleDeletePrize(record.id),
                  });
                }}>删除</Button>
              </Space>
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
        width={640}
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
          <PrizeTypeConfigForms
            value={prizeTypeConfig}
            onChange={v => {
              prizeTypeValue.setValue(v.prizeType);
              prizeCategoryValue.setValue(v.prizeCategory ?? '');
              titleImage.setValue(v.titleImage ?? '');
              detailImage.setValue(v.detailImage ?? '');
              validFrom.setValue(v.validFrom ?? '');
              validTo.setValue(v.validTo ?? '');
              typeConfigValue.setValue(v.typeConfig ?? '');
            }}
            onUpload={handleUpload}
          />
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
  const [cancelRecord, setCancelRecord] = useState<{ id: number; reason: string } | null>(null);
  const online = useOnline();
  const [acting, setActing] = useState(false);

  const fulfill = async (id: number) => {
    setActing(true);
    await getClient().post(`/exchanges/${id}/fulfill`);
    setActing(false);
    setConfirmId(null);
    message.success('已核销');
    await refetch();
  };

  const cancel = async (id: number, reason?: string) => {
    setActing(true);
    await getClient().post(`/exchanges/${id}/cancel`, { reason });
    setActing(false);
    setCancelRecord(null);
    message.success('已取消');
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
          {
            title: '孩子',
            key: 'child',
            render: (_: unknown, record: Exchange) => <span>#{record.childId}</span>,
          },
          {
            title: '奖品',
            key: 'prize',
            render: (_: unknown, record: Exchange) => {
              if (record.type === 'DIRECT') return <span>奖品 #{record.prizeId}</span>;
              return <span>盲盒 #{record.poolId}</span>;
            },
          },
          {
            title: '积分',
            dataIndex: 'costPoints',
            key: 'costPoints',
          },
          {
            title: '类型',
            key: 'type',
            render: (_: unknown, record: Exchange) =>
              record.type === 'DIRECT' ? <Tag>直接兑换</Tag> : <Tag color="purple">盲盒兑换</Tag>,
          },
          {
            title: '状态',
            key: 'status',
            render: (_: unknown, record: Exchange) => {
              const meta = EXCHANGE_STATUS_META[record.status];
              return <Tag color={meta?.color}>{meta?.label ?? record.status}</Tag>;
            },
          },
          {
            title: '创建时间',
            key: 'createdAt',
            render: (_: unknown, record: Exchange) => <span>{dayjs(record.createdAt).format('YYYY-MM-DD HH:mm')}</span>,
          },
          {
            title: '操作',
            key: 'actions',
            render: (_: unknown, record: Exchange) =>
              record.status === 'PENDING_FULFILLMENT' ? (
                <Space>
                  <Button size="small" type="primary" onClick={() => setConfirmId(record.id)} loading={acting}>核销</Button>
                  <Button size="small" onClick={() => setCancelRecord({ id: record.id, reason: '' })} loading={acting}>取消</Button>
                </Space>
              ) : (
                <Typography.Text type="secondary">-</Typography.Text>
              ),
          },
        ]}
      />

      <Modal
        open={confirmId !== null}
        onCancel={() => setConfirmId(null)}
        title="确认核销"
        footer={[
          <Button key="cancel" onClick={() => setConfirmId(null)} disabled={acting}>取消</Button>,
          <Button key="confirm" type="primary" onClick={() => confirmId !== null && fulfill(confirmId)} loading={acting}>确认核销</Button>,
        ]}
      >
        <Typography.Text>兑换一旦核销，积分将从孩子账户扣除。请确认已交付奖品。</Typography.Text>
      </Modal>

      <Modal
        open={cancelRecord !== null}
        onCancel={() => setCancelRecord(null)}
        title="取消兑换"
        onOk={() => cancelRecord && cancel(cancelRecord.id, cancelRecord.reason)}
        confirmLoading={acting}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Typography.Text>确定取消该兑换记录吗？</Typography.Text>
          <TextArea
            placeholder="取消原因（可选）"
            rows={3}
            value={cancelRecord?.reason ?? ''}
            onChange={(e) => setCancelRecord((prev) => (prev ? { ...prev, reason: e.target.value } : null))}
          />
        </Space>
      </Modal>
    </Space>
  );
}

export function ParentDevicesPage() {
  const { data: family, loading, error, refetch } = useApi<Family>('/family');
  const [deviceId, setDeviceId] = useState('');
  const [binding, setBinding] = useState(false);
  const [bindCredential, setBindCredential] = useState<string | null>(null);
  const [unbindId, setUnbindId] = useState<number | null>(null);
  const [unbinding, setUnbinding] = useState(false);
  const [manualUnbindId, setManualUnbindId] = useState('');
  const online = useOnline();

  const handleBind = async () => {
    if (!deviceId.trim()) return;
    setBinding(true);
    const res = await getClient().post<{ credential: string }>('/family/devices/bind', { deviceId: deviceId.trim() });
    setBinding(false);
    if (res.error) {
      message.error(res.error.message ?? '授权失败');
      return;
    }
    if (res.data?.credential) {
      setBindCredential(res.data.credential);
    }
    setDeviceId('');
    await refetch();
  };

  const handleUnbind = async () => {
    if (unbindId === null) return;
    setUnbinding(true);
    const res = await getClient().delete(`/family/devices/${unbindId}`);
    setUnbinding(false);
    if (res.error) {
      message.error(res.error.message ?? '解绑失败');
      return;
    }
    message.success('设备已解绑');
    setUnbindId(null);
    await refetch();
  };

  const handleManualUnbind = async () => {
    if (!manualUnbindId.trim()) return;
    const id = Number(manualUnbindId.trim());
    if (Number.isNaN(id)) {
      message.error('请输入有效的设备绑定 ID');
      return;
    }
    setUnbinding(true);
    const res = await getClient().delete(`/family/devices/${id}`);
    setUnbinding(false);
    if (res.error) {
      message.error(res.error.message ?? '解绑失败');
      return;
    }
    message.success('设备已解绑');
    setManualUnbindId('');
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
  if (!family)
    return <Empty description="暂无数据" />;

  const devices = family.devices ?? [];

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Typography.Title level={4} style={{ margin: 0 }}>设备管理</Typography.Title>

      {/* 授权新设备 */}
      <Card title="授权新设备">
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Typography.Text>输入孩子设备上显示的 deviceId 进行授权：</Typography.Text>
          <Input
            id="bind-device-id"
            placeholder="请输入 deviceId"
            value={deviceId}
            onChange={(e) => setDeviceId(e.target.value)}
          />
          <Button onClick={handleBind} loading={binding} disabled={!deviceId.trim()}>
            授权
          </Button>
        </Space>
      </Card>

      {/* 凭据展示 */}
      <Modal
        open={bindCredential !== null}
        onCancel={() => setBindCredential(null)}
        title="设备授权成功"
        footer={[<Button key="close" onClick={() => setBindCredential(null)}>关闭</Button>]}
      >
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Alert message="请将此凭据安全传递给孩子的设备" type="success" showIcon />
          <div>
            <Typography.Text strong style={{ display: 'block', marginBottom: 4 }}>一次性凭据</Typography.Text>
            <Input.TextArea
              id="bind-credential"
              value={bindCredential ?? ''}
              readOnly
              rows={3}
              style={{ background: '#f5f5f5' }}
            />
          </div>
        </Space>
      </Modal>

      {/* 设备列表 */}
      {devices.length > 0 && (
        <Table
          dataSource={devices}
          rowKey="id"
          pagination={false}
          columns={[
            {
              title: '设备 ID',
              dataIndex: 'deviceId',
              key: 'deviceId',
              render: (v: string) =>
                v.length > 20 ? (
                  <Typography.Text copyable={{ text: v }}>{v.slice(0, 20)}...</Typography.Text>
                ) : (
                  <Typography.Text copyable={{ text: v }}>{v}</Typography.Text>
                ),
            },
            {
              title: '状态',
              dataIndex: 'status',
              key: 'status',
              render: (v: string) => <Tag>{statusLabel(v)}</Tag>,
            },
            {
              title: '绑定时间',
              dataIndex: 'createdAt',
              key: 'createdAt',
            },
            {
              title: '操作',
              key: 'actions',
              render: (_: unknown, record: DeviceBinding) => (
                <Button danger size="small" onClick={() => setUnbindId(record.id)}>
                  解绑
                </Button>
              ),
            },
          ]}
        />
      )}

      {/* 手动解绑（无设备列表时的后备方案） */}
      {devices.length === 0 && (
        <Card title="解绑设备">
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Typography.Text>输入设备绑定 ID 进行解绑：</Typography.Text>
            <Input
              id="unbind-device-id"
              placeholder="请输入设备绑定 ID"
              value={manualUnbindId}
              onChange={(e) => setManualUnbindId(e.target.value)}
            />
            <Button
              danger
              onClick={handleManualUnbind}
              loading={unbinding}
              disabled={!manualUnbindId.trim()}
            >
              解绑
            </Button>
          </Space>
        </Card>
      )}

      {/* 解绑确认 */}
      <Modal
        open={unbindId !== null}
        onCancel={() => setUnbindId(null)}
        title="确认解绑"
        onOk={handleUnbind}
        okText="解绑"
        okType="danger"
        confirmLoading={unbinding}
      >
        <Typography.Text>解绑后该设备将无法使用家庭功能，是否继续？</Typography.Text>
      </Modal>
    </Space>
  );
}

export default ParentHomePage;
