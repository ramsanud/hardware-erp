# HARDWARE ERP — PROJECT REGISTRY

**Status:** Module 1 (Authentication) LOCKED
**Enforced by:** `registry/check_registry.py` (run before every commit)
**Machine-readable:** `registry/registry.json`

---

## 0. How this works

`registry.json` is generated from the code, not typed by hand. `check_registry.py`
reads it and fails with a non-zero exit code if any locked name has been renamed,
moved, or deleted.

```bash
python3 registry/check_registry.py     # exit 0 = clean, 1 = drift
```

Wire it into CI **before** `mvn test`:

```yaml
- run: python3 registry/check_registry.py
- run: cd backend && ./mvnw test
```

It currently detects:

| Violation | Example it catches |
|---|---|
| Table or column renamed/dropped | `app_user` → `users` |
| Entity renamed, moved, or remapped | `User.fullName` → `User.name` |
| DTO or DTO field renamed | `UserResponse.fullName` → `customerFullName` |
| Endpoint path or handler method renamed | `POST /api/v1/auth/login` → `/signin` |
| Any class renamed or moved package | `UserService` deleted |
| Duplicate class names anywhere in the tree | two `UserMapper.java` |
| Alias services for one concept | `UserService` **and** `AppUserService` present |
| Alias field names for one concept | `mobileNo` **and** `phoneNumber` present |

Verified: renaming `UserResponse.fullName` to `customerFullName` and deleting
`UserService.java` both produce ERROR and exit 1.

---

## 1. Naming law

These rules govern every future module. They are derived from Module 1 as
generated, so Module 1 already complies.

### 1.1 The four-hop rule

One concept keeps one name across all four layers. The **only** permitted
transformation is `snake_case` → `camelCase`.

```
DB column        full_name
Entity field     fullName
DTO field        fullName
JSON key         fullName
Frontend type    fullName
```

Never `name`, never `userName`, never `customerFullName`. One concept, one word.

### 1.2 Primary keys

```
DB column     <table>_id      user_id, role_id, customer_id
Entity field  id              always plain `id`, never `userId`
DTO field     id              never `userId`
FK column     <target>_id     role_id
FK entity     <target>        private Role role;   (the object, not the id)
FK DTO        <target>Id      roleId               (the id, not the object)
```

### 1.3 Table names

Singular, `snake_case`. Child/line tables are `<parent>_item`.

```
customer, supplier, product, product_variant, sales_invoice, sales_invoice_item
```

Exception already locked: **`app_user`**, not `user`. `USER` is a function name in
MySQL and a reserved word in H2. This exception is permanent.

### 1.4 Booleans

```
DB column      no prefix           active, system_role
Entity field   no prefix           active, system
Getter         Lombok gives isX()  isActive()
```

Exception already locked: **`role.is_system`**. Keep it; do not "fix" it.
All *new* boolean columns drop the `is_` prefix.

### 1.5 Timestamps and dates

```
Point in time   DATETIME(3)   LocalDateTime   suffix _at      created_at, locked_until
Calendar date   DATE          LocalDate       suffix _date    invoice_date, due_date
```

`DATETIME(3)` — millisecond precision — is locked. Not `DATETIME`, not `TIMESTAMP`.

### 1.6 Audit block — every table, no exceptions

```sql
created_at  DATETIME(3) NOT NULL
created_by  BIGINT      NULL
updated_at  DATETIME(3) NULL
updated_by  BIGINT      NULL
version     INT         NOT NULL DEFAULT 0
```

Plus `deleted_at DATETIME(3) NULL` on master tables (soft delete).
Posted financial documents are **never** soft-deleted — they are reversed.

Inherit from `BaseEntity`. Never redeclare these fields on a subclass.

### 1.7 Class suffixes

