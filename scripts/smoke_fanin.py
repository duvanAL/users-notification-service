"""Local integration check: no external credentials or real email delivery.
Requires Docker Compose and sibling clones. Generates ephemeral credentials in memory.
"""
import base64
import http.client
import json
import os
import secrets
import subprocess
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ENV = os.environ.copy()
ENV.update(LOCAL_DATABASE_PASSWORD=secrets.token_urlsafe(24),
           LOCAL_ADMIN_PASSWORD=secrets.token_urlsafe(32),
           LOCAL_JWT_SECRET=base64.b64encode(secrets.token_bytes(32)).decode())
CMD = ["docker", "compose", "-p", "users-fanin-smoke", "-f", "compose.fanin.yaml"]

def compose(*args, **kwargs):
    return subprocess.run(CMD + list(args), cwd=ROOT, env=ENV, check=True, **kwargs)

def request(url, body=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = None if body is None else json.dumps(body).encode()
    with urllib.request.urlopen(urllib.request.Request(url, data, headers), timeout=15) as response:
        return json.load(response)

def wait_for(description, probe, seconds=240):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            result = probe()
            if result:
                print(description + ": OK", flush=True)
                return result
        except (urllib.error.URLError, TimeoutError, http.client.HTTPException, AssertionError):
            pass
        time.sleep(2)
    raise RuntimeError(description + " timed out")

try:
    compose("up", "--build", "-d", stdout=subprocess.DEVNULL)
    for port in (18080,18081,18082):
        wait_for("HTTP health " + str(port), lambda port=port:
            request("http://localhost:" + str(port) + "/health").get("status") == "UP")
    login = request("http://localhost:18080/api/auth/login",
                    {"email":"admin@example.test","password":ENV["LOCAL_ADMIN_PASSWORD"]})
    token = login["accessToken"]
    # Seed may itself generate a user event. Only count the new recipient below.
    user_id = str(uuid.uuid4())
    email = "fanin-" + user_id + "@example.test"
    user = request("http://localhost:18080/api/users",
                   {"id":user_id,"name":"Fan In Demo","email":email,"password":secrets.token_urlsafe(24),"role":"MEMBER"})
    account = request("http://localhost:18081/api/accounts",
                      {"userId":user["id"],"accountType":"SAVINGS"},token)
    assert account["userId"] == user["id"]
    assert request("http://localhost:18081/api/accounts/" + account["id"],token=token)["id"] == account["id"]
    def delivered():
        calls = request("http://localhost:18089/__admin/requests")["requests"]
        messages = [json.loads(c["request"]["body"]) for c in calls
                    if c["request"]["url"] == "/v3/smtp/email"]
        messages = [m for m in messages if m["to"][0]["email"] == email]
        assert len(messages) == 2
        assert {m["subject"] for m in messages} == {"Bienvenido - Gestion de Usuarios","Cuenta creada"}
        assert len({m["headers"]["idempotencyKey"] for m in messages}) == 2
        return True
    wait_for("Two producers -> Kafka -> notification service -> mocked Brevo",delivered)
    # All application data and event records must have been persisted in their owned schemas.
    result = compose("exec","-T","postgres","psql","-U","local_demo","-d","fanin","-Atc",
                     "SELECT (SELECT count(*) FROM accounts.accounts), "
                     "(SELECT count(*) FROM accounts.event_outbox), "
                     "(SELECT count(*) FROM domain_event_outbox);",
                     capture_output=True,text=True)
    assert result.stdout.strip() == "1|0|0", "Outboxes were not drained"
    print("Account persisted; both outboxes drained; fan-in passed.",flush=True)
except Exception:
    # Omit logs to avoid exposing ephemeral seed credentials.
    compose("ps")
    raise
finally:
    compose("down", "--remove-orphans", stdout=subprocess.DEVNULL)
