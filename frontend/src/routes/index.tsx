import { Suspense, lazy } from 'react';
import { Navigate, Outlet, Route, Routes } from 'react-router-dom';
import { AuthLayout } from '@/layouts/AuthLayout';
import { AppLayout } from '@/layouts/AppLayout';
import { AUTH_ROUTES, PERMISSIONS } from '@/modules/auth/constants';
import { LoginPage } from '@/modules/auth/pages/LoginPage';
import { MfaEnrollPage } from '@/modules/auth/pages/MfaEnrollPage';
import { MfaVerifyPage } from '@/modules/auth/pages/MfaVerifyPage';
import { ForgotPasswordPage } from '@/modules/auth/pages/ForgotPasswordPage';
import { ResetPasswordPage } from '@/modules/auth/pages/ResetPasswordPage';
import { ForceChangePasswordPage } from '@/modules/auth/pages/ForceChangePasswordPage';
import { ProfilePage } from '@/modules/auth/pages/ProfilePage';
import { UserManagementPage } from '@/modules/auth/pages/UserManagementPage';
import { RoleManagementPage } from '@/modules/auth/pages/RoleManagementPage';
import { PermissionViewPage } from '@/modules/auth/pages/PermissionViewPage';
import { SecurityAuditLogPage } from '@/modules/auth/pages/SecurityAuditLogPage';
import { ActivityLogPage } from '@/modules/activity/pages/ActivityLogPage';
import { SUPPLIER_ROUTES } from '@/modules/supplier/constants';
import { SupplierListPage } from '@/modules/supplier/pages/SupplierListPage';
import { SupplierDetailPage } from '@/modules/supplier/pages/SupplierDetailPage';
import { SupplierCreatePage } from '@/modules/supplier/pages/SupplierCreatePage';
import { SupplierEditPage } from '@/modules/supplier/pages/SupplierEditPage';
import { PRODUCT_ROUTES } from '@/modules/product/constants';
import { CategoryListPage } from '@/modules/product/pages/CategoryListPage';
import { BrandListPage } from '@/modules/product/pages/BrandListPage';
import { ProductListPage } from '@/modules/product/pages/ProductListPage';
import { ProductDetailPage } from '@/modules/product/pages/ProductDetailPage';
import { INVOICE_ROUTES } from '@/modules/invoice/constants';
import { InvoiceListPage } from '@/modules/invoice/pages/InvoiceListPage';
import { InvoiceCreatePage } from '@/modules/invoice/pages/InvoiceCreatePage';
import { InvoiceDetailPage } from '@/modules/invoice/pages/InvoiceDetailPage';
import { INVENTORY_ROUTES } from '@/modules/inventory/constants';
import { StockListPage } from '@/modules/inventory/pages/StockListPage';
import { PURCHASE_ROUTES } from '@/modules/purchase/constants';
import { PurchaseListPage } from '@/modules/purchase/pages/PurchaseListPage';
import { PurchaseDetailPage } from '@/modules/purchase/pages/PurchaseDetailPage';
import { PurchaseCreatePage } from '@/modules/purchase/pages/PurchaseCreatePage';
import { DashboardPage } from '@/modules/dashboard/pages/DashboardPage';
import { ProfitReportPage } from '@/modules/report/pages/ProfitReportPage';
import { SyncPage } from '@/modules/sync/pages/SyncPage';
import { BranchesPage } from '@/modules/branch/pages/BranchesPage';
import { BRANCH_ROUTES } from '@/modules/branch/constants';
import { InsightsPage } from '@/modules/insights/pages/InsightsPage';
import { INSIGHTS_ROUTES } from '@/modules/insights/constants';
import { SYNC_ROUTES } from '@/modules/sync/constants';
import { SETTINGS_ROUTES } from '@/modules/settings/constants';
import { ShopSettingsPage } from '@/modules/settings/pages/ShopSettingsPage';
import { WhatsAppSettingsPage } from '@/modules/settings/pages/WhatsAppSettingsPage';
import { NotificationHistoryPage } from '@/modules/notification/pages/NotificationHistoryPage';
import { AppearancePage } from '@/modules/settings/pages/AppearancePage';
import { SUBSCRIPTION_ROUTES } from '@/modules/subscription/constants';
import { SubscriptionPage } from '@/modules/subscription/pages/SubscriptionPage';
import { SUBSTITUTE_ROUTES } from '@/modules/substitute/constants';
import { ProductRequestListPage } from '@/modules/substitute/pages/ProductRequestListPage';
import { ProductRequestDetailPage } from '@/modules/substitute/pages/ProductRequestDetailPage';
import { DISCOVERY_ROUTES } from '@/modules/discovery/constants';
import { NotificationsPage } from '@/modules/discovery/pages/NotificationsPage';
import { QUOTATION_ROUTES } from '@/modules/quotation/constants';
import { QuotationListPage } from '@/modules/quotation/pages/QuotationListPage';
import { QuotationDetailPage } from '@/modules/quotation/pages/QuotationDetailPage';
import { QuotationCreatePage } from '@/modules/quotation/pages/QuotationCreatePage';
import { CUSTOMER_ROUTES } from '@/modules/customer/constants';
import { CustomerListPage } from '@/modules/customer/pages/CustomerListPage';
import { CustomerDetailPage } from '@/modules/customer/pages/CustomerDetailPage';
import { PAYMENT_ROUTES } from '@/modules/payment/constants';
import { PaymentListPage } from '@/modules/payment/pages/PaymentListPage';
import { COUPON_ROUTES } from '@/modules/coupon/constants';
import { CouponListPage } from '@/modules/coupon/pages/CouponListPage';
import { EXPENSE_ROUTES } from '@/modules/expense/constants';
import { ExpenseListPage } from '@/modules/expense/pages/ExpenseListPage';
import { PROJECT_ROUTES } from '@/modules/project/constants';
import { ProjectListPage } from '@/modules/project/pages/ProjectListPage';
import { ProjectDetailPage } from '@/modules/project/pages/ProjectDetailPage';
import { ProjectCreatePage } from '@/modules/project/pages/ProjectCreatePage';
import { ProjectEditPage } from '@/modules/project/pages/ProjectEditPage';
import { LABOUR_ROUTES } from '@/modules/labour/constants';
import { WorkerListPage } from '@/modules/labour/pages/WorkerListPage';
import { WorkerDetailPage } from '@/modules/labour/pages/WorkerDetailPage';
import { AttendancePage } from '@/modules/labour/pages/AttendancePage';
import { RegisterPage } from '@/modules/tenant/pages/RegisterPage';
import { DEVELOPER_ROUTES } from '@/modules/developer/constants';
import { DeveloperInspectionPage } from '@/modules/developer/pages/DeveloperInspectionPage';
import { TOOLS_ROUTES } from '@/modules/tools/constants';
import { GstCalculatorPage } from '@/modules/tools/pages/GstCalculatorPage';
import { TallyExportPage } from '@/modules/tools/pages/TallyExportPage';
import { REPORT_ROUTES } from '@/modules/report/constants';
import { ReportsPage } from '@/modules/report/pages/ReportsPage';
import { NotFoundPage } from '@/shared/components/NotFoundPage';
import { LoadingState } from '@/shared/components/LoadingState';
import { LANDING_ROUTES } from '@/modules/landing/constants';
import { useAuth } from '@/modules/auth/hooks/AuthProvider';
import { PlatformAdminAuthProvider } from '@/modules/platform-admin/hooks/PlatformAdminAuthProvider';
import { PlatformAdminLoginPage } from '@/modules/platform-admin/pages/PlatformAdminLoginPage';
import { PlatformAdminMfaVerifyPage } from '@/modules/platform-admin/pages/PlatformAdminMfaVerifyPage';
import { PlatformAdminMfaEnrollPage } from '@/modules/platform-admin/pages/PlatformAdminMfaEnrollPage';
import { PlatformAdminDashboardPage } from '@/modules/platform-admin/pages/PlatformAdminDashboardPage';
import { PlatformAdminTenantListPage } from '@/modules/platform-admin/pages/PlatformAdminTenantListPage';
import { PlatformAdminTenantDetailPage } from '@/modules/platform-admin/pages/PlatformAdminTenantDetailPage';
import { PlatformAdminSystemHealthPage } from '@/modules/platform-admin/pages/PlatformAdminSystemHealthPage';
import { PlatformAdminIncidentsPage } from '@/modules/platform-admin/pages/PlatformAdminIncidentsPage';
import { PlatformAdminSupportListPage } from '@/modules/platform-admin/pages/PlatformAdminSupportListPage';
import { PlatformAdminSupportDetailPage } from '@/modules/platform-admin/pages/PlatformAdminSupportDetailPage';
import { PlatformAdminAuditLogPage } from '@/modules/platform-admin/pages/PlatformAdminAuditLogPage';
import { PlatformAdminDeveloperToolsPage } from '@/modules/platform-admin/pages/PlatformAdminDeveloperToolsPage';
import { PlatformAdminSecurityPage } from '@/modules/platform-admin/pages/PlatformAdminSecurityPage';
import { PlatformAdminFeatureFlagsPage } from '@/modules/platform-admin/pages/PlatformAdminFeatureFlagsPage';
import { PlatformAdminAnalyticsPage } from '@/modules/platform-admin/pages/PlatformAdminAnalyticsPage';
import { PlatformAdminSettingsPage } from '@/modules/platform-admin/pages/PlatformAdminSettingsPage';
import { SUPPORT_ROUTES } from '@/modules/support/constants';
import { SupportTicketListPage } from '@/modules/support/pages/SupportTicketListPage';
import { SupportTicketDetailPage } from '@/modules/support/pages/SupportTicketDetailPage';
import { PlatformAdminLayout } from '@/modules/platform-admin/layouts/PlatformAdminLayout';
import { PlatformAdminProtectedRoute } from '@/modules/platform-admin/routes/PlatformAdminProtectedRoute';
import { ProtectedRoute } from './ProtectedRoute';
import { RequirePermission } from './RequirePermission';