| Layer | Pattern | Locked example |
|---|---|---|
| Entity | `<Concept>` | `User`, `Role` |
| Repository | `<Concept>Repository` | `UserRepository` |
| Service interface | `<Concept>Service` | `UserService` |
| Service impl | `<Concept>ServiceImpl` | `UserServiceImpl` |
| Controller | `<Concept>Controller` | `UserController` |
| Mapper | `<Concept>Mapper` | `UserMapper` |
| Create DTO | `Create<Concept>Request` | `CreateUserRequest` |
| Update DTO | `Update<Concept>Request` | `UpdateUserRequest` |
| Response DTO | `<Concept>Response` | `UserResponse` |

**Banned forever:** `AppUserService`, `UserManagementService`, `UserMgmtService`,
`CustomerUserService`, `UserHelper`, `UserUtil`, `UserFacade`, `UserBO`, `UserVO`.
The checker fails the build if any of these appear alongside `UserService`.

### 1.8 Package structure

```
com.hardware.erp
├── common.{entity,dto,exception}      shared, never module-specific
├── config
├── security
└── <module>.{entity,repository,dto,mapper,service,service.impl,controller}
```

Module packages already locked: `auth`.
Planned: `customer`, `supplier`, `category`, `brand`, `product`, `quotation`,
`invoice`, `purchase`, `inventory`.

### 1.9 API paths

```
/api/v1/<plural-kebab-noun>
```

`/api/v1/users`, `/api/v1/roles`, `/api/v1/customers`, `/api/v1/product-variants`.
Never a verb in the path — `POST /api/v1/users`, not `/api/v1/createUser`.
Locked exception: `/api/v1/auth/*` uses verbs (`login`, `refresh`, `logout`,
`change-password`) because they are actions, not resources.

### 1.10 Canonical field names — locked for all modules

| Concept | Column | Java / JSON | Type |
|---|---|---|---|
| Person name (user) | `full_name` | `fullName` | VARCHAR(200) |
| Party name (customer/supplier) | `customer_name` / `supplier_name` | `customerName` / `supplierName` | VARCHAR(255) |
| Mobile | `mobile_no` | `mobileNo` | VARCHAR(15) |
| WhatsApp | `whatsapp_no` | `whatsappNo` | VARCHAR(15) |
| Email | `email` | `email` | VARCHAR(255) |
| GST number | `gst_no` | `gstNo` | VARCHAR(15) |
| Address | `address` | `address` | TEXT |
| Business code | `<concept>_code` | `<concept>Code` | VARCHAR(30) |
| Lifecycle state | `status` | `status` | VARCHAR(20) enum |
| Remarks | `remarks` | `remarks` | TEXT |

**Banned aliases:** `name`, `customerFullName`, `clientName`, `partyName`,
`phoneNo`, `phoneNumber`, `contactNo`, `emailId`, `emailAddress`, `gstin`,
`gstNumber`, `mobileNumber`. The checker warns on all of these.

Note the deliberate asymmetry: a **user** has `full_name`; a **customer** has
`customer_name`. These are different concepts (an employee vs. a business party)
and the second follows your original 100-table specification. This is recorded so
nobody "harmonises" it later.

---

## 2. Locked artifacts — Module 1

### 2.1 Tables (6)

| Table | Columns |
|---|---|
| `role` | role_id, role_code, role_name, description, is_system, active, created_at, created_by, updated_at, updated_by, version |
| `role_permission` | role_id, permission_code |
| `app_user` | user_id, role_id, employee_code, full_name, mobile_no, email, password_hash, status, must_change_password, token_version, failed_login_attempts, locked_until, last_login_at, password_changed_at, created_at, created_by, updated_at, updated_by, deleted_at, version |
| `refresh_token` | refresh_token_id, user_id, token_hash, expires_at, revoked_at, replaced_by_hash, user_agent, ip_address, created_at |
| `password_reset_token` | reset_token_id, user_id, token_hash, expires_at, used_at, created_at |
| `audit_log` | audit_id, entity_type, entity_id, action, user_id, user_name, ip_address, detail, created_at |

### 2.2 Entities (5) — field → column

**`User` → `app_user`**
`id`→user_id · `role`→role_id · `employeeCode`→employee_code · `fullName`→full_name ·
`mobileNo`→mobile_no · `email`→email · `passwordHash`→password_hash · `status`→status ·
`mustChangePassword`→must_change_password · `tokenVersion`→token_version ·
`failedLoginAttempts`→failed_login_attempts · `lockedUntil`→locked_until ·
`lastLoginAt`→last_login_at · `passwordChangedAt`→password_changed_at · `deletedAt`→deleted_at

