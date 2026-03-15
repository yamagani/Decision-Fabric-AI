#!/usr/bin/env python3
"""
Smoke test for Decision Fabric AI — runs against the local Docker stack.
Usage: python3 smoke-test.py
"""
import urllib.request
import urllib.parse
import urllib.error
import json
import pathlib
import sys
import time

TS = int(time.time())


BASE = "http://localhost:8080"
KC   = "http://localhost:9090/realms/decision-fabric-ai/protocol/openid-connect/token"


def post_form(url, data):
    req = urllib.request.Request(url, urllib.parse.urlencode(data).encode())
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())


def api(method, path, token, body=None):
    payload = json.dumps(body).encode() if body else None
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json",
    }
    req = urllib.request.Request(f"{BASE}{path}", payload, headers, method=method)
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.loads(r.read())
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"_raw": raw.decode(errors="replace")}


def check(label, code, expected, body=None):
    ok = code == expected
    mark = "✅" if ok else "❌"
    print(f"{mark}  [{label}] HTTP {code} (expected {expected})"
          + (f" → {json.dumps(body)[:120]}" if body else ""))
    if not ok:
        sys.exit(1)


# ── 1. Get token ──────────────────────────────────────────────────────────────
print("1. Acquiring token from Keycloak...")
tok = post_form(KC, {
    "client_id":     "decision-fabric-ai-client",
    "client_secret": "local-secret",
    "username":      "system-admin",
    "password":      "password",
    "grant_type":    "password",
})
token = tok["access_token"]
print(f"   Token (first 50 chars): {token[:50]}...")

# ── 2. Create rule set ────────────────────────────────────────────────────────
print("\n2. Creating rule set...")
code, body = api("POST", "/api/v1/rule-sets", token,
                 {"name": f"smoke-test-ruleset-{TS}", "description": "Smoke test"})
check("Create RuleSet", code, 201, body)
rs_id = body.get("id")

# ── 3. Create a rule with DMN content ─────────────────────────────────────────
print("\n3. Creating rule with DMN content...")
dmn = pathlib.Path(
    "src/test/resources/fixtures/dmn/valid-decision-table-comprehensive.dmn"
).read_text()
code, body = api("POST", "/api/v1/rules", token, {
    "name":        "employee-benefits",
    "ruleSetId":   rs_id,
    "description": "Employee benefits eligibility DMN",
    "dmnXml":      dmn,
})
check("Create Rule", code, 201, body)
rule_id = body.get("id")
# versions[0].version is the version number used in URL paths
ver_num = body.get("versions", [{}])[0].get("version", 1)

# ── 4. List rules ─────────────────────────────────────────────────────────────
print("\n4. Listing rules...")
code, body = api("GET", "/api/v1/rules", token)
check("List Rules", code, 200)
print(f"   total={body.get('total', 0)}, items={len(body.get('items', []))}")

# ── 5. Validate DMN content via the validate endpoint ────────────────────────
print("\n5. Validating DMN content...")
# The validate endpoint expects raw XML with Content-Type: application/xml
req_v = urllib.request.Request(
    f"{BASE}/api/v1/rules/validate",
    dmn.encode(),
    {"Authorization": f"Bearer {token}", "Content-Type": "application/xml"},
    method="POST",
)
try:
    with urllib.request.urlopen(req_v) as r:
        vbody = json.loads(r.read())
        vcode = r.status
except urllib.error.HTTPError as e:
    raw = e.read()
    try:
        vbody = json.loads(raw)
    except Exception:
        vbody = {"_raw": raw.decode(errors="replace")}
    vcode = e.code
check("Validate DMN", vcode, 200, vbody)
print(f"   valid={vbody.get('valid')}")

# ── 6. Activate the version ───────────────────────────────────────────────────
print("\n6. Activating version...")
code, body = api("POST", f"/api/v1/rules/{rule_id}/versions/{ver_num}/activate", token)
check("Activate Version", code, 200, body)

# ── 7. Confirm activation in detail ──────────────────────────────────────────
print("\n7. Fetching rule to confirm active status...")
code, body = api("GET", f"/api/v1/rules/{rule_id}", token)
check("Get Rule", code, 200)
print(f"   activeVersionCount={body.get('activeVersionCount','?')}, versions={[v.get('status') for v in body.get('versions',[])]}")

# ── 8. Unauthorised request ───────────────────────────────────────────────────
print("\n8. Confirming 401 on bad token...")
code, _ = api("GET", "/api/v1/rules", "bad-token")
check("Unauth check", code, 401)

print("\n🎉  All smoke tests passed!")
