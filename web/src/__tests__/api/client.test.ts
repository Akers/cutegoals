import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { ApiClient, configureClient, getClient, getErrorMessage } from '../../shared/api';

// ---------------------------------------------------------------------------
// Helpers – use `mockResolvedValueOnce` for sequential control
// ---------------------------------------------------------------------------

/**
 * Helper to mock a server fetch response.
 * Server format per design doc §5.1: { code, message, data, request_id }.
 * The helper accepts `code` (mapped to server field) for readability.
 * Consumer code reads `error.error_code` and `error.id` (internal mapping).
 */
function mockFetchOnce(response: {
  ok?: boolean;
  status?: number;
  body?: unknown;
  code?: string;
  message?: string;
  request_id?: string;
}) {
  const { ok = true, status = 200, body, code, message, request_id } = response;
  const jsonBody: Record<string, unknown> = {};
  if (body && typeof body === 'object') Object.assign(jsonBody, body as Record<string, unknown>);
  if (code) jsonBody.code = code;
  if (message) jsonBody.message = message;
  if (request_id) jsonBody.request_id = request_id;

  return vi.mocked(fetch).mockResolvedValueOnce({
    ok,
    status,
    headers: new Headers({ 'content-type': 'application/json' }),
    json: () => Promise.resolve(Object.keys(jsonBody).length ? jsonBody : undefined),
  } as Response);
}

/** Create a client with fast retries for testing */
function createTestClient(config?: Partial<ConstructorParameters<typeof ApiClient>[0]>) {
  return new ApiClient({
    baseUrl: '/api',
    retryBaseDelayMs: 10, // fast retries for tests
    ...config,
  });
}

beforeEach(() => {
  vi.spyOn(globalThis, 'fetch').mockReset();
});

afterEach(() => {
  vi.restoreAllMocks();
});

// ---------------------------------------------------------------------------
// Client factory
// ---------------------------------------------------------------------------

describe('ApiClient factory', () => {
  it('configureClient creates and returns a client', () => {
    const client = configureClient({ baseUrl: '/api/v1' });
    expect(client).toBeInstanceOf(ApiClient);
  });

  it('getClient returns a default client', () => {
    const client = getClient();
    expect(client).toBeInstanceOf(ApiClient);
  });
});

// ---------------------------------------------------------------------------
// 200 – success
// ---------------------------------------------------------------------------

describe('200 success', () => {
  it('returns data on GET', async () => {
    mockFetchOnce({ body: { data: { id: 1, name: 'test' } } });
    const client = createTestClient();
    const res = await client.get('/tasks');
    expect(res.data).toEqual({ id: 1, name: 'test' });
    expect(res.error).toBeUndefined();
  });

  it('returns data on POST', async () => {
    mockFetchOnce({ status: 201, body: { data: { id: 42 } } });
    const client = createTestClient();
    const res = await client.post('/tasks', { title: 'new' });
    expect(res.data).toEqual({ id: 42 });
    expect(res.error).toBeUndefined();
  });

  it('works when server returns data without wrapping', async () => {
    mockFetchOnce({ body: { id: 1, name: 'direct' } });
    const client = createTestClient();
    const res = await client.get('/items');
    expect(res.data).toEqual({ id: 1, name: 'direct' });
  });
});

// ---------------------------------------------------------------------------
// 400 – bad request with error-code mapping
// ---------------------------------------------------------------------------

