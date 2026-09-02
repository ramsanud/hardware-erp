import axios, {
  AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from 'axios';
import { ApiError, type ApiErrorResponse, type ApiResponse } from '@/shared/types/api';
import { platformAdminTokenStorage } from './platformAdminTokenStorage';

/**
 * A second, independent HTTP client from services/apiClient.ts - never the
 * same axios instance. The backend enforces the same separation with two
 * disjoint filter chains and two disjoint signing keys (PlatformAdminSecurityConfig);
 * mirroring it here means a bug in one client's interceptor (attaching the
 * wrong bearer token, retrying against the wrong refresh endpoint) can never
 * bleed into the other console's session.
 */

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

const REFRESH_PATH = '/v1/platform-admin/auth/refresh';
const PUBLIC_PATHS = [
  '/v1/platform-admin/auth/login',
  '/v1/platform-admin/auth/mfa/verify',
  '/v1/platform-admin/auth/mfa/enroll',
  '/v1/platform-admin/auth/mfa/enroll/confirm',
  REFRESH_PATH,
];

interface RetryConfig extends InternalAxiosRequestConfig {
  _retried?: boolean;
}

export const platformAdminHttp: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: 30_000,
  headers: { 'Content-Type': 'application/json' },
});

platformAdminHttp.interceptors.request.use((config) => {
  const token = platformAdminTokenStorage.get();
  if (token && !PUBLIC_PATHS.includes(config.url ?? '')) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

let refreshPromise: Promise<string> | null = null;
let onSessionExpired: (() => void) | null = null;

export function setPlatformAdminSessionExpiredHandler(handler: () => void): void {
  onSessionExpired = handler;
}

interface SessionTokens {
  accessToken: string;
  refreshToken: string;
}

async function refreshAccessToken(): Promise<string> {
  const raw = platformAdminTokenStorage.getRefreshToken();
  if (!raw) {
    throw new Error('No refresh token available');
  }
  const response = await axios.post<ApiResponse<SessionTokens>>(
    `${BASE_URL}${REFRESH_PATH}`,
    { refreshToken: raw },
    { headers: { 'Content-Type': 'application/json' } },
  );
  const { accessToken, refreshToken } = response.data.data;
  platformAdminTokenStorage.set(accessToken, refreshToken);
  return accessToken;
}

export function requestPlatformAdminRefresh(): Promise<string> {
  refreshPromise ??= refreshAccessToken().finally(() => {
    refreshPromise = null;
  });
  return refreshPromise;
}

platformAdminHttp.interceptors.response.use(
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
        const token = await requestPlatformAdminRefresh();
        config.headers.Authorization = `Bearer ${token}`;
        return await platformAdminHttp.request(config);
      } catch {
        platformAdminTokenStorage.clear();
        onSessionExpired?.();
        return Promise.reject(toApiError(error));
      }
    }

    if (status === 401 && config?.url === REFRESH_PATH) {
      platformAdminTokenStorage.clear();
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

export async function platformAdminGet<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const { data } = await platformAdminHttp.get<ApiResponse<T>>(url, config);
  return data.data;
}

export async function platformAdminPost<T>(
  url: string,
  body?: unknown,
  config?: AxiosRequestConfig,
): Promise<T> {
  const { data } = await platformAdminHttp.post<ApiResponse<T>>(url, body ?? {}, config);
  return data.data;
}

export async function platformAdminDelete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const { data } = await platformAdminHttp.delete<ApiResponse<T>>(url, config);
  return data.data;
}
