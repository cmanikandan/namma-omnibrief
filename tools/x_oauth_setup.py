#!/usr/bin/env python3
"""
One-off helper: mint X OAuth 2.0 user tokens with the scopes Namma Omnibrief needs.

WHY THIS EXISTS
---------------
Posting text to X needs `tweet.write`. Attaching a photo *additionally* needs
`media.write`, which `tweet.write` does not imply. An authorisation granted
without it publishes text perfectly well and then fails the image upload with a
bare `403 Forbidden` whose body never mentions scopes — so the failure is
invisible unless you already know to look for it.

This script runs the standard OAuth 2.0 Authorization Code flow with PKCE and
asks for the scope list explicitly, so there is no guessing about what was
granted. It then prints the `scope` the server actually returned and checks
`media.write` is in it.

WHAT IT DOES NOT DO
-------------------
Nothing is written to disk and nothing is committed. The tokens are printed to
your terminal for you to paste into the app's Settings screen. Your client
secret is read from a prompt, never from a file or an argument (arguments show
up in shell history and in `ps`).

USAGE
-----
    python3 tools/x_oauth_setup.py

Prerequisites, in the X Developer Portal (developer.x.com) under your project's
App -> "User authentication settings":

  1. App permissions: "Read and write"
  2. Type of App:     "Web App, Automated App or Bot"  (a confidential client)
  3. Callback URI:    http://127.0.0.1:8765/callback
                      Add it exactly; X does an exact-match check on this.

Then run the script and follow the browser prompt.
"""

from __future__ import annotations

import base64
import getpass
import hashlib
import http.server
import json
import os
import secrets
import subprocess
import sys
import threading
import urllib.parse
import webbrowser

# The full set the app needs. media.write is the one that is easy to miss.
SCOPES = [
    "tweet.read",
    "tweet.write",
    "users.read",
    "offline.access",   # without this you get no refresh token at all
    "media.write",      # without this the photo upload 403s
]

REDIRECT_URI = "http://127.0.0.1:8765/callback"
CALLBACK_PORT = 8765
AUTHORIZE_URL = "https://x.com/i/oauth2/authorize"
TOKEN_URL = "https://api.x.com/2/oauth2/token"


def make_pkce_pair() -> tuple[str, str]:
    """Returns (verifier, challenge) for PKCE S256."""
    verifier = base64.urlsafe_b64encode(os.urandom(64)).decode().rstrip("=")
    digest = hashlib.sha256(verifier.encode("ascii")).digest()
    challenge = base64.urlsafe_b64encode(digest).decode().rstrip("=")
    return verifier, challenge


class _CallbackHandler(http.server.BaseHTTPRequestHandler):
    """Catches the single redirect X sends back with the authorization code."""

    result: dict[str, str] = {}

    def do_GET(self):  # noqa: N802 - name fixed by BaseHTTPRequestHandler
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path != "/callback":
            self.send_response(404)
            self.end_headers()
            return

        params = urllib.parse.parse_qs(parsed.query)
        _CallbackHandler.result = {k: v[0] for k, v in params.items()}

        ok = "code" in _CallbackHandler.result
        body = (
            "<h2>Authorised.</h2><p>You can close this tab and return to the terminal.</p>"
            if ok
            else f"<h2>Authorisation failed.</h2><pre>{parsed.query}</pre>"
        )
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.end_headers()
        self.wfile.write(body.encode("utf-8"))

    def log_message(self, *args):
        pass  # keep the terminal clean


def wait_for_code(expected_state: str) -> str:
    server = http.server.HTTPServer(("127.0.0.1", CALLBACK_PORT), _CallbackHandler)
    thread = threading.Thread(target=server.handle_request, daemon=True)
    thread.start()
    thread.join(timeout=300)
    server.server_close()

    result = _CallbackHandler.result
    if not result:
        sys.exit("Timed out waiting for the browser redirect (5 minutes).")
    if "error" in result:
        sys.exit(f"X returned an error: {result.get('error_description', result['error'])}")
    if result.get("state") != expected_state:
        sys.exit("State mismatch — aborting rather than trusting this redirect.")
    return result["code"]


