import { INDIAN_STATES } from '@/shared/data/indianStates';

/**
 * CR-076. Turns a point on the map into the five address columns every
 * address-bearing table already has (customer, supplier, tenant), so the map
 * picker fills the same fields a person would type - no new columns, no
 * coordinates stored. Uses OpenStreetMap's Nominatim service, which needs no
 * API key; a self-hosted deployment behind a firewall can point
 * VITE_GEOCODER_URL at its own instance.
 *
 * Nominatim's usage policy allows at most one request per second and forbids
 * per-keystroke autocomplete, so searches are only fired on an explicit
 * submit, and every caller aborts the previous request before starting the
 * next one.
 */

export interface GeoPoint {
  lat: number;
  lng: number;
}

export interface PickedAddress {
  addressLine1: string;
  addressLine2: string;
  city: string;
  /** 2-digit GST state code, or '' when the state could not be matched. */
  stateCode: string;
  pincode: string;
  /** Nominatim's full display line, for the preview and single-line callers. */
  displayName: string;
  point: GeoPoint;
}

interface NominatimAddress {
  house_number?: string;
  house_name?: string;
  building?: string;
  amenity?: string;
  shop?: string;
  office?: string;
  road?: string;
  neighbourhood?: string;
  hamlet?: string;
  quarter?: string;
  suburb?: string;
  locality?: string;
  village?: string;
  town?: string;
  city?: string;
  municipality?: string;
  county?: string;
  state_district?: string;
  state?: string;
  postcode?: string;
  country_code?: string;
  'ISO3166-2-lvl4'?: string;
}

interface NominatimResult {
  lat: string;
  lon: string;
  name?: string;
  display_name: string;
  address?: NominatimAddress;
}

const GEOCODER_URL = (import.meta.env.VITE_GEOCODER_URL?.trim() || 'https://nominatim.openstreetmap.org')
  .replace(/\/+$/, '');

/**
 * ISO 3166-2:IN → GST state code. Nominatim reports the ISO code alongside
 * the state name and it is the more stable of the two (spelling and
 * capitalisation of `state` vary by mapper). Both old and new ISO codes are
 * listed where the standard was revised (UT→UK, OR→OD, CT→CG, TG→TS), because
 * the data carries whichever one the last editor used.
 */
const ISO_TO_GST: Record<string, string> = {
  JK: '01', HP: '02', PB: '03', CH: '04', UT: '05', UK: '05', HR: '06', DL: '07', RJ: '08',
  UP: '09', BR: '10', SK: '11', AR: '12', NL: '13', MN: '14', MZ: '15', TR: '16', ML: '17',
  AS: '18', WB: '19', JH: '20', OR: '21', OD: '21', CT: '22', CG: '22', MP: '23', GJ: '24',
  DH: '26', DD: '26', DN: '26', MH: '27', KA: '29', GA: '30', LD: '31', KL: '32', TN: '33',
  PY: '34', AN: '35', TG: '36', TS: '36', AP: '37', LA: '38',
};

/** Older or colloquial names that still appear in map data. */
const STATE_NAME_ALIASES: Record<string, string> = {
  orissa: 'Odisha',
  pondicherry: 'Puducherry',
  uttaranchal: 'Uttarakhand',
  'nct of delhi': 'Delhi',
  'national capital territory of delhi': 'Delhi',
  'chhatisgarh': 'Chhattisgarh',
  'dadra and nagar haveli': 'Dadra and Nagar Haveli and Daman and Diu',
  'daman and diu': 'Dadra and Nagar Haveli and Daman and Diu',
};

function normalise(value: string): string {
  return value.trim().toLowerCase().replace(/\s+/g, ' ');
}

export function stateCodeFor(address: NominatimAddress): string {
  const iso = address['ISO3166-2-lvl4']?.toUpperCase();
  if (iso?.startsWith('IN-')) {
    const code = ISO_TO_GST[iso.slice(3)];
    if (code) return code;
  }
  if (!address.state) return '';
  const wanted = normalise(STATE_NAME_ALIASES[normalise(address.state)] ?? address.state);
  return INDIAN_STATES.find((s) => normalise(s.name) === wanted)?.code ?? '';
}

/**
 * Indian cities are frequently mapped under the name of their civic body -
 * "Chennai Corporation", "Greater Hyderabad Municipal Corporation" - and
 * localities under an administrative label ("Ward 132", "Zone 10
 * Kodambakkam"). Neither belongs on an invoice, so the civic suffix and the
 * ward/zone prefix are dropped.
 */
