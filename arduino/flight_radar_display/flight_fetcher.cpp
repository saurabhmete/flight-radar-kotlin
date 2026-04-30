#include "flight_fetcher.h"
#include "config.h"

#include <HTTPClient.h>
#include <ArduinoJson.h>

static void safeCopy(char *dst, size_t dstLen, const char *src) {
  if (!src) { dst[0] = 0; return; }
  strncpy(dst, src, dstLen - 1);
  dst[dstLen - 1] = 0;
}

int fetchFlights(Flight *out, int maxCount) {
  HTTPClient http;
  String url = String(API_HOST) + "/api/flights/nearby?limit=" + String(maxCount);
  Serial.println("[fetch] GET " + url);

  http.begin(url);
  http.setTimeout(8000);

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
    safeCopy(fl.image_url,    sizeof(fl.image_url),       f["aircraft_image_url"] | "");
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
