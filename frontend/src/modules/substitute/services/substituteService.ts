import { apiDelete, apiGet, apiPost, apiPut } from '@/services/apiClient';
import type { PageResponse } from '@/shared/types/api';
import type {
  ComparisonResponse, CreateProductRequestBody, CreateRelationshipBody, ProductRequestResponse,
  ProductRequestStatus, RelationshipResponse, SubstituteSettingResponse,
} from '../types';

/** Backend: product/substitute/controller/*.java (CR-089) */
export const substituteService = {
  create: (body: CreateProductRequestBody) =>
    apiPost<ProductRequestResponse>('/v1/product-requests', body),

  search: (status: ProductRequestStatus | null, page = 0, size = 20) =>
    apiGet<PageResponse<ProductRequestResponse>>('/v1/product-requests', {
      params: { status: status ?? undefined, page, size, sort: 'createdAt,desc' },
    }),

  get: (id: number) => apiGet<ProductRequestResponse>(`/v1/product-requests/${id}`),

  recompute: (id: number) =>
    apiPost<ProductRequestResponse>(`/v1/product-requests/${id}/alternatives/recompute`),

  compare: (id: number, alternativeProductId: number) =>
    apiGet<ComparisonResponse>(`/v1/product-requests/${id}/compare`, { params: { alternativeProductId } }),

  selectAlternative: (id: number, productId: number) =>
    apiPost<ProductRequestResponse>(`/v1/product-requests/${id}/select-alternative`, { productId }),

  cancel: (id: number) => apiPost<ProductRequestResponse>(`/v1/product-requests/${id}/cancel`),

  relationships: (productId: number) =>
    apiGet<RelationshipResponse[]>(`/v1/products/${productId}/alternative-mappings`),

  createRelationship: (productId: number, body: CreateRelationshipBody) =>
    apiPost<RelationshipResponse>(`/v1/products/${productId}/alternative-mappings`, body),

  deleteRelationship: (productId: number, relationshipId: number) =>
    apiDelete(`/v1/products/${productId}/alternative-mappings/${relationshipId}`),

  settings: () => apiGet<SubstituteSettingResponse>('/v1/substitute-settings'),

  updateSettings: (body: SubstituteSettingResponse) =>
    apiPut<SubstituteSettingResponse>('/v1/substitute-settings', body),
};
