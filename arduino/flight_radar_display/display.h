#pragma once

#include <Arduino_GFX_Library.h>
#include <JPEGDEC.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <Wire.h>
#include <math.h>
#include "config.h"
#include "flight_fetcher.h"
#include "weather_fetcher.h"
#include "spotify_fetcher.h"

// ── Colours (RGB565, matching web: #000 bg / #f0a020 amber / #111 surface) ──
#define C_BG      0x0000   // #000000  background
#define C_SURFACE 0x1082   // #111111  card surface
#define C_BORDER  0x2104   // #1c1c1c  card border / separator
#define C_ACCENT  0xF504   // #f0a020  amber  (callsign, route codes)
#define C_TEXT    0xE71C   // #e2e2e2  primary text
#define C_DIM2    0x5ACB   // #5a5a5a  secondary text
#define C_DIM3    0x8C11   // #8a8a8a  tertiary text / aircraft type

// ── Weather colours ──────────────────────────────────────────────────────────
#define C_CLOUD   0xCE79   // #CCCCCC  cloud body
#define C_RAIN    0x4C96   // #4A90B0  rain drops
#define C_SNOW    0xE71C   // #E2E2E2  snow (reuses C_TEXT)
#define C_FOG     0x7BEF   // #787878  fog bars
#define C_STORM   0x4208   // #404040  storm cloud

// ── Layout ───────────────────────────────────────────────────────────────────
#define HEADER_H     60
#define CARD_X        4
#define CARD_W      312    // SCREEN_W - 2*CARD_X
#define CARD_PAD     10
#define CARD_R        6
#define GAP           4
#define IMAGE_H      108   // aircraft photo height inside primary card
#define PRIMARY_Y   (HEADER_H + GAP)
#define PRIMARY_H   214
#define COMPACT1_Y  (PRIMARY_Y + PRIMARY_H + GAP)
#define COMPACT_H    96
#define COMPACT2_Y  (COMPACT1_Y + COMPACT_H + GAP)
// Total: 60+4+214+4+96+4+96 = 478 ✓

// ── Hardware ─────────────────────────────────────────────────────────────────
// AXS15231B QSPI displays REQUIRE a canvas (frame buffer in PSRAM).
// Direct pixel writes to the display bus don't work — you must flush().
static Arduino_DataBus *_bus     = nullptr;
static Arduino_GFX     *_display = nullptr;
static Arduino_Canvas  *_gfx     = nullptr;

inline void displayInit() {
  if (!psramFound()) {
    Serial.println("[display] ERROR: PSRAM not found — canvas needs PSRAM. Check board settings.");
  }

  pinMode(LCD_BL, OUTPUT);
  digitalWrite(LCD_BL, HIGH);

  _bus     = new Arduino_ESP32QSPI(LCD_CS, LCD_CLK, LCD_D0, LCD_D1, LCD_D2, LCD_D3);
  _display = new Arduino_AXS15231B(_bus, GFX_NOT_DEFINED, 0, false, SCREEN_W, SCREEN_H);
  _gfx     = new Arduino_Canvas(SCREEN_W, SCREEN_H, _display, 0, 0, 0);

  if (!_gfx->begin(40000000UL)) {
    Serial.println("[display] begin() failed");
  }

  _gfx->fillScreen(C_BG);
  _gfx->flush();
}

// ── JPEG image loader ─────────────────────────────────────────────────────────
static JPEGDEC    _jpeg;
static uint16_t  *_imgBuf  = nullptr;
static int        _imgBufW, _imgBufH;

// Collect each MCU block into a flat PSRAM pixel buffer (stride = _imgBufW).
// Drawing happens once after decode — avoids all per-block stride/timing issues.
static int _jpegCb(JPEGDRAW *d) {
  if (!_imgBuf) return 0;
  int cols = min((int)d->iWidth, _imgBufW - (int)d->x);
  if (cols <= 0) return 1;
  for (int row = 0; row < (int)d->iHeight; row++) {
    if ((int)(d->y + row) >= _imgBufH) break;
    memcpy(&_imgBuf[(d->y + row) * _imgBufW + d->x],
           d->pPixels + row * d->iWidth,
           cols * sizeof(uint16_t));
  }
  return 1;
}

