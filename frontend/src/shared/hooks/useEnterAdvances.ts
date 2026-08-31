import type { KeyboardEvent } from 'react';

/**
 * Makes Enter behave as "Next" inside a multi-step wizard.
 *
 * The wizards have no <form> element - each step is validated in JS and the
 * final submit is an explicit button - so Enter did nothing at all. An owner
 * typing a mobile number and pressing Enter expects to move on, and instead
 * sat there.
 *
 * WHAT IT DELIBERATELY DOES NOT INTERCEPT, because each of these already has
 * a correct meaning for Enter and stealing it would be worse than doing
 * nothing:
 *
 *   textarea            Enter inserts a newline
 *   button / a          Enter activates the control itself
 *   open combobox       Enter picks the highlighted option (Radix Select,
 *                       and any input with aria-expanded="true" such as the
 *                       product and supplier search boxes)
 *   inside a popover    the same, for content Radix portals out of the tree
 *   already handled     a child called preventDefault first
 *   IME composition     Enter is committing a candidate, not submitting -
 *                       this matters for the Indic keyboards this app
 *                       explicitly supports
 */
export function enterAdvances(advance: () => void) {
  return (event: KeyboardEvent<HTMLElement>) => {
    if (event.key !== 'Enter' || event.defaultPrevented) return;
    // React exposes the composing flag on the native event.
    if ((event.nativeEvent as unknown as { isComposing?: boolean }).isComposing) return;

    const target = event.target as HTMLElement | null;
    if (!target) return;

    const tag = target.tagName.toLowerCase();
    if (tag === 'textarea' || tag === 'button' || tag === 'a' || tag === 'select') return;
    if (target.isContentEditable) return;
    if (target.getAttribute('aria-expanded') === 'true') return;
    if (target.getAttribute('role') === 'combobox') return;
    if (target.closest('[data-radix-popper-content-wrapper],[role="listbox"],[role="dialog"]')) return;

    event.preventDefault();
    advance();
  };
}
