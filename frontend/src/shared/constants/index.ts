export const APP_NAME = 'Hardware ERP';

export const PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const;
export const DEFAULT_PAGE_SIZE = 20;
/** Backend clamps to 100 (UserController.MAX_PAGE_SIZE). Do not exceed it. */
export const MAX_PAGE_SIZE = 100;

export const SEARCH_DEBOUNCE_MS = 350;