const CIVIC_BODY_SUFFIX = /\s+(municipal corporation|city corporation|corporation|municipality|nagar nigam|mahanagar palika|nagar palika|nagar panchayat)$/i;
const ADMIN_LABEL_PREFIX = /^(ward|zone|circle)\s*(no\.?\s*)?\d+[a-z]?\s*/i;

function cityName(raw: string): string {
  return raw.replace(CIVIC_BODY_SUFFIX, '').trim();
}

function locality(raw: string | undefined): string | undefined {
  const cleaned = raw?.replace(ADMIN_LABEL_PREFIX, '').trim();
  return cleaned || undefined;
}

function unique(parts: Array<string | undefined>): string[] {
  const seen = new Set<string>();
  return parts.filter((p): p is string => {
    if (!p) return false;
    const key = normalise(p);
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

export function toPickedAddress(result: NominatimResult): PickedAddress {
  const a = result.address ?? {};
  const city = cityName(a.city ?? a.town ?? a.municipality ?? a.village ?? a.county ?? a.state_district ?? '');

  // Line 1 is the building and street; line 2 the locality. A place with no
  // street (a bare plot, a village) falls back to the feature's own name so
  // line 1 is never empty when something was found.
  const line1 = unique([
    a.house_number, a.house_name ?? a.building ?? a.amenity ?? a.shop ?? a.office, a.road,
  ]).join(', ') || result.name || '';
  const line2 = unique([locality(a.neighbourhood ?? a.hamlet ?? a.quarter), locality(a.suburb ?? a.locality ?? a.village)])
    .filter((p) => normalise(p) !== normalise(city) && normalise(p) !== normalise(line1))
    .join(', ');

  // Nominatim occasionally returns a spaced ("600 001") or partial postcode;
  // only a clean 6-digit PIN is worth putting into a field validated as one.
  const digits = (a.postcode ?? '').replace(/\s+/g, '');
  const pincode = /^\d{6}$/.test(digits) ? digits : '';

  return {
    addressLine1: line1.slice(0, 255),
    addressLine2: line2.slice(0, 255),
    city: city.slice(0, 100),
    stateCode: stateCodeFor(a),
    pincode,
    displayName: result.display_name,
    point: { lat: Number(result.lat), lng: Number(result.lon) },
  };
}

/**
 * The form fields a pick writes to, in every form that has them. Callers
 * skip the blanks: the geocoder leaving pincode empty means "unknown here",
 * not "this place has none", so a value already typed survives.
 */
export const ADDRESS_FIELD_KEYS = ['addressLine1', 'addressLine2', 'city', 'stateCode', 'pincode'] as const;
export type AddressFieldKey = typeof ADDRESS_FIELD_KEYS[number];

/** One line for callers with a single free-text address field (Project site address). */
export function toSingleLine(address: PickedAddress): string {
  const stateName = INDIAN_STATES.find((s) => s.code === address.stateCode)?.name;
  return unique([address.addressLine1, address.addressLine2, address.city, stateName, address.pincode])
    .join(', ');
}

async function call(path: string, params: Record<string, string>, signal?: AbortSignal): Promise<unknown> {
  const query = new URLSearchParams({ format: 'jsonv2', addressdetails: '1', ...params });
  const response = await fetch(`${GEOCODER_URL}${path}?${query}`, {
    signal,
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new Error(`Geocoder answered ${response.status}`);
  }
  return response.json();
}

export async function reverseGeocode(point: GeoPoint, signal?: AbortSignal): Promise<PickedAddress | null> {
  const result = await call('/reverse', {
    lat: String(point.lat), lon: String(point.lng), zoom: '18',
  }, signal) as NominatimResult & { error?: unknown };
  // Nominatim reports "nothing here" (open sea, for instance) as 200 + error.
  if (!result || result.error || !result.address) return null;
  return toPickedAddress(result);
}

export async function searchPlaces(query: string, signal?: AbortSignal): Promise<PickedAddress[]> {
  const trimmed = query.trim();
  if (!trimmed) return [];
  const results = await call('/search', {
    q: trimmed, countrycodes: 'in', limit: '6',
  }, signal) as NominatimResult[];
  return results.map(toPickedAddress);
}
