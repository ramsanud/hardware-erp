import path from 'node:path';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  build: {
    // Explicit, not left to Vite's default, because "no source maps in
    // production" is a policy and a reader should be able to confirm it here
    // rather than by knowing what the default happens to be (CR-045).
    //
    // A source map republishes the original TypeScript - component names,
    // comments, and the shape of every API call - to anyone who opens the
    // browser's network tab. That is not a catastrophe on its own: the
    // security boundary is the API, and minified code is not a security
    // control either. It is simply free reconnaissance with no upside for a
    // shop owner.
    //
    // This does not break production error reporting: stack traces still
    // arrive, and the X-Request-ID on every error response is what ties a
    // report to the server-side log, which is where the detail lives.
    // Development builds are unaffected - `vite dev` always serves maps.
    sourcemap: false,

    // A single 564 kB chunk was fine for one module and would not stay fine
    // for twelve. Vendor code changes far less often than application code,
    // so splitting it keeps it cached across releases.
    rollupOptions: {
      output: {
        manualChunks: {
          'vendor-react': ['react', 'react-dom', 'react-router-dom'],
          'vendor-forms': ['react-hook-form', '@hookform/resolvers', 'zod'],
          // Recharts is ~100kB gzipped and only the dashboard needs it, so it
          // gets its own chunk rather than inflating the main bundle.
          'vendor-charts': ['recharts'],
          'vendor-ui': [
            '@radix-ui/react-dialog',
            '@radix-ui/react-dropdown-menu',
            '@radix-ui/react-select',
            '@radix-ui/react-checkbox',
            '@radix-ui/react-tabs',
            '@radix-ui/react-label',
            '@radix-ui/react-separator',
            '@radix-ui/react-slot',
          ],
        },
      },
    },
  },
  server: {
    port: 5173,
    // Same-origin proxy in development. The refresh token cookie is
    // SameSite=Strict, so it is only sent when the API shares the origin.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
