import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import userEvent from '@testing-library/user-event';
import { AdminInitPage } from '@admin/pages/AdminInitPage';
import { ParentLoginPage } from '@parent/pages/ParentLoginPage';
import { RoleProvider } from '@shared/RoleContext';
import { AuthProvider } from '@shared/auth';

function mockResponse(data: unknown, ok = true, status = 200) {
  return {
    ok,
    status,
    headers: new Headers({ 'content-type': 'application/json' }),
    json: () => Promise.resolve(data),
  } as unknown as Response;
}

function renderAuthPage(role: 'admin' | 'parent', element: React.ReactElement) {
  return render(
    <RoleProvider role={role}>
      <MemoryRouter>
        <AuthProvider>
          {element}
        </AuthProvider>
      </MemoryRouter>
    </RoleProvider>,
  );
}

beforeEach(() => {
  vi.spyOn(globalThis, 'fetch').mockReset();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('AdminInitPage', () => {
  it('submits initialization form', async () => {
    vi.mocked(fetch).mockResolvedValue(
      mockResponse({ data: { accountId: 1 } }),
    );

    renderAuthPage('admin', <AdminInitPage />);

    await userEvent.type(screen.getByPlaceholderText('从部署命令获取'), 'token-123');
    await userEvent.type(screen.getByPlaceholderText('11 位手机号'), '13800138000');
    await userEvent.type(screen.getByPlaceholderText('至少 8 位'), 'password123');
    await userEvent.type(screen.getByPlaceholderText('再次输入密码'), 'password123');
    await userEvent.click(screen.getByRole('button', { name: /完成初始化/ }));

    await waitFor(() => {
      expect(vi.mocked(fetch)).toHaveBeenCalledWith(
        expect.stringContaining('/auth/initialize'),
        expect.anything(),
      );
    });
  });

  it('shows validation error for mismatched passwords', async () => {
    renderAuthPage('admin', <AdminInitPage />);

    await userEvent.type(screen.getByPlaceholderText('从部署命令获取'), 'token-123');
    await userEvent.type(screen.getByPlaceholderText('11 位手机号'), '13800138000');
    await userEvent.type(screen.getByPlaceholderText('至少 8 位'), 'password123');
    await userEvent.type(screen.getByPlaceholderText('再次输入密码'), 'different');
    await userEvent.click(screen.getByRole('button', { name: /完成初始化/ }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/密码/);
    });
  });
});

describe('ParentLoginPage', () => {
  it('submits login with phone and password', async () => {
    vi.mocked(fetch).mockResolvedValue(
      mockResponse({
        data: { accountId: 2, phone: '13800138000', roles: ['PARENT'], familyId: 1 },
      }),
    );

    renderAuthPage('parent', <ParentLoginPage />);

    await userEvent.type(screen.getByPlaceholderText('11 位手机号'), '13800138000');
    await userEvent.type(screen.getByPlaceholderText('请输入密码'), 'password123');
    await userEvent.click(screen.getByRole('button', { name: /登录/ }));

    await waitFor(() => {
      expect(vi.mocked(fetch)).toHaveBeenCalledWith(
        expect.stringContaining('/auth/login'),
        expect.anything(),
      );
    });
  });

  it('shows error for empty input', async () => {
    renderAuthPage('parent', <ParentLoginPage />);

    await userEvent.click(screen.getByRole('button', { name: /登录/ }));
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/手机号/);
    });
  });
});