**`Role` → `role`**
`id`→role_id · `code`→role_code · `name`→role_name · `description`→description ·
`system`→is_system · `active`→active · `permissions`→role_permission.permission_code

**`RefreshToken` → `refresh_token`**
`id`→refresh_token_id · `user`→user_id · `tokenHash`→token_hash · `expiresAt`→expires_at ·
`revokedAt`→revoked_at · `replacedByHash`→replaced_by_hash · `userAgent`→user_agent ·
`ipAddress`→ip_address · `createdAt`→created_at

**`PasswordResetToken` → `password_reset_token`**
`id`→reset_token_id · `user`→user_id · `tokenHash`→token_hash · `expiresAt`→expires_at ·
`usedAt`→used_at · `createdAt`→created_at

**`AuditLog` → `audit_log`**
`id`→audit_id · `entityType`→entity_type · `entityId`→entity_id · `action`→action ·
`userId`→user_id · `userName`→user_name · `ipAddress`→ip_address · `detail`→detail ·
`createdAt`→created_at

### 2.3 DTOs (13)

| DTO | Fields |
|---|---|
| `LoginRequest` | login, password |
| `LoginResponse` | accessToken, refreshToken, tokenType, expiresInSeconds, mustChangePassword, user |
| `RefreshRequest` | refreshToken |
| `CreateUserRequest` | fullName, mobileNo, email, employeeCode, roleId, password, mustChangePassword |
| `UpdateUserRequest` | fullName, mobileNo, email, employeeCode, roleId, status |
| `UpdateProfileRequest` | fullName, email |
| `UserResponse` | id, fullName, mobileNo, email, employeeCode, roleId, roleCode, roleName, permissions, status, mustChangePassword, lastLoginAt, createdAt |
| `ChangePasswordRequest` | currentPassword, newPassword |
| `ForgotPasswordRequest` | login |
| `ResetPasswordRequest` | token, newPassword |
| `ResetUserPasswordRequest` | newPassword |
| `RoleRequest` | code, name, description, permissions, active |
| `RoleResponse` | id, code, name, description, system, active, permissions, userCount |

### 2.4 Endpoints (18)

All under context path `/api`. See §1 of `MODULE1-README.md` for the permission map.

```
POST   /api/v1/auth/login              POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout             POST   /api/v1/auth/logout-all
GET    /api/v1/auth/me                 PUT    /api/v1/auth/me
POST   /api/v1/auth/change-password    POST   /api/v1/auth/forgot-password
POST   /api/v1/auth/reset-password
GET    /api/v1/users                   POST   /api/v1/users
GET    /api/v1/users/{id}              PUT    /api/v1/users/{id}
POST   /api/v1/users/{id}/reset-password
DELETE /api/v1/users/{id}
GET    /api/v1/roles                   POST   /api/v1/roles
GET    /api/v1/roles/permissions       GET    /api/v1/roles/{id}
PUT    /api/v1/roles/{id}              DELETE /api/v1/roles/{id}
```

### 2.5 Enums

```
UserStatus  ACTIVE, INACTIVE, SUSPENDED
RoleCode    OWNER, MANAGER, ACCOUNTANT, STAFF     (data, seeded in V1)
```

### 2.6 Permissions (26) — permanently locked

```
USER_VIEW, USER_MANAGE, ROLE_VIEW, ROLE_MANAGE,
CUSTOMER_VIEW, CUSTOMER_MANAGE, SUPPLIER_VIEW, SUPPLIER_MANAGE,
PRODUCT_VIEW, PRODUCT_MANAGE, PRODUCT_VIEW_COST,
QUOTATION_VIEW, QUOTATION_MANAGE,
INVOICE_VIEW, INVOICE_CREATE, INVOICE_CANCEL, INVOICE_DISCOUNT_OVERRIDE,
PAYMENT_VIEW, PAYMENT_MANAGE,
PURCHASE_VIEW, PURCHASE_MANAGE,
INVENTORY_VIEW, INVENTORY_ADJUST,
REPORT_VIEW, REPORT_FINANCIAL, AUDIT_VIEW
```

