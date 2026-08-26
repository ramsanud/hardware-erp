#!/usr/bin/env python3
"""
Static consistency checks for the Java sources.

Not a substitute for `mvn compile` - it cannot resolve types or imports. It
catches the classes of error that are cheap to make and expensive to find:
package/path drift, unbalanced structure, entity/migration divergence, and
DTO constructor arity in tests.

String literals, char literals and comments are stripped before counting
delimiters, otherwise content like  .content("{ not json")  reports a false
imbalance.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MAIN = ROOT / "backend/src/main/java/com/hardware/erp"
TEST = ROOT / "backend/src/test/java/com/hardware/erp"
MIGRATIONS = ROOT / "backend/src/main/resources/db/migration"
SEED = ROOT / "backend/src/main/resources/db/seed"

errors = []


def strip_literals(source: str) -> str:
    """
    Single-pass scanner. Regex passes cannot do this correctly: stripping //
    comments first truncates "http://localhost:8080/api" mid-string and drops
    the closing parens after it, which is exactly the false positive this
    replaced.
    """
    out = []
    i, n = 0, len(source)
    while i < n:
        ch = source[i]
        # text block
        if source.startswith('"""', i):
            end = source.find('"""', i + 3)
            out.append('""')
            i = n if end == -1 else end + 3
        # string literal
        elif ch == '"':
            i += 1
            while i < n and source[i] != '"':
                i += 2 if source[i] == '\\' else 1
            out.append('""')
            i += 1
        # char literal
        elif ch == "'":
            i += 1
            while i < n and source[i] != "'":
                i += 2 if source[i] == '\\' else 1
            out.append("''")
            i += 1
        # line comment
        elif source.startswith('//', i):
            end = source.find('\n', i)
            i = n if end == -1 else end
        # block comment
        elif source.startswith('/*', i):
            end = source.find('*/', i + 2)
            i = n if end == -1 else end + 2
        else:
            out.append(ch)
            i += 1
    return ''.join(out)


def check_structure():
    for root in (MAIN, TEST):
        if not root.exists():
            continue
        for f in root.rglob("*.java"):
            raw = f.read_text()
            code = strip_literals(raw)
            pkg = re.search(r"^package ([\w.]+);", raw, re.M)
            rel = "" if f.parent == root else "." + str(f.parent.relative_to(root)).replace("/", ".")
            expected = "com.hardware.erp" + rel
            if not pkg:
                errors.append(f"NO PACKAGE  {f.relative_to(ROOT)}")
            elif pkg.group(1) != expected:
                errors.append(f"PACKAGE     {f.relative_to(ROOT)}: {pkg.group(1)} != {expected}")
            for open_c, close_c, label in (("{", "}", "BRACE"), ("(", ")", "PAREN")):
                if code.count(open_c) != code.count(close_c):
                    errors.append(f"{label}       {f.relative_to(ROOT)}: "
                                  f"{code.count(open_c)} vs {code.count(close_c)}")


def migration_columns():
    """
    Parses PostgreSQL CREATE TABLE blocks (CR-014).

    Previously matched MySQL's trailing `) ENGINE=InnoDB`; PostgreSQL blocks
    simply end with `);`, so the terminator and the type list both changed.
    """
    sql = "\n".join(f.read_text() for f in sorted(MIGRATIONS.glob("*.sql")))
    types = (r"BIGINT|INTEGER|INT|SMALLINT|VARCHAR|CHAR|TEXT|TIMESTAMP|DATE|"
             r"BOOLEAN|NUMERIC|DECIMAL|JSONB|UUID|SERIAL|BIGSERIAL")
    tables = {}
    for m in re.finditer(r"CREATE TABLE (\w+) \((.*?)\n\);", sql, re.S):
        cols = set()
        for line in m.group(2).split("\n"):
            stripped = line.strip()
            if stripped.upper().startswith(("CONSTRAINT", "PRIMARY", "UNIQUE",
                                            "FOREIGN", "CHECK", "--")):
                continue
            c = re.match(rf"^(\w+)\s+(?:{types})", stripped, re.I)
            if c:
                cols.add(c.group(1))
        tables[m.group(1)] = cols
    return tables


