import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getClient } from '@shared/api';
import {
  Button,
  CardSection,
  ConfirmModal,
  EmptyState,
  ErrorState,
  FormField,
  Input,
  Label,
  Layout,
  LoadingState,
  Modal,
  OfflineState,
  PageHeader,
  Select,
  StatusBadge,
  TextArea,
} from '@shared/components';
import { useAuth } from '@shared/auth';
import { useApi, useFormField } from '@shared/hooks/useApi';
import { useOnline } from '@shared/theme';

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

function usePaginatedData<T>(path: string) {
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const { data, loading, error, refetch } = useApi<PageResult<T>>(`${path}?page=${page}&pageSize=${pageSize}`);
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
  const navigate = useNavigate();

  if (!online) return <PageShell title="家庭"><OfflineState onRetry={refetch} /></PageShell>;
  if (loading) return <PageShell title="家庭"><LoadingState /></PageShell>;
  if (error) return <PageShell title="家庭"><ErrorState onRetry={refetch} message={error.message} /></PageShell>;
  if (!data) return <PageShell title="家庭"><EmptyState /></PageShell>;

  return (
    <PageShell
      title="家庭"
      actions={
        <div className="flex items-center gap-2">
          <Button variant="secondary" onClick={() => navigate('/parent/family')}>
            管理家庭
          </Button>
          <Button variant="secondary" onClick={() => navigate('/parent/templates')}>
            任务模板
          </Button>
        </div>
      }
    >
      <CardSection title="家庭成员">
        <div className="grid grid-cols-1 gap-3">
          {(data.members ?? []).map((member) => (
            <div key={member.id} className="flex items-center justify-between rounded-cg-md bg-cg-surface-raised p-3">
              <div className="font-medium text-cg-text">{member.nickname ?? maskPhone(member.phone ?? '')}</div>
              <StatusBadge status={member.role === 'PARENT' ? 'approved' : 'pending'} />
            </div>
          ))}
        </div>
      </CardSection>
    </PageShell>
  );
}

