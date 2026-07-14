import type { ApiClientConfig, ApiResponse, RequestConfig } from './types';
import { getErrorMessage, UNKNOWN_ERROR_MESSAGE } from './errors';

/** Default base URL – override via constructor config */
const DEFAULT_BASE_URL = '/api/v1';

/** Max retry attempts for network errors and 429 responses */
const DEFAULT_MAX_RETRIES = 3;

/** Default base delay (ms) for exponential backoff */
const DEFAULT_RETRY_BASE_DELAY_MS = 1000;

/**
 * Read CSRF token from:
 * 1. `<meta name="csrf-token">` content attribute
 * 2. `XSRF-TOKEN` cookie
 * Returns `undefined` when no token is found.
 */
function getCsrfToken(): string | undefined {
  // Try meta tag first
  const meta = document.querySelector('meta[name="csrf-token"]');
  if (meta) {
    const token = meta.getAttribute('content');
    if (token) return token;
  }

  // Try cookie — backend sets cookie named "csrf_token" (AuthConstants.COOKIE_CSRF_TOKEN)
  const match = document.cookie.match(/(?:^|;\s*)csrf_token=([^;]*)/);
  if (match) return decodeURIComponent(match[1]);

  return undefined;
}

/**
 * Parse an HTTP response into a structured ApiResponse.
 */
async function parseResponse<T>(response: Response): Promise<ApiResponse<T>> {
  let body: Record<string, unknown> | null = null;
  try {
    body = (await response.json()) as Record<string, unknown>;
  } catch {
    // Response body is not JSON
    body = null;
  }

  if (response.ok) {
    return { data: (body?.data ?? body) as T };
  }

  // Build error from server response, mapping server `code` to internal error_code
  // Server format per design doc §5.1: { code, message, data, request_id }
  const errorCode = (body?.code as string) ?? null;
  const error = {
    error_code: errorCode ?? `HTTP_${response.status}`,
    message:
      (body?.message as string) ??
      (errorCode ? getErrorMessage(errorCode) : getErrorMessageForStatus(response.status)),
    id: body?.request_id as string | undefined,
    details: body?.details as Record<string, unknown> | undefined,
  };

  return { error };
}

/**
 * Derive a basic message from HTTP status when no error_code is present.
 */
function getErrorMessageForStatus(status: number): string {
  const map: Record<number, string> = {
    400: '请求参数不合法',
    401: '未登录或会话已过期',
    403: '没有权限执行此操作',
    404: '请求的资源不存在',
    409: '请求与当前状态冲突',
    429: '请求过于频繁，请稍后重试',
    500: '服务器内部错误，请稍后重试',
    502: '网关错误，请稍后重试',
    503: '服务暂时不可用，请稍后重试',
  };
  return map[status] ?? UNKNOWN_ERROR_MESSAGE;
}

/**
 * Determine if a response/error is eligible for retry.
 */
function isRetryable(error: unknown, status?: number): boolean {
  if (status === 429) return true;
  if (
    error instanceof TypeError ||
    (error instanceof DOMException && error.name === 'AbortError')
  ) {
    // Network error or timeout-related
    return true;
  }
  return false;
}

/**
 * Compute delay for a given retry attempt using exponential backoff.
 */
function retryDelayMs(attempt: number, baseDelayMs: number): number {
  const delay = baseDelayMs * Math.pow(2, attempt - 1);
  // Add a small random jitter (±200ms) to avoid thundering herd
  const jitter = Math.round(Math.random() * 400 - 200);
  return Math.max(0, delay + jitter);
}

/**
 * Unified API client.
 *
 * - Automatically includes session cookies (`credentials: 'include'`).
 * - Injects CSRF token into `X-CSRF-TOKEN` header.
 * - Maps stable error codes to user-facing messages.
 * - Retries on network errors and HTTP 429 with exponential backoff.
 */
export class ApiClient {
  private baseUrl: string;
  private onUnauthorized?: () => void;
  private onError?: (error: { error_code: string; message: string }, httpStatus: number) => void;
  private retryBaseDelayMs: number;

  constructor(config: ApiClientConfig) {
    this.baseUrl = config.baseUrl.replace(/\/+$/, '') || DEFAULT_BASE_URL;
    this.onUnauthorized = config.onUnauthorized;
    this.onError = config.onError;
    this.retryBaseDelayMs = config.retryBaseDelayMs ?? DEFAULT_RETRY_BASE_DELAY_MS;
  }