def check_entities(tables):
    join_tables = {"role_permission"}
    for f in (MAIN / "auth/entity").glob("*.java"):
        raw = f.read_text()
        t = re.search(r'@Table\(name = "(\w+)"\)', raw)
        if not t:
            continue
        table = t.group(1)
        if table not in tables:
            errors.append(f"ENTITY      {f.stem}: table '{table}' not created in any migration")
            continue
        known = set(tables[table])
        for jt in join_tables:
            known |= tables.get(jt, set())
        for col in set(re.findall(r'@(?:Column|JoinColumn)\(name = "(\w+)"', raw)):
            if col not in known:
                errors.append(f"COLUMN      {f.stem}.{col} not in {table}")
        if "extends BaseEntity" in raw:
            for col in ("created_at", "created_by", "updated_at", "updated_by"):
                if col not in tables[table]:
                    errors.append(f"AUDIT       {table} missing {col} required by BaseEntity")
        if "@Version" in raw and "version" not in tables[table]:
            errors.append(f"VERSION     {table} has @Version but no version column")


def check_seed(tables):
    if not SEED.exists():
        return
    for f in SEED.glob("*.sql"):
        seed = f.read_text()
        for m in re.finditer(r"INSERT INTO (\w+)\s*\n?\s*\(([^)]*)\)", seed):
            table, cols = m.group(1), m.group(2)
            if table not in tables:
                errors.append(f"SEED        unknown table '{table}' in {f.name}")
                continue
            for col in {c.strip() for c in cols.replace("\n", " ").split(",")}:
                if col and col not in tables[table]:
                    errors.append(f"SEED        {table}.{col} does not exist ({f.name})")


def check_permission_constants():
    pc = MAIN / "auth/entity/PermissionCode.java"
    if not pc.exists():
        return
    declared = set(re.findall(r'String (\w+)\s*=', pc.read_text()))
    for f in (MAIN / "auth/controller").glob("*.java"):
        for used in re.findall(r"PermissionCode\)\.(\w+)", f.read_text()):
            if used not in declared:
                errors.append(f"PERMISSION  {f.stem} uses PermissionCode.{used}, not declared")


def check_dto_arity():
    arity = {}
    for f in (MAIN / "auth/dto").glob("*.java"):
        m = re.search(r"public record (\w+)\((.*?)\)\s*\{", f.read_text(), re.S)
        if m:
            body = re.sub(r"@[\w.]+(\([^)]*\))?", "", m.group(2))
            arity[m.group(1)] = len([p for p in body.split(",") if len(p.strip().split()) >= 2])

    if not TEST.exists():
        return
    for f in TEST.rglob("*.java"):
        code = strip_literals(f.read_text())
        for name, expected in arity.items():
            for m in re.finditer(rf"new {name}\(", code):
                i, depth, args = m.end(), 1, 1
                while i < len(code) and depth > 0:
                    ch = code[i]
                    if ch in "([{":
                        depth += 1
                    elif ch in ")]}":
                        depth -= 1
                    elif ch == "," and depth == 1:
                        args += 1
                    i += 1
                if args != expected:
                    errors.append(f"ARITY       {f.name}: new {name}(..) has {args} args, "
                                  f"record declares {expected}")


def check_no_mysql_syntax():
    """CR-014: PostgreSQL is the single source of truth. Comments may mention
    MySQL for history; executable syntax may not."""
    banned = {
        "ENGINE=": "MySQL table option",
        "AUTO_INCREMENT": "use GENERATED BY DEFAULT AS IDENTITY",
        "utf8mb4": "MySQL charset",
        "DATETIME(": "use TIMESTAMP(3)",
        "NOW(3)": "use CURRENT_TIMESTAMP",
        "DATE_SUB(": "use CURRENT_TIMESTAMP - INTERVAL",
        "ON UPDATE CURRENT_TIMESTAMP": "use the set_updated_at() trigger",
    }
    for folder in (MIGRATIONS, SEED):
        if not folder.exists():
            continue
        for f in folder.glob("*.sql"):
            for line_no, line in enumerate(f.read_text().split("\n"), 1):
                code = line.split("--")[0]
                for token, advice in banned.items():
                    if token in code:
                        errors.append(f"MYSQLISM    {f.name}:{line_no} '{token}' - {advice}")


check_structure()
check_no_mysql_syntax()
tables = migration_columns()
check_entities(tables)
check_seed(tables)
check_permission_constants()
check_dto_arity()

main_count = len(list(MAIN.rglob("*.java"))) if MAIN.exists() else 0
test_count = len(list(TEST.rglob("*.java"))) if TEST.exists() else 0

print("=" * 66)
print("  STATIC CONSISTENCY CHECK")
print(f"  {main_count} main sources, {test_count} test sources, {len(tables)} tables")
print("=" * 66)
for e in sorted(set(errors)):
    print("  " + e)
if errors:
    print(f"\n  FAILED - {len(set(errors))} issue(s)\n")
    sys.exit(1)
print("\n  PASS - no structural inconsistencies\n"
      "  NOTE: this is not a compile. Run `mvn clean verify` to close BUG-ENV-001.\n")
