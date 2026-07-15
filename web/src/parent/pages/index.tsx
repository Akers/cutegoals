import { useEffect, useMemo, useState } from 'react';
import { history } from 'umi';
import { getClient } from '@shared/api';
import type { TaskTypeValue } from '@shared/api/types';
import { Button, Card, Empty, Input, Modal, Result, Spin } from 'antd';
import {
  ConfirmModal,
  FormField,
  Label,
  Layout,
  PageHeader,
  StatusBadge,
} from '@shared/components';
const { TextArea } = Input;
import { useAuth } from '@shared/auth';
import { useApi, useFormField, useIdempotencyKey } from '@shared/hooks/useApi';
import { useOnline } from '@shared/theme';
import { TaskTypeConfigForms, type TypeConfigValue } from '@parent/components/TaskTypeConfigForms';
import { TaskTypeFilter } from '@parent/components/TaskTypeFilter';

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
function PageShell({
  title,
  children,
  actions,
}: {
  title: string;
  children: React.ReactNode;
  actions?: React.ReactNode;
}) {
  return (
    <Layout>
      <PageHeader title={title} actions={actions} />
      {children}
    </Layout>
  );
}

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
      <PageShell title="家庭">
        <Result status="warning" title="当前处于离线状态" subTitle="请检查网络连接，恢复后重试" extra={<Button onClick={refetch}>重试</Button>} />
      </PageShell>
    );
  if (loading)
    return (
      <PageShell title="家庭">
        <Spin className="flex justify-center py-12" />
      </PageShell>
    );
  if (error)
    return (
      <PageShell title="家庭">
        <Result status="error" title="加载失败" subTitle={error.message} extra={<Button onClick={refetch}>重试</Button>} />
      </PageShell>
    );
  if (!data)
    return (
      <PageShell title="家庭">
        <Empty description="暂无数据" />
      </PageShell>
    );

  return (
    <PageShell
      title="家庭"
      actions={
        <div className="flex items-center gap-2">
          <Button onClick={() => history.push('/parent/family')}>
            管理家庭
          </Button>
          <Button onClick={() => history.push('/parent/templates')}>
            任务模板
          </Button>
        </div>
      }
    >
      <Card title="家庭成员">
        <div className="grid grid-cols-1 gap-3">
          {(data.members ?? []).map((member) => (
            <div
              key={member.id}
              className="flex items-center justify-between rounded-cg-md bg-cg-surface-raised p-3"
            >
              <div className="font-medium text-cg-text">
                {member.nickname ?? maskPhone(member.phone ?? '')}
              </div>
              <StatusBadge status={member.role === 'PARENT' ? 'approved' : 'pending'} />
            </div>
          ))}
        </div>
      </Card>
    </PageShell>
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

  const resetChildForm = () => {
    childNickname.reset();
    childPin.reset();
    childBirthday.reset();
  };

  const openNewChild = () => {
    resetChildForm();
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
    setActionError(null);
    try {
      const res = await getClient().post('/family/children', {
        nickname: childNickname.value,
        pin: childPin.value || undefined,
        birthday: childBirthday.value || undefined,
      });
      if (res.error) {
        setActionError(res.error.message ?? '添加孩子失败');
        return;
      }
      // 刷新家庭概览即可同步成员与孩子（单一数据源）。
      await refetch();
      setShowChildModal(false);
      resetChildForm();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : '添加孩子失败');
    } finally {
      setChildSaving(false);
    }
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
      <PageShell title="家庭">
        <Result status="warning" title="当前处于离线状态" subTitle="请检查网络连接，恢复后重试" extra={<Button onClick={refetch}>重试</Button>} />
      </PageShell>
    );
  if (loading)
    return (
      <PageShell title="家庭">
        <Spin className="flex justify-center py-12" />
      </PageShell>
    );
  if (error)
    return (
      <PageShell title="家庭">
        <Result status="error" title="加载失败" subTitle={error.message} extra={<Button onClick={refetch}>重试</Button>} />
      </PageShell>
    );
  if (!data)
    return (
      <PageShell title="家庭">
        <Empty description="暂无数据" />
      </PageShell>
    );

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
    <PageShell
      title="家庭"
      actions={
        <div className="flex items-center gap-2">
          <Button onClick={() => setShowInvite(true)}>
            邀请家长
          </Button>
          <Button onClick={openNewChild}>
            添加孩子
          </Button>
        </div>
      }
    >
      <Card title="家庭成员">
        <div className="grid grid-cols-1 gap-3">
          {(data.members ?? []).map((member) => {
            const isSelf = account != null && member.accountId === Number(account.accountId);
            const canRemove = member.role === 'PARENT' && !isSelf;
            return (
              <div
                key={member.id}
                className="flex items-center justify-between rounded-cg-md bg-cg-surface-raised p-3"
              >
                <div>
                  <div className="font-medium text-cg-text">
                    {member.nickname ?? maskPhone(member.phone ?? '')}
                  </div>
                  {member.phone && (
                    <div className="text-xs text-cg-text-muted">{maskPhone(member.phone)}</div>
                  )}
                </div>
                <div className="flex items-center gap-2">
                  <StatusBadge status={member.role === 'PARENT' ? 'approved' : 'pending'} />
                  {isSelf && (
                    <Button
                      danger
                      size="small"
                      onClick={() => setConfirm({ type: 'leave' })}
                      loading={actionLoading}
                    >
                      退出家庭
                    </Button>
                  )}
                  {canRemove && (
                    <Button
                      danger
                      size="small"
                      onClick={() => setConfirm({ type: 'remove', member })}
                      loading={actionLoading}
                    >
                      移除
                    </Button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </Card>

      <Card title="孩子">
        {(data.children ?? []).length === 0 ? (
          <p className="text-cg-text-muted">暂无孩子，点击上方「添加孩子」创建档案。</p>
        ) : (
          <div className="grid grid-cols-1 gap-3">
            {(data.children ?? []).map((child) => (
              <div
                key={child.id}
                className="flex items-center justify-between rounded-cg-md bg-cg-surface-raised p-3"
              >
                <div>
                  <div className="font-medium text-cg-text">{child.nickname}</div>
                  {child.birthday && (
                    <div className="text-xs text-cg-text-muted">生日 {child.birthday}</div>
                  )}
                </div>
                <Button
                  danger
                  size="small"
                  onClick={() => setConfirm({ type: 'removeChild', child })}
                  loading={actionLoading}
                >
                  移除
                </Button>
              </div>
            ))}
          </div>
        )}
      </Card>

      <Card title="待处理邀请">
        {invitations.length === 0 ? (
          <p className="text-cg-text-muted">暂无邀请</p>
        ) : (
          <div className="grid grid-cols-1 gap-3">
            {invitations.map((inv) => (
              <div
                key={inv.id}
                className="flex items-center justify-between rounded-cg-md bg-cg-surface-raised p-3"
              >
                <div>
                  <div className="font-medium text-cg-text">{maskPhone(inv.inviteePhone)}</div>
                  <div className="text-xs text-cg-text-muted">{inv.createdAt}</div>
                </div>
                <StatusBadge status={inv.status.toLowerCase()} />
              </div>
            ))}
          </div>
        )}
      </Card>

      {actionError && <div className="text-sm text-red-600">{actionError}</div>}

      <Modal open={showInvite} onCancel={() => setShowInvite(false)} title="邀请家长">
        <form className="flex flex-col gap-4">
          <FormField label="被邀请人手机号" htmlFor="invite-phone">
            <Input id="invite-phone" type="tel" placeholder="11 位手机号" {...phone.inputProps} />
          </FormField>
          <Button onClick={handleInvite} loading={sending} htmlType="button" className="w-full">
            发送邀请
          </Button>
        </form>
      </Modal>

      <Modal open={showChildModal} onCancel={() => setShowChildModal(false)} title="添加孩子">
        <form className="flex flex-col gap-4">
          <FormField label="昵称" htmlFor="child-nickname">
            <Input id="child-nickname" {...childNickname.inputProps} />
          </FormField>
          <FormField label="PIN" htmlFor="child-pin">
            <Input id="child-pin" type="password" {...childPin.inputProps} />
          </FormField>
          <FormField label="生日" htmlFor="child-birthday">
            <Input id="child-birthday" type="date" {...childBirthday.inputProps} />
          </FormField>
          <Button
            onClick={handleSaveChild}
            loading={childSaving}
            htmlType="button"
            className="w-full"
          >
            保存
          </Button>
        </form>
      </Modal>

      <ConfirmModal
        isOpen={confirm != null}
        onClose={() => setConfirm(null)}
        title={confirmTitle}
        message={confirmMessage}
        confirmText={confirmButtonText}
        confirmVariant="danger"
        onConfirm={() => {
          if (confirm?.type === 'leave') {
            handleLeave();
          } else if (confirm?.type === 'remove' && confirm.member) {
            handleRemove(confirm.member);
          } else if (confirm?.type === 'removeChild' && confirm.child) {
            handleRemoveChild(confirm.child);
          }
        }}
        isConfirming={actionLoading}
      />
    </PageShell>
  );
}

export function ParentChildrenPage() {
  const { items, loading, error, refetch } = usePaginatedData<ChildProfile>('/family/children');
  const [showModal, setShowModal] = useState(false);
  const [editing, setEditing] = useState<ChildProfile | null>(null);
  const nickname = useFormField();
  const pin = useFormField();
  const birthday = useFormField();
  const online = useOnline();
  const [saving, setSaving] = useState(false);

  const openNew = () => {
    setEditing(null);
    nickname.reset();
    pin.reset();
    birthday.reset();
    setShowModal(true);
  };

  const openEdit = (child: ChildProfile) => {
    setEditing(child);
    nickname.setValue(child.nickname);
    pin.reset();
    birthday.setValue(child.birthday ?? '');
    setShowModal(true);
  };

  const handleSave = async () => {
    setSaving(true);
    const payload = {
      nickname: nickname.value,
      pin: pin.value || undefined,
      birthday: birthday.value || undefined,
    };
    if (editing) {
      await getClient().put(`/family/children/${editing.id}`, payload);
    } else {
      await getClient().post('/family/children', payload);
    }
    setSaving(false);
    setShowModal(false);
    await refetch();
  };

  const handleDelete = async (id: number) => {
    await getClient().delete(`/family/children/${id}`);
    await refetch();
  };

  if (!online)
    return (
      <PageShell title="孩子档案">
        <Result status="warning" title="当前处于离线状态" subTitle="请检查网络连接，恢复后重试" extra={<Button onClick={refetch}>重试</Button>} />
      </PageShell>
    );
  if (loading)
    return (
      <PageShell title="孩子档案">
        <Spin className="flex justify-center py-12" />
      </PageShell>
    );
  if (error)
    return (
      <PageShell title="孩子档案">
        <Result status="error" title="加载失败" subTitle={error.message} extra={<Button onClick={refetch}>重试</Button>} />
      </PageShell>
    );

  return (
    <PageShell
      title="孩子档案"
      actions={
        <Button onClick={openNew}>
          新增档案
        </Button>
      }
    >
      <div className="grid grid-cols-1 gap-3">
        {items.map((child) => (
          <div key={child.id} className="cg-card p-4">
            <div className="flex items-center justify-between">
              <div className="font-medium text-cg-text">{child.nickname}</div>
              <div className="flex gap-2">
                <Button type="text" size="small" onClick={() => openEdit(child)}>
                  编辑
                </Button>
                <Button danger size="small" onClick={() => handleDelete(child.id)}>
                  删除
                </Button>
              </div>
            </div>
            {child.birthday && <p className="text-sm text-cg-text-muted">生日 {child.birthday}</p>}
          </div>
        ))}
      </div>

      <Modal
        open={showModal}
        onCancel={() => setShowModal(false)}
        title={editing ? '编辑档案' : '新增档案'}
      >
        <form className="flex flex-col gap-4">
          <FormField label="昵称" htmlFor="child-nickname">
            <Input id="child-nickname" {...nickname.inputProps} />
          </FormField>
          <FormField label={editing ? '新 PIN（留空不修改）' : 'PIN'} htmlFor="child-pin">
            <Input id="child-pin" type="password" {...pin.inputProps} />
          </FormField>
          <FormField label="生日" htmlFor="child-birthday">
            <Input id="child-birthday" type="date" {...birthday.inputProps} />
          </FormField>
          <Button onClick={handleSave} loading={saving} htmlType="button" className="w-full">
            保存
          </Button>
        </form>
      </Modal>
    </PageShell>
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

  const filterParams = selectedTypes.length > 0 ? { taskType: selectedTypes.join(',') } : undefined;
  const { items, loading, error, refetch } = usePaginatedData<TaskTemplate>(
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
    setShowModal(true);
  };

  const handleSave = async () => {
    setSaving(true);
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
        alert(res.error.message ?? '保存失败');
        return;
      }
    } else {
      const res = await getClient().post('/task-templates', payload);
      if (res.error) {
        setSaving(false);
        alert(res.error.message ?? '保存失败');
        return;
      }
    }
    setSaving(false);
    setShowModal(false);
    await refetch();
  };

  const toggleEnabled = async (t: TaskTemplate) => {
    await getClient().put(`/task-templates/${t.id}/enabled`, { enabled: !t.enabled });
    await refetch();
  };

  if (!online)
    return (
      <PageShell title="任务模板">
        <Result status="warning" title="当前处于离线状态" subTitle="请检查网络连接，恢复后重试" extra={<Button onClick={refetch}>重试</Button>} />
      </PageShell>
    );
  if (loading)
    return (
      <PageShell title="任务模板">
        <Spin className="flex justify-center py-12" />
      </PageShell>
    );
  if (error)
    return (
      <PageShell title="任务模板">
        <Result status="error" title="加载失败" subTitle={error.message} extra={<Button onClick={refetch}>重试</Button>} />
      </PageShell>
    );

  return (
    <PageShell
      title="任务模板"
      actions={
        <Button onClick={openNew}>
          新建模板
        </Button>
      }
    >
      {/* 任务类型筛选器 */}
      <TaskTypeFilter selected={selectedTypes} onChange={setSelectedTypes} />

      <div className="grid grid-cols-1 gap-3">
        {items.map((t) => (
          <div key={t.id} className="cg-card p-4">
            <div className="flex items-start justify-between">
              <div>
                <div className="font-medium text-cg-text">{t.name}</div>
                <p className="text-sm text-cg-text-muted">{t.description}</p>
                <div className="mt-1 flex flex-wrap gap-2 text-sm">
                  <span className="rounded-cg-sm bg-cg-surface-raised px-2 py-0.5">
                    {t.category}
                  </span>
                  <span className="rounded-cg-sm bg-cg-surface-raised px-2 py-0.5">
                    {t.difficulties?.[0]?.rewardPoints ?? '-'} 积分
                  </span>
                  {t.taskType && (
                    <span className="rounded-cg-sm bg-cg-surface-raised px-2 py-0.5">
                      {t.taskType === 'LIMITED'
                        ? '限时'
                        : t.taskType === 'REPEAT'
                          ? '重复'
                          : '常驻'}
                    </span>
                  )}
                  <span
                    className={`rounded-cg-sm px-2 py-0.5 ${t.enabled ? 'bg-cg-success-bg text-cg-success' : 'bg-cg-surface-raised text-cg-text-muted'}`}
                    aria-label={t.enabled ? '已启用' : '已停用'}
                  >
                    {t.enabled ? '已启用' : '已停用'}
                  </span>
                </div>
              </div>
              <div className="flex gap-2">
                <Button type="text" size="small" onClick={() => openEdit(t)}>
                  编辑
                </Button>
                <Button size="small" onClick={() => toggleEnabled(t)}>
                  {t.enabled ? '停用' : '启用'}
                </Button>
              </div>
            </div>
          </div>
        ))}
      </div>

      <Modal
        open={showModal}
        onCancel={() => setShowModal(false)}
        title={editing ? '编辑模板' : '新建模板'}
      >
        <form className="flex flex-col gap-4">
          <FormField label="标题" htmlFor="tpl-title">
            <Input id="tpl-title" {...title.inputProps} />
          </FormField>
          <FormField label="描述" htmlFor="tpl-desc">
            <TextArea id="tpl-desc" {...description.inputProps} />
          </FormField>
          <FormField label="分类" htmlFor="tpl-category">
            <Input id="tpl-category" {...category.inputProps} />
          </FormField>
          <FormField label="基础积分" htmlFor="tpl-points">
            <Input id="tpl-points" type="number" {...basePoints.inputProps} />
          </FormField>

          {/* 任务类型选择器和配置表单 */}
          <TaskTypeConfigForms
            taskType={taskType}
            onTaskTypeChange={setTaskType}
            typeConfig={typeConfig}
            onTypeConfigChange={setTypeConfig}
          />

          <Button onClick={handleSave} loading={saving} htmlType="button" className="w-full">
            保存
          </Button>
        </form>
      </Modal>
    </PageShell>
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
      alert('请选择模板和难度');
      return;
    }
    if (childIds.length === 0) {
      alert('请至少选择一个孩子');
      return;
    }
    const isRepeat = selectedTemplate?.taskType === 'REPEAT';
    const payloadStartDate = startDate.value;
    const payloadEndDate = isRepeat ? payloadStartDate : endDate.value;
    if (!payloadStartDate || !payloadEndDate) {
      alert('请填写开始日期和结束日期');
      return;
    }
    if (payloadStartDate > payloadEndDate) {
      alert('开始日期不得晚于结束日期');
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
      alert(res.error.message ?? '分配失败');
      return;
    }
    setShowAssign(false);
    resetAssignForm();
    await refetch();
  };

  if (!online)
    return (
      <PageShell title="任务分配">
        <Result status="warning" title="当前处于离线状态" subTitle="请检查网络连接，恢复后重试" extra={<Button onClick={refetch}>重试</Button>} />
      </PageShell>
    );
  if (loading)
    return (
      <PageShell title="任务分配">
        <Spin className="flex justify-center py-12" />
      </PageShell>
    );
  if (error)
    return (
      <PageShell title="任务分配">
        <Result status="error" title="加载失败" subTitle={error.message} extra={<Button onClick={refetch}>重试</Button>} />
      </PageShell>
    );

  const assignments = data?.content ?? [];

  return (
    <PageShell
      title="任务分配"
      actions={
        <Button
         
          onClick={() => {
            resetAssignForm();
            setShowAssign(true);
          }}
        >
          批量分配
        </Button>
      }
    >
      <Card title="日历">
        <Label htmlFor="task-date">选择日期</Label>
        <Input id="task-date" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
      </Card>

      <Card title="任务列表">
        <div className="grid grid-cols-1 gap-3">
          {assignments.map((a) => (
            <div
              key={a.id}
              className={`cg-card p-4 ${a.isOverdue ? 'border-l-4 border-l-cg-warning' : ''}`}
            >
              <div className="flex items-start justify-between">
                <div>
                  <div className="font-medium text-cg-text">{a.templateTitle}</div>
                  <div className="text-sm text-cg-text-muted">
                    {a.childNickname} · 截止 {a.deadline}
                  </div>
                  {a.isOverdue && (
                    <div className="mt-1 text-sm font-semibold text-cg-warning">已逾期</div>
                  )}
                </div>
                <div className="flex flex-col items-end gap-1">
                  <StatusBadge status={a.status.toLowerCase()} />
                  <span className="text-sm text-cg-text-muted">{a.points} 积分</span>
                </div>
              </div>
            </div>
          ))}
          {assignments.length === 0 && <p className="text-cg-text-muted">当天暂无任务</p>}
        </div>
      </Card>

      <Modal open={showAssign} onCancel={() => setShowAssign(false)} title="批量分配任务">
        <form className="flex flex-col gap-4">
          <FormField label="模板" htmlFor="assign-template">
            <select id="assign-template" {...templateId.inputProps}>
              <option value="">请选择模板</option>
              {(templates?.content ?? []).map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
          </FormField>
          <FormField label="难度" htmlFor="assign-difficulty">
            <select
              id="assign-difficulty"
              {...difficultyId.inputProps}
              disabled={!selectedTemplate}
            >
              <option value="">请选择难度</option>
              {enabledDifficulties.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name}（{d.rewardPoints} 积分）
                </option>
              ))}
            </select>
          </FormField>
          <FormField label="孩子" htmlFor="assign-child">
            <select
              id="assign-child"
              multiple
              size={4}
              value={childIds.map(String)}
              onChange={(e) => {
                const selected = Array.from(e.target.selectedOptions).map((o) => Number(o.value));
                setChildIds(selected);
              }}
              className="w-full rounded-cg-md border border-cg-border bg-cg-surface px-3 py-2 text-cg-text focus:border-cg-focus focus:outline-none focus:ring-2 focus:ring-cg-focus min-h-touch"
            >
              {(children?.content ?? []).map((c) => (
                <option key={c.id} value={c.id}>
                  {c.nickname}
                </option>
              ))}
            </select>
            <p className="text-xs text-cg-text-muted">按住 Ctrl/Cmd 可选择多个孩子</p>
          </FormField>
          <FormField label="开始日期" htmlFor="assign-start-date">
            <Input id="assign-start-date" type="date" {...startDate.inputProps} />
          </FormField>
          {selectedTemplate?.taskType !== 'REPEAT' && (
            <FormField label="结束日期" htmlFor="assign-end-date">
              <Input id="assign-end-date" type="date" {...endDate.inputProps} />
            </FormField>
          )}
          <Button onClick={handleAssign} loading={assigning} htmlType="button" className="w-full">
            分配
          </Button>
        </form>
      </Modal>
    </PageShell>
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
      <PageShell title="任务审核">
        <Result status="warning" title="当前处于离线状态" subTitle="请检查网络连接，恢复后重试" extra={<Button onClick={refetch}>重试</Button>} />
      </PageShell>
    );
  if (loading)
    return (
      <PageShell title="任务审核">
        <Spin className="flex justify-center py-12" />
      </PageShell>
    );
  if (error)
    return (
      <PageShell title="任务审核">
        <Result status="error" title="加载失败" subTitle={error.message} extra={<Button onClick={refetch}>重试</Button>} />
      </PageShell>
    );

  const pending = data?.content ?? [];

  return (
    <PageShell title="任务审核">
      <Card title="待审核">
        <div className="grid grid-cols-1 gap-3">
          {pending.length === 0 ? (
            <p className="text-cg-text-muted">暂无待审核任务</p>
          ) : (
            pending.map((item) => (
              <div
                key={item.attemptId}
                className={`cg-card p-4 ${item.isOverdue ? 'border-l-4 border-l-cg-warning' : ''}`}
              >
                <div className="flex items-start justify-between">
                  <div>
                    <div className="font-medium text-cg-text">{item.templateTitle}</div>
                    <div className="text-sm text-cg-text-muted">
                      {item.childNickname} · {item.submittedAt}
                    </div>
                    {item.notes && <p className="mt-1 text-sm text-cg-text">{item.notes}</p>}
                    {item.isOverdue && (
                      <div className="mt-1 text-sm font-semibold text-cg-warning">已逾期</div>
                    )}
                  </div>
                  <div className="flex flex-col gap-2">
                    <Button
                      size="small"
                      onClick={() => decide(item.attemptId, true)}
                      loading={submitting}
                    >
                      通过
                    </Button>
                    <Button
                      danger
                      size="small"
                      onClick={() => setActive(item)}
                      loading={submitting}
                    >
                      驳回
                    </Button>
                  </div>
                </div>
              </div>
            ))
          )}
        </div>
      </Card>

      <Card title="审核历史">
        <div className="grid grid-cols-1 gap-3">
          {(history?.content ?? []).length === 0 ? (
            <p className="text-cg-text-muted">暂无历史</p>
          ) : (
            (history?.content ?? []).map((item) => (
              <div key={item.attemptId} className="cg-card p-4">
                <div className="font-medium text-cg-text">{item.templateTitle}</div>
                <div className="text-sm text-cg-text-muted">
                  {item.childNickname} · {item.submittedAt}
                </div>
              </div>
            ))
          )}
        </div>
      </Card>

      <Modal
        open={!!active}
        onCancel={() => {
          setActive(null);
          setReason('');
        }}
        title="驳回原因"
      >
        <div className="flex flex-col gap-4">
          <TextArea
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="请输入驳回原因"
            aria-label="驳回原因"
          />
          <div className="flex gap-2">
            <Button onClick={() => setActive(null)}>
              取消
            </Button>
            <Button
              danger
              onClick={() => active && decide(active.attemptId, false)}
              loading={submitting}
              disabled={!reason.trim()}
            >
              确认驳回
            </Button>
          </div>
        </div>
      </Modal>
    </PageShell>
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
      <PageShell title="积分">
        <Result status="warning" title="当前处于离线状态" subTitle="请检查网络连接，恢复后重试" extra={<Button onClick={refetch}>重试</Button>} />
      </PageShell>
    );
  if (loading)
    return (
      <PageShell title="积分">
        <Spin className="flex justify-center py-12" />
      </PageShell>
    );
  if (error)
    return (
      <PageShell title="积分">
        <Result status="error" title="加载失败" subTitle={error.message} extra={<Button onClick={refetch}>重试</Button>} />
      </PageShell>
    );

  return (
    <PageShell title="积分">
      <Card title="选择孩子">
        <select value={selectedChild} onChange={(e) => setSelectedChild(e.target.value)}>
          <option value="">请选择孩子</option>
          {(children?.content ?? []).map((c) => (
            <option key={c.id} value={c.id}>
              {c.nickname}
            </option>
          ))}
        </select>
      </Card>

      {selectedChild && (
        <>
          <Card title="积分余额">
            <div className="text-3xl font-bold text-cg-text">{data?.balance ?? 0} 积分</div>
          </Card>
          <Card title="积分调整">
            <div className="flex flex-col gap-3">
              <FormField label="调整数量（正数奖励、负数扣除）" htmlFor="adjust-amount">
                <Input id="adjust-amount" type="number" {...amount.inputProps} />
              </FormField>
              <FormField label="原因" htmlFor="adjust-reason">
                <Input id="adjust-reason" {...reason.inputProps} />
              </FormField>
              <Button onClick={handleAdjust} loading={adjusting}>
                确认调整
              </Button>
            </div>
          </Card>
          <Card title="流水">
            <div className="grid grid-cols-1 gap-2">
              {(data?.transactions ?? []).map((tx) => (
                <div
                  key={tx.id}
                  className="flex items-center justify-between rounded-cg-md bg-cg-surface-raised p-3"
                >
                  <div>
                    <div className="text-sm text-cg-text">{tx.reason ?? tx.type}</div>
                    <div className="text-xs text-cg-text-muted">{tx.createdAt}</div>
                  </div>
                  <div
                    className={`font-medium ${tx.amount >= 0 ? 'text-cg-success' : 'text-cg-danger'}`}
                  >
                    {tx.amount > 0 ? '+' : ''}
                    {tx.amount}
                  </div>
                </div>
              ))}
              {(data?.transactions ?? []).length === 0 && (
                <p className="text-cg-text-muted">暂无流水</p>
              )}
            </div>
          </Card>
        </>
      )}
    </PageShell>
  );
}