// Fetch JPEG from url and draw it centred in box (x,y,maxW,maxH).
// Returns true on success. Uses ps_malloc — requires PSRAM.
static bool _drawImageFromUrl(const char *url, int x, int y, int maxW, int maxH) {
  if (!url || !url[0]) return false;

  // Use WiFiClientSecure for HTTPS image URLs (Spotify CDN), plain for HTTP
  bool isHttps = strncmp(url, "https://", 8) == 0;
  WiFiClientSecure secureClient;
  if (isHttps) secureClient.setInsecure();

  HTTPClient http;
  if (isHttps) http.begin(secureClient, url);
  else         http.begin(url);
  http.setTimeout(8000);
  http.setFollowRedirects(HTTPC_STRICT_FOLLOW_REDIRECTS);
  http.addHeader("User-Agent", "FlightRadar/1.0 ESP32");
  int code = http.GET();
  if (code != 200) {
    Serial.printf("[img] HTTP %d for %s\n", code, url);
    http.end();
    return false;
  }

  int len = http.getSize();
  if (len <= 0 || len > 300000) { http.end(); return false; }

  uint8_t *buf = (uint8_t*)ps_malloc(len);
  if (!buf) { Serial.println("[img] ps_malloc failed"); http.end(); return false; }

  WiFiClient *stream = http.getStreamPtr();
  int got = 0;
  unsigned long t0 = millis();
  while (got < len && millis() - t0 < 8000) {
    int av = stream->available();
    if (av > 0) got += stream->readBytes(buf + got, min(av, len - got));
    else delay(1);
  }
  http.end();

  if (got < len) { free(buf); return false; }

  if (!_jpeg.openRAM(buf, got, _jpegCb)) { free(buf); return false; }

  int iw = _jpeg.getWidth();
  int ih = _jpeg.getHeight();

  // Pick smallest power-of-2 scale that fits within the box
  // JPEG_SCALE_EIGHTH has a known chroma reconstruction bug in JPEGDEC that
  // produces 2 green pixels per row. Cap at QUARTER — 8MB PSRAM handles it.
  int flags = 0;
  if (iw > maxW * 2 || ih > maxH * 2) flags = JPEG_SCALE_QUARTER;
  else if (iw > maxW || ih > maxH)     flags = JPEG_SCALE_HALF;

  int scale = (flags == JPEG_SCALE_QUARTER) ? 4 :
              (flags == JPEG_SCALE_HALF)    ? 2 : 1;

  _imgBufW = min(iw / scale, maxW);
  _imgBufH = min(ih / scale, maxH);
  _imgBuf  = (uint16_t*)ps_malloc(_imgBufW * _imgBufH * sizeof(uint16_t));
  if (!_imgBuf) {
    Serial.println("[img] ps_malloc imgBuf failed");
    _jpeg.close(); free(buf); return false;
  }
  memset(_imgBuf, 0, _imgBufW * _imgBufH * sizeof(uint16_t));

  _jpeg.decode(0, 0, flags);
  _jpeg.close();
  free(buf);

  // JPEGDEC produces fully-saturated green (R<4, G>48, B<4) artifacts in some
  // decoded pixels. Real image greens always contain red/blue. Replace with
  // the left neighbour so the lines disappear without affecting real colours.
  for (int i = 0; i < _imgBufW * _imgBufH; i++) {
    uint16_t p = _imgBuf[i];
    if (((p >> 11) & 0x1F) < 4 && ((p >> 5) & 0x3F) > 48 && (p & 0x1F) < 4)
      _imgBuf[i] = (i > 0) ? _imgBuf[i - 1] : 0x0000;
  }

  int dstX = x + max(0, (maxW - _imgBufW) / 2);
  for (int row = 0; row < _imgBufH; row++)
    for (int col = 0; col < _imgBufW; col++)
      _gfx->writePixel(dstX + col, y + row, _imgBuf[row * _imgBufW + col]);

  free(_imgBuf);
  _imgBuf = nullptr;
  return true;
}

// ── Helpers ──────────────────────────────────────────────────────────────────

static void truncate(char *dst, const char *src, int maxChars) {
  strncpy(dst, src, maxChars);
  dst[maxChars] = 0;
}

// Map C3 xx UTF-8 sequences (U+00C0–U+00FF) to nearest ASCII.
// Covers all Latin-1 accented letters: umlauts, acute, grave, cedilla, etc.
static const char _c3map[64] = {
  'A','A','A','A','A','A','A','C', // C0-C7  À Á Â Ã Ä Å Æ Ç
  'E','E','E','E','I','I','I','I', // C8-CF  È É Ê Ë Ì Í Î Ï
  'D','N','O','O','O','O','O','x', // D0-D7  Ð Ñ Ò Ó Ô Õ Ö ×
  'O','U','U','U','U','Y','P','s', // D8-DF  Ø Ù Ú Û Ü Ý Þ ß
  'a','a','a','a','a','a','a','c', // E0-E7  à á â ã ä å æ ç
  'e','e','e','e','i','i','i','i', // E8-EF  è é ê ë ì í î ï
  'd','n','o','o','o','o','o','.', // F0-F7  ð ñ ò ó ô õ ö ÷
  'o','u','u','u','u','y','p','y', // F8-FF  ø ù ú û ü ý þ ÿ
};

// Copy src → dst, transliterating UTF-8 accented chars to ASCII, max dstSize-1 chars.
static void deAccent(char *dst, const char *src, size_t dstSize) {
  size_t di = 0;
  for (size_t si = 0; src[si] && di < dstSize - 1; ) {
    uint8_t b = (uint8_t)src[si];
    if (b < 0x80) {
      dst[di++] = (char)b; si++;
    } else if (b == 0xC3 && src[si + 1]) {
      uint8_t b2 = (uint8_t)src[si + 1];
      if (b2 >= 0x80 && b2 <= 0xBF) dst[di++] = _c3map[b2 - 0x80];
      si += 2;
    } else {
      si++;
      while ((uint8_t)src[si] >= 0x80 && (uint8_t)src[si] < 0xC0) si++;
    }
  }
  dst[di] = 0;
}

