import { platformAdminDelete, platformAdminGet, platformAdminPost } from '@/services/platformAdminApiClient';
import type { CreateFeatureFlagRequest, FeatureFlagResponse } from '../types';

export const platformAdminFeatureFlagService = {
  list() {
    return platformAdminGet<FeatureFlagResponse[]>('/v1/platform-admin/feature-flags');
  },
  create(body: CreateFeatureFlagRequest) {
    return platformAdminPost<FeatureFlagResponse>('/v1/platform-admin/feature-flags', body);
  },
  enable(id: number) {
    return platformAdminPost<FeatureFlagResponse>(`/v1/platform-admin/feature-flags/${id}/enable`);
  },
  disable(id: number) {
    return platformAdminPost<FeatureFlagResponse>(`/v1/platform-admin/feature-flags/${id}/disable`);
  },
  remove(id: number) {
    return platformAdminDelete<void>(`/v1/platform-admin/feature-flags/${id}`);
  },
};
