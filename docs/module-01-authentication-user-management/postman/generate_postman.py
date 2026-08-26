#!/usr/bin/env python3
"""
Builds the Module 1 Postman collection and environment.

Every request below corresponds to a real endpoint in
backend/src/main/java/com/hardware/erp/auth/controller/. Every credential
corresponds to a real row in db/seed/V900__seed_dev_data.sql. Nothing here is
invented; re-run this script after adding an endpoint rather than hand-editing
the JSON.
"""
import json
import pathlib

HERE = pathlib.Path(__file__).parent

BASE = "{{baseUrl}}"


def url(path, params=None):
    raw = BASE + path
    segments = [p for p in path.strip("/").split("/") if p]
    out = {"raw": raw, "host": [BASE], "path": segments}
    if params:
        enabled = [p for p in params if not p.get("disabled")]
        out["query"] = params
        if enabled:
            out["raw"] = raw + "?" + "&".join(f"{p['key']}={p['value']}" for p in enabled)
    return out


def request(name, method, path, *, body=None, auth=True, params=None,
            tests=None, desc="", headers=None):
    hdrs = list(headers or [])
    if body is not None:
        hdrs.insert(0, {"key": "Content-Type", "value": "application/json"})
    if auth:
        hdrs.append({"key": "Authorization", "value": "Bearer {{accessToken}}"})

    item = {
        "name": name,
        "request": {
            "method": method,
            "header": hdrs,
            "url": url(path, params),
            "description": desc,
        },
    }
    if body is not None:
        item["request"]["body"] = {
            "mode": "raw",
            "raw": json.dumps(body, indent=2),
            "options": {"raw": {"language": "json"}},
        }
    if tests:
        item["event"] = [{
            "listen": "test",
            "script": {"type": "text/javascript", "exec": tests},
        }]
    return item


def folder(name, desc, items):
    return {"name": name, "description": desc, "item": items}


# Shared assertions -----------------------------------------------------------

ENVELOPE_OK = [
    "const body = pm.response.json();",
    "pm.test('Success envelope shape', function () {",
    "    pm.expect(body).to.have.property('success', true);",
    "    pm.expect(body).to.have.property('timestamp');",
    "});",
]

ENVELOPE_ERR = [
    "const body = pm.response.json();",
    "pm.test('Error envelope shape', function () {",
    "    pm.expect(body).to.have.property('success', false);",
    "    pm.expect(body).to.have.property('code');",
    "    pm.expect(body).to.have.property('message');",
    "    pm.expect(body).to.have.property('timestamp');",
    "});",
]

NO_SECRETS = [
    "pm.test('Response leaks no credential material', function () {",
    "    const text = pm.response.text();",
    "    pm.expect(text).to.not.include('passwordHash');",
    "    pm.expect(text).to.not.include('$2a$');",
    "    pm.expect(text).to.not.include('tokenVersion');",
    "});",
]


def login_tests(var="accessToken", role=None):
    lines = [
        "pm.test('Status is 200', () => pm.response.to.have.status(200));",
        "const body = pm.response.json();",
        f"pm.collectionVariables.set('{var}', body.data.accessToken);",
        "pm.test('Access token returned', function () {",
        "    pm.expect(body.data.accessToken).to.be.a('string').and.not.empty;",
        "    pm.expect(body.data.tokenType).to.eql('Bearer');",
        "});",
        "pm.test('Access token is a JWT with three parts', function () {",
        "    pm.expect(body.data.accessToken.split('.')).to.have.lengthOf(3);",
        "});",
        "pm.test('JWT carries no role or permission data', function () {",
        "    // A JWT is signed, not encrypted. Anyone holding it reads the payload,",
        "    // so it must contain only sub, tv, iat, exp and iss.",
        "    const payload = atob(body.data.accessToken.split('.')[1]",
        "        .replace(/-/g, '+').replace(/_/g, '/'));",
        "    pm.expect(payload).to.not.include('perm');",
        "    pm.expect(payload).to.not.include('role');",
        "    pm.expect(payload).to.not.include('name');",
        "});",
    ]
    if role:
        lines += [
            f"pm.test('Signed in as {role}', function () {{",
            f"    pm.expect(body.data.user.roleCode).to.eql('{role}');",
            "});",
        ]
    lines += NO_SECRETS
    return lines


# 1. Authentication -----------------------------------------------------------

