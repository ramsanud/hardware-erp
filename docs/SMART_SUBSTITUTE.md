# Smart Substitute Product Suggestion (CR-089, PREMIUM)

When a customer asks for something the shop is out of, the owner sees
in-stock alternatives from the shop's **own** catalogue - never a
customer-facing recommendation, never an automatic replacement on an
invoice. The engine only ever says "these may be suitable"; the decision
is always the owner's (brief §24).

## Flow

```
Counter: customer asks for X, X is out of stock
   ↓  POST /v1/product-requests {productId, requestedQuantity, budget?, customer?}
product_request row
   ↓  strategies, in priority order
ManualMappingRecommendationStrategy  (priority 10)  the owner's own mappings
RuleBasedRecommendationStrategy      (priority 100) attribute similarity
   ↓  first suggestion per product wins; owner's threshold / budget / limit applied
product_request_suggestion rows (score, level, reason, source)
   ↓  owner compares, then POST …/select-alternative
product_request.selected_product_id + selected_by + selected_at, status RESOLVED
   ↓
Normal billing continues with whatever was actually sold - nothing here touches an invoice.
```

## Data model (V60)

| Table | Purpose |
|---|---|
| `product` (+7 columns) | `subcategory`, `size_label`, `material`, `color_finish`, `shape`, `usage_type`, `product_type` - all nullable, none mandatory (hardware varies too much). Plus a `pg_trgm` GIN index on `product_name` for typo-tolerant lookup. |
| `product_relationship` | Owner-defined, directional: ALTERNATIVE / COMPATIBLE / UPGRADE / LOWER_COST / SAME_USE / REPLACEMENT. `CHECK (product_id <> related_product_id)`, unique per (tenant, from, to, type). |
| `substitute_setting` | One row per tenant, created only when an owner changes something: `min_score_threshold` (default 40), `show_above_budget` (default true), `max_results` (default 3). |
| `product_request` | What was asked for, how many, optional budget and customer name/mobile (stays inside the shop - never forwarded), status OPEN/RESOLVED/CANCELLED, the owner's selection and who made it. |
| `product_request_suggestion` | Every suggestion made: score, match level, the plain-English reason, and whether it came from a mapping or the scorer - persisted so §25's "which alternatives get accepted" can be measured from real rows later. |

Two new permissions: `PRODUCT_REQUEST_VIEW` (OWNER, MANAGER, ACCOUNTANT,
STAFF) and `PRODUCT_REQUEST_MANAGE` (OWNER, MANAGER, STAFF - the counter is
exactly where an out-of-stock item is hit). Mapping create/delete reuses
`PRODUCT_MANAGE`: it is product master data. `RoleGrantDriftTest` pins both.

## Scoring

Weights live in one place, `app.substitute.scoring.*`
(`SubstituteScoringProperties`) - the engine holds no literal of its own.
Defaults are the brief's worked example and sum to 110:

| Rule | Points |
|---|---|
| Same category | 30 |
| Same product type | 20 |
| Same usage | 20 |
| Same size | 15 |
| Same material | 5 |
| Same brand | 5 |
| Similar price (within 20%, `similar-price-tolerance`) | 5 |
| Currently in stock | 10 |

Bands (`MatchLevel.of`): 90+ EXCELLENT, 75-89 HIGH, 60-74 MEDIUM, 40-59 LOW,
below 40 DO_NOT_RECOMMEND (scored, never shown).

Rules that matter and are tested (`RuleBasedRecommendationStrategyTest`):

- Attribute comparison is null-safe, case-insensitive and whitespace-trimmed.
  **Two blanks are not a match** - a missing attribute on either side is an
  unknown, not evidence of similarity.
- A zero-priced requested product scores nothing on price similarity rather
  than matching every price.
- The in-stock bonus is a commercial factor, not a similarity one: it cannot
  make an unlike product like, but breaks ties toward what is on the shelf.
- The candidate pool is narrowed **in SQL** (`ProductRepository
  .findSubstituteCandidates`: same category or same product type, ACTIVE,
  stock ≥ requested quantity, never the requested product itself) before a
  single row is scored in Java. A product with no category and no type
  produces no candidates - there is nothing honest to narrow by.

