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

Critically, **the Developer Portal cannot grant `media.write` at all.** Its
"Generate OAuth 2.0 Access Token" dialog offers a hardcoded checkbox list that
does not include the scope. The only way to get it is the authorization flow
below, which sends the scope list explicitly.

This script then prints the `scope` the server actually returned and checks
`media.write` is in it. That echoed field is the only introspection X offers.

WHAT IT DOES NOT DO
-------------------
Nothing is written to disk and nothing is committed. The tokens are printed to
your terminal for you to paste into the app's Settings screen. Your client
secret is read from a hidden prompt, never from an argument (arguments show up
in shell history and in `ps`).

USAGE
-----
    python3 tools/x_oauth_setup.py [--redirect-uri URL]

The redirect URI must be registered on your App **verbatim** — X does an exact
string match. Two capture modes are chosen automatically:

  * loopback  (default, http://127.0.0.1:8765/callback)
        The script runs a one-shot local server and catches the redirect
        itself. Requires that you add the loopback URL to the App's callback
        list. Note that X may refuse a plain-http callback on a confidential
        client, in which case use the other mode.

  * hosted    (anything else, e.g. https://github.com/you)
        Use a callback already registered on the App. Your browser lands on
        that page with `?code=...` in the address bar; you paste the whole URL
        back here and the script exchanges it.

Prerequisites, in the X Developer Portal under your project's App ->
"User authentication settings":

  1. App permissions: "Read and write"  (or "... and Direct message")
  2. Type of App:     "Web App, Automated App or Bot"  (a confidential client)
  3. Callback URI:    whatever you pass to --redirect-uri

Then run the script and follow the browser prompt.

TIMING
------
X authorization codes expire in roughly **30 seconds**. In hosted mode the
paste prompt is deliberately the last thing before the network call, so have
the terminal ready. If you see "Value passed for the authorization code was
invalid", the code simply went stale — just run it again.
"""

from __future__ import annotations

import argparse
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

DEFAULT_REDIRECT_URI = "http://127.0.0.1:8765/callback"
AUTHORIZE_URL = "https://x.com/i/oauth2/authorize"
TOKEN_URL = "https://api.x.com/2/oauth2/token"


def make_pkce_pair() -> tuple[str, str]:
    """Returns (verifier, challenge) for PKCE S256."""
    verifier = base64.urlsafe_b64encode(os.urandom(64)).decode().rstrip("=")
    digest = hashlib.sha256(verifier.encode("ascii")).digest()
    challenge = base64.urlsafe_b64encode(digest).decode().rstrip("=")
    return verifier, challenge


def is_loopback(redirect_uri: str) -> bool:
    host = urllib.parse.urlparse(redirect_uri).hostname or ""
    return host in {"127.0.0.1", "::1", "localhost"}


class _CallbackHandler(http.server.BaseHTTPRequestHandler):
    """Catches the single redirect X sends back with the authorization code."""

    result: dict[str, str] = {}
    expected_path = "/callback"

    def do_GET(self):  # noqa: N802 - name fixed by BaseHTTPRequestHandler
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path != _CallbackHandler.expected_path:
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


def capture_via_listener(redirect_uri: str, expected_state: str) -> str:
    """Run a one-shot local server and pull the code out of the redirect."""
    parsed = urllib.parse.urlparse(redirect_uri)
    _CallbackHandler.expected_path = parsed.path or "/"
    port = parsed.port or 80

    server = http.server.HTTPServer(("127.0.0.1", port), _CallbackHandler)
    thread = threading.Thread(target=server.handle_request, daemon=True)
    thread.start()
    print(f"Waiting for the redirect to {redirect_uri} ...")
    thread.join(timeout=300)
    server.server_close()

    result = _CallbackHandler.result
    if not result:
        sys.exit("Timed out waiting for the browser redirect (5 minutes).")
    return _code_from(result, expected_state)


def capture_via_paste(expected_state: str) -> str:
    """
    Ask for the URL the browser ended up on.

    Used when the registered callback is a hosted page we cannot listen on. The
    prompt sits immediately before the exchange because the code is only valid
    for about 30 seconds.
    """
    print("\nAuthorise in the browser. You will land on your callback page —")
    print("it does not need to do anything, the code is in the address bar.")
    raw = input("\nPaste the FULL redirected URL here, then press Enter:\n> ").strip()
    if not raw:
        sys.exit("Nothing pasted.")
    query = urllib.parse.urlparse(raw).query
    if not query:
        sys.exit("That URL has no query string — did you copy the whole address bar?")
    return _code_from({k: v[0] for k, v in urllib.parse.parse_qs(query).items()},
                      expected_state)


def _code_from(result: dict[str, str], expected_state: str) -> str:
    if "error" in result:
        sys.exit(f"X returned an error: {result.get('error_description', result['error'])}")
    if result.get("state") != expected_state:
        sys.exit("State mismatch — aborting rather than trusting this redirect.")
    if "code" not in result:
        sys.exit(f"No authorization code in the redirect: {result}")
    return result["code"]


def exchange_code(client_id: str, client_secret: str, code: str,
                  verifier: str, redirect_uri: str) -> dict:
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
        "redirect_uri": redirect_uri,
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


def read_credentials() -> tuple[str, str]:
    """
    Returns (client_id, client_secret).

    Normally both are typed at the prompt, with the secret hidden. When the
    script is driven from a harness there is no usable terminal for getpass, so
    X_CLIENT_ID / X_CLIENT_SECRET are accepted as a fallback. Prefer putting
    them in a mode-600 file you `source` rather than on the command line, where
    they would show up in `ps` and in your shell history.
    """
    client_id = os.environ.get("X_CLIENT_ID", "").strip()
    client_secret = os.environ.get("X_CLIENT_SECRET", "").strip()

    if client_id and client_secret:
        print("Using X_CLIENT_ID / X_CLIENT_SECRET from the environment.")
        return client_id, client_secret

    if not sys.stdin.isatty():
        sys.exit(
            "No terminal available for the prompts. Set X_CLIENT_ID and "
            "X_CLIENT_SECRET in the environment instead."
        )

    client_id = client_id or input("X Client ID: ").strip()
    client_secret = client_secret or getpass.getpass("X Client Secret (hidden): ").strip()
    return client_id, client_secret


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Mint X OAuth 2.0 tokens including the media.write scope.")
    parser.add_argument(
        "--redirect-uri", default=DEFAULT_REDIRECT_URI,
        help=("A callback URL registered on your App, matched exactly. "
              f"Default {DEFAULT_REDIRECT_URI}. Pass a hosted https URL you have "
              "already registered to avoid having to add a loopback one."))
    args = parser.parse_args()
    redirect_uri = args.redirect_uri

    print(__doc__.split("USAGE")[0].strip())
    print("-" * 72)

    client_id, client_secret = read_credentials()
    if not client_id:
        sys.exit("Client ID is required.")
    if not client_secret:
        sys.exit("Client Secret is required.")

    verifier, challenge = make_pkce_pair()
    state = secrets.token_urlsafe(24)

    auth_url = AUTHORIZE_URL + "?" + urllib.parse.urlencode({
        "response_type": "code",
        "client_id": client_id,
        "redirect_uri": redirect_uri,
        "scope": " ".join(SCOPES),
        "state": state,
        "code_challenge": challenge,
        "code_challenge_method": "S256",
    })

    print(f"\nRedirect URI : {redirect_uri}")
    print("Opening your browser to authorise these scopes:")
    for scope in SCOPES:
        print(f"    {scope}")
    print("\nThe consent screen should list \"Upload media like photos and videos\".")
    print("If it does not, media.write was not offered and the rest will not help.")
    print("\nIf the browser does not open, paste this URL yourself:\n")
    print(auth_url + "\n")
    webbrowser.open(auth_url)

    if is_loopback(redirect_uri):
        code = capture_via_listener(redirect_uri, state)
    else:
        code = capture_via_paste(state)

    tokens = exchange_code(client_id, client_secret, code, verifier, redirect_uri)
    if "access_token" not in tokens:
        if "authorization code was invalid" in json.dumps(tokens):
            print("\nThe code expired (they last about 30 seconds). Just run this again.")
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