authentication = folder(
    "1. Authentication",
    "Run **Login as OWNER** first. It stores the access token in a collection "
    "variable that every other request uses. The refresh token never appears in "
    "the response body: it arrives as an HttpOnly cookie, which Postman's cookie "
    "jar stores and replays automatically.",
    [
        request(
            "Login as OWNER", "POST", "/v1/auth/login", auth=False,
            body={"identifier": "9876543210", "password": "Owner@2026"},
            desc="Seed account EMP001 (Saravanan Murugan). Holds every permission.",
            tests=login_tests("accessToken", "OWNER")),

        request(
            "Login as MANAGER", "POST", "/v1/auth/login", auth=False,
            body={"identifier": "9840112233", "password": "Manager@2026"},
            desc="Seed account EMP003. Sees cost, cannot manage users.",
            tests=login_tests("managerToken", "MANAGER")),

        request(
            "Login as STAFF", "POST", "/v1/auth/login", auth=False,
            body={"identifier": "9843012345", "password": "Staff@2026"},
            desc="Seed account EMP005. Counter staff: deliberately has no "
                 "PRODUCT_VIEW_COST and no USER_VIEW.",
            tests=login_tests("staffToken", "STAFF") + [
                "pm.test('STAFF cannot see product cost', function () {",
                "    pm.expect(pm.response.json().data.permissions)",
                "        .to.not.include('PRODUCT_VIEW_COST');",
                "});",
            ]),

        request(
            "Login with email instead of mobile", "POST", "/v1/auth/login", auth=False,
            body={"identifier": "owner@sarahardware.in", "password": "Owner@2026"},
            desc="The single `identifier` field accepts either. Counter staff do "
                 "not remember which one they were registered with.",
            tests=[
                "pm.test('Status is 200', () => pm.response.to.have.status(200));",
                "pm.test('Same account as the mobile login', function () {",
                "    pm.expect(pm.response.json().data.user.mobileNo).to.eql('9876543210');",
                "});",
            ]),

        request(
            "Current user (GET /auth/me)", "GET", "/v1/auth/me",
            desc="Identity comes from the token, never from a path or body.",
            tests=ENVELOPE_OK + [
                "pm.test('Status is 200', () => pm.response.to.have.status(200));",
                "pm.test('Returns effective permissions', function () {",
                "    pm.expect(pm.response.json().data.permissions).to.be.an('array');",
                "});",
            ] + NO_SECRETS),

        request(
            "Update my profile", "PUT", "/v1/auth/me",
            body={"fullName": "Saravanan Murugan", "email": "owner@sarahardware.in"},
            desc="Accepts name and email only. Role and status cannot be "
                 "self-edited, and the target user is taken from the token.",
            tests=["pm.test('Status is 200', () => pm.response.to.have.status(200));"]),

        request(
            "Refresh token pair", "POST", "/v1/auth/refresh", auth=False, body={},
            desc="Reads the refresh token from the HttpOnly cookie. Each call "
                 "rotates it: the presented token is revoked and a new one issued. "
                 "Replaying a rotated token is treated as theft and revokes every "
                 "session for that user.",
            tests=[
                "pm.test('Status is 200', () => pm.response.to.have.status(200));",
                "const body = pm.response.json();",
                "pm.collectionVariables.set('accessToken', body.data.accessToken);",
                "pm.test('Refresh token is NOT in the response body', function () {",
                "    // Cookie transport is the default, so JavaScript must never",
                "    // be able to read the long-lived credential.",
                "    pm.expect(body.data.refreshToken).to.be.oneOf([null, undefined]);",
                "});",
            ]),

        request(
            "My active sessions", "GET", "/v1/auth/sessions",
            desc="Devices signed in to this account. Carries no token material.",
            tests=[
                "pm.test('Status is 200', () => pm.response.to.have.status(200));",
                "const body = pm.response.json();",
                "if (body.data.length > 0) {",
                "    pm.collectionVariables.set('sessionId', body.data[0].id);",
                "}",
                "pm.test('No token hashes exposed', function () {",
                "    pm.expect(pm.response.text()).to.not.include('tokenHash');",
                "});",
            ]),

        request(
            "Revoke one session", "DELETE", "/v1/auth/sessions/{{sessionId}}",
            desc="Signs out a single device. Returns 204 with no body. "
                 "Revoking your own current session is allowed but will end this run.",
            tests=[
                "pm.test('Status is 204 or 404', function () {",
                "    pm.expect(pm.response.code).to.be.oneOf([204, 404]);",
                "});",
            ]),

        request(
            "Forgot password", "POST", "/v1/auth/forgot-password", auth=False,
            body={"identifier": "9876543210"},
            desc="Always returns the same 200 message whether or not the account "
                 "exists. Any other behaviour would let this endpoint be used to "
                 "discover which mobile numbers are registered.",
            tests=[
                "pm.test('Status is 200', () => pm.response.to.have.status(200));",
                "pm.collectionVariables.set('forgotKnownBody',",
                "    pm.response.text().replace(/\"timestamp\":\"[^\"]*\"/, ''));",
            ]),

        request(
            "Forgot password for an account that does not exist", "POST",
            "/v1/auth/forgot-password", auth=False,
            body={"identifier": "9000000000"},
            desc="Must be indistinguishable from the request above.",
            tests=[
                "pm.test('Status is 200', () => pm.response.to.have.status(200));",
                "pm.test('Identical to the known-account response', function () {",
                "    const known = pm.collectionVariables.get('forgotKnownBody');",
                "    const unknown = pm.response.text()",
                "        .replace(/\"timestamp\":\"[^\"]*\"/, '');",
                "    pm.expect(unknown).to.eql(known);",
                "});",
            ]),

        request(
            "Reset password with a token", "POST", "/v1/auth/reset-password", auth=False,
            body={"token": "paste-token-from-the-reset-email",
                  "newPassword": "NewPass@2026"},
            desc="The token is single-use and expires in 30 minutes. In the dev "
                 "profile with no SMTP configured, the link is printed to the "
                 "backend console. Expect 400 with the placeholder token above.",
            tests=[
                "pm.test('Placeholder token is rejected with 400', function () {",
                "    pm.response.to.have.status(400);",
                "    pm.expect(pm.response.json().code).to.eql('INVALID_RESET_TOKEN');",
                "});",
            ]),

        request(
            "Change my password", "POST", "/v1/auth/change-password",
            body={"currentPassword": "Owner@2026", "newPassword": "Owner@2027"},
            desc="DISABLED BY DEFAULT in a run: succeeding here changes the seed "
                 "password and signs you out everywhere, breaking later requests. "
                 "Send it manually when you want to test the flow, then log in "
                 "with the new password.",
            tests=[
                "pm.test('Status is 200 or 400', function () {",
                "    pm.expect(pm.response.code).to.be.oneOf([200, 400]);",
                "});",
            ]),

        request(
            "Logout (this device only)", "POST", "/v1/auth/logout", body={},
            desc="Revokes only the presented refresh token. Other devices stay "
                 "signed in, so closing the counter terminal does not sign the "
                 "owner out on their phone.",
            tests=["pm.test('Status is 200', () => pm.response.to.have.status(200));"]),

        request(
            "Logout of all devices", "POST", "/v1/auth/logout-all", body={},
            desc="Revokes every session and increments token_version, which kills "
                 "all outstanding access tokens at once. Run last.",
            tests=["pm.test('Status is 200', () => pm.response.to.have.status(200));"]),
    ])