## Manual mappings outrank the score - and why that needed a real test

`RelationshipType.isSubstitute()` excludes COMPATIBLE: a compatible product
goes *with* the requested one (a hinge for a door), it does not replace it,
and offering it as a substitute is exactly the unsafe substitution §16
warns about. For electrical, plumbing, load-bearing and lock products a
mapping is the intended path; similarity alone will still score them, so a
shop that cares should set its threshold high and map explicitly.

The first cut of the service sorted the final list by score alone. A
near-identical-on-paper product can reach 110 while a mapping is fixed at
100, so `ProductRequestIT.manualMappingOutranksSimilarity` failed on its
first run - the owner's "this is the replacement" sorted *below* a generic
lookalike. The sort is now source first (`SuggestionSource.rank()`, the same
numbers the strategies' `priority()` returns, so the two cannot drift),
score second. Unit tests on the scorer could not have caught this; it is a
property of the orchestration.

## Honest deviations from the brief

- **"Available = physical − reserved − pending allocation"** assumes a
  reservation mechanism this codebase does not have (Sales Order never
  reserves physical stock - CR-052). Availability is the real
  `stock.quantity_on_hand`, nothing invented on top (hard rule 12).
- **Product-form attribute inputs** are not yet on the React product form;
  the fields are accepted by `POST/PUT /v1/products` and shown in
  `ProductResponse`, and the substitute screens render them. Filling them
  from the UI is a small follow-up; the engine, mappings, requests, compare
  and select are complete.
- **`GET /v1/products/{id}/alternatives`** from the brief is served as
  `GET /v1/products/{id}/alternative-mappings` - the same read, named for
  what it returns (the owner's mappings, not a computed list).

## API

```
POST /v1/product-requests                          PRODUCT_REQUEST_MANAGE  record + compute
GET  /v1/product-requests?status=                  PRODUCT_REQUEST_VIEW
GET  /v1/product-requests/{id}                     PRODUCT_REQUEST_VIEW
GET  /v1/product-requests/{id}/alternatives        PRODUCT_REQUEST_VIEW    (same shape as get)
POST /v1/product-requests/{id}/alternatives/recompute  PRODUCT_REQUEST_MANAGE
GET  /v1/product-requests/{id}/compare?alternativeProductId=  PRODUCT_REQUEST_VIEW
POST /v1/product-requests/{id}/select-alternative  PRODUCT_REQUEST_MANAGE  must be a suggested product
POST /v1/product-requests/{id}/cancel              PRODUCT_REQUEST_MANAGE
GET  /v1/products/{id}/alternative-mappings        PRODUCT_VIEW
POST /v1/products/{id}/alternative-mappings        PRODUCT_MANAGE
DELETE /v1/products/{id}/alternative-mappings/{relationshipId}  PRODUCT_MANAGE
GET  /v1/substitute-settings                       PRODUCT_REQUEST_VIEW
PUT  /v1/substitute-settings                       SETTINGS_MANAGE
```

Every `/v1/product-requests/*` and `/v1/substitute-settings` call is also
gated on `FeatureKey.SMART_SUBSTITUTE` (PREMIUM) inside the service; a
Basic or Pro shop gets `403 FEATURE_NOT_AVAILABLE` and the frontend's
upgrade dialog. Mappings are deliberately **not** plan-gated: describing
your own catalogue is not the premium part, the engine that uses it is.

## Tests

- `RuleBasedRecommendationStrategyTest` (9) - the weights, bands, blank
  handling, case-insensitivity, in-stock bonus, zero-price rule.
- `ProductRequestIT` (8, real PostgreSQL incl. the `pg_trgm` migration) -
  out-of-stock candidates never offered, best-first ordering with reason
  and level, manual mapping outranks similarity, select resolves and
  touches no invoice, an unsuggested product cannot be selected, owner
  threshold hides weak matches immediately, compare rows, Basic → 403,
  tenant isolation (another shop's request is a 404 that leaks no
  customer detail).
