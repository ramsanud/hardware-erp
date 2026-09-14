# Manual WhatsApp integration (CR-080)

One click opens the official WhatsApp app (on a phone) or WhatsApp Web (on a
desktop) **on the customer's own chat, with the message already typed**. The
shop owner reads it and presses Send.

That sentence is the whole feature. Everything below is what it deliberately
is not, and how the pieces fit.

## What it is

A link of the form

```
https://wa.me/919876543210?text=Hello%20Ravi%20Kumar%20%F0%9F%91%8B%0A...
```

generated **server-side** from the customer's stored mobile number and one of
five templates, returned to the browser, and opened in a new tab with
`noopener,noreferrer`. WhatsApp itself decodes the `text` parameter into the
message box. Nothing has been sent until the human taps Send.

| | |
|---|---|
| Cost | Free. No Meta Business account, no registered templates, no per-message price. |
| Credentials | None exist. There is nothing to configure and nothing to leak. |
| Storage | Nothing. No table, no log row, no message history. |
| Sending | **Never by the application.** Each message is one chat, one human tap. |
| Bulk | Not possible, by design — that is precisely what keeps it free of Meta's rules. |

## What it is not

- Not the **WhatsApp Cloud API**. That is CR-056 (`WhatsAppBusinessProvider`),
  unchanged, and it still needs a shop to connect its own WhatsApp Business
  account under Settings. The two coexist: the manual link works for every
  shop; the automatic send works for a connected one.
- Not Twilio, not WhatsApp Web automation, not an unofficial library, not a
  QR-code login, not scraping, not bulk messaging.

## Where the pieces are

```
backend/…/common/util/PhoneNumberNormalizer.java       one normaliser, shared with the Meta and Twilio providers
backend/…/notification/whatsapp/WhatsAppService.java   generateChatUrl(phone, message)
backend/…/notification/whatsapp/ManualWhatsAppService  the only implementation today
backend/…/notification/whatsapp/WhatsAppUrlBuilder     wa.me + percent-encoding, one place
backend/…/notification/whatsapp/WhatsAppMessageTemplates   invoice / quotation / receipt / reminder / greeting
backend/…/notification/whatsapp/WhatsAppLinkService    entity-aware: which customer, which template
backend/…/notification/whatsapp/WhatsAppLinkController GET /v1/whatsapp/links/…
frontend/src/shared/components/WhatsAppButton.tsx      the one button, reused everywhere
frontend/src/modules/notification/services/whatsAppLinkService.ts
```

### Endpoints

All `GET`, all side-effect free, each gated by the VIEW permission of the
document it describes:

| Path | Permission | Message |
|---|---|---|
| `/v1/whatsapp/links/invoices/{id}` | `INVOICE_VIEW` | invoice summary: number, total, paid, balance |
| `/v1/whatsapp/links/invoices/{id}/reminder` | `INVOICE_VIEW` | payment reminder; 422 if the invoice is paid or cancelled |
| `/v1/whatsapp/links/invoices/{id}/payments/{paymentId}` | `PAYMENT_VIEW` | receipt for one payment |
| `/v1/whatsapp/links/quotations/{id}` | `QUOTATION_VIEW` | quotation summary with validity date |
| `/v1/whatsapp/links/customers/{id}` | `CUSTOMER_VIEW` | greeting |

Response: `{ url, toMobileNo, message }`. `url` is what to open; `message` is
the plain text for display; `toMobileNo` is the number as the shop entered it.

### Tenant isolation

Inherited, not re-implemented. Every lookup goes through the existing
tenant-scoped `InvoiceService.get` / `QuotationService.get` /
`CustomerService.get`. A foreign tenant's id is a 404 exactly as it is on the
document's own endpoint, and `WhatsAppLinkControllerIT` proves the 404 body
carries neither the number nor the name.

### Phone numbers

`PhoneNumberNormalizer.toE164` is the single rule:

| Input | Result |
|---|---|
| `9876543210` | `+919876543210` |
| `+91 98765 43210`, `+91-98765-43210`, `(+91) 98765.43210` | `+919876543210` |
| `919876543210`, `09876543210` | `+919876543210` |
| `+44 20 7946 0958`, `0044 20 7946 0958` | `+442079460958` |
| `98765432101`, `4155550123`, `5876543210`, `12345` | **refused** — never guessed |

Bare digits are only ever read as Indian, because that is how this
application stores every mobile. Anything else is refused with
"Please add a valid customer WhatsApp number." rather than dialled wrong.

### Encoding

`URLEncoder` then `+` → `%20`, giving plain RFC 3986 percent-encoding. Tamil,
emoji, `&`, `?`, `/`, `#`, `%`, quotes and a literal `+` all round-trip
unchanged — `ManualWhatsAppServiceTest` decodes the URL back and compares.
Message text is never concatenated raw into a URL.

### Privacy

A wa.me URL **is** the customer's phone number and the message. It is never
logged, at any level, anywhere. Nor is the number or the message text.

## Adding a future implementation

Implement `WhatsAppService`. Callers (`WhatsAppLinkService`, and through it
every button) depend on the interface only. A connected-shop implementation
could, for example, return a deep link into an already-sent conversation
while the manual one keeps serving everyone else — decided per tenant, not by
a rebuild of invoice, quotation or customer code.

## Running the tests

```bash
cd backend  && mvn -o test -Dtest='PhoneNumberNormalizerTest,ManualWhatsAppServiceTest,WhatsAppLinkServiceTest'
cd backend  && mvn -o verify -Dit.test=WhatsAppLinkControllerIT   # needs Docker
cd frontend && node tests/run.mjs                                   # the 'whatsapp' suite, against dist/
```
