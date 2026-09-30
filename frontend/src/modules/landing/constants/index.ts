/** CR-100. The public front door. Signed-in users never see it - `/` sends them to the dashboard. */
export const LANDING_ROUTES = {
  home: '/',
} as const;
