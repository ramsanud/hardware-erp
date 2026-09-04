/**
 * Loads Razorpay's official Checkout.js widget on demand - never eagerly on
 * page load, so a shop that hasn't clicked "Upgrade" makes no third-party
 * request at all (same principle as the Turnstile CAPTCHA widget).
 */
declare global {
  interface Window {
    Razorpay?: new (options: Record<string, unknown>) => { open: () => void };
  }
}

let loadPromise: Promise<void> | null = null;

export function loadRazorpayCheckout(): Promise<void> {
  if (window.Razorpay) return Promise.resolve();
  if (loadPromise) return loadPromise;

  loadPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = 'https://checkout.razorpay.com/v1/checkout.js';
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => {
      loadPromise = null;
      reject(new Error('Could not load the payment widget. Check your connection and try again.'));
    };
    document.body.appendChild(script);
  });
  return loadPromise;
}
