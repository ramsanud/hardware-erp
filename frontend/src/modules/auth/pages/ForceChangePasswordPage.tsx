import { useNavigate } from 'react-router-dom';
import { KeyRound } from 'lucide-react';
import {
  Card, CardContent, CardDescription, CardHeader, CardTitle,
} from '@/shared/components/ui/card';
import { AUTH_ROUTES } from '../constants';
import { useAuth } from '../hooks/AuthProvider';
import { authService } from '../services/authService';
import { ChangePasswordForm } from '../forms/ChangePasswordForm';
import { useToast } from '../hooks/useToast';
import type { ChangePasswordValues } from '../validation/schemas';

/**
 * Shown when the account carries mustChangePassword. ProtectedRoute redirects
 * every other route here, so a temporary password cannot be used to browse the
 * application.
 */
export function ForceChangePasswordPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();

  const handleSubmit = async (values: ChangePasswordValues) => {
    await authService.changePassword({
      currentPassword: values.currentPassword,
      newPassword: values.newPassword,
    });
    // The change revokes every session including this one, so there is nothing
    // to keep: clear local state and send the user back to sign in.
    await logout();
    toast.success('Password changed. Please sign in with your new password.');
    navigate(AUTH_ROUTES.login, { replace: true });
  };

  return (
    <div className="mx-auto flex min-h-dvh max-w-md items-center px-4">
      <Card className="w-full">
        <CardHeader>
          <div className="mb-2 flex h-10 w-10 items-center justify-center rounded-full bg-warning/10">
            <KeyRound className="h-5 w-5 text-warning" aria-hidden />
          </div>
          <CardTitle>Choose your own password</CardTitle>
          <CardDescription>
            {user?.fullName}, your account uses a temporary password set by the shop
            owner. Replace it before continuing.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <ChangePasswordForm onSubmit={handleSubmit} submitLabel="Set my password" />
        </CardContent>
      </Card>
    </div>
  );
}
