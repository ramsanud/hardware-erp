# 16 - Module 1 Complete Verification
The honest status of every gate, and how to close the ones that are open.

## What is this?

A checklist of everything Module 1 must satisfy, with the current state of each
and the exact command that proves it.

> [!IMPORTANT] Nothing here is marked COMPLETE without evidence
> "The code exists" is not evidence. "This command was run and produced this
> output" is evidence. Several gates below are marked **BLOCKED**, meaning the
> work is done but could not be verified in the environment where it was
> written. You must close those on your own machine.

## Status key

| Word | Meaning |
|---|---|
| **COMPLETE** | Built, and verified by a command that was actually executed |
| **PARTIAL** | Built, but part of the required scope is missing |
| **BLOCKED** | Built, but could not be verified here due to the environment |
| **NOT VERIFIED** | Built, but nobody has checked it yet |

## The status table

| Component | Status | Evidence |
|---|---|---|
| Backend source | COMPLETE | 88 Java files; `static_check.py` passes |
| PostgreSQL migration | COMPLETE | No MySQL syntax in any executable line; checker enforces it |
| Flyway migrations | NOT VERIFIED | 2 files written; never executed against a live database |
| Seed data | PARTIAL | Written; BCrypt hashes verified against plaintext; migration never run |
| Backend tests | BLOCKED | 11 files, 149 methods written; never executed |
| Testcontainers | BLOCKED | `PostgreSQLContainer("postgres:16-alpine")` wired; Docker unavailable |
| Swagger | NOT VERIFIED | Annotations and config written; page never opened |
| Postman | COMPLETE | 54 requests, all 25 endpoints covered; JSON validated |
| Frontend | COMPLETE | 71 files, 9 pages; `npm run build` exit 0 |
| Frontend/API integration | NOT VERIFIED | Services match controllers; never run against a live backend |
| Documentation | PARTIAL | 6 of 18 planned PDFs written |
| Architecture diagrams | NOT STARTED | Planned for document 18 |
| Static checks | COMPLETE | `python3 registry/static_check.py` passes |
| Maven verify | BLOCKED | Maven Central unreachable in the authoring environment |
| Full integration tests | BLOCKED | Requires Docker |
| **Module 1 overall** | **NOT COMPLETE** | 5 gates blocked, 2 partial |

## Why some gates are blocked

> [!IMPORTANT] BUG-ENV-001, recorded in the bug registry
> The environment this code was written in cannot reach Maven Central and cannot
> run Docker. Therefore:
> - `mvn clean verify` has never run - the backend has **never been compiled**
> - The 149 tests have never executed
> - Flyway has never applied a migration to a real database
> - Swagger has never been opened
>
> Expect to fix a small number of import-level errors on your first compile.
> That is normal for code that has been statically checked but not built. The
> static checker verifies package declarations, brace and parenthesis balance,
> entity-to-migration column agreement, interface-to-implementation method
> coverage, permission constants, and seed-to-schema column agreement - but it
> cannot resolve a Java type.

## Closing the blocked gates

Run these in order on a machine with Docker and internet access.

### Gate 1 - the backend compiles

> [!COMMAND] From the project root
> ```
> cd backend
> ./mvnw clean compile
> ```

> [!SUCCESS] Expected
> ```
> [INFO] BUILD SUCCESS
> ```

> [!TROUBLESHOOTING] If it fails
> Read the first error only; later ones are usually consequences. The likely
> causes are a missing import or a method signature that drifted. Fix, re-run.
> Record anything you fix in `project-knowledge/BUG_REGISTRY.md`.

### Gate 2 - the tests pass

> [!COMMAND] Docker must be running
> ```
> docker info > /dev/null && echo "docker ok"
> cd backend
> ./mvnw clean verify
> ```

> [!SUCCESS] Expected
> ```
> Tests run: 149, Failures: 0, Errors: 0, Skipped: 0
> [INFO] BUILD SUCCESS
> ```
> The first run is slow - Testcontainers downloads `postgres:16-alpine`.

> [!VERIFY] What passing actually proves
> - Real PostgreSQL, not H2, so CHECK constraints and functional indexes are
>   genuinely exercised
> - Flyway ran V1 and V900 successfully against a real database
> - The seeded BCrypt hashes really do authenticate
> - Enumeration protection holds: wrong-password and unknown-account responses
>   are byte-identical
> - Refresh token reuse revokes every session
> - The last active owner cannot be removed

### Gate 3 - Flyway and seed data on your own database

> [!COMMAND]
> ```
> docker compose up -d
> cd backend && ./mvnw spring-boot:run
> ```

> [!COMMAND] In another terminal
> ```
> docker exec -it hardware-erp-postgres psql -U hardware_erp -d hardware_erp \
>   -c "SELECT version, description, success FROM flyway_schema_history;"
> docker exec -it hardware-erp-postgres psql -U hardware_erp -d hardware_erp \
>   -c "SELECT count(*) AS users FROM app_user;"
> ```