// Bearing from observer to a flight (degrees, 0=N clockwise)
static float _bearing(float lat1, float lon1, float lat2, float lon2) {
  float dLon = (lon2 - lon1) * M_PI / 180.0f;
  float lat1r = lat1 * M_PI / 180.0f;
  float lat2r = lat2 * M_PI / 180.0f;
  float y = sinf(dLon) * cosf(lat2r);
  float x = cosf(lat1r) * sinf(lat2r) - sinf(lat1r) * cosf(lat2r) * cosf(dLon);
  return fmodf(atan2f(y, x) * 180.0f / M_PI + 360.0f, 360.0f);
}

// Draw an "AMS ─────→ JFK" route row at (x,y) with total content width w
static void _drawRoute(int x, int y, const char *dep, const char *arr, int w) {
  if (!dep || !dep[0] || !arr || !arr[0]) return;

  int depW  = strlen(dep) * 12;  // textSize 2 → 12px per char
  int arrW  = strlen(arr) * 12;

  _gfx->setTextColor(C_ACCENT);
  _gfx->setTextSize(2);
  _gfx->setCursor(x, y);
  _gfx->print(dep);

  _gfx->setCursor(x + w - arrW, y);
  _gfx->print(arr);

  // Arrow line between the two codes
  int lx1 = x + depW + 5;
  int lx2 = x + w - arrW - 10;
  int ly  = y + 8;   // vertical midpoint of size-2 text

  if (lx2 > lx1 + 10) {
    _gfx->drawLine(lx1, ly, lx2, ly, C_DIM2);
    // Arrowhead (two short lines)
    _gfx->drawLine(lx2, ly, lx2 - 6, ly - 4, C_DIM3);
    _gfx->drawLine(lx2, ly, lx2 - 6, ly + 4, C_DIM3);
  }
}

// ── Mini radar (top-right of header) ─────────────────────────────────────────
// cx,cy = centre; r = radius; flights array for dot positions
static void _drawRadar(int cx, int cy, int r, const Flight *f, int count) {
  // Background circle
  _gfx->fillCircle(cx, cy, r, 0x0841);
  _gfx->drawCircle(cx, cy, r, C_BORDER);

  // Concentric range rings (10, 20, 30, 40 km)
  for (int km = 10; km <= 40; km += 10) {
    int pr = (int)((float)km / RADAR_MAX_KM * r);
    _gfx->drawCircle(cx, cy, pr, C_BORDER);
  }

  // Cross-hairs
  _gfx->drawLine(cx - r, cy, cx + r, cy, C_BORDER);
  _gfx->drawLine(cx, cy - r, cx, cy + r, C_BORDER);

  // Flight dots
  for (int i = 0; i < count; i++) {
    float b   = _bearing(OBSERVER_LAT, OBSERVER_LON, f[i].lat, f[i].lon);
    float fr  = fminf((f[i].distance_km / RADAR_MAX_KM) * r, r - 3.0f);
    float rad = b * M_PI / 180.0f;
    int fx = cx + (int)(fr * sinf(rad));
    int fy = cy - (int)(fr * cosf(rad));
    uint16_t c = (i == 0) ? C_ACCENT : C_DIM3;
    _gfx->fillCircle(fx, fy, 3, c);
  }

  // Observer dot (amber, centre)
  _gfx->fillCircle(cx, cy, 3, C_ACCENT);
}

// ── Splash screen ─────────────────────────────────────────────────────────────
inline void showSplash() {
  _gfx->fillScreen(C_BG);

  _gfx->setTextColor(C_DIM3);
  _gfx->setTextSize(1);
  _gfx->setCursor(8, 14);
  _gfx->print("F L I G H T  R A D A R");

  _gfx->setTextColor(C_ACCENT);
  _gfx->setTextSize(3);
  _gfx->setCursor(46, 200);
  _gfx->print("loading...");

  _gfx->setTextColor(C_DIM2);
  _gfx->setTextSize(1);
  _gfx->setCursor(60, 240);
  _gfx->print("connecting to WiFi");

  _gfx->flush();
}

inline void showStatus(const char *msg) {
  _gfx->setTextColor(C_DIM2);
  _gfx->setTextSize(1);
  _gfx->fillRect(60, 240, 200, 10, C_BG);
  _gfx->setCursor(60, 240);
  _gfx->print(msg);
  _gfx->flush();
}