describe('400 bad request', () => {
  it('maps POINTS_INSUFFICIENT_BALANCE', async () => {
    mockFetchOnce({
      ok: false,
      status: 400,
      code: 'POINTS_INSUFFICIENT_BALANCE',
    });
    const client = createTestClient();
    const res = await client.post('/exchanges', {});
    expect(res.error).toBeDefined();
    expect(res.error!.error_code).toBe('POINTS_INSUFFICIENT_BALANCE');
    expect(res.error!.message).toBe(getErrorMessage('POINTS_INSUFFICIENT_BALANCE'));
  });

  it('maps PRIZE_OUT_OF_STOCK', async () => {
    mockFetchOnce({
      ok: false,
      status: 409,
      code: 'PRIZE_OUT_OF_STOCK',
    });
    const client = createTestClient();
    const res = await client.post('/exchanges', {});
    expect(res.error!.error_code).toBe('PRIZE_OUT_OF_STOCK');
    expect(res.error!.message).toBe(getErrorMessage('PRIZE_OUT_OF_STOCK'));
  });

  it('includes optional correlation id from server', async () => {
    mockFetchOnce({
      ok: false,
      status: 400,
      code: 'POINTS_INVALID_TRANSACTION',
      request_id: 'req-abc-123',
    });
    const client = createTestClient();
    const res = await client.get('/points/1/transactions');
    expect(res.error!.id).toBe('req-abc-123');
  });
});

// ---------------------------------------------------------------------------
// 401 – triggers logout
// ---------------------------------------------------------------------------

describe('401 unauthorized', () => {
  it('calls onUnauthorized callback', async () => {
    mockFetchOnce({ ok: false, status: 401, code: 'AUTH_UNAUTHENTICATED' });
    const onUnauthorized = vi.fn();
    const client = createTestClient({ onUnauthorized });
    await client.get('/tasks');
    expect(onUnauthorized).toHaveBeenCalledOnce();
  });

  it('does not throw when no callback is set', async () => {
    mockFetchOnce({ ok: false, status: 401, code: 'AUTH_UNAUTHENTICATED' });
    const client = createTestClient();
    await expect(client.get('/tasks')).resolves.not.toThrow();
  });
});

// ---------------------------------------------------------------------------
// 403 – forbidden
// ---------------------------------------------------------------------------

describe('403 forbidden', () => {
  it('returns FORBIDDEN error code', async () => {
    mockFetchOnce({ ok: false, status: 403, code: 'FORBIDDEN' });
    const client = createTestClient();
    const res = await client.get('/admin/config');
    expect(res.error!.error_code).toBe('FORBIDDEN');
    expect(res.error!.message).toBe(getErrorMessage('FORBIDDEN'));
  });

  it('handles CAPABILITY_NOT_SUPPORTED', async () => {
    mockFetchOnce({
      ok: false,
      status: 403,
      code: 'CAPABILITY_NOT_SUPPORTED',
    });
    const client = createTestClient();
    const res = await client.get('/admin/multi-family');
    expect(res.error!.error_code).toBe('CAPABILITY_NOT_SUPPORTED');
  });
});

// ---------------------------------------------------------------------------
// 404 – not found
// ---------------------------------------------------------------------------

describe('404 not found', () => {
  it('returns error with HTTP_404 fallback', async () => {
    mockFetchOnce({ ok: false, status: 404 });
    const client = createTestClient();
    const res = await client.get('/nonexistent');
    expect(res.error).toBeDefined();
    expect(res.error!.message).toContain('不存在');
  });

  it('respects server error_code when present', async () => {
    mockFetchOnce({
      ok: false,
      status: 404,
      code: 'PRIZE_NOT_FOUND',
    });
    const client = createTestClient();
    const res = await client.get('/prizes/999');
    expect(res.error!.error_code).toBe('PRIZE_NOT_FOUND');
    expect(res.error!.message).toBe(getErrorMessage('PRIZE_NOT_FOUND'));
  });
});

// ---------------------------------------------------------------------------
// 409 – conflict
// ---------------------------------------------------------------------------

