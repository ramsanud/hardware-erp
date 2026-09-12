/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  /** CR-076. Nominatim-compatible geocoder; defaults to the public OpenStreetMap instance. */
  readonly VITE_GEOCODER_URL?: string;
  /** CR-076. Leaflet tile URL template; defaults to OpenStreetMap tiles. */
  readonly VITE_MAP_TILE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