export function ParentPrizesPage() {
  const { items, loading, error, refetch } = usePaginatedData<Prize>('/prizes');
  const [showModal, setShowModal] = useState(false);
  const [editing, setEditing] = useState<Prize | null>(null);
  const name = useFormField();
  const description = useFormField();
  const pointsCost = useFormField('0');
  const availableStock = useFormField('0');
  const online = useOnline();
  const [saving, setSaving] = useState(false);

  const openNew = () => {
    setEditing(null);
    name.reset();
    description.reset();
    pointsCost.setValue('0');
    availableStock.setValue('0');
    setShowModal(true);
  };

  const openEdit = (p: Prize) => {
    setEditing(p);
    name.setValue(p.name);
    description.setValue(p.description);
    pointsCost.setValue(String(p.pointsCost));
    availableStock.setValue(String(p.availableStock));
    setShowModal(true);
  };

  const handleSave = async () => {
    setSaving(true);
    const payload = {
      name: name.value,
      description: description.value,
      pointsCost: Number(pointsCost.value),
      availableStock: Number(availableStock.value),
    };
    if (editing) {
      await getClient().put(`/prizes/${editing.id}`, payload);
    } else {
      await getClient().post('/prizes', payload);
    }
    setSaving(false);
    setShowModal(false);
    await refetch();
  };

  if (!online)
    return (
      <PageShell title="奖品">
        <Result status="warning" title="当前处于离线状态" subTitle="请检查网络连接，恢复后重试" extra={<Button onClick={refetch}>重试</Button>} />
      </PageShell>
    );
  if (loading)
    return (
      <PageShell title="奖品">
        <Spin className="flex justify-center py-12" />
      </PageShell>
    );
  if (error)
    return (
      <PageShell title="奖品">
        <Result status="error" title="加载失败" subTitle={error.message} extra={<Button onClick={refetch}>重试</Button>} />
      </PageShell>
    );

  return (
    <PageShell
      title="奖品"
      actions={
        <Button onClick={openNew}>
          新增奖品
        </Button>
      }
    >
      <div className="grid grid-cols-1 gap-3">
        {items.map((p) => (
          <div key={p.id} className="cg-card p-4">
            <div className="flex items-start justify-between">
              <div>
                <div className="font-medium text-cg-text">{p.name}</div>
                <p className="text-sm text-cg-text-muted">{p.description}</p>
                <div className="mt-1 text-sm">
                  {p.pointsCost} 积分 · 库存 {p.availableStock}
                </div>
              </div>
              <div className="flex gap-2">
                <Button type="text" size="small" onClick={() => openEdit(p)}>
                  编辑
                </Button>
              </div>
            </div>
          </div>
        ))}
      </div>

      <Modal
        open={showModal}
        onCancel={() => setShowModal(false)}
        title={editing ? '编辑奖品' : '新增奖品'}
      >
        <form className="flex flex-col gap-4">
          <FormField label="名称" htmlFor="prize-name">
            <Input id="prize-name" {...name.inputProps} />
          </FormField>
          <FormField label="描述" htmlFor="prize-desc">
            <TextArea id="prize-desc" {...description.inputProps} />
          </FormField>
          <FormField label="积分价格" htmlFor="prize-cost">
            <Input id="prize-cost" type="number" {...pointsCost.inputProps} />
          </FormField>
          <FormField label="库存" htmlFor="prize-stock">
            <Input id="prize-stock" type="number" {...availableStock.inputProps} />
          </FormField>
          <Button onClick={handleSave} loading={saving} htmlType="button" className="w-full">
            保存
          </Button>
        </form>
      </Modal>
    </PageShell>
  );
}