Adding a permission is allowed. Renaming or removing one is not.

### 2.7 Reusable components — use these, never re-create

| Need | Use | Do not create |
|---|---|---|
| Audit columns | `BaseEntity` | per-entity `createdAt` fields |
| Success envelope | `ApiResponse<T>` | `ResponseWrapper`, `Result<T>` |
| Pagination | `PageResponse<T>` | returning Spring `Page` directly |
| Not found | `ResourceNotFoundException` | `NotFoundException`, `EntityNotFound` |
| Duplicate | `DuplicateResourceException` | `AlreadyExistsException` |
| Business rule failure | `BusinessException` | `ValidationException`, `AppException` |
| Auth failure | `AuthException` | `UnauthorizedException` |
| Error handling | `GlobalExceptionHandler` | per-controller `@ExceptionHandler` |
| Current user | `SecurityUtils.requireCurrentUser()` | injecting `Principal` and casting |
| Client IP | `SecurityUtils.clientIp(request)` | `request.getRemoteAddr()` |
| Audit trail | `AuditService.record(...)` | direct `AuditLogRepository.save()` |
| Permission check | `@PreAuthorize("hasAuthority(T(...Permissions).X)")` | `hasRole('OWNER')` |
| Outbound email | `MailService` | injecting `JavaMailSender` |

---

## 3. Defects found in Module 1 — decide before locking

The registry cannot be honestly sealed while it contains contradictions. These
four exist in the generated code. Fixing them **now** costs one edit each. After
Module 2 ships they cost a migration.

### D1 — Two vocabularies for "is this record usable"

```
role.active                BOOLEAN
app_user.status            VARCHAR(20)   ACTIVE | INACTIVE | SUSPENDED
```

Your original 100-table spec used `status TINYINT` on both.

**Recommendation:** keep both, and make the rule explicit — a record with a real
lifecycle (a user can be suspended, an invoice can be cancelled) gets
`status VARCHAR(20)`; a simple on/off toggle gets `active BOOLEAN`.
Reject `TINYINT`: `status = 2` is unreadable in a DB console at 11pm during an
incident, and that is when you will be reading it.

**Cost if changed now:** one column + one enum.

### D2 — `role_code` becomes three different names

```
DB      role.role_code
Entity  Role.code
DTO     RoleResponse.code       ← one name
DTO     UserResponse.roleCode   ← different name, same value
```

This is precisely the `customerName` / `customerFullName` failure you described.

**Recommendation:** declare the rule rather than rename — *a field carrying its
own table's prefix drops it inside that table's own entity and DTO, and re-adds
it when embedded in another type.* So `Role.code` and `UserResponse.roleCode`
are both correct, and `Product.code` / `InvoiceItemResponse.productCode` will
follow the same shape. This must be written down or Module 6 will invent
`productCode` on the `Product` entity itself.

**Cost if changed now:** zero — rule only, no code change.

### D3 — Two column names for a person's name

```
app_user.full_name      the user's name
audit_log.user_name     the same user's name, snapshotted
```

**Recommendation:** rename `audit_log.user_name` → `audit_log.full_name`, and
`AuditLog.userName` → `AuditLog.fullName`. `audit_log` has no production rows.

**Cost if changed now:** one column, one field, one line in `AuditServiceImpl`.
**Cost after Module 2:** a Flyway migration on a table that will already hold
tens of thousands of rows.

### D4 — `audit_log` diverges from your original table 75

```
Your spec:      module_name, action_name, record_id
Generated:      entity_type, action,      entity_id
```

**Recommendation:** keep the generated names. `entity_type` is more accurate
than `module_name` (one module writes several entity types), and per your own
rule the generated code is the source of truth. Recorded here so the divergence
is deliberate, not forgotten.

**Cost:** zero.

---

