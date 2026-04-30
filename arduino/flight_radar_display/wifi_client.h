#pragma once

#include <WiFi.h>

inline void wifiConnect(const char *ssid, const char *pass) {
  WiFi.mode(WIFI_STA);
  WiFi.begin(ssid, pass);
  Serial.printf("[wifi] connecting to %s", ssid);

  unsigned long t = millis();
  while (WiFi.status() != WL_CONNECTED) {
    if (millis() - t > 15000) {
      Serial.println("\n[wifi] timeout — reboot");
      ESP.restart();
    }
    delay(500);
    Serial.print('.');
  }

  Serial.printf("\n[wifi] connected  IP=%s\n", WiFi.localIP().toString().c_str());
}
