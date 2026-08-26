import { apiDelete, apiGet, apiPost, apiPut } from '@/services/apiClient';
import type { PageResponse } from '@/shared/types/api';
import type {
  CreateUserRequest, ResetUserPasswordRequest, UpdateUserRequest,
  UserResponse, UserSearchParams,
} from '../types';

/**
 * Backend: auth/controller/UserController.java
 *
 * There is no self-registration endpoint (CR-008). `create` is the only way an
 * account comes into existence and it requires USER_MANAGE.
 */
export const userService = {
  search: (params: UserSearchParams) =>
    apiGet<PageResponse<UserResponse>>('/v1/users', { params }),

  get: (id: number) => apiGet<UserResponse>(`/v1/users/${id}`),

  create: (body: CreateUserRequest) => apiPost<UserResponse>('/v1/users', body),

  update: (id: number, body: UpdateUserRequest) =>
    apiPut<UserResponse>(`/v1/users/${id}`, body),

  resetPassword: (id: number, body: ResetUserPasswordRequest) =>
    apiPost<void>(`/v1/users/${id}/reset-password`, body),

  /** Soft delete. The row survives so historical created_by still resolves. */
  remove: (id: number) => apiDelete(`/v1/users/${id}`),
};