  /**
   * Core request method.
   */
  async request<T = unknown>(
    path: string,
    config: RequestConfig = {},
  ): Promise<ApiResponse<T>> {
    const { method = 'GET', body, headers: extraHeaders, signal, maxRetries = DEFAULT_MAX_RETRIES } = config;

    for (let attempt = 0; attempt <= maxRetries; attempt++) {
      // Build headers
      const csrfToken = getCsrfToken();
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        ...extraHeaders,
      };
      if (csrfToken) {
        headers['X-CSRF-TOKEN'] = csrfToken;
      }

      // Build request
      const requestInit: RequestInit = {
        method,
        headers,
        credentials: 'include',
        signal,
      };
      if (body !== undefined && method !== 'GET' && method !== 'DELETE') {
        requestInit.body = JSON.stringify(body);
      }

      try {
        const url = `${this.baseUrl}${path}`;
        const response = await fetch(url, requestInit);
        const result = await parseResponse<T>(response);

        // Handle 401 – trigger logout callback
        if (response.status === 401) {
          if (this.onUnauthorized) {
            this.onUnauthorized();
          }
        }

        // Notify onError for non-success
        if (!response.ok && result.error && this.onError) {
          this.onError(
            {
              error_code: result.error.error_code,
              message: result.error.message ?? getErrorMessage(result.error.error_code),
            },
            response.status,
          );
        }

        // Retry on 429
        if (response.status === 429 && attempt < maxRetries) {
          await this.sleep(retryDelayMs(attempt + 1, this.retryBaseDelayMs));
          continue;
        }

        return result;
      } catch (err: unknown) {
        const error = err instanceof Error ? err : new Error(String(err));

        // Do not retry aborted requests
        if (error instanceof DOMException && error.name === 'AbortError') {
          return { error: { error_code: 'REQUEST_ABORTED', message: '请求已取消' } };
        }

        // Retry on network errors
        if (isRetryable(error) && attempt < maxRetries) {
          await this.sleep(retryDelayMs(attempt + 1, this.retryBaseDelayMs));
          continue;
        }

        // Non-retryable error
        return {
          error: {
            error_code: 'NETWORK_ERROR',
            message: '网络连接失败，请检查网络后重试',
          },
        };
      }
    }

    // Exhausted retries – reached only if the loop never returned
    return {
      error: {
        error_code: 'NETWORK_ERROR',
        message: '网络连接失败，请检查网络后重试',
      },
    };
  }

  /** Shorthand GET */
  async get<T = unknown>(path: string, config?: RequestConfig): Promise<ApiResponse<T>> {
    return this.request<T>(path, { ...config, method: 'GET' });
  }

  /** Shorthand POST */
  async post<T = unknown>(path: string, body?: unknown, config?: RequestConfig): Promise<ApiResponse<T>> {
    return this.request<T>(path, { ...config, method: 'POST', body });
  }

  /** Shorthand PUT */
  async put<T = unknown>(path: string, body?: unknown, config?: RequestConfig): Promise<ApiResponse<T>> {
    return this.request<T>(path, { ...config, method: 'PUT', body });
  }

  /** Shorthand PATCH */
  async patch<T = unknown>(path: string, body?: unknown, config?: RequestConfig): Promise<ApiResponse<T>> {
    return this.request<T>(path, { ...config, method: 'PATCH', body });
  }

  /** Shorthand DELETE */
  async delete<T = unknown>(path: string, config?: RequestConfig): Promise<ApiResponse<T>> {
    return this.request<T>(path, { ...config, method: 'DELETE' });
  }

  private sleep(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }
}

/** Default singleton instance – consumers configure via `configureClient()` */
let defaultClient: ApiClient | null = null;

/**
 * Configure (or reconfigure) the default ApiClient.
 */
export function configureClient(config: ApiClientConfig): ApiClient {
  defaultClient = new ApiClient(config);
  return defaultClient;
}

/**
 * Get the default ApiClient. Throws if not configured.
 */
export function getClient(): ApiClient {
  if (!defaultClient) {
    defaultClient = new ApiClient({ baseUrl: DEFAULT_BASE_URL });
  }
  return defaultClient;
}
