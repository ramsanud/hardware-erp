export const APP_NAME = 'Hardware ERP';
export const APP_TAGLINE = 'Smart business management for hardware shops';
/**
 * Shown on the sign-in panel. Customer-facing, so it carries the product
 * version - never internal build or module terminology.
 */
export const APP_VERSION = '1.0.0';

export const PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const;
export const DEFAULT_PAGE_SIZE = 20;
/** Backend clamps to 100 (UserController.MAX_PAGE_SIZE). Do not exceed it. */
export const MAX_PAGE_SIZE = 100;

export const SEARCH_DEBOUNCE_MS = 350;