export function ParentFamilyPage() {
  const { data, loading, error, refetch } = useApi<Family>('/family');
  const { items: invitations, refetch: refetchInvitations } = usePaginatedData<Invitation>('/family/invitations');
  const [showInvite, setShowInvite] = useState(false);
  const [showChildModal, setShowChildModal] = useState(false);
  const [confirm, setConfirm] = useState<{ type: 'remove' | 'leave' | 'removeChild'; member?: FamilyMember; child?: ChildProfile } | null>(null);
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
    } catch (err: any) {
      setActionError(err.message ?? '添加孩子失败');
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
    } catch (err: any) {
      setActionError(err.message ?? '移除失败');
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
    } catch (err: any) {
      setActionError(err.message ?? '移除孩子失败');
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
    } catch (err: any) {
      setActionError(err.message ?? '退出失败');
    } finally {
      setActionLoading(false);
      setConfirm(null);
    }
  };

  if (!online) return <PageShell title="家庭"><OfflineState onRetry={refetch} /></PageShell>;
  if (loading) return <PageShell title="家庭"><LoadingState /></PageShell>;
  if (error) return <PageShell title="家庭"><ErrorState onRetry={refetch} message={error.message} /></PageShell>;
  if (!data) return <PageShell title="家庭"><EmptyState /></PageShell>;

  const confirmTitle =
    confirm?.type === 'leave' ? '退出家庭' :
    confirm?.type === 'removeChild' ? '移除孩子' : '移除成员';
  const confirmMessage =
    confirm?.type === 'leave' ? '退出后你将无法管理该家庭，是否继续？' :
    confirm?.type === 'removeChild' ? '移除后该孩子将无法继续使用家庭功能，是否继续？' :
    '移除后该家长将无法管理此家庭，是否继续？';
  const confirmButtonText =
    confirm?.type === 'leave' ? '退出' :
    confirm?.type === 'removeChild' ? '移除' : '移除';

  return (
    <PageShell
      title="家庭"
      actions={
        <div className="flex items-center gap-2">
          <Button variant="secondary" onClick={() => setShowInvite(true)}>
            邀请家长
          </Button>
          <Button variant="secondary" onClick={openNewChild}>
            添加孩子
          </Button>
        </div>
      }
    >
      <CardSection title="家庭成员">
        <div className="grid grid-cols-1 gap-3">
          {(data.members ?? []).map((member) => {
            const isSelf = account != null && member.accountId === Number(account.accountId);
            const canRemove = member.role === 'PARENT' && !isSelf;
            return (
              <div key={member.id} className="flex items-center justify-between rounded-cg-md bg-cg-surface-raised p-3">
                <div>
                  <div className="font-medium text-cg-text">{member.nickname ?? maskPhone(member.phone ?? '')}</div>
                  {member.phone && <div className="text-xs text-cg-text-muted">{maskPhone(member.phone)}</div>}
                </div>
                <div className="flex items-center gap-2">
                  <StatusBadge status={member.role === 'PARENT' ? 'approved' : 'pending'} />
                  {isSelf && (
                    <Button variant="danger" size="sm" onClick={() => setConfirm({ type: 'leave' })} isLoading={actionLoading}>
                      退出家庭
                    </Button>
                  )}
                  {canRemove && (
                    <Button variant="danger" size="sm" onClick={() => setConfirm({ type: 'remove', member })} isLoading={actionLoading}>
                      移除
                    </Button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </CardSection>

      <CardSection title="孩子">
        {(data.children ?? []).length === 0 ? (
          <p className="text-cg-text-muted">暂无孩子，点击上方「添加孩子」创建档案。</p>
        ) : (
          <div className="grid grid-cols-1 gap-3">
            {(data.children ?? []).map((child) => (
              <div key={child.id} className="flex items-center justify-between rounded-cg-md bg-cg-surface-raised p-3">
                <div>
                  <div className="font-medium text-cg-text">{child.nickname}</div>
                  {child.birthday && <div className="text-xs text-cg-text-muted">生日 {child.birthday}</div>}
                </div>
                <Button
                  variant="danger"
                  size="sm"
                  onClick={() => setConfirm({ type: 'removeChild', child })}
                  isLoading={actionLoading}
                >
                  移除
                </Button>
              </div>
            ))}
          </div>
        )}
      </CardSection>

      <CardSection title="待处理邀请">
        {invitations.length === 0 ? (
          <p className="text-cg-text-muted">暂无邀请</p>
        ) : (
          <div className="grid grid-cols-1 gap-3">
            {invitations.map((inv) => (
              <div key={inv.id} className="flex items-center justify-between rounded-cg-md bg-cg-surface-raised p-3">
                <div>
                  <div className="font-medium text-cg-text">{maskPhone(inv.inviteePhone)}</div>
                  <div className="text-xs text-cg-text-muted">{inv.createdAt}</div>
                </div>
                <StatusBadge status={inv.status.toLowerCase()} />
              </div>
            ))}
          </div>
        )}
      </CardSection>

      {actionError && (
        <div className="text-sm text-red-600">{actionError}</div>
      )}

      <Modal isOpen={showInvite} onClose={() => setShowInvite(false)} title="邀请家长">
        <form className="flex flex-col gap-4">
          <FormField label="被邀请人手机号" htmlFor="invite-phone">
            <Input id="invite-phone" type="tel" placeholder="11 位手机号" {...phone.inputProps} />
          </FormField>
          <Button onClick={handleInvite} isLoading={sending} type="button" className="w-full">
            发送邀请
          </Button>
        </form>
      </Modal>

      <Modal isOpen={showChildModal} onClose={() => setShowChildModal(false)} title="添加孩子">
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
          <Button onClick={handleSaveChild} isLoading={childSaving} type="button" className="w-full">
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

  if (!online) return <PageShell title="孩子档案"><OfflineState onRetry={refetch} /></PageShell>;
  if (loading) return <PageShell title="孩子档案"><LoadingState /></PageShell>;
  if (error) return <PageShell title="孩子档案"><ErrorState onRetry={refetch} message={error.message} /></PageShell>;

  return (
    <PageShell
      title="孩子档案"
      actions={
        <Button variant="secondary" onClick={openNew}>
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
                <Button variant="ghost" size="sm" onClick={() => openEdit(child)}>编辑</Button>
                <Button variant="danger" size="sm" onClick={() => handleDelete(child.id)}>删除</Button>
              </div>
            </div>
            {child.birthday && <p className="text-sm text-cg-text-muted">生日 {child.birthday}</p>}
          </div>
        ))}
      </div>

      <Modal
        isOpen={showModal}
        onClose={() => setShowModal(false)}
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
          <Button onClick={handleSave} isLoading={saving} type="button" className="w-full">
            保存
          </Button>
        </form>
      </Modal>
    </PageShell>
  );
}

export function ParentTemplatesPage() {
  const { items, loading, error, refetch } = usePaginatedData<TaskTemplate>('/task-templates');
  const [showModal, setShowModal] = useState(false);
  const [editing, setEditing] = useState<TaskTemplate | null>(null);
  const title = useFormField();
  const description = useFormField();
  const category = useFormField();
  const basePoints = useFormField('10');
  const online = useOnline();
  const [saving, setSaving] = useState(false);

  const openNew = () => {
    setEditing(null);
    title.reset();
    description.reset();
    category.reset();
    basePoints.setValue('10');
    setShowModal(true);
  };

  const openEdit = (t: TaskTemplate) => {
    setEditing(t);
    title.setValue(t.name);
    description.setValue(t.description ?? '');
    category.setValue(t.category ?? '');
    basePoints.setValue(String(t.difficulties?.[0]?.rewardPoints ?? 10));
    setShowModal(true);
  };

  const handleSave = async () => {
    setSaving(true);
    const payload: Record<string, unknown> = {
      name: title.value,
      description: description.value,
      category: category.value,
      difficulties: [
        { name: '标准', displayOrder: 1, rewardPoints: Number(basePoints.value) || 1, enabled: true },
      ],
    };
    if (editing) {
      payload.version = editing.version;
      const res = await getClient().put(`/task-templates/${editing.id}`, payload);
      if (res.error) { setSaving(false); alert(res.error.message ?? '保存失败'); return; }
    } else {
      const res = await getClient().post('/task-templates', payload);
      if (res.error) { setSaving(false); alert(res.error.message ?? '保存失败'); return; }
    }
    setSaving(false);
    setShowModal(false);
    await refetch();
  };

  const toggleEnabled = async (t: TaskTemplate) => {
    await getClient().put(`/task-templates/${t.id}/enabled`, { enabled: !t.enabled });
    await refetch();
  };

  if (!online) return <PageShell title="任务模板"><OfflineState onRetry={refetch} /></PageShell>;
  if (loading) return <PageShell title="任务模板"><LoadingState /></PageShell>;
  if (error) return <PageShell title="任务模板"><ErrorState onRetry={refetch} message={error.message} /></PageShell>;

  return (
    <PageShell
      title="任务模板"
      actions={
        <Button variant="secondary" onClick={openNew}>
          新建模板
        </Button>
      }
    >
      <div className="grid grid-cols-1 gap-3">
        {items.map((t) => (
          <div key={t.id} className="cg-card p-4">
            <div className="flex items-start justify-between">
              <div>
                <div className="font-medium text-cg-text">{t.name}</div>
                <p className="text-sm text-cg-text-muted">{t.description}</p>
                <div className="mt-1 flex gap-2 text-sm">
                  <span className="rounded-cg-sm bg-cg-surface-raised px-2 py-0.5">{t.category}</span>
                  <span className="rounded-cg-sm bg-cg-surface-raised px-2 py-0.5">{t.difficulties?.[0]?.rewardPoints ?? '-'} 积分</span>
                </div>
              </div>
              <div className="flex gap-2">
                <Button variant="ghost" size="sm" onClick={() => openEdit(t)}>编辑</Button>
                <Button variant="secondary" size="sm" onClick={() => toggleEnabled(t)}>
                  {t.enabled ? '停用' : '启用'}
                </Button>
              </div>
            </div>
          </div>
        ))}
      </div>

      <Modal
        isOpen={showModal}
        onClose={() => setShowModal(false)}
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
          <Button onClick={handleSave} isLoading={saving} type="button" className="w-full">
            保存
          </Button>
        </form>
      </Modal>
    </PageShell>
  );
}

export function ParentTasksPage() {
  const [date, setDate] = useState(() => new Date().toISOString().split('T')[0]);
  const { data, loading, error, refetch } = useApi<PageResult<TaskAssignment>>(`/task-assignments?page=1&pageSize=100&startDate=${date}&endDate=${date}`);
  const { data: templates } = useApi<PageResult<TaskTemplate>>('/task-templates');
  const { data: children } = useApi<PageResult<ChildProfile>>('/family/children');
  const [showAssign, setShowAssign] = useState(false);
  const templateId = useFormField();
  const childId = useFormField();
  const deadline = useFormField();
  const online = useOnline();
  const [assigning, setAssigning] = useState(false);

  const handleAssign = async () => {
    setAssigning(true);
    await getClient().post('/task-assignments/batch', {
      assignments: [
        {
          templateId: Number(templateId.value),
          childId: Number(childId.value),
          deadline: deadline.value,
        },
      ],
    });
    setAssigning(false);
    setShowAssign(false);
    await refetch();
  };

  if (!online) return <PageShell title="任务分配"><OfflineState onRetry={refetch} /></PageShell>;
  if (loading) return <PageShell title="任务分配"><LoadingState /></PageShell>;
  if (error) return <PageShell title="任务分配"><ErrorState onRetry={refetch} message={error.message} /></PageShell>;

  const assignments = data?.content ?? [];

  return (
    <PageShell
      title="任务分配"
      actions={
        <Button variant="secondary" onClick={() => setShowAssign(true)}>
          批量分配
        </Button>
      }
    >
      <CardSection title="日历">
        <Label htmlFor="task-date">选择日期</Label>
        <Input id="task-date" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
      </CardSection>

      <CardSection title="任务列表">
        <div className="grid grid-cols-1 gap-3">
          {assignments.map((a) => (
            <div key={a.id} className={`cg-card p-4 ${a.isOverdue ? 'border-l-4 border-l-cg-warning' : ''}`}>
              <div className="flex items-start justify-between">
                <div>
                  <div className="font-medium text-cg-text">{a.templateTitle}</div>
                  <div className="text-sm text-cg-text-muted">{a.childNickname} · 截止 {a.deadline}</div>
                  {a.isOverdue && <div className="mt-1 text-sm font-semibold text-cg-warning">已逾期</div>}
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
      </CardSection>

      <Modal isOpen={showAssign} onClose={() => setShowAssign(false)} title="批量分配任务">
        <form className="flex flex-col gap-4">
          <FormField label="模板" htmlFor="assign-template">
            <Select id="assign-template" {...templateId.inputProps}>
              <option value="">请选择模板</option>
              {(templates?.content ?? []).map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField label="孩子" htmlFor="assign-child">
            <Select id="assign-child" {...childId.inputProps}>
              <option value="">请选择孩子</option>
              {(children?.content ?? []).map((c) => (
                <option key={c.id} value={c.id}>
                  {c.nickname}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField label="截止时间" htmlFor="assign-deadline">
            <Input id="assign-deadline" type="datetime-local" {...deadline.inputProps} />
          </FormField>
          <Button onClick={handleAssign} isLoading={assigning} type="button" className="w-full">
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

  if (!online) return <PageShell title="任务审核"><OfflineState onRetry={refetch} /></PageShell>;
  if (loading) return <PageShell title="任务审核"><LoadingState /></PageShell>;
  if (error) return <PageShell title="任务审核"><ErrorState onRetry={refetch} message={error.message} /></PageShell>;

  const pending = data?.content ?? [];

  return (
    <PageShell title="任务审核">
      <CardSection title="待审核">
        <div className="grid grid-cols-1 gap-3">
          {pending.length === 0 ? (
            <p className="text-cg-text-muted">暂无待审核任务</p>
          ) : (
            pending.map((item) => (
              <div key={item.attemptId} className={`cg-card p-4 ${item.isOverdue ? 'border-l-4 border-l-cg-warning' : ''}`}>
                <div className="flex items-start justify-between">
                  <div>
                    <div className="font-medium text-cg-text">{item.templateTitle}</div>
                    <div className="text-sm text-cg-text-muted">{item.childNickname} · {item.submittedAt}</div>
                    {item.notes && <p className="mt-1 text-sm text-cg-text">{item.notes}</p>}
                    {item.isOverdue && <div className="mt-1 text-sm font-semibold text-cg-warning">已逾期</div>}
                  </div>
                  <div className="flex flex-col gap-2">
                    <Button size="sm" onClick={() => decide(item.attemptId, true)} isLoading={submitting}>通过</Button>
                    <Button
                      variant="danger"
                      size="sm"
                      onClick={() => setActive(item)}
                      isLoading={submitting}
                    >
                      驳回
                    </Button>
                  </div>
                </div>
              </div>
            ))
          )}
        </div>
      </CardSection>

      <CardSection title="审核历史">
        <div className="grid grid-cols-1 gap-3">
          {(history?.content ?? []).length === 0 ? (
            <p className="text-cg-text-muted">暂无历史</p>
          ) : (
            (history?.content ?? []).map((item) => (
              <div key={item.attemptId} className="cg-card p-4">
                <div className="font-medium text-cg-text">{item.templateTitle}</div>
                <div className="text-sm text-cg-text-muted">{item.childNickname} · {item.submittedAt}</div>
              </div>
            ))
          )}
        </div>
      </CardSection>

      <Modal
        isOpen={!!active}
        onClose={() => {
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
            <Button variant="secondary" onClick={() => setActive(null)}>取消</Button>
            <Button
              variant="danger"
              onClick={() => active && decide(active.attemptId, false)}
              isLoading={submitting}
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
    transactions: { id: number; amount: number; type: string; createdAt: string; reason?: string }[];
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

  if (!online) return <PageShell title="积分"><OfflineState onRetry={refetch} /></PageShell>;
  if (loading) return <PageShell title="积分"><LoadingState /></PageShell>;
  if (error) return <PageShell title="积分"><ErrorState onRetry={refetch} message={error.message} /></PageShell>;

  return (
    <PageShell title="积分">
      <CardSection title="选择孩子">
        <Select value={selectedChild} onChange={(e) => setSelectedChild(e.target.value)}>
          <option value="">请选择孩子</option>
          {(children?.content ?? []).map((c) => (
            <option key={c.id} value={c.id}>
              {c.nickname}
            </option>
          ))}
        </Select>
      </CardSection>

      {selectedChild && (
        <>
          <CardSection title="积分余额">
            <div className="text-3xl font-bold text-cg-text">{data?.balance ?? 0} 积分</div>
          </CardSection>
          <CardSection title="积分调整">
            <div className="flex flex-col gap-3">
              <FormField label="调整数量（正数奖励、负数扣除）" htmlFor="adjust-amount">
                <Input id="adjust-amount" type="number" {...amount.inputProps} />
              </FormField>
              <FormField label="原因" htmlFor="adjust-reason">
                <Input id="adjust-reason" {...reason.inputProps} />
              </FormField>
              <Button onClick={handleAdjust} isLoading={adjusting}>确认调整</Button>
            </div>
          </CardSection>
          <CardSection title="流水">
            <div className="grid grid-cols-1 gap-2">
              {(data?.transactions ?? []).map((tx) => (
                <div key={tx.id} className="flex items-center justify-between rounded-cg-md bg-cg-surface-raised p-3">
                  <div>
                    <div className="text-sm text-cg-text">{tx.reason ?? tx.type}</div>
                    <div className="text-xs text-cg-text-muted">{tx.createdAt}</div>
                  </div>
                  <div className={`font-medium ${tx.amount >= 0 ? 'text-cg-success' : 'text-cg-danger'}`}>
                    {tx.amount > 0 ? '+' : ''}
                    {tx.amount}
                  </div>
                </div>
              ))}
              {(data?.transactions ?? []).length === 0 && <p className="text-cg-text-muted">暂无流水</p>}
            </div>
          </CardSection>
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

  if (!online) return <PageShell title="奖品"><OfflineState onRetry={refetch} /></PageShell>;
  if (loading) return <PageShell title="奖品"><LoadingState /></PageShell>;
  if (error) return <PageShell title="奖品"><ErrorState onRetry={refetch} message={error.message} /></PageShell>;

  return (
    <PageShell
      title="奖品"
      actions={
        <Button variant="secondary" onClick={openNew}>
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
                <Button variant="ghost" size="sm" onClick={() => openEdit(p)}>编辑</Button>
              </div>
            </div>
          </div>
        ))}
      </div>

      <Modal isOpen={showModal} onClose={() => setShowModal(false)} title={editing ? '编辑奖品' : '新增奖品'}>
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
          <Button onClick={handleSave} isLoading={saving} type="button" className="w-full">
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
  const { data: candidates } = useApi<{ candidates: BlindBoxCandidate[] }>(selected ? `/blind-boxes/${selected.id}/candidates` : '');
  const online = useOnline();

  if (!online) return <PageShell title="盲盒"><OfflineState onRetry={refetch} /></PageShell>;
  if (loading) return <PageShell title="盲盒"><LoadingState /></PageShell>;
  if (error) return <PageShell title="盲盒"><ErrorState onRetry={refetch} message={error.message} /></PageShell>;

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
            <div className="text-sm text-cg-text-muted">{box.cost} 积分 · 版本 {box.availabilityVersion.slice(0, 8)}...</div>
          </div>
        ))}
      </div>

      {selected && (
        <CardSection title="概率预览">
          <div className="grid grid-cols-1 gap-2">
            {(candidates?.candidates ?? []).map((c) => (
              <div key={c.prizeId} className="flex items-center justify-between rounded-cg-md bg-cg-surface-raised p-3">
                <span className="text-cg-text">{c.prizeName}</span>
                <span className="font-medium text-cg-text">{(c.probability * 100).toFixed(1)}%</span>
              </div>
            ))}
            {(candidates?.candidates ?? []).length === 0 && <p className="text-cg-text-muted">暂无候选</p>}
          </div>
        </CardSection>
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

  if (!online) return <PageShell title="兑换履约"><OfflineState onRetry={refetch} /></PageShell>;
  if (loading) return <PageShell title="兑换履约"><LoadingState /></PageShell>;
  if (error) return <PageShell title="兑换履约"><ErrorState onRetry={refetch} message={error.message} /></PageShell>;

  return (
    <PageShell title="兑换履约">
      <div className="grid grid-cols-1 gap-3">
        {items.length === 0 ? (
          <EmptyState />
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
                      <Button size="sm" onClick={() => setConfirmId(ex.id)} isLoading={acting}>
                        兑现
                      </Button>
                      <Button variant="secondary" size="sm" onClick={() => cancel(ex.id)} isLoading={acting}>
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
