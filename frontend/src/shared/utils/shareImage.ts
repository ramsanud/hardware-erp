import { downloadBlob } from '@/shared/lib/utils';

/*
  CR-101. Client-side DOM -> PNG capture for the "send me that as a picture"
  request - a rate card, a statement, an invoice - straight from what is on
  the screen, with no round trip. The server-side counterpart
  (DocumentImageRenderer, PDF -> PNG) is what the async job queue uses; this
  one exists for the immediate case where the person is looking at the
  thing and wants it in a WhatsApp chat now.

  html2canvas is loaded on demand: it is ~200 KB, and nothing on the
  sign-in page, the dashboard or a list view ever needs it. The dynamic
  import lands it in its own chunk, paid for only by the first share.
*/

export interface CaptureOptions {
  /** Device-pixel multiplier. 2 is crisp on a phone without a 10 MB file. */
  scale?: number;
  /** CSS colour behind transparent regions - tokens do not resolve inside the canvas, so pass the computed value. */
  backgroundColor?: string | null;
}

/** Renders one element to a PNG Blob. Throws if html2canvas cannot load or the element is detached. */
export async function captureElementAsPng(element: HTMLElement, options: CaptureOptions = {}): Promise<Blob> {
  if (!element.isConnected) {
    throw new Error('The element to capture is no longer on the page.');
  }
  const { default: html2canvas } = await import('html2canvas');
  const canvas = await html2canvas(element, {
    scale: options.scale ?? 2,
    backgroundColor: options.backgroundColor ?? resolveBackground(element),
    useCORS: true,
    logging: false,
  });
  return new Promise<Blob>((resolve, reject) => {
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('The browser could not encode the image.'))), 'image/png');
  });
}

/**
 * The element's own computed background, walking up until one is opaque -
 * so a dark-theme card captured on a phone is not pasted onto a white
 * rectangle. Falls back to the document's body colour.
 */
function resolveBackground(element: HTMLElement): string {
  let node: HTMLElement | null = element;
  while (node) {
    const colour = getComputedStyle(node).backgroundColor;
    if (colour && colour !== 'rgba(0, 0, 0, 0)' && colour !== 'transparent') return colour;
    node = node.parentElement;
  }
  return getComputedStyle(document.body).backgroundColor || '#ffffff';
}

export interface ShareImageOptions {
  fileName: string;
  title?: string;
  text?: string;
  /** Opened after a download when the browser cannot share files itself - typically a wa.me link (CR-080). */
  fallbackUrl?: string;
}

export type ShareOutcome = 'shared' | 'downloaded' | 'cancelled';

/**
 * Hands the image to the device's own share sheet (WhatsApp included on a
 * phone) when the browser can share files; otherwise downloads it and,
 * when given one, opens the fallback link so the person can attach it by
 * hand. The same two-path rule InvoiceDetailPage.handleShareViaApp already
 * follows for a PDF - a URL cannot carry a file, so nothing here pretends
 * it attached one.
 */
export async function shareOrDownloadImage(blob: Blob, options: ShareImageOptions): Promise<ShareOutcome> {
  const file = new File([blob], options.fileName, { type: blob.type || 'image/png' });
  const shareData: ShareData = { files: [file], title: options.title, text: options.text };
  if (typeof navigator.canShare === 'function' && navigator.canShare(shareData)) {
    try {
      await navigator.share(shareData);
      return 'shared';
    } catch (caught) {
      if (caught instanceof DOMException && caught.name === 'AbortError') return 'cancelled';
      // Any other failure falls through to the download, which always works.
    }
  }
  downloadBlob(blob, options.fileName);
  if (options.fallbackUrl) {
    window.open(options.fallbackUrl, '_blank', 'noopener,noreferrer');
  }
  return 'downloaded';
}
