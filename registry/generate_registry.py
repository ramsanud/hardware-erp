#!/usr/bin/env python3
"""
Regenerates registry/registry.json from the current source tree.

Run this ONLY after an approved amendment or after adding a new module.
Never run it to "fix" a check_registry.py failure - that defeats the purpose.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "backend/src/main/java/com/hardware/erp"
MIG = ROOT / "backend/src/main/resources/db/migration"

reg = json.load(open(ROOT / "registry/registry.json"))
reg.update({"tables": {}, "entities": {}, "dtos": {}, "endpoints": {}, "classes": {}})

sql = "\n".join(f.read_text() for f in sorted(MIG.glob("*.sql")))
for m in re.finditer(r"CREATE TABLE (\w+) \((.*?)\n\) ENGINE", sql, re.S):
    cols = {}
    for line in m.group(2).split("\n"):
        c = re.match(r"^(\w+)\s+((?:BIGINT|INT|VARCHAR|CHAR|DATETIME|DATE|BOOLEAN|TEXT|DECIMAL|TINYINT)(?:\(\d+(?:,\d+)?\))?)",
                     line.strip())
        if c and c.group(1).upper() not in ("PRIMARY", "UNIQUE", "KEY", "CONSTRAINT", "FOREIGN"):
            cols[c.group(1)] = c.group(2)
    reg["tables"][m.group(1)] = {"columns": cols}

for f in sorted(SRC.rglob("entity/*.java")):
    s = f.read_text()
    tbl = re.search(r'@Table\(name = "(\w+)"\)', s)
    if not tbl:
        continue
    fields = {}
    for m in re.finditer(r'@Column\(name = "(\w+)"[^)]*\)\s*(?:@\w+[^\n]*\s*)*private\s+([\w<>,\s]+?)\s+(\w+)\s*[;=]', s):
        fields[m.group(3)] = {"column": m.group(1), "javaType": m.group(2).strip()}
    for m in re.finditer(r'@JoinColumn\(name = "(\w+)"[^)]*\)\s*private\s+(\w+)\s+(\w+);', s):
        fields[m.group(3)] = {"column": m.group(1), "javaType": m.group(2), "fk": True}
    pkg = re.search(r"^package ([\w.]+);", s, re.M).group(1)
    reg["entities"][f.stem] = {"table": tbl.group(1), "package": pkg, "fields": fields}

for f in sorted(SRC.rglob("dto/*.java")):
    s = f.read_text()
    m = re.search(r"public record (\w+)\((.*?)\)\s*\{", s, re.S)
    if not m:
        continue
    body = re.sub(r"@[\w.]+(\([^)]*\))?", "", m.group(2))
    fields = {p.strip().split()[-1]: p.strip().split()[-2]
              for p in body.split(",") if len(p.strip().split()) >= 2}
    reg["dtos"][m.group(1)] = {"package": re.search(r"^package ([\w.]+);", s, re.M).group(1),
                               "fields": fields}

for f in sorted(SRC.rglob("controller/*.java")):
    s = f.read_text()
    base = re.search(r'@RequestMapping\("([^"]+)"\)', s)
    if not base:
        continue
    for m in re.finditer(
            r'@(Get|Post|Put|Delete|Patch)Mapping(?:\("([^"]*)"\))?\s*'
            r'(?:@PreAuthorize\("([^"]*)"\)\s*)?(?:@Operation[^\n]*\s*)*'
            r'public ResponseEntity<([^>]*(?:<[^>]*>)?[^>]*)>\s+(\w+)\(', s):
        verb, path, pre, ret, method = m.groups()
        perm = "authenticated"
        if pre:
            pm = re.search(r"\.(\w+)\)$", pre.strip())
            perm = pm.group(1) if pm else "authenticated"
        reg["endpoints"][f"{verb.upper()} /api{base.group(1)}{path or ''}"] = {
            "controller": f.stem, "method": method, "permission": perm, "returns": ret.strip()}

for f in sorted(SRC.rglob("*.java")):
    if "/dto/" in str(f) or "/entity/" in str(f):
        continue
    s = f.read_text()
    reg["classes"][f.stem] = {
        "package": re.search(r"^package ([\w.]+);", s, re.M).group(1),
        "kind": "interface" if re.search(r"public interface " + f.stem, s) else "class"}

perms_src = SRC / "auth/entity/Permissions.java"
if perms_src.exists():
    reg["permissions"] = sorted(set(re.findall(
        r'public static final String \w+\s*=\s*"(\w+)"\s*;', perms_src.read_text())))

json.dump(reg, open(ROOT / "registry/registry.json", "w"), indent=2)
print(f"registry.json regenerated: {len(reg['tables'])} tables, {len(reg['entities'])} entities, "
      f"{len(reg['dtos'])} DTOs, {len(reg['endpoints'])} endpoints, {len(reg['classes'])} classes, "
      f"{len(reg['permissions'])} permissions")
