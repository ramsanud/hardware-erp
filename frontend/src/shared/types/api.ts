/**
 * Mirrors the backend envelopes exactly.
 * Backend: common/dto/ApiResponse.java, ErrorResponse.java, PageResponse.java
 */

export interface ApiResponse<T> {
  success: true;
  message?: string;
  data: T;
  timestamp: string;
}

export interface ApiErrorResponse {
  success: false;
  message: string;
  code: string;
  path?: string;
  requestId?: string;
  timestamp: string;
  /** Field name -> message, populated only for VALIDATION_ERROR. */
  errors?: Record<string, string>;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

/**
 * A resolved-but-empty page. CR-058: a list page showing its deleted-records
 * view must not also fire the ordinary paginated search, so its fetcher
 * short-circuits to this instead of leaving the previous page's rows on screen.
 */
export function emptyPage<T>(size = 0): PageResponse<T> {
  return { content: [], page: 0, size, totalElements: 0, totalPages: 0, first: true, last: true };
}

/** Error codes the backend actually emits. Kept in sync with GlobalExceptionHandler. */
export type ApiErrorCode =
  | 'VALIDATION_ERROR'
  | 'MISSING_PARAMETER'
  | 'BAD_PARAMETER'
  | 'MALFORMED_REQUEST'
  | 'METHOD_NOT_ALLOWED'
  | 'INVALID_CREDENTIALS'
  | 'UNAUTHENTICATED'
  | 'INVALID_REFRESH_TOKEN'
  | 'REFRESH_TOKEN_EXPIRED'
  | 'TOKEN_REUSE'
  | 'WRONG_PASSWORD'
  | 'INVALID_RESET_TOKEN'
  | 'ACCESS_DENIED'
  | 'NOT_FOUND'
  | 'DUPLICATE_RESOURCE'
  | 'DATA_CONFLICT'
  | 'STALE_RECORD'
  | 'BUSINESS_RULE_VIOLATION'
  | 'LAST_OWNER_PROTECTED'
  | 'RATE_LIMIT_EXCEEDED'
  | 'INTERNAL_ERROR';

/** Normalised shape every service throws, so pages never touch axios types. */
export class ApiError extends Error {
  readonly code: string;
  readonly status: number;
  readonly fieldErrors?: Record<string, string>;
  readonly requestId?: string;

  constructor(params: {
    message: string;
    code: string;
    status: number;
    fieldErrors?: Record<string, string>;
    requestId?: string;
  }) {
    super(params.message);
    this.name = 'ApiError';
    this.code = params.code;
    this.status = params.status;
    this.fieldErrors = params.fieldErrors;
    this.requestId = params.requestId;
  }

  get isValidation() { return this.code === 'VALIDATION_ERROR'; }
  get isForbidden() { return this.status === 403; }
  get isRateLimited() { return this.status === 429; }
}
