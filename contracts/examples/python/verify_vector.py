import hashlib, hmac, json
from pathlib import Path

vector = json.loads((Path(__file__).parents[1] / "signing-vector.json").read_text(encoding="utf-8"))
actual = hmac.new(vector["appSecret"].encode(), vector["canonicalRequest"].encode(), hashlib.sha256).hexdigest()
if actual != vector["expectedSignature"]:
    raise SystemExit("signing vector mismatch")
if hashlib.sha256(b"").hexdigest() != vector["bodySha256"]:
    raise SystemExit("empty body hash mismatch")
for changed in (vector["canonicalRequest"] + "x", vector["canonicalRequest"].replace("GET", "get"), vector["canonicalRequest"].replace("/orders", "/orders/"), vector["canonicalRequest"] + "\n"):
    if hmac.new(vector["appSecret"].encode(), changed.encode(), hashlib.sha256).hexdigest() == vector["expectedSignature"]:
        raise SystemExit("tampered vector accepted")
print("Python signing vector: PASS")