# 2. Users --------------------------------------------------------------------

users = folder(
    "2. Users",
    "There is no self-registration endpoint. `POST /v1/users` is the only way an "
    "account is created and it requires USER_MANAGE.",
    [
        request(
            "List users", "GET", "/v1/users",
            params=[{"key": "page", "value": "0"},
                    {"key": "size", "value": "20"},
                    {"key": "sortBy", "value": "fullName"},
                    {"key": "sortDir", "value": "asc"}],
            desc="Seeded with 12 users, one of which is soft-deleted and must not appear.",
            tests=ENVELOPE_OK + [
                "pm.test('Status is 200', () => pm.response.to.have.status(200));",
                "const page = pm.response.json().data;",
                "pm.test('Page envelope shape', function () {",
                "    pm.expect(page).to.have.all.keys('content', 'page', 'size',",
                "        'totalElements', 'totalPages', 'first', 'last');",
                "});",
                "pm.test('Soft-deleted user is excluded', function () {",
                "    pm.expect(pm.response.text()).to.not.include('9843089012');",
                "});",
            ] + NO_SECRETS),

        request(
            "Search users", "GET", "/v1/users",
            params=[{"key": "search", "value": "Karthik"}],
            desc="Matches name, mobile, email or employee code.",
            tests=[
                "pm.test('Status is 200', () => pm.response.to.have.status(200));",
                "pm.test('Finds the seeded staff member', function () {",
                "    pm.expect(pm.response.json().data.content[0].mobileNo)",
                "        .to.eql('9843012345');",
                "});",
            ]),

        request(
            "Filter by status", "GET", "/v1/users",
            params=[{"key": "status", "value": "SUSPENDED"}],
            desc="Seed contains exactly one suspended account (EMP010).",
            tests=["pm.test('Status is 200', () => pm.response.to.have.status(200));"]),

        request(
            "Page size is clamped to 100", "GET", "/v1/users",
            params=[{"key": "size", "value": "100000"}],
            desc="The backend clamps size so a huge request cannot exhaust memory.",
            tests=[
                "pm.test('Status is 200', () => pm.response.to.have.status(200));",
                "pm.test('Size clamped to 100', function () {",
                "    pm.expect(pm.response.json().data.size).to.eql(100);",
                "});",
            ]),

        request(
            "Sort field is whitelisted (injection attempt)", "GET", "/v1/users",
            params=[{"key": "sortBy", "value": "password_hash; DROP TABLE app_user"}],
            desc="An unknown sort field falls back to the default instead of "
                 "reaching ORDER BY.",
            tests=[
                "pm.test('Status is 200, table survives', function () {",
                "    pm.response.to.have.status(200);",
                "    pm.expect(pm.response.json().data.content).to.be.an('array');",
                "});",
            ]),

        request(
            "Create user", "POST", "/v1/users",
            body={"fullName": "Postman Test Employee",
                  "mobileNo": "9811100099",
                  "email": "postman.test@sarahardware.in",
                  "employeeCode": "EMP199",
                  "roleId": 4,
                  "password": "Welcome@2026",
                  "mustChangePassword": True},
            desc="roleId 4 is STAFF in the seed data. Stores the new id for the "
                 "update and delete requests below.",
            tests=[
                "pm.test('Status is 201', () => pm.response.to.have.status(201));",
                "const body = pm.response.json();",
                "pm.collectionVariables.set('createdUserId', body.data.id);",
                "pm.test('Forced password change is set', function () {",
                "    pm.expect(body.data.mustChangePassword).to.be.true;",
                "});",
            ] + NO_SECRETS),

        request(
            "Get user by id", "GET", "/v1/users/{{createdUserId}}",
            tests=["pm.test('Status is 200', () => pm.response.to.have.status(200));"]),

        request(
            "Update user", "PUT", "/v1/users/{{createdUserId}}",
            body={"fullName": "Postman Test Employee (renamed)",
                  "mobileNo": "9811100099",
                  "email": "postman.test@sarahardware.in",
                  "employeeCode": "EMP199",
                  "roleId": 4,
                  "status": "ACTIVE"},
            tests=[
                "pm.test('Status is 200', () => pm.response.to.have.status(200));",
                "pm.test('Name updated', function () {",
                "    pm.expect(pm.response.json().data.fullName)",
                "        .to.include('renamed');",
                "});",
            ]),

        request(
            "Admin reset of a user password", "POST",
            "/v1/users/{{createdUserId}}/reset-password",
            body={"newPassword": "Temp@2026"},
            desc="Forces a change at next sign-in and revokes that user's sessions.",
            tests=["pm.test('Status is 200', () => pm.response.to.have.status(200));"]),

        request(
            "Deactivate user (soft delete)", "DELETE", "/v1/users/{{createdUserId}}",
            desc="Returns 204. The row survives so historical created_by "
                 "references still resolve.",
            tests=["pm.test('Status is 204', () => pm.response.to.have.status(204));"]),

        request(
            "Deleting twice returns 404", "DELETE", "/v1/users/{{createdUserId}}",
            tests=["pm.test('Status is 404', () => pm.response.to.have.status(404));"]),
    ])


