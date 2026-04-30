/**
 * Minimal display test for JC3248W535C (AXS15231B, QSPI)
 * Uses Arduino_Canvas — required for this display to work.
 *
 * Board:  ESP32S3 Dev Module
 * PSRAM:  OPI PSRAM  ← must be enabled
 * Flash Size: 4MB
 * Partition Scheme: Huge APP (3MB No OTA/1MB SPIFFS)
 * USB CDC On Boot: Enabled
 *
 * Library: Arduino_GFX by moononournation
 */

#include <Arduino_GFX_Library.h>

#define LCD_CS   45
#define LCD_CLK  47
#define LCD_D0   21
#define LCD_D1   48
#define LCD_D2   40
#define LCD_D3   39
#define LCD_BL    1

Arduino_DataBus *bus     = nullptr;
Arduino_GFX     *display = nullptr;
Arduino_Canvas  *gfx     = nullptr;

void setup() {
  Serial.begin(115200);
  unsigned long t0 = millis();
  while (!Serial && millis() - t0 < 3000) delay(10);
  delay(200);

  Serial.println("=== display_test boot ===");

  Serial.print("step 1: PSRAM check — ");
  Serial.println(psramFound() ? "FOUND" : "NOT FOUND (will likely fail)");

  Serial.println("step 2: backlight ON");
  pinMode(LCD_BL, OUTPUT);
  digitalWrite(LCD_BL, HIGH);

  Serial.println("step 3: create QSPI bus");
  bus = new Arduino_ESP32QSPI(LCD_CS, LCD_CLK, LCD_D0, LCD_D1, LCD_D2, LCD_D3);

  Serial.println("step 4: create AXS15231B driver");
  display = new Arduino_AXS15231B(bus, GFX_NOT_DEFINED, 0, false, 320, 480);

  Serial.println("step 5: create Canvas (needs PSRAM for 300KB frame buffer)");
  gfx = new Arduino_Canvas(320, 480, display, 0, 0, 0);

  Serial.println("step 6: gfx->begin(40MHz)");
  if (!gfx->begin(40000000UL)) {
    Serial.println("ERROR: begin() returned false");
  }
  Serial.println("step 6: done");

  Serial.println("step 7: fill RED + flush");
  gfx->fillScreen(RED);
  gfx->flush();
  delay(1000);

  Serial.println("step 8: fill GREEN + flush");
  gfx->fillScreen(GREEN);
  gfx->flush();
  delay(1000);

  Serial.println("step 9: fill BLUE + flush");
  gfx->fillScreen(BLUE);
  gfx->flush();
  delay(1000);

  Serial.println("step 10: draw text + flush");
  gfx->fillScreen(BLACK);
  gfx->setTextColor(WHITE);
  gfx->setTextSize(2);
  gfx->setCursor(60, 220);
  gfx->print("IT WORKS!");
  gfx->flush();

  Serial.println("=== all done ===");
}

void loop() {
  delay(5000);
  Serial.println("[alive]");
}