export function ParentBlindBoxesPage() {
  const { items, loading, error, refetch } = usePaginatedData<BlindBox>('/blind-boxes');
  const [selected, setSelected] = useState<BlindBox | null>(null);
  const { data: candidates } = useApi<{ candidates: BlindBoxCandidate[] }>(
    selected ? `/blind-boxes/${selected.id}/candidates` : '',
  );
  const online = useOnline();

  if (!online)
    return (
      <PageShell title="盲盒">
        <Result status="warning" title="当前处于离线状态" subTitle="请检查网络连接，恢复后重试" extra={<Button onClick={refetch}>重试</Button>} />
      </PageShell>
    );
  if (loading)
    return (
      <PageShell title="盲盒">
        <Spin className="flex justify-center py-12" />
      </PageShell>
    );
  if (error)
    return (
      <PageShell title="盲盒">
        <Result status="error" title="加载失败" subTitle={error.message} extra={<Button onClick={refetch}>重试</Button>} />
      </PageShell>
    );

  return (
    <PageShell title="盲盒">
      <div className="grid grid-cols-1 gap-3">
        {items.map((box) => (
          <div
            key={box.id}
            className={`cg-card p-4 cursor-pointer ${selected?.id === box.id ? 'ring-2 ring-cg-focus' : ''}`}
            onClick={() => setSelected(box)}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') setSelected(box);
            }}
          >
            <div className="font-medium text-cg-text">{box.name}</div>
            <div className="text-sm text-cg-text-muted">
              {box.cost} 积分 · 版本 {box.availabilityVersion.slice(0, 8)}...
            </div>
          </div>
        ))}
      </div>

      {selected && (
        <Card title="概率预览">
          <div className="grid grid-cols-1 gap-2">
            {(candidates?.candidates ?? []).map((c) => (
              <div
                key={c.prizeId}
                className="flex items-center justify-between rounded-cg-md bg-cg-surface-raised p-3"
              >
                <span className="text-cg-text">{c.prizeName}</span>
                <span className="font-medium text-cg-text">
                  {(c.probability * 100).toFixed(1)}%
                </span>
              </div>
            ))}
            {(candidates?.candidates ?? []).length === 0 && (
              <p className="text-cg-text-muted">暂无候选</p>
            )}
          </div>
        </Card>
      )}
    </PageShell>
  );
}