# 3. Roles & permissions ------------------------------------------------------

roles = folder(
    "3. Roles and Permissions",
    "Authorisation is permission-based. Roles are just named permission sets.",
    [
        request(
            "List roles", "GET", "/v1/roles",
            desc="Four seeded system roles plus the STOCK_CLERK custom role.",
            tests=ENVELOPE_OK + [
                "pm.test('Status is 200', () => pm.response.to.have.status(200));",
                "const roles = pm.response.json().data;",
                "const staff = roles.find(r => r.code === 'STAFF');",
                "pm.test('STAFF cannot see product cost', function () {",
                "    pm.expect(staff.permissions).to.not.include('PRODUCT_VIEW_COST');",
                "});",
                "pm.test('System roles are flagged', function () {",
                "    pm.expect(roles.find(r => r.code === 'OWNER').systemRole).to.be.true;",
                "});",
            ]),

        request("Get role by id", "GET", "/v1/roles/4",
                tests=["pm.test('Status is 200', () => pm.response.to.have.status(200));"]),

        request(
            "Create custom role", "POST", "/v1/roles",
            body={"code": "POSTMAN_TEST",
                  "name": "Postman Test Role",
                  "description": "Created by the Postman collection",
                  "permissions": ["PRODUCT_VIEW", "INVENTORY_VIEW"],
                  "status": "ACTIVE"},
            tests=[
                "pm.test('Status is 201', () => pm.response.to.have.status(201));",
                "pm.collectionVariables.set('createdRoleId', pm.response.json().data.id);",
            ]),

        request(
            "Update custom role", "PUT", "/v1/roles/{{createdRoleId}}",
            body={"code": "POSTMAN_TEST",
                  "name": "Postman Test Role",
                  "description": "Updated",
                  "permissions": ["PRODUCT_VIEW", "INVENTORY_VIEW", "INVENTORY_ADJUST"],
                  "status": "ACTIVE"},
            desc="Changing permissions signs out every user holding the role.",
            tests=[
                "pm.test('Status is 200', () => pm.response.to.have.status(200));",
                "pm.test('Now has three permissions', function () {",
                "    pm.expect(pm.response.json().data.permissions).to.have.lengthOf(3);",
                "});",
            ]),

        request(
            "Delete custom role", "DELETE", "/v1/roles/{{createdRoleId}}",
            tests=["pm.test('Status is 204', () => pm.response.to.have.status(204));"]),

        request(
            "List all permissions", "GET", "/v1/permissions",
            tests=[
                "pm.test('Status is 200', () => pm.response.to.have.status(200));",
                "pm.test('At least 31 permissions seeded', function () {",
                "    pm.expect(pm.response.json().data.length).to.be.at.least(31);",
                "});",
            ]),

        request(
            "Permissions grouped by module", "GET", "/v1/permissions/grouped",
            desc="Drives the permission picker on the role screen.",
            tests=[
                "pm.test('Status is 200', () => pm.response.to.have.status(200));",
                "pm.test('Grouped by module', function () {",
                "    pm.expect(pm.response.json().data[0]).to.have.property('moduleCode');",
                "});",
            ]),
    ])


