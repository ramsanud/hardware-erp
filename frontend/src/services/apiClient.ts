import axios, {
  AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from 'axios';
import { ApiError, type ApiErrorResponse, type ApiResponse } from '@/shared/types/api';
import { tokenStorage } from './tokenStorage';

/**
 * The single HTTP entry point. No page or component calls axios directly, so
 * auth headers, refresh-on-401 and error normalisation exist in exactly one
 * place.
 */

/**
 * Where the API lives.
 *
 * Unset - the intended configuration - means the relative '/api', so requests
 * are same-origin: the Vite dev proxy locally, the vercel.json rewrite in
 * production. That is not a convenience. The refresh token cookie is
 * SameSite=Strict, so a cross-origin XHR never sends it, and sign-in silently
 * stops persisting the moment the 15-minute access token expires.
 *
 * When it IS set, the '/api' suffix is added if missing. The server's
 * context-path is a fixed '/api' (application.yml), so a base URL without it
 * cannot be right - and getting it wrong produces
 * '<host>/v1/auth/login', a 404 whose CORS preflight failure looks like a
 * CORS problem rather than a path one. That cost a deploy on 2026-09-05.
 */
function resolveBaseUrl(): string {
  const configured = import.meta.env.VITE_API_BASE_URL?.trim();
  if (!configured) {
    return '/api';
  }

  const trimmed = configured.replace(/\/+$/, '');
  const normalised = trimmed.endsWith('/api') ? trimmed : `${trimmed}/api`;

  // An absolute URL means cross-origin, which breaks the refresh cookie. Say
  // so once, loudly, rather than letting it surface days later as users being
  // logged out at seemingly random moments.
  if (/^https?:\/\//i.test(normalised)) {
    console.warn(
      `[apiClient] VITE_API_BASE_URL is set to an absolute URL (${normalised}). ` +
      'Requests will be cross-origin, and the SameSite=Strict refresh cookie will NOT be sent - ' +
      'sign-in will stop persisting once the access token expires. Prefer leaving ' +
      'VITE_API_BASE_URL unset and routing /api through the vercel.json rewrite (or the Vite dev proxy).',
    );
  }
  return normalised;
}

const BASE_URL = resolveBaseUrl();

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
}

export const http: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: 30_000,
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
      message: 'The server took too long to respond. Please try again.',
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
