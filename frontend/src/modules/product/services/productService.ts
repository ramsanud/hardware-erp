import {
  apiDelete, apiGet, apiPost, apiPut, apiUploadFile, http,
} from '@/services/apiClient';
import type { ApiResponse, PageResponse } from '@/shared/types/api';
import type {
  ProductImportConfirmRequest, ProductImportPreviewResponse, ProductImportResultResponse,
  ProductRequest, ProductResponse, ProductSearchParams, ProductSummaryResponse,
} from '../types';

/** Backend: product/controller/ProductController.java, ProductImageController.java, ProductImportController.java */
export const productService = {
  search: (params: ProductSearchParams) =>
    apiGet<PageResponse<ProductSummaryResponse>>('/v1/products', { params }),

  get: (id: number) => apiGet<ProductResponse>(`/v1/products/${id}`),

  create: (body: ProductRequest) => apiPost<ProductResponse>('/v1/products', body),

  update: (id: number, body: ProductRequest) =>
    apiPut<ProductResponse>(`/v1/products/${id}`, body),

  /** Soft delete. Purchase and invoice history will reference product_id permanently. */
  remove: (id: number) => apiDelete(`/v1/products/${id}`),

  imageUrl: (id: number) => `/v1/products/${id}/image`,
  uploadImage: (id: number, file: File) => apiUploadFile(`/v1/products/${id}/image`, file),
  removeImage: (id: number) => apiDelete(`/v1/products/${id}/image`),

  /** CR-036 - bulk import from CSV/Excel. Preview never writes; confirm is the only step that does. */
  importPreview: async (file: File): Promise<ProductImportPreviewResponse> => {
    const form = new FormData();
    form.append('file', file);
    const { data } = await http.post<ApiResponse<ProductImportPreviewResponse>>(
      '/v1/products/import/preview', form, { headers: { 'Content-Type': 'multipart/form-data' } },
    );
    return data.data;
  },
  importConfirm: (body: ProductImportConfirmRequest) =>
    apiPost<ProductImportResultResponse>('/v1/products/import/confirm', body),
};
