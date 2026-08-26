# MODULE DEPENDENCY MAP

One Spring Boot monolith, one MySQL database, one React application.
Dependencies below are **package-level**, not network calls.

```
AUTH (Module 1)
 │  every module depends on it for identity and permissions
 ├── SUPPLIER    (2)
 ├── CUSTOMER    (3)
 ├── CATEGORY    (4)
 ├── BRAND       (5)
 ├── PRODUCT     (6)  ──┬── CATEGORY
 │                      └── BRAND
 ├── PRODUCT_VARIANT (7) ── PRODUCT
 ├── PURCHASE    (8)  ──┬── SUPPLIER
 │                      └── PRODUCT_VARIANT
 ├── INVENTORY   (9)  ─── PURCHASE
 ├── QUOTATION  (10)  ──┬── CUSTOMER
 │                      ├── PRODUCT_VARIANT
 │                      └── INVENTORY
 ├── INVOICE    (11)  ──┬── CUSTOMER
 │                      ├── PRODUCT_VARIANT
 │                      ├── PURCHASE     (purchase price -> loss-sale rule)
 │                      ├── INVENTORY    (stock qty -> stock value alert)
 │                      └── QUOTATION
 └── PAYMENT    (12)  ─── INVOICE
```

## Build order and why it is not negotiable

| Module | Cannot be built before | Reason |
|---|---|---|
| PRODUCT | CATEGORY, BRAND | `category_id` and `brand_id` are NOT NULL FKs (CR-004) |
| PURCHASE | SUPPLIER, PRODUCT_VARIANT | a purchase line needs a supplier and a variant |
| INVENTORY | PURCHASE | stock arrives through goods receipt |
| INVOICE | PURCHASE, INVENTORY | loss-sale protection needs `purchase_price`; the stock-value alert formula `stock_qty x (old - new)` needs `stock_qty` |
| PAYMENT | INVOICE | a receipt settles an invoice |

CR-007 moved PURCHASE and INVENTORY ahead of INVOICE for exactly this reason.
Building INVOICE first would leave the loss-sale alert, the RED/GREEN price
alert and the margin badge stubbed, then retrofitted into a module already
signed off as complete.

## Class-level dependency comments

Every class that reaches into another module declares it:

```java
/**
 * Depends On:
 *   Category Module - category must exist and be active
 *   Brand Module    - brand must exist and be active
 *
 * Do not remove the existence checks in createProduct(); the FKs are
 * NOT NULL and a missing check surfaces as a 500 instead of a 422.
 */
public class ProductServiceImpl implements ProductService { }
```

## Shared code — never duplicated per module

Backend: `common/` (BaseEntity, ApiResponse, ErrorResponse, PageResponse,
exception hierarchy, GlobalExceptionHandler, RequestCorrelationFilter),
`security/` (JWT, filters, rate limiting, SecurityUtils), `config/`.

Frontend: `shared/` (api client, interceptors, permission hooks, UI components,
form utilities), `layouts/`, `routes/`, `theme/` (dark and light mode).

A module that needs auth imports from `security/`. It never ships its own JWT
handling, its own error envelope, or its own API client.
