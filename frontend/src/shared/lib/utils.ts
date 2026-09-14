import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

/** Saves a Blob response (e.g. a PDF) to the user's downloads without navigating away. */
export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

/**
 * Opens a Blob response (e.g. a PDF) in a new tab for viewing, without
 * triggering a download - the tab needs a moment to actually load the
 * bytes, so the object URL is only revoked after a delay rather than
 * immediately the way downloadBlob's is.
 */
export function previewBlob(blob: Blob): void {
  const url = URL.createObjectURL(blob);
  window.open(url, '_blank', 'noopener,noreferrer');
  setTimeout(() => URL.revokeObjectURL(url), 30000);
}

const SHORT_MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/**
 * 2026-09-14 (an ISO date, no time) -> 14 Sep 2026. Parsed as a calendar date,
 * so no timezone can shift it a day. The month names are a table rather than
 * Intl.DateTimeFormat because en-IN and en-GB have printed "Sept" since CLDR
 * 42 - the brief, the CSV and the tests all say "Sep", and a formatter whose
 * output depends on the browser's ICU build cannot be asserted against.
 */
export function formatDate(value?: string | null): string {
  if (!value) return '—';
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value);
  if (!match) return '—';
  const month = Number(match[2]);
  if (month < 1 || month > 12) return '—';
  return `${match[3]} ${SHORT_MONTHS[month - 1]} ${match[1]}`;
}

/** 2026-08-13T09:14:22.331 -> 13 Aug 2026, 09:14 */
export function formatDateTime(value?: string | null): string {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat('en-IN', {
    day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(date);
}

export function formatRelative(value?: string | null): string {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  const seconds = Math.round((date.getTime() - Date.now()) / 1000);
  const units: [Intl.RelativeTimeFormatUnit, number][] = [
    ['year', 31536000], ['month', 2592000], ['day', 86400],
    ['hour', 3600], ['minute', 60], ['second', 1],
  ];
  const formatter = new Intl.RelativeTimeFormat('en-IN', { numeric: 'auto' });
  for (const [unit, secondsInUnit] of units) {
    if (Math.abs(seconds) >= secondsInUnit || unit === 'second') {
      return formatter.format(Math.round(seconds / secondsInUnit), unit);
    }
  }
  return '—';
}

export function initials(fullName?: string | null): string {
  if (!fullName) return '?';
  return fullName.trim().split(/\s+/).slice(0, 2).map((p) => p[0]?.toUpperCase() ?? '').join('');
}
