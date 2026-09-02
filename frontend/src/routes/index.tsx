import { Navigate, Outlet, Route, Routes } from 'react-router-dom';
import { AuthLayout } from '@/layouts/AuthLayout';
import { AppLayout } from '@/layouts/AppLayout';
import { AUTH_ROUTES, PERMISSIONS } from '@/modules/auth/constants';
import { LoginPage } from '@/modules/auth/pages/LoginPage';
import { ForgotPasswordPage } from '@/modules/auth/pages/ForgotPasswordPage';
import { ResetPasswordPage } from '@/modules/auth/pages/ResetPasswordPage';
import { ForceChangePasswordPage } from '@/modules/auth/pages/ForceChangePasswordPage';
import { ProfilePage } from '@/modules/auth/pages/ProfilePage';
import { UserManagementPage } from '@/modules/auth/pages/UserManagementPage';
import { RoleManagementPage } from '@/modules/auth/pages/RoleManagementPage';
import { PermissionViewPage } from '@/modules/auth/pages/PermissionViewPage';
import { SecurityAuditLogPage } from '@/modules/auth/pages/SecurityAuditLogPage';
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
import { SETTINGS_ROUTES } from '@/modules/settings/constants';
import { ShopSettingsPage } from '@/modules/settings/pages/ShopSettingsPage';
import { AppearancePage } from '@/modules/settings/pages/AppearancePage';
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
import { NotFoundPage } from '@/shared/components/NotFoundPage';
import { PlatformAdminAuthProvider } from '@/modules/platform-admin/hooks/PlatformAdminAuthProvider';
import { PlatformAdminLoginPage } from '@/modules/platform-admin/pages/PlatformAdminLoginPage';
import { PlatformAdminMfaVerifyPage } from '@/modules/platform-admin/pages/PlatformAdminMfaVerifyPage';
import { PlatformAdminMfaEnrollPage } from '@/modules/platform-admin/pages/PlatformAdminMfaEnrollPage';
import { PlatformAdminDashboardPage } from '@/modules/platform-admin/pages/PlatformAdminDashboardPage';
import { PlatformAdminProtectedRoute } from '@/modules/platform-admin/routes/PlatformAdminProtectedRoute';
import { ProtectedRoute } from './ProtectedRoute';
import { RequirePermission } from './RequirePermission';

/**
 * Routes are declared per module. Modules 2-12 add their own block here and
 * nothing else in this file changes.
 */
export function AppRoutes() {
  return (
    <Routes>
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
          <Route path="dashboard" element={<PlatformAdminDashboardPage />} />
        </Route>
        <Route index element={<Navigate to="login" replace />} />
      </Route>

      {/* Public */}
      <Route element={<AuthLayout />}>
        <Route path={AUTH_ROUTES.login} element={<LoginPage />} />
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

          <Route element={<RequirePermission permission={PERMISSIONS.USER_VIEW} />}>
            <Route path={AUTH_ROUTES.users} element={<UserManagementPage />} />
          </Route>

          <Route element={<RequirePermission permission={PERMISSIONS.ROLE_VIEW} />}>
            <Route path={AUTH_ROUTES.roles} element={<RoleManagementPage />} />
            <Route path={AUTH_ROUTES.permissions} element={<PermissionViewPage />} />
          </Route>

          <Route element={<RequirePermission permission={PERMISSIONS.AUDIT_VIEW} />}>
            <Route path={AUTH_ROUTES.auditLog} element={<SecurityAuditLogPage />} />
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

          <Route element={<RequirePermission permission={PERMISSIONS.INVOICE_VIEW} />}>
            <Route path={INVOICE_ROUTES.list} element={<InvoiceListPage />} />
            <Route path="/invoices/:id" element={<InvoiceDetailPage />} />
          </Route>
          <Route element={<RequirePermission permission={PERMISSIONS.INVOICE_CREATE} />}>
            <Route path={INVOICE_ROUTES.create} element={<InvoiceCreatePage />} />
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

          <Route element={<RequirePermission permission={PERMISSIONS.REPORT_FINANCIAL} />}>
            <Route path={TOOLS_ROUTES.tallyExport} element={<TallyExportPage />} />
          </Route>

          {/* Every role can reach the dashboard - it only renders the cards
              a role actually has permission to see. */}
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
