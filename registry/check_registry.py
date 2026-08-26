#!/usr/bin/env python3
"""
Registry drift checker.

Fails the build if any locked name has been renamed, moved or deleted.
Run from the project root:   python3 registry/check_registry.py
Exit code 0 = clean, 1 = drift detected.

Wire into CI before `mvn test`. A registry nobody enforces is a document,
not a rule.
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
REG = json.load(open(ROOT / "registry" / "registry.json"))
SRC = ROOT / "backend" / "src" / "main" / "java" / "com" / "hardware" / "erp"
MIGRATIONS = ROOT / "backend" / "src" / "main" / "resources" / "db" / "migration"

errors, warnings = [], []


def pkg_dir(package: str) -> pathlib.Path:
    """Resolve a package name to a directory under SRC, handling the root package."""
    if package == "com.hardware.erp":
        return SRC
    return SRC / package.replace("com.hardware.erp.", "").replace(".", "/")


def all_sql():
    return "\n".join(f.read_text() for f in sorted(MIGRATIONS.glob("*.sql")))


def java_files():
    return list(SRC.rglob("*.java"))


# ---------------------------------------------------------------- tables
def check_tables():
    sql = all_sql()
    for table, meta in REG["tables"].items():
        if not re.search(rf"\bCREATE TABLE {table}\b", sql):
            errors.append(f"TABLE RENAMED/DROPPED: '{table}' no longer created in any migration")
            continue
        # A locked column may be added to by a later migration but never removed.
        for col in meta["columns"]:
            if not re.search(rf"\b{col}\b", sql):
                errors.append(f"COLUMN RENAMED/DROPPED: {table}.{col}")


# -------------------------------------------------------------- entities
def check_entities():
    for name, meta in REG["entities"].items():
        path = pkg_dir(meta["package"]) / f"{name}.java"
        if not path.exists():
            errors.append(f"ENTITY RENAMED/MOVED: {meta['package']}.{name}")
            continue
        s = path.read_text()
        if not re.search(rf'@Table\(name = "{meta["table"]}"\)', s):
            errors.append(f"ENTITY REMAPPED: {name} no longer maps to table '{meta['table']}'")
        for field, fmeta in meta["fields"].items():
            if not re.search(rf"\b{field}\b", s):
                errors.append(f"FIELD RENAMED: {name}.{field}")
            if not re.search(rf'name = "{fmeta["column"]}"', s):
                errors.append(f"COLUMN MAPPING CHANGED: {name}.{field} -> {fmeta['column']}")


# ------------------------------------------------------------------ dtos
def check_dtos():
    for name, meta in REG["dtos"].items():
        path = pkg_dir(meta["package"]) / f"{name}.java"
        if not path.exists():
            errors.append(f"DTO RENAMED/MOVED: {meta['package']}.{name}")
            continue
        s = path.read_text()
        for field in meta["fields"]:
            if not re.search(rf"\b{field}\b", s):
                errors.append(f"DTO FIELD RENAMED: {name}.{field}")


# ------------------------------------------------------------- endpoints
def check_endpoints():
    blob = "\n".join(f.read_text() for f in java_files())
    for ep, meta in REG["endpoints"].items():
        verb, full = ep.split(" ", 1)
        path = full[len("/api"):]
        ctrl = SRC / "auth" / "controller" / f"{meta['controller']}.java"
        if not ctrl.exists():
            errors.append(f"CONTROLLER RENAMED/MOVED: {meta['controller']} (owns {ep})")
            continue
        s = ctrl.read_text()
        base = re.search(r'@RequestMapping\("([^"]+)"\)', s)
        if not base:
            errors.append(f"CONTROLLER BASE PATH REMOVED: {meta['controller']}")
            continue
        suffix = path[len(base.group(1)):]
        if suffix:
            if f'"{suffix}"' not in s:
                errors.append(f"ENDPOINT PATH CHANGED: {ep}")
        if not re.search(rf"\b{meta['method']}\s*\(", s):
            errors.append(f"HANDLER METHOD RENAMED: {meta['controller']}.{meta['method']} ({ep})")


# --------------------------------------------------------------- classes
def check_classes():
    for name, meta in REG["classes"].items():
        if not (pkg_dir(meta["package"]) / f"{name}.java").exists():
            errors.append(f"CLASS RENAMED/MOVED: {meta['package']}.{name}")


# ----------------------------------------------------------- permissions
def check_permissions():
    src = SRC / "auth" / "entity" / "Permissions.java"
    if not src.exists():
        errors.append("CLASS RENAMED/MOVED: Permissions")
        return
    s = src.read_text()
    declared = set(re.findall(r'public static final String \w+\s*=\s*"(\w+)"\s*;', s))
    for p in REG["permissions"]:
        if p not in declared:
            errors.append(f"PERMISSION REMOVED/RENAMED: {p}")
    for p in sorted(declared - set(REG["permissions"])):
        warnings.append(f"New permission '{p}' not yet in registry - re-run the generator")


# -------------------------------------------------- duplicate-name guard
def check_duplicates():
    """Catches AppUserService / UserManagementService style drift."""
    stems = {}
    for f in java_files():
        stems.setdefault(f.stem, []).append(str(f.relative_to(ROOT)))
    for stem, paths in stems.items():
        if len(paths) > 1:
            errors.append(f"DUPLICATE CLASS NAME '{stem}': " + ", ".join(paths))

    concepts = {
        "User":     ["UserService", "AppUserService", "UserManagementService",
                     "CustomerUserService", "AccountService", "LoginService"],
        "Customer": ["CustomerService", "ClientService", "BuyerService", "PartyService"],
        "Supplier": ["SupplierService", "VendorService"],
        "Product":  ["ProductService", "ItemService", "SkuService", "GoodsService"],
        "Invoice":  ["InvoiceService", "BillService", "SalesInvoiceService"],
    }
    present = {f.stem for f in java_files()}
    for concept, aliases in concepts.items():
        found = [a for a in aliases if a in present]
        if len(found) > 1:
            errors.append(f"ALIAS SERVICES for '{concept}': {found} - pick one, delete the rest")

    field_aliases = [
        ("person name",  ["fullName", "customerFullName", "clientName", "personName", "name_"]),
        ("mobile",       ["mobileNo", "mobileNumber", "phoneNo", "phone", "contactNo"]),
        ("email",        ["email", "emailId", "emailAddress"]),
        ("GST number",   ["gstNo", "gstin", "gstNumber", "gstinNo"]),
    ]
    blob = "\n".join(f.read_text() for f in java_files())
    for concept, aliases in field_aliases:
        found = [a for a in aliases if re.search(rf"\b{a}\b", blob)]
        if len(found) > 1:
            warnings.append(f"Possible alias fields for {concept}: {found}")


for fn in (check_tables, check_entities, check_dtos, check_endpoints,
           check_classes, check_permissions, check_duplicates):
    fn()

print("=" * 68)
print("  HARDWARE ERP - REGISTRY DRIFT CHECK")
print("=" * 68)
print(f"  locked modules : {', '.join(REG['modules_locked'])}")
print(f"  checked        : {len(REG['tables'])} tables, {len(REG['entities'])} entities, "
      f"{len(REG['dtos'])} DTOs, {len(REG['endpoints'])} endpoints, {len(REG['classes'])} classes")
print("=" * 68)

for w in warnings:
    print(f"  WARN   {w}")
for e in errors:
    print(f"  ERROR  {e}")

if errors:
    print(f"\n  FAILED - {len(errors)} registry violation(s).")
    print("  Renaming a locked name requires an approved migration plan.\n")
    sys.exit(1)

print(f"\n  PASS - no drift.{f' ({len(warnings)} warning(s))' if warnings else ''}\n")
sys.exit(0)
