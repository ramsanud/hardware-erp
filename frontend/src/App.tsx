import { BrowserRouter } from 'react-router-dom';
import { Toaster } from 'sonner';
import { ThemeProvider } from '@/theme/ThemeProvider';
import { ColorThemeProvider } from '@/theme/ColorThemeProvider';
import { DesignStyleProvider } from '@/theme/DesignStyleProvider';
import { AuthProvider } from '@/modules/auth/hooks/AuthProvider';
import { AppRoutes } from '@/routes';

/**
 * Provider order matters. ThemeProvider is outermost so the loading state
 * inside ProtectedRoute is already themed. ColorThemeProvider and
 * DesignStyleProvider both sit right inside it - each reads resolvedTheme
 * from ThemeProvider to pick its light-vs-dark token set (CR-033/CR-034).
 * AuthProvider sits inside BrowserRouter because its consumers navigate;
 * it also drives theme *scoping* (see theme/themeScope.ts) by telling
 * every theme provider which signed-in user's saved preferences to read -
 * which is why the theme providers sit outside AuthProvider (first paint
 * is never blocked on auth resolving) but still react to it via a
 * subscription rather than a prop.
 */
export default function App() {
  return (
    <ThemeProvider>
      <ColorThemeProvider>
        <DesignStyleProvider>
          <BrowserRouter>
            <AuthProvider>
              <AppRoutes />
              <Toaster
                position="top-right"
                richColors
                closeButton
                // Sonner reads the class on <html>, which ThemeProvider maintains.
                toastOptions={{ duration: 5000 }}
              />
            </AuthProvider>
          </BrowserRouter>
        </DesignStyleProvider>
      </ColorThemeProvider>
    </ThemeProvider>
  );
}
