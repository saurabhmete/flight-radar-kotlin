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

static Weather lastWeather  = {};
static unsigned long lastWeatherFetch = 0;
static const unsigned long WEATHER_TTL_MS = 600000UL;  // re-fetch weather every 10 min

void setup() {
  Serial.begin(115200);
  unsigned long t0 = millis();
  while (!Serial && millis() - t0 < 3000) delay(10);
  delay(200);

  Serial.println("[boot] starting...");

  Serial.println("[boot] init display");
  displayInit();
  Serial.println("[boot] display OK");
  showSplash();

  Serial.println("[boot] connecting WiFi");
  wifiConnect(WIFI_SSID, WIFI_PASSWORD);
  showStatus("WiFi OK");
  Serial.println("[boot] WiFi OK");

  Serial.println("[boot] syncing NTP...");
  configTime(0, 0, "pool.ntp.org", "time.nist.gov");
  struct tm ti;
  for (int i = 0; i < 20 && !getLocalTime(&ti); i++) delay(500);
  Serial.printf("[boot] NTP OK — %04d-%02d-%02d %02d:%02d\n",
                ti.tm_year + 1900, ti.tm_mon + 1, ti.tm_mday, ti.tm_hour, ti.tm_min);
}

void loop() {
  static unsigned long lastFetch = 0;
  unsigned long now = millis();

  if (now - lastFetch >= FETCH_INTERVAL_MS) {
    lastFetch = now;

    Flight flights[MAX_FLIGHTS];
    int count = fetchFlights(flights, MAX_FLIGHTS);

    if (count > 0) {
      renderFlights(flights, count);
    } else {
      // No flights — show weather instead
      bool weatherStale = !lastWeather.valid ||
                          (now - lastWeatherFetch) >= WEATHER_TTL_MS;
      if (weatherStale) {
        Serial.println("[boot] fetching weather...");
        fetchWeather(lastWeather);
        lastWeatherFetch = now;
      }
      renderWeather(lastWeather);
    }
  }
}
