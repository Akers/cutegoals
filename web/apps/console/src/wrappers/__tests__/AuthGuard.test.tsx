import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
// @ts-ignore - umi mock exposes test helper from src/__tests__/setup.ts
import { __setMockLocation } from 'umi';
import AuthGuard from '@/wrappers/AuthGuard';
import { AuthProvider } from '@shared/auth';
import { RoleProvider } from '@shared/RoleContext';
import type { Role } from '@shared/role';

function mockResponse(data: unknown, ok = true, status = 200) {
  return {
    ok,
    status,
    headers: new Headers({ 'content-type': 'application/json' }),
    json: () => Promise.resolve(data),
  } as unknown as Response;
}

function mockMe(roles: string[]) {
  vi.mocked(fetch).mockResolvedValue(
    mockResponse({
      data: { accountId: 1, phone: '13800138000', roles, familyId: 1 },
    }),
  );
}

/**
 * contextRole 模拟生产环境中 deriveRole 折叠后的 RoleContext 主角色：
 * 双角色账号（INSTANCE_ADMIN+PARENT）在主角色优先级下被折叠为 'admin'。
 */
function renderGuard(contextRole: Role) {
  return render(
    <RoleProvider role={contextRole}>
      <AuthProvider>
        <AuthGuard />
      </AuthProvider>
    </RoleProvider>,
  );
}

beforeEach(() => {
  vi.spyOn(globalThis, 'fetch').mockReset();
  __setMockLocation('/parent');
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('wrappers/AuthGuard 角色成员检查', () => {
  it('持有 INSTANCE_ADMIN+PARENT 双角色的账号访问 /parent 应放行', async () => {
    mockMe(['INSTANCE_ADMIN', 'PARENT']);
    renderGuard('admin');

    await waitFor(() => {
      expect(screen.getByTestId('outlet')).toBeInTheDocument();
    });
    expect(screen.queryByText('无权访问此页面')).not.toBeInTheDocument();
  });

  it('仅持有 CHILD 角色的账号访问 /parent 应 403', async () => {
    mockMe(['CHILD']);
    renderGuard('child');

    await screen.findByText('无权访问此页面');
    expect(screen.queryByTestId('outlet')).not.toBeInTheDocument();
  });

  it('未认证访问 /parent 应重定向到 /parent/login', async () => {
    vi.mocked(fetch).mockResolvedValue(
      mockResponse({ code: 'UNAUTHENTICATED', message: '未认证' }, false, 401),
    );
    renderGuard('child');

    await waitFor(() => {
      expect(screen.getByTestId('navigate')).toHaveAttribute(
        'data-to',
        '/parent/login',
      );
    });
  });
});