// ── Primary card (tall, first flight) ────────────────────────────────────────
static void _drawPrimaryCard(const Flight &f) {
  int cx = CARD_X, cy = PRIMARY_Y, cw = CARD_W, ch = PRIMARY_H;
  int px = cx + CARD_PAD;
  int pw = cw - CARD_PAD * 2;  // 292

  _gfx->fillRoundRect(cx, cy, cw, ch, CARD_R, C_SURFACE);
  _gfx->drawRoundRect(cx, cy, cw, ch, CARD_R, C_BORDER);

  // ── Aircraft image (top of card) ─────────────────────────────────────────
  bool hasImage = false;
  if (f.image_url[0]) {
    // Dark placeholder while loading
    _gfx->fillRect(cx + 1, cy + 1, cw - 2, IMAGE_H - 1, 0x0841);
    // Flush so user sees the placeholder before we block on HTTP
    _gfx->flush();
    hasImage = _drawImageFromUrl(f.image_url, cx + 1, cy + 1, cw - 2, IMAGE_H - 1);
    if (!hasImage) {
      // Draw aircraft type text as fallback inside placeholder
      _gfx->setTextColor(C_DIM2);
      _gfx->setTextSize(1);
      _gfx->setCursor(cx + cw/2 - 20, cy + IMAGE_H/2 - 4);
      _gfx->print(f.aircraft[0] ? f.aircraft : "no image");
    }
    // Separator line between image and content
    _gfx->drawLine(cx, cy + IMAGE_H, cx + cw, cy + IMAGE_H, C_BORDER);
  }

  int y = cy + (f.image_url[0] ? IMAGE_H + 8 : 10);

  // ── Callsign (SIZE 3, amber) + icao24 (SIZE 1, dim) + aircraft type right ─
  _gfx->setTextColor(C_ACCENT);
  _gfx->setTextSize(3);
  _gfx->setCursor(px, y);
  char cs[12]; truncate(cs, f.callsign, 7);
  _gfx->print(cs);

  // icao24 hex code in small dim text right after the callsign
  if (f.icao24[0]) {
    int csW = strlen(cs) * 18;  // size-3 char is 18px wide
    _gfx->setTextColor(C_DIM2);
    _gfx->setTextSize(1);
    _gfx->setCursor(px + csW + 5, y + 16);
    _gfx->print(f.icao24);
  }

  // Aircraft type right-aligned (only when no image; image area already shows it)
  if (f.aircraft[0] && !f.image_url[0]) {
    _gfx->setTextColor(C_DIM3);
    _gfx->setTextSize(1);
    char ac[16]; truncate(ac, f.aircraft, 15);
    int acW = strlen(ac) * 6;
    _gfx->setCursor(cx + cw - CARD_PAD - acW, y + 16);
    _gfx->print(ac);
  }
  y += 28;

  // ── Operator name (SIZE 1, tertiary) ─────────────────────────────────────
  if (f.operator_name[0]) {
    _gfx->setTextColor(C_DIM3);
    _gfx->setTextSize(1);
    char op[40]; deAccent(op, f.operator_name, sizeof(op));
    op[pw / 6] = 0;
    _gfx->setCursor(px, y);
    _gfx->print(op);
    y += 10;
  }
  y += 2;

  // ── Route: AMS ──→ JFK ───────────────────────────────────────────────────
  _drawRoute(px, y, f.origin, f.destination, pw);
  y += 20;

  // Airport names (dim, SIZE 1)
  if (f.origin_name[0] || f.dest_name[0]) {
    _gfx->setTextColor(C_DIM2);
    _gfx->setTextSize(1);
    int halfChars = (pw / 2 - 4) / 6;

    char dn[24]; deAccent(dn, f.origin_name[0] ? f.origin_name : f.origin, sizeof(dn));
    dn[halfChars] = 0;
    _gfx->setCursor(px, y);
    _gfx->print(dn);

    char an[24]; deAccent(an, f.dest_name[0] ? f.dest_name : f.destination, sizeof(an));
    an[halfChars] = 0;
    int anW = strlen(an) * 6;
    _gfx->setCursor(cx + cw - CARD_PAD - anW, y);
    _gfx->print(an);
    y += 12;
  }
  y += 2;

  // ── Stats: altitude · speed · distance ───────────────────────────────────
  _gfx->setTextColor(C_DIM2);
  _gfx->setTextSize(1);
  _gfx->setCursor(px, y);

  char stats[60];
  int altFt = (int)f.altitude;
  int spd   = (int)f.speed;

  if (f.distance_km > 0.1f) {
    snprintf(stats, sizeof(stats), "%d ft | %d km/h | %.1f km",
             altFt, spd, f.distance_km);
  } else {
    snprintf(stats, sizeof(stats), "%d ft | %d km/h", altFt, spd);
  }
  _gfx->print(stats);
  y += 12;

  // ── Heading (if available) ────────────────────────────────────────────────
  if (f.heading > 0.5f) {
    _gfx->setTextColor(C_DIM2);
    _gfx->setTextSize(1);
    _gfx->setCursor(px, y);
    char hdg[24];
    snprintf(hdg, sizeof(hdg), "hdg %.0fdeg | FL%d", f.heading, altFt / 100);
    _gfx->print(hdg);
  }
}

