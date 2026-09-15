/**
 * CR-087. GSTIN structure and Modulo-36 checksum - the same rule the server
 * applies (backend common/util/Gstin.java), so a typo is caught at the field
 * rather than on submit. Blank is valid: every GSTIN field is optional.
 */
const ALPHABET = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ';
export const GSTIN_STRUCTURE = /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][0-9A-Z]Z[0-9A-Z]$/;

export function gstinCheckCharacter(first14: string): string {
  let sum = 0;
  for (let i = 0; i < 14; i += 1) {
    const code = ALPHABET.indexOf(first14[i]);
    const product = code * (i % 2 === 0 ? 1 : 2);
    sum += Math.floor(product / 36) + (product % 36);
  }
  return ALPHABET[(36 - (sum % 36)) % 36];
}

export function isValidGstin(value: string): boolean {
  if (!GSTIN_STRUCTURE.test(value)) return false;
  return value[14] === gstinCheckCharacter(value.slice(0, 14));
}

export function isValidOrBlankGstin(value: string | null | undefined): boolean {
  return !value || value.trim() === '' || isValidGstin(value.trim());
}

export const GSTIN_MESSAGE = 'Enter a valid 15-character GSTIN';
