import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent, within } from '@testing-library/react';
import {
  ParentFamilyPage,
  ParentChildrenPage,
  ParentTemplatesPage,
  ParentPrizesPage,
} from '../parent/pages';

// Mock API client（参照 admin-config-page.test.tsx 模式）
const { getMock, postMock, putMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
  putMock: vi.fn(),
}));

vi.mock('@shared/api/client', () => ({
  getClient: () => ({ get: getMock, post: postMock, put: putMock, delete: vi.fn() }),
}));

vi.mock('@shared/auth', () => ({
  useAuth: () => ({ account: { id: 1, role: 'PARENT' }, isAuthenticated: true }),
}));

const EMPTY_PAGE = { content: [], page: 1, pageSize: 20, totalElements: 0, totalPages: 0 };

beforeEach(() => {
  getMock.mockReset();
  postMock.mockReset();
  putMock.mockReset();
  getMock.mockImplementation((url: string) => {
    if (url.startsWith('/family') && !url.includes('children') && !url.includes('invitations')) {
      return Promise.resolve({ data: { id: 1, name: '测试家庭', members: [], children: [] } });
    }
    return Promise.resolve({ data: EMPTY_PAGE });
  });
  postMock.mockResolvedValue({ data: { id: 1 } });
  putMock.mockResolvedValue({ data: { id: 1 } });
});

/** 在 antd Modal footer 中查找"保存"按钮（修复前 footer 只有"确定"，查询失败即 RED） */
function clickFooterSave() {
  const footer = document.querySelector('.ant-modal-footer') as HTMLElement | null;
  expect(footer, 'Modal footer 应存在').not.toBeNull();
  const saveBtn = within(footer!).getByRole('button', { name: /保\s*存/ });
  fireEvent.click(saveBtn);
}

function modalEl(): HTMLElement | null {
  return document.querySelector('.ant-modal');
}

describe('parent 保存对话框：底部确认按钮即保存按钮', () => {
  it('ParentChildrenPage 新增档案：点击底部"保存"触发提交，成功后关闭对话框并提示保存成功', async () => {
    render(<ParentChildrenPage />);
    fireEvent.click(await screen.findByRole('button', { name: '新增档案' }));
    await screen.findByRole('dialog');

    clickFooterSave();

    await waitFor(() => expect(postMock).toHaveBeenCalledTimes(1));
    expect(postMock.mock.calls[0][0]).toBe('/family/children');
    // 成功：对话框关闭 + 保存成功提示
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    await screen.findByText('保存成功');
  });

  it('ParentChildrenPage 新增档案：保存失败时对话框保持打开并在框内显示错误', async () => {
    postMock.mockResolvedValue({ error: { error_code: 'X', message: '昵称已存在' } });
    render(<ParentChildrenPage />);
    fireEvent.click(await screen.findByRole('button', { name: '新增档案' }));
    await screen.findByRole('dialog');

    clickFooterSave();

    await waitFor(() => expect(postMock).toHaveBeenCalledTimes(1));
    // 对话框不关闭，错误显示在对话框内
    await screen.findByText('昵称已存在');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(modalEl()?.textContent).toContain('昵称已存在');
  });

  it('ParentPrizesPage 新增奖品：点击底部"保存"触发提交，失败时框内显错且不关闭', async () => {
    postMock.mockResolvedValue({ error: { error_code: 'X', message: '积分价格无效' } });
    render(<ParentPrizesPage />);
    fireEvent.click(await screen.findByRole('button', { name: '新增奖品' }));
    await screen.findByRole('dialog');

    clickFooterSave();

    await waitFor(() => expect(postMock).toHaveBeenCalledTimes(1));
    expect(postMock.mock.calls[0][0]).toBe('/prizes');
    await screen.findByText('积分价格无效');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(modalEl()?.textContent).toContain('积分价格无效');
  });

  it('ParentTemplatesPage 新建模板：点击底部"保存"触发提交，失败时错误显示在对话框内', async () => {
    postMock.mockResolvedValue({ error: { error_code: 'X', message: '模板名称重复' } });
    render(<ParentTemplatesPage />);
    fireEvent.click(await screen.findByRole('button', { name: '新建模板' }));
    await screen.findByRole('dialog');

    clickFooterSave();

    await waitFor(() => expect(postMock).toHaveBeenCalledTimes(1));
    expect(postMock.mock.calls[0][0]).toBe('/task-templates');
    await screen.findByText('模板名称重复');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(modalEl()?.textContent).toContain('模板名称重复');
  });

  it('ParentFamilyPage 添加孩子：点击底部"保存"触发提交，失败时框内显错且不关闭', async () => {
    postMock.mockResolvedValue({ error: { error_code: 'X', message: 'PIN 格式不正确' } });
    render(<ParentFamilyPage />);
    fireEvent.click(await screen.findByRole('button', { name: '添加孩子' }));
    await screen.findByRole('dialog');

    clickFooterSave();

    await waitFor(() => expect(postMock).toHaveBeenCalledTimes(1));
    expect(postMock.mock.calls[0][0]).toBe('/family/children');
    await screen.findByText('PIN 格式不正确');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(modalEl()?.textContent).toContain('PIN 格式不正确');
  });
});