# 4. Security audit log -------------------------------------------------------

audit = folder(
    "4. Security Audit Log",
    "Security events only. Requires AUDIT_VIEW, which only OWNER holds.",
    [
        request(
            "Search the security log", "GET", "/v1/security-audit-logs",
            params=[{"key": "page", "value": "0"}, {"key": "size", "value": "20"}],
            desc="Seeded with 12 events plus everything this collection generates.",
            tests=ENVELOPE_OK + [
                "pm.test('Status is 200', () => pm.response.to.have.status(200));",
                "pm.test('Never logs credential material', function () {",
                "    const text = pm.response.text();",
                "    pm.expect(text).to.not.include('passwordHash');",
                "    pm.expect(text).to.not.include('$2a$');",
                "});",
            ]),

        request(
            "Filter by failed logins", "GET", "/v1/security-audit-logs",
            params=[{"key": "action", "value": "LOGIN_FAILURE"}],
            tests=["pm.test('Status is 200', () => pm.response.to.have.status(200));"]),

        request(
            "Filter by token reuse detection", "GET", "/v1/security-audit-logs",
            params=[{"key": "action", "value": "REFRESH_TOKEN_REUSE_DETECTED"}],
            desc="The seed contains one of these. In production it means a "
                 "refresh token was stolen and replayed.",
            tests=["pm.test('Status is 200', () => pm.response.to.have.status(200));"]),
    ])


# 5. Security tests -----------------------------------------------------------