/*
 * CR-100. The landing page is the only lazy route: it pulls in `motion` for
 * the integrations visual, and a signed-in user opening the app should not
 * pay for a page they are redirected away from.
 */
const LandingPage = lazy(() => import('@/modules/landing/pages/LandingPage'));

/**
 * CR-100. `/` is the front door for a visitor and the dashboard for a user.
 * Waiting on `initialising` matters: the startup refresh is in flight for a
 * moment after a reload, and rendering the landing page in that gap would
 * flash marketing at someone who is already signed in.
 */
function LandingRoute() {
  const { isAuthenticated, initialising } = useAuth();
  if (initialising) return <LoadingState variant="page" />;
  if (isAuthenticated) return <Navigate to="/dashboard" replace />;
  return (
    <Suspense fallback={<LoadingState variant="page" />}>
      <LandingPage />
    </Suspense>
  );
}

/**
 * Routes are declared per module. Modules 2-12 add their own block here and
 * nothing else in this file changes.
 */
export function AppRoutes() {
  return (
    <Routes>
      {/* Public front door (CR-100). Above ProtectedRoute on purpose. */}
      <Route path={LANDING_ROUTES.home} element={<LandingRoute />} />

      {/*
        Platform Admin Console (CR-054) - deliberately outside AuthLayout/
        AppLayout and the tenant AuthProvider entirely. Its own provider
        wraps just this subtree, so a tenant session and a platform-admin
        session never share state even if both are open in the same browser.
      */}
      <Route
        path="/platform-admin/*"
        element={<PlatformAdminAuthProvider><Outlet /></PlatformAdminAuthProvider>}
      >
        <Route path="login" element={<PlatformAdminLoginPage />} />
        <Route path="mfa" element={<PlatformAdminMfaVerifyPage />} />
        <Route path="enroll" element={<PlatformAdminMfaEnrollPage />} />
        <Route element={<PlatformAdminProtectedRoute />}>
          <Route element={<PlatformAdminLayout />}>
            <Route path="dashboard" element={<PlatformAdminDashboardPage />} />
            <Route path="tenants" element={<PlatformAdminTenantListPage />} />
            <Route path="tenants/:id" element={<PlatformAdminTenantDetailPage />} />
            <Route path="system-health" element={<PlatformAdminSystemHealthPage />} />
            <Route path="incidents" element={<PlatformAdminIncidentsPage />} />
            <Route path="support" element={<PlatformAdminSupportListPage />} />
            <Route path="support/:id" element={<PlatformAdminSupportDetailPage />} />
            <Route path="audit-logs" element={<PlatformAdminAuditLogPage />} />
            <Route path="developer-tools" element={<PlatformAdminDeveloperToolsPage />} />
            <Route path="security" element={<PlatformAdminSecurityPage />} />
            <Route path="feature-flags" element={<PlatformAdminFeatureFlagsPage />} />
            <Route path="analytics" element={<PlatformAdminAnalyticsPage />} />
            <Route path="settings" element={<PlatformAdminSettingsPage />} />
          </Route>
        </Route>
        <Route index element={<Navigate to="login" replace />} />
      </Route>

      {/* Public */}
      <Route element={<AuthLayout />}>
        <Route path={AUTH_ROUTES.login} element={<LoginPage />} />
        {/*
          CR-058 - the second factor. Public on purpose: a correct password
          clears only the first factor, so at this point there is no session
          yet, just a short-lived MFA challenge token. Putting these behind
          ProtectedRoute would be a deadlock - the user cannot obtain the
          session the guard demands without first passing through here.

          These two were written but never registered, which made MFA
          mandatory *and* unreachable: LoginPage navigates to
          AUTH_ROUTES.mfaEnroll/mfaVerify, React Router matched nothing, and
          every sign-in on a fresh database dead-ended on "page not found".
          The platform-admin equivalents above were wired up; these were the
          pair that got missed.
        */}
        <Route path={AUTH_ROUTES.mfaEnroll} element={<MfaEnrollPage />} />
        <Route path={AUTH_ROUTES.mfaVerify} element={<MfaVerifyPage />} />
        <Route path={AUTH_ROUTES.register} element={<RegisterPage />} />
        <Route path={AUTH_ROUTES.forgotPassword} element={<ForgotPasswordPage />} />
        <Route path={AUTH_ROUTES.resetPassword} element={<ResetPasswordPage />} />
      </Route>

      {/* Authenticated */}
      <Route element={<ProtectedRoute />}>
        {/*
          Outside AppLayout on purpose: a user with a temporary password must
          not see the navigation, because every other route redirects here
          until the password is replaced.
        */}
        <Route path={AUTH_ROUTES.forceChangePassword} element={<ForceChangePasswordPage />} />

        <Route element={<AppLayout />}>
          <Route path={AUTH_ROUTES.profile} element={<ProfilePage />} />
          <Route path={AUTH_ROUTES.appearance} element={<AppearancePage />} />
          <Route path={SUPPORT_ROUTES.list} element={<SupportTicketListPage />} />
          <Route path="/support/:id" element={<SupportTicketDetailPage />} />

          <Route element={<RequirePermission permission={PERMISSIONS.USER_VIEW} />}>
            <Route path={AUTH_ROUTES.users} element={<UserManagementPage />} />
          </Route>

          <Route element={<RequirePermission permission={PERMISSIONS.ROLE_VIEW} />}>
            <Route path={AUTH_ROUTES.roles} element={<RoleManagementPage />} />
            <Route path={AUTH_ROUTES.permissions} element={<PermissionViewPage />} />
          </Route>

          <Route element={<RequirePermission permission={PERMISSIONS.AUDIT_VIEW} />}>
            <Route path={AUTH_ROUTES.auditLog} element={<SecurityAuditLogPage />} />
            {/* CR-072. Same permission as the security log: both answer "who
                changed what", and needing two grants to answer one question
                would be an odd thing to ask an owner for. */}
            <Route path={AUTH_ROUTES.activityLog} element={<ActivityLogPage />} />
          </Route>

          <Route element={<RequirePermission permission={PERMISSIONS.SUPPLIER_VIEW} />}>
            <Route path={SUPPLIER_ROUTES.list} element={<SupplierListPage />} />
            <Route path="/suppliers/:id" element={<SupplierDetailPage />} />
          </Route>
          <Route element={<RequirePermission permission={PERMISSIONS.SUPPLIER_MANAGE} />}>
            <Route path={SUPPLIER_ROUTES.create} element={<SupplierCreatePage />} />
            <Route path="/suppliers/:id/edit" element={<SupplierEditPage />} />
          </Route>

          <Route element={<RequirePermission permission={PERMISSIONS.PRODUCT_VIEW} />}>
            <Route path={PRODUCT_ROUTES.categories} element={<CategoryListPage />} />
            <Route path={PRODUCT_ROUTES.brands} element={<BrandListPage />} />
            <Route path={PRODUCT_ROUTES.list} element={<ProductListPage />} />
            <Route path="/products/:id" element={<ProductDetailPage />} />
          </Route>

          {/* CR-089. Permission gate here is UX only - the plan gate
              (Premium) is enforced server-side and surfaces as the
              upgrade dialog on the page itself. */}
          <Route element={<RequirePermission permission={PERMISSIONS.PRODUCT_REQUEST_VIEW} />}>
            <Route path={SUBSTITUTE_ROUTES.list} element={<ProductRequestListPage />} />
            <Route path="/product-requests/:id" element={<ProductRequestDetailPage />} />
          </Route>

          <Route element={<RequirePermission permission={PERMISSIONS.INVOICE_VIEW} />}>
            <Route path={INVOICE_ROUTES.list} element={<InvoiceListPage />} />
            <Route path="/invoices/:id" element={<InvoiceDetailPage />} />
          </Route>
          <Route element={<RequirePermission permission={PERMISSIONS.INVOICE_CREATE} />}>
            <Route path={INVOICE_ROUTES.create} element={<InvoiceCreatePage />} />
            {/* CR-091 Phase 9 - syncing an offline invoice is exactly the authority to create one. */}
            <Route path={SYNC_ROUTES.outbox} element={<SyncPage />} />
          </Route>

          <Route element={<RequirePermission permission={PERMISSIONS.INVENTORY_VIEW} />}>
            <Route path={INVENTORY_ROUTES.stock} element={<StockListPage />} />
          </Route>

          <Route element={<RequirePermission permission={PERMISSIONS.PURCHASE_VIEW} />}>
            <Route path={PURCHASE_ROUTES.list} element={<PurchaseListPage />} />
            <Route path="/purchases/:id" element={<PurchaseDetailPage />} />
          </Route>
          <Route element={<RequirePermission permission={PERMISSIONS.PURCHASE_MANAGE} />}>
            <Route path={PURCHASE_ROUTES.create} element={<PurchaseCreatePage />} />
          </Route>

          <Route element={<RequirePermission permission={PERMISSIONS.CUSTOMER_VIEW} />}>
            <Route path={CUSTOMER_ROUTES.list} element={<CustomerListPage />} />
            <Route path="/customers/:id" element={<CustomerDetailPage />} />
          </Route>

          <Route element={<RequirePermission permission={PERMISSIONS.PAYMENT_VIEW} />}>
            <Route path={PAYMENT_ROUTES.list} element={<PaymentListPage />} />
          </Route>

          <Route element={<RequirePermission permission={PERMISSIONS.COUPON_VIEW} />}>
            <Route path={COUPON_ROUTES.list} element={<CouponListPage />} />
          </Route>

          <Route element={<RequirePermission permission={PERMISSIONS.EXPENSE_VIEW} />}>
            <Route path={EXPENSE_ROUTES.list} element={<ExpenseListPage />} />
          </Route>

          <Route element={<RequirePermission permission={PERMISSIONS.QUOTATION_VIEW} />}>
            <Route path={QUOTATION_ROUTES.list} element={<QuotationListPage />} />
            <Route path="/quotations/:id" element={<QuotationDetailPage />} />
          </Route>
          <Route element={<RequirePermission permission={PERMISSIONS.QUOTATION_MANAGE} />}>
            <Route path={QUOTATION_ROUTES.create} element={<QuotationCreatePage />} />
          </Route>

          <Route element={<RequirePermission permission={PERMISSIONS.PROJECT_VIEW} />}>
            <Route path={PROJECT_ROUTES.list} element={<ProjectListPage />} />
            <Route path="/projects/:id" element={<ProjectDetailPage />} />
          </Route>
          <Route element={<RequirePermission permission={PERMISSIONS.PROJECT_MANAGE} />}>
            <Route path={PROJECT_ROUTES.create} element={<ProjectCreatePage />} />
            <Route path="/projects/:id/edit" element={<ProjectEditPage />} />
          </Route>

          <Route element={<RequirePermission permission={PERMISSIONS.LABOUR_VIEW} />}>
            <Route path={LABOUR_ROUTES.workers} element={<WorkerListPage />} />
            <Route path="/labour/workers/:id" element={<WorkerDetailPage />} />
            <Route path={LABOUR_ROUTES.attendance} element={<AttendancePage />} />
          </Route>

          <Route element={<RequirePermission permission={PERMISSIONS.SETTINGS_VIEW} />}>
            <Route path={SETTINGS_ROUTES.shop} element={<ShopSettingsPage />} />
            <Route path={SETTINGS_ROUTES.whatsapp} element={<WhatsAppSettingsPage />} />
            <Route path={SETTINGS_ROUTES.whatsappHistory} element={<NotificationHistoryPage />} />
            <Route path={SUBSCRIPTION_ROUTES.pricing} element={<SubscriptionPage />} />
          </Route>

          {/* Developer inspection (CR-045). This gate is convenience only:
              the endpoints behind the page refuse anyone without
              DEVELOPER_INSPECT, and refuse everyone in production. */}
          <Route element={<RequirePermission permission={PERMISSIONS.DEVELOPER_INSPECT} />}>
            <Route path={DEVELOPER_ROUTES.inspection} element={<DeveloperInspectionPage />} />
          </Route>

          {/* CR-053 backlog item 7 - no permission gate, same reasoning as
              its sidebar entry: pure client-side arithmetic, no tenant data. */}
          <Route path={TOOLS_ROUTES.gstCalculator} element={<GstCalculatorPage />} />

          {/* CR-090 - the shop's own notifications; any signed-in user, tenant-scoped server-side. */}
          <Route path={DISCOVERY_ROUTES.notifications} element={<NotificationsPage />} />

          {/* CR-092 - branches (every role sees them; managing is owner-only inside the page) and insights (a report). */}
          <Route element={<RequirePermission permission={PERMISSIONS.BRANCH_VIEW} />}>
            <Route path={BRANCH_ROUTES.list} element={<BranchesPage />} />
          </Route>
          <Route element={<RequirePermission permission={PERMISSIONS.REPORT_VIEW} />}>
            <Route path={INSIGHTS_ROUTES.overview} element={<InsightsPage />} />
          </Route>

          <Route element={<RequirePermission permission={PERMISSIONS.REPORT_FINANCIAL} />}>
            <Route path={TOOLS_ROUTES.tallyExport} element={<TallyExportPage />} />
            {/* CR-091 Phase 6 - profit is owner/accountant information, same gate as the Tally export. */}
            <Route path={REPORT_ROUTES.profit} element={<ProfitReportPage />} />
          </Route>

          {/* CR-086 / CR-087 - the report is in the URL; GSTR-1 is gated again inside the page. */}
          <Route element={<RequirePermission permission={PERMISSIONS.REPORT_VIEW} />}>
            <Route path={REPORT_ROUTES.list} element={<ReportsPage />} />
            <Route path={`${REPORT_ROUTES.list}/:report`} element={<ReportsPage />} />
          </Route>

          {/* Every role can reach the dashboard - it only renders the cards
              a role actually has permission to see. */}
          <Route path="/dashboard" element={<DashboardPage />} />
          {/* `/` itself is the landing route above; a signed-in user is sent on to the dashboard from there. */}
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
