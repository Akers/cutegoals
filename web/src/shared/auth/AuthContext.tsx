import {
  createContext,
  ReactNode,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import { useNavigate } from 'react-router-dom';
import { getClient, configureClient } from '@shared/api/client';
import type { ApiError } from '@shared/api/types';

export interface Account {
  accountId: string | number;
  phone?: string;
  roles: string[];
  familyId?: string | number;
  childId?: string | number;
  nickname?: string;
  expiresIn?: number;
}

interface AuthContextValue {
  account: Account | null;
  isAuthenticated: boolean;
  login: (data: Account) => void;
  logout: () => Promise<void>;
  loading: boolean;
  error: string | null;
  setError: (error: string | null) => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used within AuthProvider');
  return value;
}

export function AuthProvider({
  children,
  initialAccount,
}: {
  children: ReactNode;
  initialAccount?: Account;
}) {
  const [account, setAccount] = useState<Account | null>(initialAccount ?? null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();

  const handleUnauthorized = useCallback(() => {
    setAccount(null);
    const path = window.location.pathname;
    if (path.startsWith('/admin')) {
      navigate('/admin/login');
    } else if (path.startsWith('/parent')) {
      navigate('/parent/login');
    } else {
      navigate('/child/login');
    }
  }, [navigate]);

  useEffect(() => {
    configureClient({
      baseUrl: '/api',
      onUnauthorized: handleUnauthorized,
    });
  }, [handleUnauthorized]);

  // Restore session from cookie on mount
  useEffect(() => {
    let cancelled = false;
    getClient()
      .get<{ accountId: number; phone: string; roles: string[]; familyId: number | null }>('/auth/me')
      .then((response) => {
        if (cancelled) return;
        if (response.data) {
          setAccount({
            accountId: response.data.accountId,
            phone: response.data.phone,
            roles: response.data.roles,
            familyId: response.data.familyId ?? undefined,
          });
        }
        // 401 → do nothing, account stays null
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback((data: Account) => {
    setAccount(data);
    setError(null);
  }, []);

  const logout = useCallback(async () => {
    setLoading(true);
    try {
      await getClient().post('/auth/logout');
    } catch {
      // ignore
    } finally {
      setAccount(null);
      setLoading(false);
      handleUnauthorized();
    }
  }, [handleUnauthorized]);

  const value = useMemo(
    () => ({
      account,
      isAuthenticated: !!account,
      login,
      logout,
      loading,
      error,
      setError,
    }),
    [account, login, logout, loading, error],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function getErrorMessage(error: ApiError | string | undefined): string {
  if (!error) return '发生未知错误';
  if (typeof error === 'string') return error;
  return error.message ?? '发生未知错误';
}

export function maskPhone(phone?: string): string {
  if (!phone || phone.length < 7) return phone ?? '';
  return phone.slice(0, 3) + '****' + phone.slice(7);
}
