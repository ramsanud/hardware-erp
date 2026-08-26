# Hardware ERP — Frontend

One React application. Modules 2–12 add folders under `src/modules/`; nothing
else in the structure changes (CR-012).

## Run

```bash
npm install
npm run dev        # http://localhost:5173, proxies /api -> localhost:8080
npm run typecheck  # tsc -b --force
npm run build      # tsc -b && vite build
```

The dev server proxies `/api` to the backend so the refresh-token cookie stays
same-site. `SameSite=Strict` means the cookie is **not** sent cross-origin, so
running the frontend on a different origin without the proxy breaks silent
refresh. Set `VITE_API_BASE_URL` only if you have arranged for that.

## Structure

```
src/
├── modules/auth/          Module 1
│   ├── pages/             9 pages
│   ├── forms/             7 forms, all react-hook-form + zod
│   ├── components/        PermissionPicker, SessionList, badges
│   ├── services/          one file per backend controller
│   ├── hooks/             AuthProvider, useToast
│   ├── types/             mirrors backend DTOs exactly
│   ├── validation/        zod schemas mirroring Bean Validation
│   └── constants/
├── shared/                never module-specific
│   ├── components/ui/     14 shadcn-style primitives
│   ├── components/        PageHeader, EmptyState, ErrorState, Pagination…
│   ├── hooks/             useDebouncedValue, useAsyncList
│   └── types/api.ts       ApiResponse, ErrorResponse, PageResponse, ApiError
├── services/              apiClient, tokenStorage — the only place axios lives
├── layouts/               AuthLayout, AppLayout, Sidebar
├── routes/                AppRoutes, ProtectedRoute, RequirePermission
└── theme/                 ThemeProvider, ModeToggle (light/dark/system)
```

## Token handling

**Access token: memory only.** Not `localStorage`, not `sessionStorage`. An XSS
bug then steals a credential that expires in 15 minutes instead of one that
survives a browser restart. A page reload loses it and `AuthProvider` silently
calls `/auth/refresh` on startup to restore the session.

**Refresh token: never touched by JavaScript.** It is an `HttpOnly`,
`SameSite=Strict` cookie scoped to `/api/v1/auth`, issued by the backend.

**Single-flight refresh.** When four requests 401 at once, only the first
triggers a refresh; the others await the same promise. Without this each would
rotate the token, and the backend treats a replayed rotated token as theft — it
revokes every session and throws the user out.

## Authorization

`RequirePermission` and `PermissionGate` hide UI the server would reject anyway.
They are convenience, not enforcement: every guarded endpoint is re-checked by
`@PreAuthorize`. Removing a gate in devtools reveals a page whose API calls all
return 403.

## Responsiveness

No fixed breakpoints assumed. Tables hide low-priority columns progressively
(`sm` → `lg` → `xl`) and the primary column carries the hidden data as
sub-text, so a phone still shows mobile number and email. The sidebar is a
persistent rail from `lg` and a dialog below it. Layout heights use `dvh`, and
`env(safe-area-inset-*)` handles notches.