// ── Compact card (shorter, flights 2 & 3) ────────────────────────────────────
static void _drawCompactCard(const Flight &f, int cardY) {
  int cx = CARD_X, cw = CARD_W, ch = COMPACT_H;
  int px = cx + CARD_PAD;
  int pw = cw - CARD_PAD * 2;

  _gfx->fillRoundRect(cx, cardY, cw, ch, CARD_R, C_SURFACE);
  _gfx->drawRoundRect(cx, cardY, cw, ch, CARD_R, C_BORDER);

  int y = cardY + 10;

  // ── Row 1: callsign (SIZE 2) + route (SIZE 1) + distance right ───────────
  _gfx->setTextColor(C_ACCENT);
  _gfx->setTextSize(2);
  char cs[10]; truncate(cs, f.callsign, 6);
  _gfx->setCursor(px, y);
  _gfx->print(cs);

  int csW = strlen(cs) * 12;

  // Route "FRA → LHR" in SIZE 1 after callsign
  if (f.origin[0] && f.destination[0]) {
    _gfx->setTextColor(C_ACCENT);
    _gfx->setTextSize(1);
    char route[14];
    snprintf(route, sizeof(route), "%s \x10 %s", f.origin, f.destination);  // \x10 = ▶ in some fonts, fallback to >
    snprintf(route, sizeof(route), "%s > %s", f.origin, f.destination);
    _gfx->setCursor(px + csW + 6, y + 4);
    _gfx->print(route);
  }

  // Distance right-aligned
  if (f.distance_km > 0.1f) {
    char dist[12];
    snprintf(dist, sizeof(dist), "%.1fkm", f.distance_km);
    int dW = strlen(dist) * 6;
    _gfx->setTextColor(C_DIM2);
    _gfx->setTextSize(1);
    _gfx->setCursor(cx + cw - CARD_PAD - dW, y + 4);
    _gfx->print(dist);
  }
  y += 22;

  // ── Row 2: operator · aircraft (SIZE 1) + altitude right ─────────────────
  _gfx->setTextColor(C_DIM3);
  _gfx->setTextSize(1);

  char meta[48]; meta[0] = 0;
  char _op[32]; deAccent(_op, f.operator_name, sizeof(_op));
  if (f.operator_name[0] && f.aircraft[0]) {
    snprintf(meta, sizeof(meta), "%s | %s", _op, f.aircraft);
  } else if (f.operator_name[0]) {
    snprintf(meta, sizeof(meta), "%s", _op);
  } else if (f.aircraft[0]) {
    snprintf(meta, sizeof(meta), "%s", f.aircraft);
  }

  // Truncate meta to leave room for altitude on the right
  int altFt = (int)f.altitude;
  char altStr[12]; snprintf(altStr, sizeof(altStr), "FL%d", altFt / 100);
  int altW  = strlen(altStr) * 6;
  int metaMaxChars = (pw - altW - 6) / 6;
  if ((int)strlen(meta) > metaMaxChars) meta[metaMaxChars] = 0;

  _gfx->setCursor(px, y);
  _gfx->print(meta);

  _gfx->setTextColor(C_DIM2);
  _gfx->setCursor(cx + cw - CARD_PAD - altW, y);
  _gfx->print(altStr);
}

// ── Full render ───────────────────────────────────────────────────────────────
inline void renderFlights(const Flight *flights, int count) {
  _gfx->fillScreen(C_BG);

  // ── Header ───────────────────────────────────────────────────────────────
  // Wordmark
  _gfx->setTextColor(C_DIM3);
  _gfx->setTextSize(1);
  _gfx->setCursor(8, 10);
  _gfx->print("F L I G H T  R A D A R");

  // Status line
  char statusLine[40];
  if (count > 0) {
    snprintf(statusLine, sizeof(statusLine), "%d overhead | upd %lus", count, millis() / 1000);
  } else {
    snprintf(statusLine, sizeof(statusLine), "no flights | upd %lus", millis() / 1000);
  }
  _gfx->setTextColor(C_DIM2);
  _gfx->setCursor(8, 24);
  _gfx->print(statusLine);

  // Mini radar (top-right)
  _drawRadar(280, 34, 22, flights, count);

  // Header separator
  _gfx->drawLine(0, HEADER_H - 1, SCREEN_W, HEADER_H - 1, C_BORDER);

  // ── Cards ─────────────────────────────────────────────────────────────────
  if (count >= 1) _drawPrimaryCard(flights[0]);
  if (count >= 2) _drawCompactCard(flights[1], COMPACT1_Y);
  if (count >= 3) _drawCompactCard(flights[2], COMPACT2_Y);
  _gfx->flush();
}

// ═══════════════════════════════════════════════════════════════════════════
// ── Weather screen (shown when no flights are overhead) ────────────────────
// ═══════════════════════════════════════════════════════════════════════════

// Cloud shape helper: three bumps + rounded base, centred at (cx,cy)
static void _wCloud(int cx, int cy, int w, int h, uint16_t col) {
  int r = h / 2;
  _gfx->fillCircle(cx - w / 4,  cy - r / 2, r * 2 / 3, col);
  _gfx->fillCircle(cx,           cy - r,     r,          col);
  _gfx->fillCircle(cx + w / 4,  cy - r / 2, r * 3 / 4,  col);
  _gfx->fillRoundRect(cx - w / 2, cy - r / 2, w, r + 4, 4, col);
}

// Moon phase: scanline terminator algorithm.
// phase 0=new 0.25=first quarter 0.5=full 0.75=last quarter
static void _drawMoon(int cx, int cy, int r, float phase) {
  uint16_t moonCol = 0xDEFB;   // warm off-white

  _gfx->fillCircle(cx, cy, r, C_BG);
  _gfx->drawCircle(cx, cy, r, C_DIM2);

  float phi = phase * 2.0f * M_PI;
  // Waxing (0–0.5): draw right of terminator. Waning (0.5–1): draw left.
  float txScale  = (phase <= 0.5f) ?  cosf(phi) : -cosf(phi);
  bool  litRight = (phase <= 0.5f);

  for (int dy = -r; dy <= r; dy++) {
    float hw_f = sqrtf((float)(r * r - dy * dy));
    int hw = (int)hw_f;
    if (hw == 0) continue;
    int tx = cx + (int)(txScale * hw_f);
    if (litRight) {
      int x0 = max(tx, cx - hw);
      if (cx + hw > x0) _gfx->drawFastHLine(x0, cy + dy, cx + hw - x0, moonCol);
    } else {
      int x1 = min(tx, cx + hw);
      if (x1 > cx - hw) _gfx->drawFastHLine(cx - hw, cy + dy, x1 - (cx - hw), moonCol);
    }
  }
}