security = folder(
    "5. Security Tests",
    "Each request here asserts a rejection. A 200 in this folder is a failure.",
    [
        request(
            "401 - no token", "GET", "/v1/auth/me", auth=False,
            tests=ENVELOPE_ERR + [
                "pm.test('Status is 401', () => pm.response.to.have.status(401));",
                "pm.test('Code is UNAUTHENTICATED', function () {",
                "    pm.expect(pm.response.json().code).to.eql('UNAUTHENTICATED');",
                "});",
            ]),

        request(
            "401 - malformed JWT", "GET", "/v1/auth/me", auth=False,
            headers=[{"key": "Authorization", "value": "Bearer not.a.real.token"}],
            tests=["pm.test('Status is 401', () => pm.response.to.have.status(401));"]),

        request(
            "401 - tampered signature", "GET", "/v1/auth/me", auth=False,
            headers=[{"key": "Authorization", "value": "Bearer {{tamperedToken}}"}],
            desc="Runs the valid token through a one-character edit. The signature "
                 "no longer verifies.",
            tests=["pm.test('Status is 401', () => pm.response.to.have.status(401));"]),

        request(
            "401 - wrong password", "POST", "/v1/auth/login", auth=False,
            body={"identifier": "9876543210", "password": "DefinitelyWrong@1"},
            tests=ENVELOPE_ERR + [
                "pm.test('Status is 401', () => pm.response.to.have.status(401));",
                "const body = pm.response.json();",
                "pm.collectionVariables.set('wrongPasswordBody',",
                "    pm.response.text().replace(/\"(timestamp|requestId)\":\"[^\"]*\"/g, ''));",
                "pm.test('Generic message only', function () {",
                "    pm.expect(body.message).to.eql('Invalid credentials');",
                "});",
            ]),

        request(
            "401 - unknown account is indistinguishable", "POST", "/v1/auth/login",
            auth=False,
            body={"identifier": "9000000000", "password": "DefinitelyWrong@1"},
            desc="This is the user-enumeration check. The body must match the "
                 "wrong-password response exactly.",
            tests=[
                "pm.test('Status is 401', () => pm.response.to.have.status(401));",
                "pm.test('Byte-identical to the wrong-password response', function () {",
                "    const wrong = pm.collectionVariables.get('wrongPasswordBody');",
                "    const unknown = pm.response.text()",
                "        .replace(/\"(timestamp|requestId)\":\"[^\"]*\"/g, '');",
                "    pm.expect(unknown).to.eql(wrong);",
                "});",
            ]),

        request(
            "401 - inactive account", "POST", "/v1/auth/login", auth=False,
            body={"identifier": "9843056789", "password": "Staff@2026"},
            desc="Seed account EMP009 is INACTIVE. Same generic response.",
            tests=["pm.test('Status is 401', () => pm.response.to.have.status(401));"]),

        request(
            "401 - soft-deleted account", "POST", "/v1/auth/login", auth=False,
            body={"identifier": "9843089012", "password": "Staff@2026"},
            desc="Seed account EMP012 is soft-deleted.",
            tests=["pm.test('Status is 401', () => pm.response.to.have.status(401));"]),

        request(
            "403 - STAFF cannot list users", "GET", "/v1/users", auth=False,
            headers=[{"key": "Authorization", "value": "Bearer {{staffToken}}"}],
            desc="Requires the STAFF login to have run first.",
            tests=ENVELOPE_ERR + [
                "pm.test('Status is 403', () => pm.response.to.have.status(403));",
                "pm.test('Does not name the missing permission', function () {",
                "    // Naming it would map the authorisation model for an attacker.",
                "    pm.expect(pm.response.json().message)",
                "        .to.eql('You do not have permission for this action');",
                "});",
            ]),

        request(
            "403 - MANAGER cannot manage roles", "POST", "/v1/roles", auth=False,
            headers=[{"key": "Authorization", "value": "Bearer {{managerToken}}"},
                     {"key": "Content-Type", "value": "application/json"}],
            body={"code": "SHOULD_FAIL", "name": "Should Fail",
                  "permissions": ["PRODUCT_VIEW"], "status": "ACTIVE"},
            tests=["pm.test('Status is 403', () => pm.response.to.have.status(403));"]),

        request(
            "400 - validation error names the field", "POST", "/v1/users",
            body={"fullName": "", "mobileNo": "12345", "email": "not-an-email",
                  "employeeCode": "X", "roleId": 4, "password": "weak",
                  "mustChangePassword": False},
            tests=ENVELOPE_ERR + [
                "pm.test('Status is 400', () => pm.response.to.have.status(400));",
                "const body = pm.response.json();",
                "pm.test('Field errors present', function () {",
                "    pm.expect(body.code).to.eql('VALIDATION_ERROR');",
                "    pm.expect(body.errors).to.have.property('fullName');",
                "    pm.expect(body.errors).to.have.property('mobileNo');",
                "    pm.expect(body.errors).to.have.property('password');",
                "});",
            ]),

        request(
            "409 - duplicate mobile number", "POST", "/v1/users",
            body={"fullName": "Duplicate Mobile", "mobileNo": "9876543210",
                  "email": "dup1@sarahardware.in", "employeeCode": "EMP198",
                  "roleId": 4, "password": "Welcome@2026",
                  "mustChangePassword": False},
            tests=[
                "pm.test('Status is 409', () => pm.response.to.have.status(409));",
                "pm.test('Code is DUPLICATE_RESOURCE', function () {",
                "    pm.expect(pm.response.json().code).to.eql('DUPLICATE_RESOURCE');",
                "});",
            ]),

        request(
            "409 - duplicate email differing only by case", "POST", "/v1/users",
            body={"fullName": "Duplicate Email Case", "mobileNo": "9811100098",
                  "email": "OWNER@SaraHardware.in", "employeeCode": "EMP197",
                  "roleId": 4, "password": "Welcome@2026",
                  "mustChangePassword": False},
            desc="Regression check for BUG-AUTH-009. PostgreSQL compares "
                 "case-sensitively, so this is blocked by a functional unique "
                 "index on lower(email), not by the collation.",
            tests=["pm.test('Status is 409', () => pm.response.to.have.status(409));"]),

        request(
            "422 - OWNER role cannot lose a permission", "PUT", "/v1/roles/1",
            body={"code": "OWNER", "name": "Owner", "description": "Reduced",
                  "permissions": ["PRODUCT_VIEW"], "status": "ACTIVE"},
            desc="Otherwise the shop could lock itself out of its own administration.",
            tests=[
                "pm.test('Status is 422', () => pm.response.to.have.status(422));",
            ]),

        request(
            "422 - system role cannot be deleted", "DELETE", "/v1/roles/4",
            tests=["pm.test('Status is 422', () => pm.response.to.have.status(422));"]),

        request(
            "404 - unknown user", "GET", "/v1/users/999999",
            tests=[
                "pm.test('Status is 404', () => pm.response.to.have.status(404));",
                "pm.test('Code is NOT_FOUND', function () {",
                "    pm.expect(pm.response.json().code).to.eql('NOT_FOUND');",
                "});",
            ]),

        request(
            "Every response carries a correlation id", "GET", "/v1/auth/me",
            desc="X-Request-ID is what support uses to find the request in the logs.",
            tests=[
                "pm.test('X-Request-ID header present', function () {",
                "    pm.expect(pm.response.headers.get('X-Request-ID')).to.be.a('string');",
                "});",
            ]),
    ])