def exchange_code(client_id: str, client_secret: str, code: str, verifier: str) -> dict:
    """
    Swaps the authorization code for tokens.

    Uses curl rather than urllib: on some macOS Python installs urllib has no
    usable CA bundle and fails with CERTIFICATE_VERIFY_FAILED, which looks like
    an auth problem but is not.
    """
    basic = base64.b64encode(f"{client_id}:{client_secret}".encode()).decode()
    form = urllib.parse.urlencode({
        "grant_type": "authorization_code",
        "code": code,
        "redirect_uri": REDIRECT_URI,
        "code_verifier": verifier,
        "client_id": client_id,
    })

    proc = subprocess.run(
        [
            "curl", "-s", "-X", "POST", TOKEN_URL,
            "-H", f"Authorization: Basic {basic}",
            "-H", "Content-Type: application/x-www-form-urlencoded",
            "--data", form,
        ],
        capture_output=True,
        text=True,
        check=False,
    )
    if proc.returncode != 0:
        sys.exit(f"curl failed: {proc.stderr}")
    try:
        return json.loads(proc.stdout)
    except json.JSONDecodeError:
        sys.exit(f"Unexpected response from X:\n{proc.stdout}")


def main() -> None:
    print(__doc__.split("USAGE")[0].strip())
    print("-" * 72)

    client_id = input("X Client ID: ").strip()
    if not client_id:
        sys.exit("Client ID is required.")
    client_secret = getpass.getpass("X Client Secret (hidden): ").strip()
    if not client_secret:
        sys.exit("Client Secret is required.")

    verifier, challenge = make_pkce_pair()
    state = secrets.token_urlsafe(24)

    auth_url = AUTHORIZE_URL + "?" + urllib.parse.urlencode({
        "response_type": "code",
        "client_id": client_id,
        "redirect_uri": REDIRECT_URI,
        "scope": " ".join(SCOPES),
        "state": state,
        "code_challenge": challenge,
        "code_challenge_method": "S256",
    })

    print("\nOpening your browser to authorise these scopes:")
    for scope in SCOPES:
        print(f"    {scope}")
    print("\nIf the browser does not open, paste this URL yourself:\n")
    print(auth_url + "\n")
    webbrowser.open(auth_url)

    print(f"Waiting for the redirect to {REDIRECT_URI} ...")
    code = wait_for_code(state)

    tokens = exchange_code(client_id, client_secret, code, verifier)
    if "access_token" not in tokens:
        sys.exit(f"Token exchange failed:\n{json.dumps(tokens, indent=2)}")

    granted = tokens.get("scope", "")
    print("\n" + "=" * 72)
    print("ACCESS TOKEN\n" + tokens["access_token"])
    print("\nREFRESH TOKEN\n" + tokens.get("refresh_token", "(none returned)"))
    print("\nExpires in:", tokens.get("expires_in"), "seconds")
    print("Granted scopes:", granted)
    print("=" * 72)

    if "media.write" in granted.split():
        print("\nmedia.write IS present. Photo uploads will work.")
    else:
        print(
            "\nmedia.write is MISSING from the granted scopes.\n"
            "Check App permissions is set to 'Read and write' in the Developer\n"
            "Portal, then re-run. Photo uploads will 403 until this is fixed."
        )

    if not tokens.get("refresh_token"):
        print(
            "\nNo refresh token was returned, so the app cannot renew itself and\n"
            "you will have to re-paste an access token every two hours. This means\n"
            "offline.access was not granted."
        )

    print(
        "\nPaste the Access Token and Refresh Token into the app:\n"
        "  Settings -> OAuth 2.0 User Context Keys -> Access Token / Refresh Token\n"
        "  then tap 'Test Connection & Refresh Token'.\n"
        "\nThese values are secrets. They are not saved anywhere by this script;\n"
        "clear your terminal scrollback when you are done."
    )


if __name__ == "__main__":
    main()