// Sun: filled amber circle + 8 thick rays
static void _wSun(int cx, int cy, int r) {
  int disk = r * 4 / 9;
  _gfx->fillCircle(cx, cy, disk, C_ACCENT);
  for (int i = 0; i < 8; i++) {
    float a = i * M_PI / 4.0f;
    int x1 = cx + (int)(cosf(a) * (disk + 5));
    int y1 = cy + (int)(sinf(a) * (disk + 5));
    int x2 = cx + (int)(cosf(a) * r);
    int y2 = cy + (int)(sinf(a) * r);
    _gfx->drawLine(x1 - 1, y1, x2 - 1, y2, C_ACCENT);
    _gfx->drawLine(x1,     y1, x2,     y2, C_ACCENT);
    _gfx->drawLine(x1 + 1, y1, x2 + 1, y2, C_ACCENT);
  }
}

// Single snowflake asterisk: 3 crossing lines
static void _wFlake(int cx, int cy, int r, uint16_t col) {
  for (int i = 0; i < 3; i++) {
    float a = i * M_PI / 3.0f;
    int dx = (int)(cosf(a) * r), dy = (int)(sinf(a) * r);
    _gfx->drawLine(cx - dx, cy - dy, cx + dx, cy + dy, col);
    _gfx->drawLine(cx - dx + 1, cy - dy, cx + dx + 1, cy + dy, col);
  }
}

// Lightning bolt (two filled triangles forming a Z shape)
static void _wLightning(int cx, int cy, int w, int h) {
  _gfx->fillTriangle(cx - w / 2, cy - h / 2,
                     cx + w / 4, cy,
                     cx - w / 4, cy,          C_ACCENT);
  _gfx->fillTriangle(cx - w / 4, cy,
                     cx + w / 2, cy,
                     cx + w / 2 - 4, cy + h / 2, C_ACCENT);
}

// Draw the large weather icon centred at (cx, cy), outer radius r
static void _drawWeatherIcon(int cx, int cy, int r, WeatherIcon icon) {
  switch (icon) {

    case WEATHER_CLEAR:
      _wSun(cx, cy, r);
      break;

    case WEATHER_PARTLY:
      // Small sun top-right, cloud bottom-left overlapping
      _wSun(cx + r / 4, cy - r / 5, r * 3 / 5);
      _wCloud(cx - r / 6, cy + r / 5, r * 3 / 2, r * 3 / 4, C_CLOUD);
      break;

    case WEATHER_CLOUDY:
      _wCloud(cx, cy, r * 2, r, C_CLOUD);
      break;

    case WEATHER_FOG: {
      // 5 horizontal rounded bars
      int barH = 8, gap = 14;
      for (int i = 0; i < 5; i++) {
        int y = cy - 28 + i * gap;
        int w2 = r * 2 - i * 10;
        _gfx->fillRoundRect(cx - w2 / 2, y, w2, barH, 4, C_FOG);
      }
      break;
    }

    case WEATHER_DRIZZLE:
      _wCloud(cx, cy - r / 4, r * 2, r * 3 / 4, C_CLOUD);
      // 3 short drop lines
      for (int i = 0; i < 3; i++) {
        int x = cx - 22 + i * 22;
        _gfx->drawLine(x, cy + r / 4, x - 5, cy + r / 4 + 14, C_RAIN);
        _gfx->drawLine(x + 1, cy + r / 4, x - 4, cy + r / 4 + 14, C_RAIN);
      }
      break;

    case WEATHER_RAIN:
      _wCloud(cx, cy - r / 4, r * 2, r * 3 / 4, C_CLOUD);
      // 5 longer diagonal drop lines
      for (int i = 0; i < 5; i++) {
        int x = cx - 36 + i * 18;
        int dy = (i % 2) * 8;
        _gfx->drawLine(x, cy + r / 4 + dy, x - 8, cy + r / 4 + dy + 22, C_RAIN);
        _gfx->drawLine(x + 1, cy + r / 4 + dy, x - 7, cy + r / 4 + dy + 22, C_RAIN);
      }
      break;

    case WEATHER_SNOW:
      _wCloud(cx, cy - r / 4, r * 2, r * 3 / 4, C_CLOUD);
      // 3 snowflakes below cloud
      for (int i = 0; i < 3; i++) {
        int sx = cx - 30 + i * 30;
        int sy = cy + r / 2 + (i % 2) * 10;
        _wFlake(sx, sy, 10, C_SNOW);
      }
      break;

    case WEATHER_STORM:
      _wCloud(cx, cy - r / 4, r * 2, r * 3 / 4, C_STORM);
      _wLightning(cx, cy + r / 5, r / 2, r * 3 / 4);
      break;

    default: {
      // Question mark outline
      _gfx->setTextColor(C_DIM2);
      _gfx->setTextSize(5);
      _gfx->setCursor(cx - 15, cy - 20);
      _gfx->print("?");
      break;
    }
  }
}