export function ParentExchangesPage() {
  const { items, loading, error, refetch } = usePaginatedData<Exchange>('/exchanges');
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
      <PageShell title="兑换履约">
        <Result status="warning" title="当前处于离线状态" subTitle="请检查网络连接，恢复后重试" extra={<Button onClick={refetch}>重试</Button>} />
      </PageShell>
    );
  if (loading)
    return (
      <PageShell title="兑换履约">
        <Spin className="flex justify-center py-12" />
      </PageShell>
    );
  if (error)
    return (
      <PageShell title="兑换履约">
        <Result status="error" title="加载失败" subTitle={error.message} extra={<Button onClick={refetch}>重试</Button>} />
      </PageShell>
    );

  return (
    <PageShell title="兑换履约">
      <div className="grid grid-cols-1 gap-3">
        {items.length === 0 ? (
          <Empty description="暂无数据" />
        ) : (
          items.map((ex) => (
            <div key={ex.id} className="cg-card p-4">
              <div className="flex items-start justify-between">
                <div>
                  <div className="font-medium text-cg-text">{ex.targetName}</div>
                  <div className="text-sm text-cg-text-muted">
                    {ex.childNickname} · {ex.pointsCost} 积分 · {ex.createdAt}
                  </div>
                  <div className="mt-1">
                    <StatusBadge status={ex.status.toLowerCase()} />
                  </div>
                </div>
                <div className="flex flex-col gap-2">
                  {ex.status === 'PENDING_FULFILLMENT' && (
                    <>
                      <Button size="small" onClick={() => setConfirmId(ex.id)} loading={acting}>
                        兑现
                      </Button>
                      <Button
                       
                        size="small"
                        onClick={() => cancel(ex.id)}
                        loading={acting}
                      >
                        取消
                      </Button>
                    </>
                  )}
                </div>
              </div>
            </div>
          ))
        )}
      </div>

      <ConfirmModal
        isOpen={confirmId !== null}
        onClose={() => setConfirmId(null)}
        title="确认兑现"
        message="兑换一旦兑现，积分将从孩子账户扣除。请确认已交付奖品。"
        confirmText="确认兑现"
        onConfirm={() => confirmId !== null && fulfill(confirmId)}
        isConfirming={acting}
      />
    </PageShell>
  );
}

export default ParentHomePage;
