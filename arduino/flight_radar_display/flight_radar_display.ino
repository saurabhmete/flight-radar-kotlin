/**
 * Flight Radar Display for JC3248W535C (ESP32-S3, 3.5" 320x480 QSPI IPS)
 *
 * Board:  ESP32S3 Dev Module
 * PSRAM:  OPI PSRAM (enabled)
 * Flash Size: 4MB
 * Partition Scheme: Huge APP (3MB No OTA/1MB SPIFFS)
 * USB CDC on Boot: Enabled
 *
 * Libraries required (Library Manager):
 *   - Arduino_GFX  by moononournation  >= 1.5.0
 *   - ArduinoJson  by Benoit Blanchon  >= 7.x
 *   - JPEGDEC      by Larry Bank
 */

#include "config.h"
#include "display.h"
#include "wifi_client.h"
#include "flight_fetcher.h"
#include "weather_fetcher.h"
#include "spotify_fetcher.h"

// ── State ─────────────────────────────────────────────────────────────────────
static Weather       lastWeather        = {};
static SpotifyTrack  lastTrack          = {};
static char          lastRenderedTitle[64] = {};
static unsigned long lastWeatherFetch   = 0;
static unsigned long lastSpotifyFetch   = 0;
static unsigned long lastFlightFetch    = 0;
static unsigned long lastProgressRender = 0;

static const unsigned long WEATHER_TTL_MS    = 600000UL;  // 10 min
static const unsigned long SPOTIFY_POLL_MS   =   5000UL;  //  5 s
static const unsigned long PROGRESS_RENDER_MS =  1000UL;  //  1 s

enum DisplayMode { MODE_FLIGHTS, MODE_WEATHER, MODE_MUSIC };
static DisplayMode currentMode = MODE_FLIGHTS;

// ── Setup ─────────────────────────────────────────────────────────────────────
void setup() {
  Serial.begin(115200);
  unsigned long t0 = millis();
  while (!Serial && millis() - t0 < 3000) delay(10);
  delay(200);

  Serial.println("[boot] starting...");
  displayInit();
  showSplash();
  touchInit();

  wifiConnect(WIFI_SSID, WIFI_PASSWORD);
  showStatus("WiFi OK");

  configTime(0, 0, "pool.ntp.org", "time.nist.gov");
  struct tm ti;
  for (int i = 0; i < 20 && !getLocalTime(&ti); i++) delay(500);
  Serial.printf("[boot] NTP %04d-%02d-%02d %02d:%02d\n",
                ti.tm_year + 1900, ti.tm_mon + 1, ti.tm_mday, ti.tm_hour, ti.tm_min);

  showStatus("Spotify auth...");
  spotifyRefreshToken();
  showStatus("Ready");
}

// ── Loop ──────────────────────────────────────────────────────────────────────
void loop() {
  unsigned long now = millis();

  // Spotify poll every 5 s
  if (now - lastSpotifyFetch >= SPOTIFY_POLL_MS) {
    lastSpotifyFetch = now;
    fetchNowPlaying(lastTrack);
  }

  // Music screen when Spotify is active
  if (lastTrack.valid && lastTrack.is_playing) {
    bool trackChanged = strcmp(lastRenderedTitle, lastTrack.title) != 0;

    if (currentMode != MODE_MUSIC || trackChanged) {
      currentMode = MODE_MUSIC;
      renderMusic(lastTrack);
      strncpy(lastRenderedTitle, lastTrack.title, sizeof(lastRenderedTitle) - 1);
      lastProgressRender = now;
    } else if (now - lastProgressRender >= PROGRESS_RENDER_MS) {
      unsigned long elapsed = now - lastProgressRender;
      lastProgressRender = now;
      if (lastTrack.is_playing) {
        lastTrack.progress_ms += elapsed;
        if (lastTrack.progress_ms > lastTrack.duration_ms)
          lastTrack.progress_ms = lastTrack.duration_ms;
      }
      renderMusicProgress(lastTrack);
    }
    return;
  }

  // Back to flights/weather when music stops
  if (currentMode == MODE_MUSIC) {
    lastRenderedTitle[0] = 0;
    currentMode = MODE_FLIGHTS;
    lastFlightFetch = 0;
  }

  // Flights / weather every 15 s
  if (now - lastFlightFetch >= FETCH_INTERVAL_MS) {
    lastFlightFetch = now;

    Flight flights[MAX_FLIGHTS];
    int count = fetchFlights(flights, MAX_FLIGHTS);

    if (count > 0) {
      currentMode = MODE_FLIGHTS;
      renderFlights(flights, count);
    } else {
      bool stale = !lastWeather.valid || (now - lastWeatherFetch) >= WEATHER_TTL_MS;
      if (stale) {
        fetchWeather(lastWeather);
        lastWeatherFetch = now;
      }
      currentMode = MODE_WEATHER;
      renderWeather(lastWeather);
    }
  }
}
