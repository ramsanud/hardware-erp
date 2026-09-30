import { Component, type ErrorInfo, type ReactNode } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Bug, Copy, Home, RefreshCw } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { APP_VERSION } from '@/shared/constants';

interface CrashScreenProps {
  error: Error;
  /** Clears the boundary so the subtree renders again without a full reload. */
  onReset: () => void;
  /** `page` fills the viewport (the root boundary); `section` sits inside the app shell. */
  variant: 'page' | 'section';
}

/**
 * What the boundary shows. Deliberately plain: no stack on screen, no
 * invented reference number. The details it copies are the real ones -
 * message, stack, URL, version, time - which is what a support ticket needs
 * and what the browser console already has.
 */
export function CrashScreen({ error, onReset, variant }: CrashScreenProps) {
  const details = [
    `Hardware ERP ${APP_VERSION}`,
    `When: ${new Date().toISOString()}`,
    `Where: ${window.location.href}`,
    `Error: ${error.name}: ${error.message}`,
    error.stack ?? '',
  ].join('\n');

  const copy = () => {
    // Clipboard access can be refused (http origin, permissions); the copy is
    // a convenience and the console still has everything.
    void navigator.clipboard?.writeText(details).catch(() => undefined);
  };

  return (
    <div
      role="alert"
      data-crash-screen={variant}
      className={variant === 'page'
        ? 'flex min-h-dvh items-center justify-center bg-background px-4 text-foreground'
        : 'flex items-center justify-center px-4 py-16'}
    >
      <div className="flex max-w-md flex-col items-center gap-4 text-center">
        <div className="rounded-full bg-destructive/10 p-3">
          <Bug className="h-6 w-6 text-destructive" aria-hidden />
        </div>
        <div className="space-y-1.5">
          <h1 className="text-lg font-semibold">Something went wrong</h1>
          <p className="text-sm text-muted-foreground">
            This part of the app hit an error it could not recover from. Nothing you saved before
            this is affected. Try again, or go back to the dashboard.
          </p>
        </div>
        <div className="flex flex-wrap items-center justify-center gap-2">
          <Button onClick={onReset}>
            <RefreshCw className="h-4 w-4" />
            Try again
          </Button>
          <Button variant="outline" asChild>
            <Link to="/dashboard" onClick={onReset}>
              <Home className="h-4 w-4" />
              Dashboard
            </Link>
          </Button>
          <Button variant="ghost" size="sm" onClick={copy}>
            <Copy className="h-4 w-4" />
            Copy details
          </Button>
        </div>
        <p className="font-mono text-xs text-muted-foreground">{error.name}: {error.message}</p>
      </div>
    </div>
  );
}

interface ErrorBoundaryProps {
  children: ReactNode;
  variant?: 'page' | 'section';
  /** When this changes the boundary forgets the last error - a route change is a fresh start. */
  resetKey?: string;
}

interface ErrorBoundaryState {
  error: Error | null;
}

/**
 * CR-100. Catches a render error so it does not white-screen the whole app.
 *
 * React unmounts the entire tree on an uncaught render error. For a
 * single-page ERP that means one bad row in one list takes the sidebar, the
 * header and the user's place with it, and the only recovery is a hard
 * reload. Two of these are mounted: one around the routes (the last line)
 * and one around the app shell's outlet, so a page can fail while the
 * navigation stays usable.
 *
 * A class because React has no hook for componentDidCatch. It does not
 * catch errors in event handlers or async code - those go through the API
 * layer's ApiError and the toaster, which is the right place for them.
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    // The console is the log this app has on the client. The request id on
    // API errors ties server-side failures to the server log; render errors
    // have no such id, so the component stack is the trail.
    console.error('Render error caught by ErrorBoundary', error, info.componentStack);
  }

  componentDidUpdate(prev: ErrorBoundaryProps) {
    if (this.state.error && prev.resetKey !== this.props.resetKey) {
      this.setState({ error: null });
    }
  }

  reset = () => this.setState({ error: null });

  render() {
    if (this.state.error) {
      return <CrashScreen error={this.state.error} onReset={this.reset} variant={this.props.variant ?? 'section'} />;
    }
    return this.props.children;
  }
}

/** The shell's boundary: forgets the error whenever the route changes. */
export function RouteErrorBoundary({ children }: { children: ReactNode }) {
  const { pathname } = useLocation();
  return <ErrorBoundary variant="section" resetKey={pathname}>{children}</ErrorBoundary>;
}
