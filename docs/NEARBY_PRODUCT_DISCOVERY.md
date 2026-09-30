# Owner-side Nearby Product Discovery (CR-090, PREMIUM, opt-in, OFF by default)

When a shop is out of something a customer asked for, its **owner** can see
whether nearby participating hardware shops may have it, and contact them.
The customer sees nothing but "This product is currently unavailable." This
is not a marketplace and has no customer-facing surface at all.

## The privacy model - enforced structurally, not by convention

This is the one deliberately cross-tenant read in the system. The rules
below are not "the frontend hides it"; each is a property of the SQL or the
schema and is proven by `ShopDiscoveryIT` against real PostgreSQL.

| Rule | Where it is enforced |
|---|---|
| A shop is invisible unless it opted in | `WHERE d.discovery_enabled AND d.share_availability` - a shop with no row, or with either flag off, is not a candidate row at all |
| Name, phone, distance each need their own consent | `CASE WHEN d.share_shop_name THEN … ELSE NULL END` etc. in the SELECT list - the column is never read, not "read then hidden" |
| Never a quantity, never a price | Only `AVAILABLE` / `LIKELY_AVAILABLE` is selected (on hand ≥ requested vs some on hand). Nothing else about the product or shop is in the SELECT list |
| Zero-stock shops are not "Unavailable", they are absent | `COALESCE(s.quantity_on_hand, 0) > 0` - listing them would leak catalogue membership for nothing |
| One row per shop | `row_number() OVER (PARTITION BY tenant_id)` - a requester cannot enumerate another shop's catalogue by asking repeatedly |
| Never the requester itself | `d.tenant_id <> :requestingTenantId` |
| Coordinates exist only while you take part | They live on `shop_discovery_setting`, not `tenant`; `CHECK (discovery_enabled = FALSE OR (latitude IS NOT NULL AND longitude IS NOT NULL))` |
| Off means everything off | Disabling clears every sub-flag server-side, so re-enabling never silently re-shares (`optingOutTakesEffectImmediately`) |
| Opting out is immediate | The next search anyone runs reads the current row; a match already consented to stays on the requesting owner's record (spec: "future results") |
| Reciprocity | A shop searches the network only if it is in it (`discovery_enabled`) - no free-riding on others' consent (`nonParticipantCannotSearch`) |
| The customer never crosses the boundary | What crosses is the product code/model/manufacturer code/name and a quantity bucket. The request's `customer_name`/`customer_mobile` are never in the search, the match, the notification, or the wa.me link (which carries no pre-filled text) |
| Consent is audited | Every `PUT /v1/discovery/settings` writes before/after to `activity_log` |

Response DTOs carry **no source tenant id** - `product_request_discovery_match
.source_tenant_id` exists for audit and de-duplication only and is never
serialised.

## Matching

Product identity first, name second: exact case-insensitive match on
`product_code`, `model_no` or `manufacturer_code`, otherwise a `pg_trgm`
`similarity(product_name, :name) > 0.45`. Ranked per shop by identity rank,
then similarity, then stock. Distance is the great-circle formula in SQL
(acos argument clamped to [-1, 1]); the radius filter is on that same
expression. Radius is the requesting shop's own setting (1-100 km, default
5). Results capped at 10.

## Data model (V61)

| Table | Purpose |
|---|---|
| `shop_discovery_setting` | `tenant_id` PK, five consent flags (all `DEFAULT FALSE`), `latitude`/`longitude` (nullable, `DECIMAL(9,6)`), `search_radius_km` (1-100). Partial index on `discovery_enabled = TRUE` - the only rows the search scans. |
| `product_request_discovery_match` | Snapshot per (request, source shop) of exactly what was permitted at search time. `UNIQUE (product_request_id, source_tenant_id)`. |
| `owner_notification` | Generic in-app notification (`PRODUCT_DISCOVERY` today; CR-092 adds `DAILY_SUMMARY`/`LOW_STOCK`). Tenant-scoped, `read_at`. |

## API

```
GET  /v1/discovery/settings                    SETTINGS_VIEW
PUT  /v1/discovery/settings                    SETTINGS_MANAGE + NEARBY_PRODUCT_DISCOVERY   audited
POST /v1/product-requests/{id}/discover        PRODUCT_REQUEST_MANAGE + feature   runs the search, snapshots, notifies
GET  /v1/product-requests/{id}/nearby          PRODUCT_REQUEST_VIEW + feature     the snapshot
GET  /v1/owner-notifications?unreadOnly=       any signed-in user of the shop
GET  /v1/owner-notifications/unread-count
POST /v1/owner-notifications/{id}/read         404 for another shop's row
POST /v1/owner-notifications/read-all
```

`discover` returns `searchUnavailable: true` with a reason (and no shops)
when the caller's own shop has not opted in or has no location - it never
errors, and it never leaks a match to a shop that is not in the network.

## Frontend

- **Shop settings → "Product discovery sharing"** (`DiscoverySharingCard`):
  the explanation up front, five checkboxes with what each one reveals,
  location (browser geolocation or typed), radius. Turning it **on** asks
  twice - a list of exactly what will be shared, then typing `ENABLE`.
  Turning it **off** is one click. Premium badge; a Basic shop gets the
  upgrade dialog on save.
- **Product request → "Nearby availability"** (`NearbyAvailabilityPanel`):
  search / search again, one row per shop with whatever it shared, "about
  N km", Available / Likely available, Call (`tel:`) and WhatsApp (bare
  `wa.me`, no text). A withheld name reads "A nearby hardware shop";
  withheld contact says so. The panel renders nothing at all on a plan
  without the feature.
- **Notifications** (`/notifications`, sidebar utility list): unread/all,
  mark read, "View nearby availability" deep link.

## Honest deviations

- **"Approximate location"** is a distance, never a rounded pin or a
  locality name - the brief allows either and distance leaks strictly
  less.
- **PostGIS** is not introduced: at hundreds of shops a Haversine over the
  partial index is a few milliseconds; the brief's "for larger-scale
  location queries, consider PostGIS" is noted for when scale demands it.
- **Push / WhatsApp / email delivery of the owner alert** is not wired in
  this pass; the alert is in-app (`owner_notification`). CR-092's
  notification work is the place to add channels, metered by CR-088.

## Tests

`ShopDiscoveryIT` (8, real PostgreSQL): opted-in 2 km shop found with
every permitted field and no id/quantity/price; a shop that never opted in
is not; 50 km shop outside a 5 km radius is not; name/phone/distance each
withheld per flag and absent from the whole response body; opting out hides
a shop on the very next search and clears its sub-flags; a non-participant
cannot search and learns nothing; enabling without a location is 422; the
owner is notified with the product and count and never a shop or the
customer, another shop cannot mark it read; a Basic shop is refused.