// Full weather screen rendered when no flights are overhead
inline void renderWeather(const Weather &w) {
  _gfx->fillScreen(C_BG);

  // ── Header (same as flight screen) ─────────────────────────────────────
  _gfx->setTextColor(C_DIM3);
  _gfx->setTextSize(1);
  _gfx->setCursor(8, 10);
  _gfx->print("F L I G H T  R A D A R");

  _gfx->setTextColor(C_DIM2);
  _gfx->setCursor(8, 24);
  _gfx->print("no flights | area clear");

  // Moon phase in top-right when it's night (mirrors radar position on flight screen)
  if (w.is_night) _drawMoon(280, 34, 20, w.moon_phase);

  _gfx->drawLine(0, HEADER_H - 1, SCREEN_W, HEADER_H - 1, C_BORDER);

  // ── "No flights" label ─────────────────────────────────────────────────
  _gfx->setTextColor(C_DIM2);
  _gfx->setTextSize(1);
  _gfx->setCursor(74, 68);
  _gfx->print("CURRENT WEATHER");

  if (!w.valid) {
    _gfx->setTextColor(C_DIM2);
    _gfx->setTextSize(2);
    _gfx->setCursor(52, 220);
    _gfx->print("No weather data");
    _gfx->flush();
    return;
  }

  // ── Weather icon ────────────────────────────────────────────────────────
  _drawWeatherIcon(160, 175, 65, w.icon);

  // ── Temperature ─────────────────────────────────────────────────────────
  char tempBuf[8];
  int tempInt = (int)roundf(w.temp_c);
  snprintf(tempBuf, sizeof(tempBuf), "%d", tempInt);

  // Measure and centre
  int tempW = strlen(tempBuf) * 24 + 18;   // SIZE 4 = 24px/char, "C" in SIZE 3 = 18px
  int tempX = (SCREEN_W - tempW) / 2;

  _gfx->setTextColor(C_ACCENT);
  _gfx->setTextSize(4);
  _gfx->setCursor(tempX, 262);
  _gfx->print(tempBuf);

  _gfx->setTextColor(C_DIM3);
  _gfx->setTextSize(3);
  _gfx->setCursor(tempX + (int)strlen(tempBuf) * 24 + 2, 268);
  _gfx->print("C");

  // ── Condition string ────────────────────────────────────────────────────
  _gfx->setTextColor(C_TEXT);
  _gfx->setTextSize(2);
  int condW = strlen(w.condition) * 12;
  _gfx->setCursor((SCREEN_W - condW) / 2, 306);
  _gfx->print(w.condition);

  // ── Feels like ──────────────────────────────────────────────────────────
  char feelsBuf[24];
  snprintf(feelsBuf, sizeof(feelsBuf), "Feels like %dC", (int)roundf(w.feels_c));
  _gfx->setTextColor(C_DIM2);
  _gfx->setTextColor(C_TEXT);
  _gfx->setTextSize(1);
  int feelsW = strlen(feelsBuf) * 6;
  _gfx->setCursor((SCREEN_W - feelsW) / 2, 330);
  _gfx->print(feelsBuf);

  // ── Wind + Humidity ─────────────────────────────────────────────────────
  char statsBuf[36];
  snprintf(statsBuf, sizeof(statsBuf), "Wind %d km/h   Humidity %d%%",
           w.wind_kmh, w.humidity);
  int statsW = strlen(statsBuf) * 6;
  _gfx->setCursor((SCREEN_W - statsW) / 2, 344);
  _gfx->print(statsBuf);

  // ── Separator + status ──────────────────────────────────────────────────
  _gfx->drawLine(20, 364, SCREEN_W - 20, 364, C_BORDER);

  _gfx->setTextColor(C_DIM2);
  _gfx->setTextSize(1);
  char updBuf[40];
  snprintf(updBuf, sizeof(updBuf), "Checking for flights every 15s");
  int updW = strlen(updBuf) * 6;
  _gfx->setCursor((SCREEN_W - updW) / 2, 374);
  _gfx->print(updBuf);

  _gfx->flush();
}

// ═══════════════════════════════════════════════════════════════════════════
// ── Touch ──────────────────────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════════

inline void touchInit() {
  pinMode(TOUCH_RST, OUTPUT);
  pinMode(TOUCH_INT, INPUT_PULLUP);
  digitalWrite(TOUCH_RST, LOW);  delay(10);
  digitalWrite(TOUCH_RST, HIGH); delay(50);
  Wire.begin(TOUCH_SDA, TOUCH_SCL);
  Wire.setClock(TOUCH_I2C_HZ);
  Serial.println("[touch] init OK");
}

// AXS15231B: INT pin goes LOW when a finger is down.
// Read 6 bytes directly — no register write needed.
inline bool touchRead(int &x, int &y) {
  if (digitalRead(TOUCH_INT) != LOW) return false;  // no touch

  uint8_t buf[6] = {};
  Wire.requestFrom((uint8_t)TOUCH_ADDR, (uint8_t)6);
  for (int i = 0; i < 6 && Wire.available(); i++) buf[i] = Wire.read();

  // buf[1] = finger count, buf[2..5] = x/y coords
  if (buf[1] == 0) return false;
  x = ((buf[2] & 0x0F) << 8) | buf[3];
  y = ((buf[4] & 0x0F) << 8) | buf[5];
  return true;
}

