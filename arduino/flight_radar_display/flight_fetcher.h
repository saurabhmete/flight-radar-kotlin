#pragma once

#include <Arduino.h>

struct Flight {
  char callsign[12];       // flight callsign (e.g. EJU35XM)
  char icao24[8];          // hex transponder code (e.g. 3c4b26)
  char origin[6];          // departure_iata
  char destination[6];     // arrival_iata
  char aircraft[20];       // aircraft_name_short or type ICAO
  char operator_name[28];
  char origin_name[28];    // departure_name (truncated)
  char dest_name[28];      // arrival_name   (truncated)
  char image_url[192];     // aircraft_image_url
  bool image_exact;        // true when type == "EXACT" (real photo)
  float altitude;          // feet  (converted from metres)
  float speed;             // km/h  (converted from m/s x3.6)
  float heading;
  float lat;
  float lon;
  float distance_km;
};

int fetchFlights(Flight *out, int maxCount);
