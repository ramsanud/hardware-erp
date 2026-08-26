import { apiDelete, apiUploadFile } from '@/services/apiClient';

/** Backend: auth/controller/AvatarController.java */
export const avatarService = {
  url: '/v1/auth/me/avatar',
  upload: (file: File) => apiUploadFile('/v1/auth/me/avatar', file),
  remove: () => apiDelete('/v1/auth/me/avatar'),
};