## 4. Decisions required BEFORE Module 6 (Product) and Module 8 (Invoice)

These are not naming questions — they are the ones that force a data migration
if answered late. I need your call on each.

### DEC-1 — Money representation (most expensive decision in the project)

| Option | Column | Java | Trade-off |
|---|---|---|---|
| **A** | `DECIMAL(15,2)` | `BigDecimal` | Matches your original spec. Readable in SQL. Cannot represent ₹0.875/screw. |
| **B** | `BIGINT` paise | `long` | Exact. No rounding drift across a year of invoices. Every report needs `/100`. |
| **C** | Hybrid *(recommended)* | amounts `DECIMAL(15,2)`, unit rates `DECIMAL(18,6)` | Keeps your spec for totals; fixes the sub-paisa rate problem. |

**Why C:** a 1000-piece screw box at ₹875 is ₹0.875 each. At two decimals you
store 0.88, and 1000 pieces reconciles to ₹880 — a ₹5 error per box, on the
highest-volume SKU in a hardware shop. That error lands directly in the
loss-making-sale detection you asked for in requirement 8.

### DEC-2 — Quantity representation

`DECIMAL(15,3)` (your spec) or `DECIMAL(18,4)`? Mica sheets sold by sq.ft and
chain sold by metre both want 4 decimals. **Recommendation: `DECIMAL(18,4)`.**

### DEC-3 — Unit of measure

Module 6 cannot be generated without this. Does a `product_variant` have one
implicit unit, or does it need `uom` + `variant_uom` conversion (box→piece,
roll→metre, sheet→sq.ft)?

**Recommendation: build it.** Without it, purchase price ₹875/box against
selling price ₹1.20/piece makes the margin engine report a catastrophic loss on
every screw you sell. This is the single most common reason SMB hardware ERPs
get abandoned in month three.

### DEC-4 — Stock representation

Your table 33 has `stock.available_qty` as a snapshot and table 34
`stock_movement` as the ledger. That is the right shape. Confirm the ledger is
**append-only** (no UPDATE, no DELETE, corrections are reversing rows) and that
`stock` carries `@Version` for optimistic locking. Two concurrent billing
terminals will otherwise silently lose stock deductions.

### DEC-5 — Costing method

`purchase_price` on an invoice line must be a **snapshot of cost at sale time**,
not a lookup. Moving weighted average (simpler) or FIFO (more accurate, needs a
`stock_layer` table)? **Recommendation: moving weighted average**, with
`costing_method` stored as config so FIFO stays possible.

### DEC-6 — Frontend naming

Do TypeScript interfaces mirror the DTO names exactly (`UserResponse`,
`CreateUserRequest`)? **Recommendation: yes, generated from the same registry**,
so a backend rename breaks the frontend build immediately rather than at runtime.

---

## 5. Amendment procedure

A locked name changes only like this:

1. **Do not edit the code.** Open an amendment entry below.
2. State: current name, proposed name, every layer affected, why the rename is
   unavoidable.
3. Attach a Flyway migration (`V<n>__rename_<x>.sql`) using
   `ALTER TABLE ... RENAME COLUMN`, never DROP + ADD.
4. Get approval.
5. Apply across DB, entity, DTO, service, controller, frontend, tests **in one
   commit**.
6. Regenerate `registry.json`.
7. `python3 registry/check_registry.py` must pass before merge.

### Amendment log

| # | Date | From | To | Status |
|---|---|---|---|---|
| — | — | — | — | none yet |

---

## 6. Checklist before generating any new module

- [ ] `python3 registry/check_registry.py` passes on the current tree
- [ ] Every new table name checked against §2.1
- [ ] Every new field checked against §1.10 canonical names
- [ ] No new service duplicates an existing concept (§1.7 banned list)
- [ ] `BaseEntity` extended, audit columns not redeclared
- [ ] `ApiResponse` / `PageResponse` used, not new wrappers
- [ ] Existing exceptions reused, no new not-found type
- [ ] New permissions added to `Permissions.java` **and** seeded into
      `role_permission` in the same migration
- [ ] `registry.json` regenerated and committed
