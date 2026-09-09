import axios, {
  AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from 'axios';
import { ApiError, type ApiErrorResponse, type ApiResponse } from '@/shared/types/api';
import { resolveApiBaseUrl } from './apiBaseUrl';
import { tokenStorage } from './tokenStorage';

/**
 * The single HTTP entry point. No page or component calls axios directly, so
 * auth headers, refresh-on-401 and error normalisation exist in exactly one
 * place.
 */

const BASE_URL = resolveApiBaseUrl();

/** Never retried on 401 - a failure here means the session is genuinely over. */
const REFRESH_PATH = '/v1/auth/refresh';
const PUBLIC_PATHS = [
  '/v1/auth/login',
  REFRESH_PATH,
  '/v1/auth/forgot-password',
  '/v1/auth/reset-password',
];

interface RetryConfig extends InternalAxiosRequestConfig {
  _retried?: boolean;
  /** Separate from _retried so a 401-refresh and a timeout retry cannot cancel each other out. */
  _timeoutRetried?: boolean;
}

/**
 * BUG-FE-033. 90s, not 30s.
 *
 * A timeout here does NOT mean "the server is down". The TLS handshake
 * completed and the request was accepted - measured at 0.18s against the
 * production host - so something upstream is merely busy. A genuinely
 * unreachable server fails at connect time instead and surfaces as
 * NETWORK_ERROR in seconds, which is why raising this ceiling costs nothing
 * in that case.
 *
 * What it buys: the free Render tier sleeps after 15 minutes and documents a
 * 30-60s cold start (docs/DEPLOYMENT.md), so a 30s timeout was BELOW the
 * platform's own best case - the first action after any idle period was
 * guaranteed to fail. Measured worse than that in practice, because Hibernate
 * runs ddl-auto: validate over ~50 tables at boot and each round trip pays the
 * latency between the service and its database.
 */
export const http: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: 90_000,
  // Required for the HttpOnly refresh cookie to be sent.
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
});

http.interceptors.request.use((config) => {
  const token = tokenStorage.get();
  if (token && !PUBLIC_PATHS.includes(config.url ?? '')) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// ---------------------------------------------------------------------------
// Single-flight refresh.
//
// When several requests 401 at once - a dashboard firing four calls on mount -
// only the first triggers a refresh. The rest wait on the same promise. Without
// this they would each rotate the refresh token, and the backend treats a
// rotated token being replayed as theft: it revokes every session and the user
// is thrown out (AuthServiceImpl reuse detection).
// ---------------------------------------------------------------------------
let refreshPromise: Promise<string> | null = null;
let onSessionExpired: (() => void) | null = null;

export function setSessionExpiredHandler(handler: () => void): void {
  onSessionExpired = handler;
}

async function refreshAccessToken(): Promise<string> {
  const response = await axios.post<ApiResponse<{ accessToken: string }>>(
    `${BASE_URL}${REFRESH_PATH}`,
    {},
    { withCredentials: true, headers: { 'Content-Type': 'application/json' } },
  );
  const token = response.data.data.accessToken;
  tokenStorage.set(token);
  return token;
}

export function requestRefresh(): Promise<string> {
  refreshPromise ??= refreshAccessToken().finally(() => {
    refreshPromise = null;
  });
  return refreshPromise;
}

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiErrorResponse>) => {
    const config = error.config as RetryConfig | undefined;
    const status = error.response?.status;

    /*
     * BUG-FE-033. One retry of a SAFE method turns a cold start into a slow
     * page load rather than an error screen.
     *
     * Deliberately GET only. A timed-out write may already have been applied
     * server-side - the response was lost, not the request - and this client
     * sends no Idempotency-Key, so replaying a POST could duplicate a record.
     * The backend has an IdempotencyService but no frontend call site uses it
     * yet; wiring that up is the prerequisite for ever retrying a write here.
     */
    if (
      error.code === 'ECONNABORTED'
      && config
      && !config._timeoutRetried
      && (config.method ?? 'get').toLowerCase() === 'get'
    ) {
      config._timeoutRetried = true;
      return await http.request(config);
    }

    const canRetry =
      status === 401 &&
      config &&
      !config._retried &&
      !PUBLIC_PATHS.includes(config.url ?? '');

    if (canRetry) {
      config._retried = true;
      try {
        const token = await requestRefresh();
        config.headers.Authorization = `Bearer ${token}`;
        return await http.request(config);
      } catch {
        tokenStorage.clear();
        onSessionExpired?.();
        return Promise.reject(toApiError(error));
      }
    }

    if (status === 401 && config?.url === REFRESH_PATH) {
      tokenStorage.clear();
      onSessionExpired?.();
    }

    return Promise.reject(toApiError(error));
  },
);

function toApiError(error: AxiosError<ApiErrorResponse>): ApiError {
  const body = error.response?.data;

  if (body && typeof body === 'object' && 'code' in body) {
    return new ApiError({
      message: body.message ?? 'Request failed',
      code: body.code,
      status: error.response?.status ?? 500,
      fieldErrors: body.errors,
      requestId: body.requestId,
    });
  }

  if (error.code === 'ECONNABORTED') {
    return new ApiError({
      // Names the usual cause. "Took too long" reads as a fault the user
      // caused or can fix by retrying immediately; on a sleeping free-tier
      // instance the truthful advice is to wait a few seconds and retry.
      message: 'The server is still starting up. Give it a few seconds and try again.',
      code: 'TIMEOUT',
      status: 408,
    });
  }

  if (!error.response) {
    return new ApiError({
      message: 'Cannot reach the server. Check that the backend is running.',
      code: 'NETWORK_ERROR',
      status: 0,
    });
  }

  return new ApiError({
    message: 'Something went wrong. Please try again.',
    code: 'INTERNAL_ERROR',
    status: error.response.status,
  });
}

/** Unwraps the ApiResponse envelope so services return domain data directly. */
export async function apiGet<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const { data } = await http.get<ApiResponse<T>>(url, config);
  return data.data;
}

export async function apiPost<T>(url: string, body?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const { data } = await http.post<ApiResponse<T>>(url, body ?? {}, config);
  return data.data;
}

export async function apiPut<T>(url: string, body?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const { data } = await http.put<ApiResponse<T>>(url, body ?? {}, config);
  return data.data;
}

export async function apiPatch<T>(url: string, body?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const { data } = await http.patch<ApiResponse<T>>(url, body ?? {}, config);
  return data.data;
}

/** DELETE endpoints return 204 with no body. */
export async function apiDelete(url: string, config?: AxiosRequestConfig): Promise<void> {
  await http.delete(url, config);
}

/** For endpoints that return a raw binary body (PDF) rather than the ApiResponse envelope. */
export async function apiGetBlob(url: string, config?: AxiosRequestConfig): Promise<Blob> {
  const { data } = await http.get<Blob>(url, { ...config, responseType: 'blob' });
  return data;
}

/** Multipart image upload endpoints (avatar/logo/signature) return 204, not the ApiResponse envelope. */
export async function apiUploadFile(url: string, file: File): Promise<void> {
  const form = new FormData();
  form.append('file', file);
  await http.put(url, form, { headers: { 'Content-Type': 'multipart/form-data' } });
}
