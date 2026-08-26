import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { KeyRound, Loader2 } from 'lucide-react';
import { Badge } from '@/shared/components/ui/badge';
import { Button } from '@/shared/components/ui/button';
import {
  Card, CardContent, CardHeader, CardTitle,
} from '@/shared/components/ui/card';
import {
  Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
import { Separator } from '@/shared/components/ui/separator';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/components/ui/tabs';
import { PageHeader } from '@/shared/components/PageHeader';
import { ImageUpload } from '@/shared/components/ImageUpload';
import { formatDateTime, initials } from '@/shared/lib/utils';
import { useAuthenticatedImage } from '@/shared/hooks/useAuthenticatedImage';
import { useAppChrome } from '@/layouts/AppChromeProvider';
import { AppearanceSettings } from '@/modules/settings/components/AppearanceSettings';
import { AUTH_ROUTES } from '../constants';
import { useAuth } from '../hooks/AuthProvider';
import { useToast } from '../hooks/useToast';
import { authService } from '../services/authService';
import { avatarService } from '../services/avatarService';
import { permissionService } from '../services/permissionService';
import { ProfileForm } from '../forms/ProfileForm';
import { ChangePasswordForm } from '../forms/ChangePasswordForm';
import { SessionList } from '../components/SessionList';
import { UserStatusBadge } from '../components/StatusBadge';
import type { ChangePasswordValues, ProfileValues } from '../validation/schemas';
import type { PermissionGroupResponse } from '../types';

/** Friendly headings for the raw module_code seeded in the database. */
const MODULE_LABELS: Record<string, string> = {
  AUTH: 'Authentication & Users',
  CUSTOMER: 'Customers',
  SUPPLIER: 'Suppliers',
  PRODUCT: 'Products',
  PURCHASE: 'Purchases',
  SALES: 'Sales & Invoices',
  INVENTORY: 'Inventory',
  PAYMENT: 'Payments',
  EXPENSE: 'Expenses',
  REPORT: 'Reports',
  SETTINGS: 'Settings',
};

export function ProfilePage() {
  const { user, refreshUser, logout } = useAuth();
  const [passwordDialogOpen, setPasswordDialogOpen] = useState(false);
  const [groups, setGroups] = useState<PermissionGroupResponse[] | null>(null);
  const navigate = useNavigate();
  const toast = useToast();

  useEffect(() => {
    permissionService.grouped().then(setGroups).catch(() => setGroups([]));
  }, []);

  const myGroups = useMemo(() => {
    if (!groups || !user) return [];
    const held = new Set(user.permissions);
    return groups
      .map((group) => ({
        ...group,
        permissions: group.permissions.filter((permission) => held.has(permission.code)),
      }))
      .filter((group) => group.permissions.length > 0);
  }, [groups, user]);

  const { avatarVersion, bumpAvatarVersion } = useAppChrome();
  const avatarSrc = useAuthenticatedImage(avatarService.url, avatarVersion);

  if (!user) return null;

  const handleProfileSave = async (values: ProfileValues) => {
    await authService.updateProfile({
      fullName: values.fullName,
      email: values.email ? values.email : null,
    });
    await refreshUser();
    toast.success('Profile updated.');
  };

  const handlePasswordChange = async (values: ChangePasswordValues) => {
    await authService.changePassword({
      currentPassword: values.currentPassword,
      newPassword: values.newPassword,
    });
    await logout();
    toast.success('Password changed. Please sign in again.');
    navigate(AUTH_ROUTES.login, { replace: true });
  };

  return (
    <>
      <PageHeader
        title="My profile"
        description="Your details, password and active sessions."
        actions={
          <Button variant="outline" onClick={() => setPasswordDialogOpen(true)}>
            Change password
          </Button>
        }
      />

      <div className="grid gap-5 lg:grid-cols-4">
        <Card className="lg:col-span-1">
          <CardHeader>
            <CardTitle className="text-base">Photo</CardTitle>
          </CardHeader>
          <CardContent>
            <ImageUpload
              src={avatarSrc}
              alt={user.fullName}
              fallback={<span className="text-lg font-semibold text-muted-foreground">{initials(user.fullName)}</span>}
              onUpload={async (file) => {
                await avatarService.upload(file);
                bumpAvatarVersion();
              }}
              onRemove={async () => {
                await avatarService.remove();
                bumpAvatarVersion();
              }}
            />
          </CardContent>
        </Card>

        <Card className="lg:col-span-1">
          <CardHeader>
            <CardTitle className="text-base">Account</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3 text-sm">
            <div className="flex items-center justify-between gap-3">
              <span className="text-muted-foreground">Role</span>
              <Badge variant="secondary">{user.roleName}</Badge>
            </div>
            <div className="flex items-center justify-between gap-3">
              <span className="text-muted-foreground">Status</span>
              <UserStatusBadge status={user.status} />
            </div>
            <div className="flex items-center justify-between gap-3">
              <span className="text-muted-foreground">Employee code</span>
              <span className="tabular">{user.employeeCode ?? '—'}</span>
            </div>
            <div className="flex items-center justify-between gap-3">
              <span className="text-muted-foreground">Last sign-in</span>
              <span className="tabular text-right">{formatDateTime(user.lastLoginAt)}</span>
            </div>
            <div className="flex items-center justify-between gap-3">
              <span className="text-muted-foreground">Permissions held</span>
              <Badge variant="secondary">{user.permissions.length}</Badge>
            </div>
          </CardContent>
        </Card>

        <Card className="lg:col-span-2">
          <CardContent className="pt-6">
            <Tabs defaultValue="details">
              <TabsList>
                <TabsTrigger value="details">Details</TabsTrigger>
                <TabsTrigger value="appearance">Appearance</TabsTrigger>
                <TabsTrigger value="permissions">Permissions</TabsTrigger>
                <TabsTrigger value="sessions">Sessions</TabsTrigger>
              </TabsList>

              <TabsContent value="details">
                <ProfileForm user={user} onSubmit={handleProfileSave} />
              </TabsContent>

              <TabsContent value="appearance">
                <AppearanceSettings />
              </TabsContent>

              <TabsContent value="permissions" className="space-y-4">
                {groups === null ? (
                  <div className="flex justify-center py-10">
                    <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" aria-label="Loading" />
                  </div>
                ) : myGroups.length === 0 ? (
                  <p className="py-6 text-center text-sm text-muted-foreground">
                    <KeyRound className="mx-auto mb-2 h-5 w-5" aria-hidden />
                    No permissions are assigned to your role.
                  </p>
                ) : (
                  myGroups.map((group, index) => (
                    <div key={group.moduleCode}>
                      {index > 0 ? <Separator className="mb-4" /> : null}
                      <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                        {MODULE_LABELS[group.moduleCode] ?? group.moduleCode}
                      </p>
                      <div className="flex flex-wrap gap-1.5">
                        {group.permissions.map((permission) => (
                          <Badge key={permission.code} variant="outline" title={permission.description ?? undefined}>
                            {permission.name}
                          </Badge>
                        ))}
                      </div>
                    </div>
                  ))
                )}
              </TabsContent>

              <TabsContent value="sessions">
                <p className="mb-2 text-sm text-muted-foreground">
                  Devices currently signed in to your account. If you do not recognise
                  one, sign it out and change your password.
                </p>
                <SessionList />
              </TabsContent>
            </Tabs>
          </CardContent>
        </Card>
      </div>

      <Dialog open={passwordDialogOpen} onOpenChange={setPasswordDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Change password</DialogTitle>
            <DialogDescription>
              You will be signed out everywhere and asked to sign in again.
            </DialogDescription>
          </DialogHeader>
          <ChangePasswordForm onSubmit={handlePasswordChange} />
        </DialogContent>
      </Dialog>
    </>
  );
}
