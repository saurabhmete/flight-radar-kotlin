#include "spotify_fetcher.h"
#include "config.h"

#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <mbedtls/base64.h>

static char          _token[256]  = {};
static unsigned long _tokenExpiry = 0;

// ── Token management ─────────────────────────────────────────────────────────

bool spotifyRefreshToken() {
  // base64-encode "client_id:client_secret" for Basic auth
  char creds[256];
  snprintf(creds, sizeof(creds), "%s:%s", SPOTIFY_CLIENT_ID, SPOTIFY_CLIENT_SECRET);
  unsigned char b64[512];
  size_t b64len = 0;
  mbedtls_base64_encode(b64, sizeof(b64), &b64len,
                        (const unsigned char *)creds, strlen(creds));
  b64[b64len] = 0;

  WiFiClientSecure client;
  client.setInsecure();
  HTTPClient http;
  http.begin(client, "https://accounts.spotify.com/api/token");
  http.addHeader("Authorization", String("Basic ") + (char *)b64);
  http.addHeader("Content-Type", "application/x-www-form-urlencoded");

  String body = "grant_type=refresh_token&refresh_token=";
  body += SPOTIFY_REFRESH_TOKEN;

  int code = http.POST(body);
  if (code != 200) {
    Serial.printf("[spotify] token refresh HTTP %d\n", code);
    http.end();
    return false;
  }

  String payload = http.getString();
  http.end();

  JsonDocument doc;
  if (deserializeJson(doc, payload)) return false;

  const char *tok = doc["access_token"] | "";
  if (!tok[0]) return false;

  strncpy(_token, tok, sizeof(_token) - 1);
  int expiresIn = doc["expires_in"] | 3600;
  _tokenExpiry  = millis() + (unsigned long)(expiresIn - 60) * 1000UL;

  Serial.println("[spotify] token OK");
  return true;
}

static bool _ensureToken() {
  if (_token[0] && millis() < _tokenExpiry) return true;
  return spotifyRefreshToken();
}

// ── Now-playing ──────────────────────────────────────────────────────────────

bool fetchNowPlaying(SpotifyTrack &out) {
  if (!_ensureToken()) return false;

  WiFiClientSecure client;
  client.setInsecure();
  HTTPClient http;
  http.begin(client, "https://api.spotify.com/v1/me/player/currently-playing");
  http.addHeader("Authorization", String("Bearer ") + _token);
  http.setTimeout(8000);

  int code = http.GET();

  if (code == 204) {          // nothing playing
    http.end();
    out.valid      = false;
    out.is_playing = false;
    return true;
  }
  if (code != 200) {
    Serial.printf("[spotify] now-playing HTTP %d\n", code);
    http.end();
    return false;
  }

  String payload = http.getString();
  http.end();

  JsonDocument doc;
  if (deserializeJson(doc, payload)) return false;

  out.is_playing  = doc["is_playing"] | false;
  out.progress_ms = doc["progress_ms"] | 0;

  JsonObject item = doc["item"];
  if (!item) { out.valid = false; return true; }

  out.duration_ms = item["duration_ms"] | 0;

  strncpy(out.title, item["name"] | "", sizeof(out.title) - 1);
  out.title[sizeof(out.title) - 1] = 0;

  JsonArray artists = item["artists"].as<JsonArray>();
  if (artists.size() > 0) {
    strncpy(out.artist, artists[0]["name"] | "", sizeof(out.artist) - 1);
    out.artist[sizeof(out.artist) - 1] = 0;
  }

  strncpy(out.album, item["album"]["name"] | "", sizeof(out.album) - 1);
  out.album[sizeof(out.album) - 1] = 0;

  // Spotify returns images largest-first [640, 300, 64].
  // Use the medium (300x300) — good quality, reasonable download size.
  JsonArray images = item["album"]["images"].as<JsonArray>();
  out.art_url[0] = 0;
  if (images.size() > 0) {
    int idx = (images.size() >= 2) ? 1 : 0;   // prefer 300x300, fall back to 640
    strncpy(out.art_url, images[idx]["url"] | "", sizeof(out.art_url) - 1);
    out.art_url[sizeof(out.art_url) - 1] = 0;
  }

  out.valid = true;
  Serial.printf("[spotify] %s - %s  %lu/%lus  %s\n",
                out.artist, out.title,
                out.progress_ms / 1000, out.duration_ms / 1000,
                out.is_playing ? "playing" : "paused");
  return true;
}

// ── Playback controls ─────────────────────────────────────────────────────────

static void _cmd(bool usePost, const char *path) {
  if (!_ensureToken()) return;
  WiFiClientSecure client;
  client.setInsecure();
  HTTPClient http;
  http.begin(client, String("https://api.spotify.com/v1/me/player/") + path);
  http.addHeader("Authorization", String("Bearer ") + _token);
  http.addHeader("Content-Length", "0");
  int code = usePost ? http.POST("") : http.PUT("");
  Serial.printf("[spotify] %s %s -> %d\n", usePost ? "POST" : "PUT", path, code);
  http.end();
}

void spotifyPlay()  { _cmd(false, "play");     }
void spotifyPause() { _cmd(false, "pause");    }
void spotifyNext()  { _cmd(true,  "next");     }
void spotifyPrev()  { _cmd(true,  "previous"); }
