# Module 1 - Authentication & User Management

## Quick start (5 minutes)

```bash
docker compose up -d                          # PostgreSQL 16
cd backend && ./mvnw spring-boot:run          # http://localhost:8080/api
cd frontend && npm install && npm run dev     # http://localhost:5173
```

Sign in with `9876543210` / `Owner@2026` (development seed data only).

## Postman - import and test in minutes

```
postman/hardware-erp-module-01.postman_collection.json
postman/hardware-erp-module-01.postman_environment.json
```

1. Postman > **Import** > drop both files
2. Select **Hardware ERP - Local (PostgreSQL)** in the environment dropdown, top right
3. Run **1. Authentication > Login as OWNER** - the token saves itself
4. Or use the Collection Runner to fire all 54 requests at once

54 requests covering all 25 endpoints. Folder 5 asserts security rejections:
a 200 there is a failure, not a success.

## Documentation

PDFs are in `pdf/`. Markdown sources are in `src/` - edit those, then run:

```bash
python3 docs/build_pdfs.py
```

| # | Document | Status |
|---|---|---|
| 01 | Project overview | Written |
| 02 | Development environment setup | Written |
| 03 | PostgreSQL setup | Written |
| 04 | Flyway database migration | Written |
| 05 | Backend architecture | Not written |
| 06 | Backend development guide | Not written |
| 07 | Authentication, JWT and security | Not written |
| 08 | API and Swagger guide | Not written |
| 09 | Postman API testing | Written |
| 10 | Frontend development guide | Not written |
| 11 | Frontend/backend integration | Not written |
| 12 | Testing and Testcontainers | Not written |
| 13 | Dependencies and libraries | Not written |
| 14 | Seed data and test data | Not written |
| 15 | Security testing guide | Not written |
| 16 | Module 1 complete verification | Written |
| 17 | Troubleshooting guide | Not written |
| 18 | Architecture and flow diagrams | Not written |

## Status

Module 1 is **NOT COMPLETE**. See document 16 for the gate-by-gate status.
The backend has never been compiled and the 149 tests have never run, because
the authoring environment could not reach Maven Central or run Docker.