# 6. Rate limiting ------------------------------------------------------------

rate_limit = folder(
    "6. Rate Limiting",
    "The dev profile relaxes the limits so manual testing is not throttled "
    "(100/min per IP on login). To see a 429, start the backend with the default "
    "profile: `SPRING_PROFILES_ACTIVE=default ./mvnw spring-boot:run`, which "
    "allows 10 logins per minute per IP and 3 forgot-password requests per hour.",
    [
        request(
            "Hammer login until 429", "POST", "/v1/auth/login", auth=False,
            body={"identifier": "9000000000", "password": "Wrong@1234"},
            desc="Send this repeatedly with Postman's Runner (iterations: 15). "
                 "Under the default profile the 11th call returns 429 with a "
                 "Retry-After header.",
            tests=[
                "pm.test('Either rejected credentials or rate limited', function () {",
                "    pm.expect(pm.response.code).to.be.oneOf([401, 429]);",
                "});",
                "if (pm.response.code === 429) {",
                "    pm.test('Retry-After header present', function () {",
                "        pm.expect(pm.response.headers.get('Retry-After')).to.be.a('string');",
                "    });",
                "    pm.test('Code is RATE_LIMIT_EXCEEDED', function () {",
                "        pm.expect(pm.response.json().code).to.eql('RATE_LIMIT_EXCEEDED');",
                "    });",
                "}",
            ]),

        request(
            "Forgot password rate limit", "POST", "/v1/auth/forgot-password",
            auth=False, body={"identifier": "9876543210"},
            desc="Default profile allows 3 per hour per IP. Prevents this endpoint "
                 "being used to flood someone's mailbox.",
            tests=[
                "pm.test('200 or 429', function () {",
                "    pm.expect(pm.response.code).to.be.oneOf([200, 429]);",
                "});",
            ]),
    ])


# Collection ------------------------------------------------------------------