describe('409 conflict', () => {
  it('returns EXCHANGE_IDEMPOTENCY_CONFLICT', async () => {
    mockFetchOnce({
      ok: false,
      status: 409,
      code: 'EXCHANGE_IDEMPOTENCY_CONFLICT',
    });
    const client = createTestClient();
    const res = await client.post('/exchanges', { idempotency_key: 'dup' });
    expect(res.error!.error_code).toBe('EXCHANGE_IDEMPOTENCY_CONFLICT');
    expect(res.error!.message).toBe(getErrorMessage('EXCHANGE_IDEMPOTENCY_CONFLICT'));
  });
});

// ---------------------------------------------------------------------------
// 429 – rate limit with retry
// ---------------------------------------------------------------------------

describe('429 rate limit', () => {
  it('retries after 429 and succeeds', async () => {
    mockFetchOnce({ ok: false, status: 429, code: 'RATE_LIMITED' });
    mockFetchOnce({ body: { data: { id: 1 } } });
    const client = createTestClient();
    const res = await client.get('/tasks', { maxRetries: 3 });
    expect(res.data).toEqual({ id: 1 });
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it('returns RATE_LIMITED error after exhausting retries', async () => {
    // 4 responses: initial + 3 retries = all 429
    mockFetchOnce({ ok: false, status: 429, code: 'RATE_LIMITED' });
    mockFetchOnce({ ok: false, status: 429, code: 'RATE_LIMITED' });
    mockFetchOnce({ ok: false, status: 429, code: 'RATE_LIMITED' });
    mockFetchOnce({ ok: false, status: 429, code: 'RATE_LIMITED' });
    const client = createTestClient();
    const res = await client.get('/tasks', { maxRetries: 3 });
    expect(res.error).toBeDefined();
    expect(res.error!.error_code).toBe('RATE_LIMITED');
    expect(fetch).toHaveBeenCalledTimes(4); // initial + 3 retries
  }, 20_000);
});

// ---------------------------------------------------------------------------
// Network error retry
// ---------------------------------------------------------------------------

describe('network error retry', () => {
  it('retries on network failure and succeeds', async () => {
    let callCount = 0;
    vi.mocked(fetch).mockImplementation(() => {
      callCount++;
      if (callCount === 1) return Promise.reject(new TypeError('Failed to fetch'));
      return Promise.resolve({
        ok: true,
        status: 200,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: () => Promise.resolve({ data: { ok: true } }),
      } as Response);
    });
    const client = createTestClient();
    const res = await client.get('/tasks', { maxRetries: 3 });
    expect(res.data).toEqual({ ok: true });
    expect(callCount).toBe(2);
  });

  it('returns NETWORK_ERROR after exhausting retries', async () => {
    let callCount = 0;
    vi.mocked(fetch).mockImplementation(() => {
      callCount++;
      return Promise.reject(new TypeError('Failed to fetch'));
    });
    const client = createTestClient();
    const res = await client.get('/tasks', { maxRetries: 2 });
    expect(res.error).toBeDefined();
    expect(res.error!.error_code).toBe('NETWORK_ERROR');
    // initial + 2 retries = 3 calls
    expect(callCount).toBe(3);
  });
});

// ---------------------------------------------------------------------------
// CSRF token header injection
// ---------------------------------------------------------------------------

describe('CSRF token injection', () => {
  beforeEach(() => {
    document.querySelectorAll('meta[name="csrf-token"]').forEach((el) => el.remove());
    document.cookie = 'csrf_token=; Max-Age=-1; path=/';
  });

  it('reads CSRF token from meta tag', async () => {
    const meta = document.createElement('meta');
    meta.name = 'csrf-token';
    meta.content = 'meta-token-abc';
    document.head.appendChild(meta);

    mockFetchOnce({ body: { data: 'ok' } });
    const client = createTestClient();
    await client.get('/tasks');

    const call = vi.mocked(fetch).mock.calls[0];
    const headers = (call[1] as RequestInit).headers as Record<string, string>;
    expect(headers['X-CSRF-TOKEN']).toBe('meta-token-abc');

    meta.remove();
  });

  it('reads CSRF token from cookie when meta is absent', async () => {
    document.cookie = 'csrf_token=cookie-token-xyz; path=/';

    mockFetchOnce({ body: { data: 'ok' } });
    const client = createTestClient();
    await client.get('/tasks');

    const call = vi.mocked(fetch).mock.calls[0];
    const headers = (call[1] as RequestInit).headers as Record<string, string>;
    expect(headers['X-CSRF-TOKEN']).toBe('cookie-token-xyz');
  });

  it('omits X-CSRF-TOKEN header when no token source is available', async () => {
    mockFetchOnce({ body: { data: 'ok' } });
    const client = createTestClient();
    await client.get('/tasks');

    const call = vi.mocked(fetch).mock.calls[0];
    const headers = (call[1] as RequestInit).headers as Record<string, string>;
    // The header should either not exist or be undefined
    expect(headers['X-CSRF-TOKEN']).toBeUndefined();
  });
});

// ---------------------------------------------------------------------------
// Error-code → message mapping coverage (all prefixes from §5.2)
// ---------------------------------------------------------------------------

describe('getErrorMessage coverage (§5.2)', () => {
  const verify = (code: string) => {
    const msg = getErrorMessage(code);
    expect(msg).toBeTruthy();
    expect(typeof msg).toBe('string');
    expect(msg.length).toBeGreaterThan(0);
  };

  it('POINTS_*', () => {
    verify('POINTS_FORBIDDEN');
    verify('POINTS_ACCOUNT_NOT_FOUND');
    verify('POINTS_INVALID_TRANSACTION');
    verify('POINTS_LEDGER_IMMUTABLE');
    verify('POINTS_REFERENCE_CONFLICT');
    verify('POINTS_INSUFFICIENT_BALANCE');
    verify('POINTS_ACCOUNT_CONFLICT');
    verify('POINTS_SPEND_SOURCE_INVALID');
    verify('POINTS_ALREADY_REFUNDED');
    verify('POINTS_ADJUST_REASON_REQUIRED');
  });

  it('EXCHANGE_*', () => {
    verify('EXCHANGE_IDEMPOTENCY_KEY_REQUIRED');
    verify('EXCHANGE_IDEMPOTENCY_CONFLICT');
    verify('EXCHANGE_INVALID_STATE');
    verify('EXCHANGE_TRANSACTION_FAILED');
    verify('EXCHANGE_CANCELLATION_FAILED');
    verify('EXCHANGE_NOT_FOUND');
    verify('EXCHANGE_INVALID_QUERY');
  });

  it('TASK_TEMPLATE_*', () => {
    verify('TASK_TEMPLATE_FORBIDDEN');
    verify('TASK_TEMPLATE_NOT_FOUND');
    verify('TASK_TEMPLATE_VALIDATION_FAILED');
    verify('TASK_TEMPLATE_REQUIRES_ACTIVE_DIFFICULTY');
    verify('TASK_TEMPLATE_INVALID_RECURRENCE');
    verify('TASK_TEMPLATE_VERSION_CONFLICT');
    verify('TASK_TEMPLATE_INACTIVE');
    verify('TASK_TEMPLATE_TYPE_IMMUTABLE');
    verify('TASK_TEMPLATE_TYPE_CONFIG_MISMATCH');
    verify('TASK_TEMPLATE_INVALID_QUERY');
  });

  it('TASK_LIMITED_*', () => {
    verify('TASK_LIMITED_NOT_STARTED');
    verify('TASK_LIMITED_EXPIRED');
  });

  it('TASK_REPEAT_*', () => {
    verify('TASK_REPEAT_NOT_TRIGGER_DAY');
  });

  it('TASK_STANDING_*', () => {
    verify('TASK_STANDING_LIMIT_REACHED');
  });

  it('TASK_REVIEW_*', () => {
    verify('TASK_REVIEW_FORBIDDEN');
    verify('TASK_REVIEW_NOT_FOUND');
    verify('TASK_SUBMISSION_VALIDATION_FAILED');
    verify('TASK_REVIEW_INVALID_STATE');
    verify('TASK_SUBMISSION_IDEMPOTENCY_CONFLICT');
    verify('TASK_SUBMISSION_LATE_NOT_ALLOWED');
    verify('TASK_REVIEW_REASON_REQUIRED');
    verify('TASK_REVIEW_VALIDATION_FAILED');
    verify('TASK_REVIEW_IDEMPOTENCY_CONFLICT');
    verify('TASK_REVIEW_ALREADY_DECIDED');
    verify('TASK_REVIEW_STALE_ATTEMPT');
    verify('TASK_ASSIGNMENT_CANCELLED');
    verify('TASK_REVIEW_INVALID_QUERY');
    verify('TASK_REVIEW_HISTORY_IMMUTABLE');
  });

  it('PRIZE_*', () => {
    verify('PRIZE_INVALID_POINTS_COST');
    verify('PRIZE_INVALID_STOCK');
    verify('PRIZE_NOT_FOUND');
    verify('PRIZE_OUT_OF_STOCK');
  });

  it('BLIND_BOX_*', () => {
    verify('BLIND_BOX_NOT_FOUND');
    verify('BLIND_BOX_POOL_CHANGED');
  });

  it('AUTH_*', () => {
    verify('AUTH_UNAUTHENTICATED');
    verify('AUTH_INVALID_TOKEN');
    verify('AUTH_EXPIRED_TOKEN');
    verify('AUTH_INSUFFICIENT_PERMISSIONS');
  });

  it('INSTANCE_*', () => {
    verify('INSTANCE_ADMIN_REQUIRED');
    verify('LAST_INSTANCE_ADMIN');
    verify('AUDIT_QUERY_LIMIT_EXCEEDED');
  });

  it('general codes', () => {
    verify('CAPABILITY_NOT_SUPPORTED');
    verify('FORBIDDEN');
    verify('RATE_LIMITED');
    verify('INTERNAL_ERROR');
  });

  it('returns fallback for unknown codes', () => {
    const msg = getErrorMessage('SOME_UNKNOWN_CODE');
    expect(msg).toBeTruthy();
    expect(typeof msg).toBe('string');
  });
});

// ---------------------------------------------------------------------------
// Base URL & credentials
// ---------------------------------------------------------------------------

describe('credentials and base URL', () => {
  it('sends credentials: include', async () => {
    mockFetchOnce({ body: { data: true } });
    const client = createTestClient();
    await client.get('/tasks');

    const call = vi.mocked(fetch).mock.calls[0];
    expect((call[1] as RequestInit).credentials).toBe('include');
  });

  it('constructs correct URL from baseUrl and path', async () => {
    mockFetchOnce({ body: { data: true } });
    const client = createTestClient({ baseUrl: '/api/v2' });
    await client.get('/users/5');

    expect(vi.mocked(fetch).mock.calls[0][0]).toBe('/api/v2/users/5');
  });

  it('trims trailing slashes from baseUrl', async () => {
    mockFetchOnce({ body: { data: true } });
    const client = createTestClient({ baseUrl: '/api/v1//' });
    await client.get('/health');
    expect(vi.mocked(fetch).mock.calls[0][0]).toBe('/api/v1/health');
  });
});

// ---------------------------------------------------------------------------
// onError callback
// ---------------------------------------------------------------------------

describe('onError callback', () => {
  it('is called for error responses', async () => {
    mockFetchOnce({ ok: false, status: 403, code: 'FORBIDDEN' });
    const onError = vi.fn();
    const client = createTestClient({ onError });
    await client.get('/admin');
    expect(onError).toHaveBeenCalledOnce();
    expect(onError).toHaveBeenCalledWith(
      expect.objectContaining({ error_code: 'FORBIDDEN' }),
      403,
    );
  });
});