> [!SUCCESS] Expected
> Versions 1 and 900, both `success = t`, and 12 users.

### Gate 4 - Swagger

> [!COMMAND] With the backend running, open in a browser
> ```
> http://localhost:8080/api/swagger-ui.html
> ```

> [!SUCCESS] Expected
> A page titled "Hardware ERP API", with five tag groups: Authentication, Users,
> Roles, Permissions and Security Audit Log. Expand
> `POST /v1/auth/login`, click **Try it out**, send
> `{"identifier":"9876543210","password":"Owner@2026"}` and get a 200 with a
> token. Copy the `accessToken` value, click **Authorize** at the top right,
> paste it, and protected endpoints then work.

### Gate 5 - Postman

Follow document 09. The Collection Runner should report all 54 requests green.

### Gate 6 - the frontend against the live backend

> [!COMMAND]
> ```
> cd frontend
> npm install
> npm run dev
> ```
> Then open `http://localhost:5173` and sign in as `9876543210` / `Owner@2026`.

> [!VERIFY] Walk through every page
> 1. **Login** - sign in as OWNER
> 2. **Profile** - your name, role and permissions appear; the Sessions tab lists
>    this device
> 3. **Users** - 11 users listed, the soft-deleted one absent; search for
>    "Karthik" narrows it to one
> 4. Create a user, edit it, deactivate it; confirm it leaves the list
> 5. **Roles** - five roles; open STAFF and confirm `PRODUCT_VIEW_COST` is
>    unticked
> 6. **Permissions** - 31 permissions grouped by module
> 7. **Security log** - shows the LOGIN_SUCCESS your sign-in just created
> 8. Sign out, then sign in as STAFF (`9843012345` / `Staff@2026`). The Users,
>    Roles and Security log links must be **absent** from the sidebar
> 9. With STAFF signed in, type `/users` in the address bar directly. You should
>    get an access message, not the user list

> [!WHY] Why step 9 matters
> Steps 1 to 8 test the happy path. Step 9 tests that hiding a link is not the
> only thing stopping access. Even if the guard were removed, every API call the
> page makes would return 403, because the backend checks the permission
> independently.

## The already-verified gates

These were genuinely executed. The commands and their output:

### Static consistency check

> [!COMMAND]
> ```
> python3 registry/static_check.py
> ```

> [!SUCCESS] Actual output
> ```
> ==================================================================
>   STATIC CONSISTENCY CHECK
>   88 main sources, 11 test sources, 7 tables
> ==================================================================
>
>   PASS - no structural inconsistencies
>   NOTE: this is not a compile.
> ```

### Frontend build

> [!COMMAND]
> ```
> cd frontend && npm run build
> ```

> [!SUCCESS] Actual output
> ```
> vite v6.4.3 building for production...
> 1794 modules transformed.
> dist/assets/vendor-forms-*.js    87.82 kB
> dist/assets/vendor-ui-*.js      114.92 kB
> dist/assets/vendor-react-*.js   165.80 kB
> dist/assets/index-*.js          193.54 kB
> built in 6.72s
> ```

### Postman endpoint coverage

> [!SUCCESS] Actual output
> ```
> BACKEND ENDPOINTS: 25
> COVERED BY POSTMAN: 25
> MISSING: none - full coverage
> ```

## What is genuinely not built yet

> [!IMPORTANT] Remaining work, stated plainly
> - **12 of the 18 planned PDFs.** Written: 01, 02, 03, 04, 09, 16.
>   Not written: 05, 06, 07, 08, 10, 11, 12, 13, 14, 15, 17, 18.
> - **Architecture diagrams** (document 18) - none produced yet
> - **Frontend unit tests** - `src/modules/auth/tests/` is an empty folder
> - **Swagger has never been opened**, so the annotations are unproven

## Sign-off

> [!VERIFY] Module 1 may be called COMPLETE only when all of these are true
> ```
> [ ] mvn clean compile          BUILD SUCCESS
> [ ] mvn clean verify           149 tests, 0 failures
> [ ] docker compose up -d       container healthy
> [ ] Flyway                     versions 1 and 900, success = t
> [ ] Seed data                  12 users present
> [ ] Swagger                    loads, Authorize works, endpoints respond
> [ ] Postman                    54 requests green
> [ ] Frontend build             exit 0
> [ ] Frontend to backend        all 9 pages work against the live API
> [ ] STAFF restriction          cannot reach /users even by typing the URL
> [ ] static_check.py            PASS
> [ ] Documentation              all 18 PDFs written
> [ ] Diagrams                   produced
> ```
> Until every line is ticked, the status stays **NOT COMPLETE**. Module 2 does
> not start before then.