// ═══════════════════════════════════════════════════════════════════════════
// ── Music screen ───────────────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════════

// Layout (320x480):
//  y=  0– 59  header
//  y= 60–339  album art (280px tall, full width)
//  y=346–362  song title  (SIZE 2, centred)
//  y=368–376  artist      (SIZE 1, centred)
//  y=382–387  album name  (SIZE 1, dim, centred)
//  y=400–405  progress bar
//  y=409–417  time labels

#define MUSIC_ART_Y   60
#define MUSIC_ART_H  280
#define MUSIC_BAR_Y  400
#define MUSIC_BAR_H    5
#define MUSIC_BAR_X   12
#define MUSIC_BAR_W  (SCREEN_W - 24)

static void _musicDrawProgress(const SpotifyTrack &t) {
  _gfx->fillRoundRect(MUSIC_BAR_X, MUSIC_BAR_Y, MUSIC_BAR_W, MUSIC_BAR_H, 2, C_BORDER);
  if (t.duration_ms > 0) {
    int filled = (int)((float)t.progress_ms / t.duration_ms * MUSIC_BAR_W);
    if (filled > 0)
      _gfx->fillRoundRect(MUSIC_BAR_X, MUSIC_BAR_Y, filled, MUSIC_BAR_H, 2, C_ACCENT);
  }
  _gfx->fillRect(MUSIC_BAR_X, 409, MUSIC_BAR_W, 8, C_BG);
  _gfx->setTextColor(C_DIM3); _gfx->setTextSize(1);
  char tL[8], tT[8];
  snprintf(tL, sizeof(tL), "%d:%02d", t.progress_ms/60000, (t.progress_ms/1000)%60);
  snprintf(tT, sizeof(tT), "%d:%02d", t.duration_ms/60000, (t.duration_ms/1000)%60);
  _gfx->setCursor(MUSIC_BAR_X, 409); _gfx->print(tL);
  _gfx->setCursor(MUSIC_BAR_X + MUSIC_BAR_W - (int)strlen(tT)*6, 409); _gfx->print(tT);
}

inline void renderMusic(const SpotifyTrack &t) {
  _gfx->fillScreen(C_BG);

  // ── Header ─────────────────────────────────────────────────────────────
  _gfx->setTextColor(C_DIM3); _gfx->setTextSize(1);
  _gfx->setCursor(8, 10); _gfx->print("F L I G H T  R A D A R");
  _gfx->setTextColor(t.is_playing ? C_ACCENT : C_DIM2);
  _gfx->setCursor(8, 24); _gfx->print(t.is_playing ? "NOW PLAYING" : "PAUSED");
  _gfx->drawLine(0, HEADER_H - 1, SCREEN_W, HEADER_H - 1, C_BORDER);

  // ── Album art (full width, 280px tall) ─────────────────────────────────
  _gfx->fillRect(0, MUSIC_ART_Y, SCREEN_W, MUSIC_ART_H, 0x0841);
  if (t.art_url[0]) {
    _gfx->flush();
    _drawImageFromUrl(t.art_url, 0, MUSIC_ART_Y, SCREEN_W, MUSIC_ART_H);
  }

  // ── Song title (SIZE 2, centred) ────────────────────────────────────────
  _gfx->setTextColor(C_TEXT); _gfx->setTextSize(2);
  char title[27]; truncate(title, t.title, 26);
  int titleW = strlen(title) * 12;
  _gfx->setCursor(max(0, (SCREEN_W - titleW) / 2), 346);
  _gfx->print(title);

  // ── Artist (SIZE 1, centred) ────────────────────────────────────────────
  _gfx->setTextColor(C_DIM2); _gfx->setTextSize(1);
  char artist[50]; truncate(artist, t.artist, 49);
  int artistW = strlen(artist) * 6;
  _gfx->setCursor(max(0, (SCREEN_W - artistW) / 2), 368);
  _gfx->print(artist);

  // ── Album (SIZE 1, dim, centred) ───────────────────────────────────────
  _gfx->setTextColor(C_DIM3); _gfx->setTextSize(1);
  char album[50]; truncate(album, t.album, 49);
  int albumW = strlen(album) * 6;
  _gfx->setCursor(max(0, (SCREEN_W - albumW) / 2), 382);
  _gfx->print(album);

  // ── Progress bar + times ────────────────────────────────────────────────
  _musicDrawProgress(t);

  _gfx->flush();
}

// Lightweight 1-second update — only redraws progress bar, times, and
// the NOW PLAYING / PAUSED status. No art re-download.
inline void renderMusicProgress(const SpotifyTrack &t) {
  _gfx->fillRect(8, 24, 120, 8, C_BG);
  _gfx->setTextColor(t.is_playing ? C_ACCENT : C_DIM2); _gfx->setTextSize(1);
  _gfx->setCursor(8, 24); _gfx->print(t.is_playing ? "NOW PLAYING" : "PAUSED");
  _musicDrawProgress(t);
  _gfx->flush();
}
