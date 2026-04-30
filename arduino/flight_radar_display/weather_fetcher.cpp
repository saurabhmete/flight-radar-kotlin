#include "weather_fetcher.h"
#include "config.h"

#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <math.h>
#include <time.h>

// Moon phase 0.0=new 0.25=first quarter 0.5=full 0.75=last quarter
// Reference new moon: 2000-01-06 18:14 UTC (unix 947182440)
static float _moonPhase() {
  time_t now = time(nullptr);
  if (now < 946684800L) return 0.0f;  // NTP not synced yet
  float elapsed = (float)(now - 947182440L);
  float phase = fmodf(elapsed, 2551443.0f) / 2551443.0f;
  return (phase < 0) ? phase + 1.0f : phase;
}

static WeatherIcon _icon(int code) {
  if (code <= 1)                             return WEATHER_CLEAR;
  if (code == 2)                             return WEATHER_PARTLY;
  if (code == 3)                             return WEATHER_CLOUDY;
  if (code == 45 || code == 48)             return WEATHER_FOG;
  if (code >= 51 && code <= 55)             return WEATHER_DRIZZLE;
  if ((code >= 61 && code <= 65) ||
      (code >= 80 && code <= 82))           return WEATHER_RAIN;
  if ((code >= 71 && code <= 77) ||
      (code >= 85 && code <= 86))           return WEATHER_SNOW;
  if (code >= 95)                           return WEATHER_STORM;
  return WEATHER_UNKNOWN;
}

static const char* _condition(int code) {
  if (code == 0)                return "Clear Sky";
  if (code == 1)                return "Mainly Clear";
  if (code == 2)                return "Partly Cloudy";
  if (code == 3)                return "Overcast";
  if (code == 45 || code == 48) return "Foggy";
  if (code >= 51 && code <= 55) return "Drizzle";
  if (code >= 61 && code <= 65) return "Rain";
  if (code >= 71 && code <= 77) return "Snow";
  if (code >= 80 && code <= 82) return "Rain Showers";
  if (code >= 85 && code <= 86) return "Snow Showers";
  if (code >= 95)               return "Thunderstorm";
  return "Unknown";
}

bool fetchWeather(Weather &out) {
  char url[256];
  snprintf(url, sizeof(url),
    "https://api.open-meteo.com/v1/forecast"
    "?latitude=%.4f&longitude=%.4f"
    "&current=temperature_2m,apparent_temperature,relative_humidity_2m,"
    "wind_speed_10m,weather_code,is_day"
    "&wind_speed_unit=kmh&timezone=auto",
    (double)OBSERVER_LAT, (double)OBSERVER_LON);

  Serial.println("[weather] GET " + String(url));

  WiFiClientSecure client;
  client.setInsecure();   // home network — skip cert verification

  HTTPClient http;
  http.begin(client, url);
  http.setTimeout(10000);

  int code = http.GET();
  if (code != 200) {
    Serial.printf("[weather] HTTP %d\n", code);
    http.end();
    return false;
  }

  String payload = http.getString();
  http.end();

  JsonDocument doc;
  DeserializationError err = deserializeJson(doc, payload);

  if (err) {
    Serial.printf("[weather] JSON error: %s\n", err.c_str());
    return false;
  }

  JsonObject cur = doc["current"];
  out.temp_c    = cur["temperature_2m"]        | 0.0f;
  out.feels_c   = cur["apparent_temperature"]   | 0.0f;
  out.humidity  = cur["relative_humidity_2m"]   | 0;
  out.wind_kmh  = (uint8_t)(cur["wind_speed_10m"].as<float>());
  out.code      = cur["weather_code"]           | 0;
  out.is_night  = (cur["is_day"] | 1) == 0;
  out.icon      = _icon(out.code);
  strncpy(out.condition, _condition(out.code), sizeof(out.condition) - 1);
  out.condition[sizeof(out.condition) - 1] = 0;
  out.moon_phase = _moonPhase();
  out.valid     = true;

  Serial.printf("[weather] %.1fC feels %.1fC  wind %dkm/h  hum %d%%  code %d (%s)  night=%d  moon=%.2f\n",
                out.temp_c, out.feels_c, out.wind_kmh, out.humidity,
                out.code, out.condition, out.is_night, out.moon_phase);
  return true;
}