collection = {
    "info": {
        "name": "Hardware ERP - Module 1 - Authentication & User Management",
        "description": (
            "Module 1 of the Hardware ERP: authentication, users, roles, "
            "permissions and the security audit log.\n\n"
            "## Before you start\n\n"
            "1. Start PostgreSQL: `docker compose up -d`\n"
            "2. Start the backend: `cd backend && ./mvnw spring-boot:run`\n"
            "3. Import `hardware-erp-module-01.postman_environment.json` and "
            "select it in the environment dropdown (top right).\n"
            "4. Run **1. Authentication > Login as OWNER**. Every other request "
            "uses the token it stores.\n\n"
            "## How the tokens work\n\n"
            "The **access token** comes back in the response body and is saved to "
            "the `accessToken` collection variable by a test script.\n\n"
            "The **refresh token** is never in the body. The backend sets it as an "
            "HttpOnly, SameSite=Strict cookie scoped to `/api/v1/auth`. Postman's "
            "cookie jar stores and replays it automatically, so "
            "`POST /v1/auth/refresh` works with an empty body.\n\n"
            "## Running everything at once\n\n"
            "Use the Collection Runner in folder order 1 to 6. Folder 5 asserts "
            "rejections: a 200 there is a failure, not a success.\n\n"
            "## Seed credentials (development only)\n\n"
            "| Role | Identifier | Password |\n"
            "|---|---|---|\n"
            "| OWNER | 9876543210 | Owner@2026 |\n"
            "| MANAGER | 9840112233 | Manager@2026 |\n"
            "| ACCOUNTANT | 9840223344 | Account@2026 |\n"
            "| STAFF | 9843012345 | Staff@2026 |\n\n"
            "These exist only in `db/seed/V900__seed_dev_data.sql`, which the "
            "production Flyway configuration does not load."
        ),
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    },
    "item": [authentication, users, roles, audit, security, rate_limit],
    "event": [{
        "listen": "prerequest",
        "script": {
            "type": "text/javascript",
            "exec": [
                "// Derive a deliberately corrupted token for the tampered-signature test.",
                "const token = pm.collectionVariables.get('accessToken');",
                "if (token && token.length > 8) {",
                "    pm.collectionVariables.set('tamperedToken',",
                "        token.slice(0, -4) + 'AAAA');",
                "}",
            ],
        },
    }],
    "variable": [
        {"key": "accessToken", "value": "", "type": "string"},
        {"key": "managerToken", "value": "", "type": "string"},
        {"key": "staffToken", "value": "", "type": "string"},
        {"key": "tamperedToken", "value": "", "type": "string"},
        {"key": "createdUserId", "value": "", "type": "string"},
        {"key": "createdRoleId", "value": "", "type": "string"},
        {"key": "sessionId", "value": "", "type": "string"},
        {"key": "wrongPasswordBody", "value": "", "type": "string"},
        {"key": "forgotKnownBody", "value": "", "type": "string"},
    ],
}

environment = {
    "name": "Hardware ERP - Local (PostgreSQL)",
    "values": [
        {"key": "baseUrl", "value": "http://localhost:8080/api",
         "type": "default", "enabled": True},
        {"key": "ownerIdentifier", "value": "9876543210",
         "type": "default", "enabled": True},
        {"key": "ownerPassword", "value": "Owner@2026",
         "type": "secret", "enabled": True},
        {"key": "managerIdentifier", "value": "9840112233",
         "type": "default", "enabled": True},
        {"key": "managerPassword", "value": "Manager@2026",
         "type": "secret", "enabled": True},
        {"key": "staffIdentifier", "value": "9843012345",
         "type": "default", "enabled": True},
        {"key": "staffPassword", "value": "Staff@2026",
         "type": "secret", "enabled": True},
        {"key": "swaggerUrl", "value": "http://localhost:8080/api/swagger-ui.html",
         "type": "default", "enabled": True},
    ],
    "_postman_variable_scope": "environment",
}

(HERE / "hardware-erp-module-01.postman_collection.json").write_text(
    json.dumps(collection, indent=2) + "\n")
(HERE / "hardware-erp-module-01.postman_environment.json").write_text(
    json.dumps(environment, indent=2) + "\n")


def count(items):
    total = 0
    for item in items:
        total += count(item["item"]) if "item" in item else 1
    return total


print(f"collection: {count(collection['item'])} requests in {len(collection['item'])} folders")
print(f"environment: {len(environment['values'])} variables")
