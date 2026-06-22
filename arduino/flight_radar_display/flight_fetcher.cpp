#include "flight_fetcher.h"
#include "config.h"

#include <HTTPClient.h>
#include <ArduinoJson.h>

static void safeCopy(char *dst, size_t dstLen, const char *src) {
  if (!src) { dst[0] = 0; return; }
  strncpy(dst, src, dstLen - 1);
  dst[dstLen - 1] = 0;
}

static String urlEncode(const char *s) {
  static const char *hex = "0123456789ABCDEF";
  String out;
  out.reserve(strlen(s) * 3);
  for (const char *p = s; *p; ++p) {
    unsigned char c = (unsigned char)*p;
    if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') ||
        c == '-' || c == '_' || c == '.' || c == '~') {
      out += (char)c;
    } else {
      out += '%';
      out += hex[c >> 4];
      out += hex[c & 0x0F];
    }
  }
  return out;
}

// Wrap a remote image URL with our backend resize proxy so the ESP32 can decode
// at scale=1 (JPEGDEC's scaled modes leave coloured-line artifacts on MCU edges).
static void buildProxiedImageUrl(char *dst, size_t dstLen, const char *origUrl, int w, int h) {
  if (!origUrl || !origUrl[0]) { dst[0] = 0; return; }
  String u = String(API_HOST) + "/api/image/proxy?w=" + String(w) + "&h=" + String(h) +
             "&u=" + urlEncode(origUrl);
  safeCopy(dst, dstLen, u.c_str());
}

int fetchFlights(Flight *out, int maxCount) {
  HTTPClient http;
  String url = String(API_HOST) + "/api/flights/nearby?limit=" + String(maxCount);
  Serial.println("[fetch] GET " + url);

  http.begin(url);
  http.setTimeout(3000);

  int code = http.GET();
  if (code != 200) {
    Serial.printf("[fetch] HTTP %d\n", code);
    http.end();
    return 0;
  }

  String payload = http.getString();
  http.end();

  JsonDocument doc;
  DeserializationError err = deserializeJson(doc, payload);

  if (err) {
    Serial.printf("[fetch] JSON error: %s\n", err.c_str());
    return 0;
  }

  JsonArray arr = doc["flights"].as<JsonArray>();
  int count = 0;
  for (JsonObject f : arr) {
    if (count >= maxCount) break;
    Flight &fl = out[count++];

    // If callsign is missing/empty, fall back to icao24 (same as web app)
    safeCopy(fl.icao24, sizeof(fl.icao24), f["icao24"] | "");
    const char *cs = f["callsign"] | "";
    if (!cs || !cs[0]) cs = fl.icao24;
    safeCopy(fl.callsign, sizeof(fl.callsign), cs[0] ? cs : "------");
    safeCopy(fl.origin,        sizeof(fl.origin),         f["departure_iata"]     | "");
    safeCopy(fl.destination,   sizeof(fl.destination),    f["arrival_iata"]       | "");
    safeCopy(fl.origin_name,   sizeof(fl.origin_name),    f["departure_name"]     | "");
    safeCopy(fl.dest_name,     sizeof(fl.dest_name),      f["arrival_name"]       | "");
    safeCopy(fl.operator_name, sizeof(fl.operator_name),  f["operator_name"]      | "");
    // Route aircraft image through backend proxy at exactly the display slot dimensions
    // (310x107 matches cw-2 x IMAGE_H-1 in display.h) so JPEGDEC decodes at scale=1 — no
    // SCALE_HALF/QUARTER artifacts, and the photo fills the box (proxy does center-crop).
    buildProxiedImageUrl(fl.image_url, sizeof(fl.image_url), f["aircraft_image_url"] | "", 310, 107);
    const char *imgType = f["aircraft_image_type"] | "";
    fl.image_exact = (strcmp(imgType, "EXACT") == 0);

    // Prefer short name, fall back to ICAO type code
    const char *ac = f["aircraft_name_short"] | "";
    if (!ac || !ac[0]) ac = f["aircraft_type_icao"] | "";
    safeCopy(fl.aircraft, sizeof(fl.aircraft), ac);

    // OpenSky units: altitude metres → feet, velocity m/s → km/h (matches web)
    fl.altitude    = (f["altitude"]    | 0.0f) * 3.28084f;
    fl.speed       = (f["velocity"]    | 0.0f) * 3.6f;
    fl.heading     = f["true_track"]   | 0.0f;
    fl.lat         = f["lat"]          | 0.0f;
    fl.lon         = f["lon"]          | 0.0f;
    fl.distance_km = f["distance_km"]  | 0.0f;

    Serial.printf("[fetch] #%d: %s  %s→%s  %.0fft  %.0fkm/h  %.1fkm\n",
                  count, fl.callsign, fl.origin, fl.destination,
                  fl.altitude, fl.speed, fl.distance_km);
  }

  Serial.printf("[fetch] total: %d flights\n", count);
  return count;
}
