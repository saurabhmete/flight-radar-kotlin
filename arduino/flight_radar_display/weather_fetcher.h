#pragma once
#include <Arduino.h>

enum WeatherIcon {
  WEATHER_CLEAR,    // WMO 0,1 — clear / mainly clear
  WEATHER_PARTLY,   // WMO 2   — partly cloudy
  WEATHER_CLOUDY,   // WMO 3   — overcast
  WEATHER_FOG,      // WMO 45,48
  WEATHER_DRIZZLE,  // WMO 51-55
  WEATHER_RAIN,     // WMO 61-65, 80-82
  WEATHER_SNOW,     // WMO 71-77, 85-86
  WEATHER_STORM,    // WMO 95, 96, 99
  WEATHER_UNKNOWN
};

struct Weather {
  float       temp_c;
  float       feels_c;
  uint8_t     humidity;   // %
  uint8_t     wind_kmh;
  int         code;       // raw WMO code
  WeatherIcon icon;
  char        condition[28];
  bool        valid;
};

bool fetchWeather(Weather &out);
