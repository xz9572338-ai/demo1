"""Public documentation example. Never log APP_SECRET in real integrations."""
import hashlib, hmac, os, secrets, shlex, time
from urllib.parse import quote

APP_ID = os.environ["OPEN_PLATFORM_APP_ID"]
APP_SECRET = os.environ["OPEN_PLATFORM_APP_SECRET"].encode()
BASE_URL = os.getenv("OPEN_PLATFORM_BASE_URL", "https://sandbox.example.invalid/sandbox/v1").rstrip("/")

def encode(value: str) -> str:
    return quote(value, safe="~-._")

def canonical_query(values: list[tuple[str, str]]) -> str:
    return "&".join(f"{k}={v}" for k, v in sorted((encode(k), encode(v)) for k, v in values))

def sign(method: str, path: str, query: str, body: bytes, timestamp: str, nonce: str) -> str:
    body_hash = hashlib.sha256(body).hexdigest()
    canonical = "\n".join((method.upper(), path, query, body_hash, APP_ID, timestamp, nonce))
    return hmac.new(APP_SECRET, canonical.encode("utf-8"), hashlib.sha256).hexdigest()

timestamp, nonce = str(int(time.time())), secrets.token_hex(16)
params = [("startTime", "2026-08-18T01:00:00Z"), ("endTime", "2026-08-18T02:00:00Z"), ("page", "1"), ("pageSize", "20")]
query = canonical_query(params)
signature = sign("GET", "/orders", query, b"", timestamp, nonce)
print(shlex.join(["curl", "--connect-timeout", "10", "--max-time", "30", "--fail-with-body", f"{BASE_URL}/orders?{query}", "-H", f"X-App-ID: {APP_ID}", "-H", f"X-Timestamp: {timestamp}", "-H", f"X-Nonce: {nonce}", "-H", f"X-Signature: {signature}"]))
