import { apiGet, apiPost } from '@/services/apiClient';
import type {
  DataResetPreviewResponse, DataResetRequest, DataResetResponse,
} from '../types';

/** Backend: tenant/controller/DataResetController.java (CR-067). */
export const dataResetService = {
  /**
   * Counted server-side at the moment the dialog opens, never cached. The
   * numbers are the whole reason the dialog is trustworthy, so a stale count
   * would be worse than none.
   */
  preview: () => apiGet<DataResetPreviewResponse>('/v1/settings/data-reset/preview'),

  reset: (body: DataResetRequest) => apiPost<DataResetResponse>('/v1/settings/data-reset', body),
};
