#!/usr/bin/env python3
"""
One-time Spotify OAuth setup — generates the refresh_token for config.h.

Requirements:  pip install requests
Usage:         python3 spotify_auth.py

Spotify Dashboard setup:
  1. Go to https://developer.spotify.com/dashboard and create an app.
  2. Under "Redirect URIs" add:  http://127.0.0.1:8888/callback
     HTTP is allowed for loopback IPs (127.0.0.1). Do NOT use "localhost" —
     Spotify's new policy (April 2025) requires the explicit IPv4 loopback address.
  3. Run this script and follow the prompts.
"""

import webbrowser
import urllib.parse
import http.server
import threading
import base64
import sys

try:
    import requests
except ImportError:
    sys.exit("Install requests first:  pip install requests")

CLIENT_ID     = input("Client ID:     ").strip()
CLIENT_SECRET = input("Client Secret: ").strip()
REDIRECT_URI  = "http://127.0.0.1:8888/callback"
SCOPE         = "user-read-playback-state user-modify-playback-state"

# ── Open auth page ────────────────────────────────────────────────────────────
params = urllib.parse.urlencode({
    "client_id":     CLIENT_ID,
    "response_type": "code",
    "redirect_uri":  REDIRECT_URI,
    "scope":         SCOPE,
})
webbrowser.open(f"https://accounts.spotify.com/authorize?{params}")
print("\nBrowser opened — log in and click Agree.")
print("The script will continue automatically once you approve...\n")

# ── Local server catches the redirect ─────────────────────────────────────────
auth_code = None

class _Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        global auth_code
        qs        = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
        auth_code = qs.get("code", [None])[0]
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b"<h1>Done! You can close this tab and return to the terminal.</h1>")

    def log_message(self, *_):
        pass

srv = http.server.HTTPServer(("localhost", 8888), _Handler)
threading.Thread(target=srv.handle_request, daemon=True).start()

# Block until code arrives (or user hits Ctrl-C)
import time
while auth_code is None:
    time.sleep(0.2)

if not auth_code:
    sys.exit("No auth code received.")

# ── Exchange code for tokens ──────────────────────────────────────────────────
creds = base64.b64encode(f"{CLIENT_ID}:{CLIENT_SECRET}".encode()).decode()
r = requests.post(
    "https://accounts.spotify.com/api/token",
    headers={
        "Authorization": f"Basic {creds}",
        "Content-Type":  "application/x-www-form-urlencoded",
    },
    data={
        "grant_type":  "authorization_code",
        "code":         auth_code,
        "redirect_uri": REDIRECT_URI,
    },
)

data = r.json()
if "refresh_token" not in data:
    sys.exit(f"Token exchange failed: {data}")

print("── Add these lines to arduino/flight_radar_display/config.h ──────────────")
print(f'#define SPOTIFY_CLIENT_ID     "{CLIENT_ID}"')
print(f'#define SPOTIFY_CLIENT_SECRET "{CLIENT_SECRET}"')
print(f'#define SPOTIFY_REFRESH_TOKEN "{data["refresh_token"]}"')
print("────────────────────────────────────────────────────────────────────────────")
