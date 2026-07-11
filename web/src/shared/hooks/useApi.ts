import { useCallback, useEffect, useRef, useState } from 'react';
import { getClient } from '@shared/api/client';
import type { ApiError } from '@shared/api/types';
import { useOnline } from '@shared/theme';

interface UseApiOptions {
  skip?: boolean;
}

interface UseApiResult<T> {
  data: T | undefined;
  loading: boolean;
  error: ApiError | undefined;
  refetch: () => Promise<void>;
}

export function useApi<T>(path: string, options: UseApiOptions = {}): UseApiResult<T> {
  const { skip = false } = options;
  const [data, setData] = useState<T | undefined>(undefined);
  const [loading, setLoading] = useState(!skip);
  const [error, setError] = useState<ApiError | undefined>(undefined);
  const online = useOnline();
  const pathRef = useRef(path);

  const fetchData = useCallback(async () => {
    if (!online) {
      setError({ error_code: 'NETWORK_ERROR', message: '当前处于离线状态' });
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(undefined);
    try {
      const response = await getClient().get<T>(pathRef.current);
      if (response.error) {
        setError(response.error);
      } else {
        setData(response.data);
      }
    } catch (err) {
      setError({ error_code: 'NETWORK_ERROR', message: '网络连接失败' });
    } finally {
      setLoading(false);
    }
  }, [online]);

  useEffect(() => {
    if (skip) return;
    fetchData();
  }, [skip, fetchData]);

  return { data, loading, error, refetch: fetchData };
}

export function useMutation<T, P = unknown>(
  fn: (payload: P) => Promise<{ data?: T; error?: ApiError }>,
) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<ApiError | undefined>(undefined);
  const online = useOnline();

  const mutate = useCallback(
    async (payload: P) => {
      if (!online) {
        return {
          error: { error_code: 'NETWORK_ERROR', message: '当前处于离线状态' } as ApiError,
        };
      }
      setLoading(true);
      setError(undefined);
      try {
        const result = await fn(payload);
        if (result.error) {
          setError(result.error);
        }
        return result;
      } catch (err) {
        const apiError = { error_code: 'NETWORK_ERROR', message: '网络连接失败' };
        setError(apiError);
        return { error: apiError };
      } finally {
        setLoading(false);
      }
    },
    [fn, online],
  );

  return { mutate, loading, error };
}

export function useFormField(initialValue = '') {
  const [value, setValue] = useState(initialValue);
  const [touched, setTouched] = useState(false);
  const onChange = useCallback((e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    setValue(e.target.value);
  }, []);
  const onBlur = useCallback(() => setTouched(true), []);
  const reset = useCallback(() => {
    setValue(initialValue);
    setTouched(false);
  }, [initialValue]);
  return { value, setValue, touched, onChange, onBlur, reset };
}

export function useIdempotencyKey(): string {
  const [key] = useState(() => {
    if (typeof crypto !== 'undefined' && crypto.randomUUID) {
      return crypto.randomUUID();
    }
    return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  });
  return key;
}
