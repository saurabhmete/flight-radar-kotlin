# ESP32 Display – Full Session Log

This document records everything done to get the JC3248W535C display working with the flight-radar-kotlin backend. Read this before starting any new session on this feature.

---

## Hardware

| Property | Value |
|---|---|
| Module | JC3248W535C_I_Y (SKU 10120004, bought on AliExpress) |
| MCU | ESP32-S3-WROOM-1 |
| Display | 3.5" IPS, **320×480 portrait**, AXS15231B driver |
| Display interface | **QSPI (Quad SPI) at 40 MHz** |
| Touch | AXS15231B I²C (same chip), addr 0x3B |
| Flash | **4 MB** (NOT 16 MB — this caused the first crash loop) |
| PSRAM | 8 MB OPI (required for frame buffer) |

### Pin map

| Function | GPIO |
|---|---|
| LCD CS | 45 |
| LCD CLK | 47 |
| LCD D0 | 21 |
| LCD D1 | 48 |
| LCD D2 | 40 |
| LCD D3 | 39 |
| Backlight | 1 |
| Touch SDA | 4 |
| Touch SCL | 8 |
| Touch INT | 11 |
| Touch RST | 12 |

---

## Arduino IDE Board Settings (CRITICAL — all must be exact)

| Setting | Value |
|---|---|
| Board | `ESP32S3 Dev Module` |
| PSRAM | `OPI PSRAM` ← **must be enabled or canvas crashes** |
| Flash Size | `4MB` ← **NOT 16MB** |
| Partition Scheme | `Huge APP (3MB No OTA/1MB SPIFFS)` |
| USB CDC On Boot | `Enabled` ← **required for Serial Monitor** |
| Upload Speed | `921600` |

---

## Required Libraries (install via Library Manager)

| Library | Author | Min version |
|---|---|---|
| `Arduino_GFX` | moononournation | 1.5.0 |
| `ArduinoJson` | Benoit Blanchon | 7.x |
| `JPEGDEC` | Larry Bank | any |

---

## Project File Structure

```
arduino/
├── SESSION_LOG.md                    ← this file
├── README.md                         ← quick-start guide
├── display_test/
│   └── display_test.ino              ← minimal test sketch (no WiFi, colours only)
└── flight_radar_display/
    ├── flight_radar_display.ino      ← main sketch entry point
    ├── config.h                      ← WiFi creds, API host, pin defs, observer location
    ├── display.h                     ← all rendering: init, splash, cards, JPEG loader, radar
    ├── wifi_client.h                 ← WiFi connect with auto-reboot on timeout
    ├── flight_fetcher.h              ← Flight struct definition
    └── flight_fetcher.cpp            ← HTTP fetch + ArduinoJson parsing
```

---

## Backend API Used

```
GET http://192.168.0.95:8000/api/flights/nearby?limit=3
```

Returns `{ "flights": [ ... ] }` — each flight has:

| JSON field | Flight struct field | Notes |
|---|---|---|
| `callsign` | `callsign[12]` | falls back to `icao24` if empty |
| `icao24` | `icao24[8]` | hex transponder code, shown dim next to callsign |
| `departure_iata` | `origin[6]` | |
| `arrival_iata` | `destination[6]` | |
| `departure_name` | `origin_name[28]` | full airport name |
| `arrival_name` | `dest_name[28]` | full airport name |
| `operator_name` | `operator_name[28]` | |
| `aircraft_name_short` / `aircraft_type_icao` | `aircraft[20]` | short name preferred |
| `aircraft_image_url` | `image_url[192]` | JPEG from Planespotters CDN |
| `aircraft_image_type` | `image_exact` (bool) | true when `== "EXACT"` |
| `altitude` (metres) | `altitude` (feet) | converted: × 3.28084 |
| `velocity` (m/s) | `speed` (km/h) | converted: × 3.6 |
| `true_track` | `heading` | degrees |
| `lat`, `lon` | `lat`, `lon` | |
| `distance_km` | `distance_km` | |

---

## Problems Encountered & Solutions (read these carefully)

### 1. Crash loop on first boot — wrong partition scheme
**Symptom:** Serial monitor scrolled non-stop with:
```
E flash_parts: partition 3 invalid - offset 0x310000 size 0x300000 exceeds flash chip size 0x400000
E boot: Failed to verify partition table
```
**Cause:** Board settings had `16M Flash` partition scheme but the chip is only 4 MB.  
**Fix:** Tools → Flash Size → `4MB`, Partition Scheme → `Huge APP (3MB No OTA/1MB SPIFFS)`.

---

### 2. Serial Monitor shows nothing after upload
**Cause:** ESP32-S3 USB CDC port disappears and re-enumerates after reset. Serial Monitor loses the port.  
**Fix:**
1. After "Hard resetting via RTS pin..." appears, **close** Serial Monitor.
2. Wait 3 seconds.
3. Tools → Port → re-select the port.
4. Reopen Serial Monitor.
5. Press **RESET** on the board.

The sketch also waits up to 3 s for USB CDC to enumerate (`while (!Serial && millis()-t0<3000)`).

---

### 3. Upload fails — board not responding
**Cause:** After firmware was deleted, the board won't auto-enter bootloader.  
**Fix (must do every upload):**
1. Hold **BOOT** button on the board.
2. Tap **RESET** button.
3. Release **BOOT**.
4. Then click Upload in Arduino IDE.

---

### 4. Display all white — `ips = true/false` made no difference
**Cause:** The AXS15231B QSPI display **requires** `Arduino_Canvas` (a frame buffer in PSRAM). Direct pixel writes to the display bus are silently ignored — the display stays white (its default uninitialized state).  
**Fix:** Wrap the display driver in `Arduino_Canvas` and call `flush()` after every render operation.

```cpp
// WRONG — white screen:
_gfx = new Arduino_AXS15231B(_bus, ...);
_gfx->fillScreen(BLACK);  // never appears

// CORRECT:
_display = new Arduino_AXS15231B(_bus, ...);
_gfx     = new Arduino_Canvas(320, 480, _display, 0, 0, 0);
_gfx->begin(40000000UL);
_gfx->fillScreen(BLACK);
_gfx->flush();  // ← this is what actually sends to the display
```

The frame buffer is 320×480×2 = ~300 KB. This **requires OPI PSRAM** to be enabled in board settings — it won't fit in internal SRAM.

---

### 5. ICAO hex code shown instead of callsign
**Cause:** OpenSky sometimes returns an empty callsign. The fetcher had no fallback, so it stored `""` and the display showed `------`.  
**Fix:** Mirror the web app's fallback logic: if `callsign` is empty, use `icao24` instead.
```cpp
const char *cs = f["callsign"] | "";
if (!cs || !cs[0]) cs = fl.icao24;
safeCopy(fl.callsign, sizeof(fl.callsign), cs[0] ? cs : "------");
```
The `icao24` field is also stored separately in the struct so it can be displayed alongside the callsign (small dim text, matching the web's `.icao24` element).

---

### 6. Green horizontal lines across the aircraft JPEG
**Cause:** JPEGDEC's callback delivers pixel blocks where the buffer stride is always `d->iWidth`. When we clipped the width to fit the image box and passed a smaller `w` to `draw16bitRGBBitmap(x, y, buf, w, h)` with `h > 1`, the function advanced the buffer pointer by `w` per row instead of `d->iWidth` — reading from the wrong offset and producing green (0x07E0) artefact lines.  
**Fix:** Draw row-by-row (`h=1` per call) so stride is never an issue. See "JPEGDEC stride bug" section below.

---

### 7. Garbled characters (looked like Cyrillic "тт") for `·` and `°`
**Cause:** Arduino's built-in bitmap font only covers ASCII 0–127. The middle-dot `·` (U+00B7) and degree sign `°` (U+00B0) are encoded in UTF-8 as 2 bytes each (0xC2 + 0xB7 / 0xC2 + 0xB0). Each byte is rendered as a separate garbage glyph.  
**Fix:** Replace all non-ASCII characters with plain ASCII alternatives:
- `·` → `|`
- `°` → `deg`

**Rule:** Never use any character outside 0x20–0x7E in string literals passed to `_gfx->print()`.

---

## Display Architecture

```
Arduino_Canvas (320×480, ~300KB in PSRAM)
    ↓  flush()
Arduino_AXS15231B (QSPI display driver)
    ↓  40 MHz QSPI
JC3248W535C display panel
```

Every drawing call (`fillScreen`, `drawLine`, `print`, etc.) writes into the PSRAM canvas.  
`flush()` transfers the entire canvas to the display over QSPI in one burst.  
**Never forget `flush()` after a render pass — otherwise nothing appears.**

---

## Screen Layout (320×480 portrait)

```
y=0  ┌─────────────────────────────────────────┐
     │ F L I G H T  R A D A R    [mini radar]  │  Header 60px
     │ 2 overhead | upd 45s                    │
y=60 ├─────────────────────────────────────────┤
     │ ┌───────────────────────────────────┐   │
     │ │  [aircraft JPEG photo 108px tall] │   │  Primary card 214px
     │ │                                   │   │  (y=64)
     │ ├───────────────────────────────────┤   │
     │ │ EJU35XM  3c4b26    (aircraft type)│   │  callsign + icao24
     │ │ easyJet                           │   │
     │ │ AMS ──────────────────────→ JFK   │   │
     │ │ Amsterdam              New York   │   │
     │ │ 35000 ft | 829 km/h | 15.2 km    │   │
     │ │ hdg 123deg | FL350                │   │
     │ └───────────────────────────────────┘   │
y=282├─────────────────────────────────────────┤
     │ DLH456  FRA > LHR           22.3km      │  Compact card 1 96px
     │ Lufthansa | A320             FL310       │
y=382├─────────────────────────────────────────┤
     │ BAW789  LHR > JFK           38.1km      │  Compact card 2 96px
     │ British Airways | B789       FL380       │
y=478└─────────────────────────────────────────┘
```

### Mini radar (top-right of header, cx=280 cy=34 r=22)
- Concentric rings at 10/20/30/40 km
- Amber centre dot = observer position (lat 51.505, lon 7.466)
- Amber dot = primary flight, grey dots = secondary flights
- Position calculated from bearing + distance_km

---

## Aircraft Image Loading

When the API returns `aircraft_image_url` (a Planespotters CDN JPEG):

1. Dark placeholder (`#080808`) fills the image slot immediately
2. `flush()` is called so the user sees the placeholder while the download happens
3. JPEG is downloaded into PSRAM via `ps_malloc()` (up to 300 KB)
4. Decoded with JPEGDEC library, scaled by power-of-2 to fit 310×107px
5. Drawn centred horizontally in the image slot, **row-by-row** (see bug #6 below)
6. If download fails: aircraft type string shown as fallback text

If `image_url` is empty (no enrichment data), the image slot is skipped entirely and the callsign starts from the top of the card.

### JPEGDEC stride bug — why we draw row-by-row (important)

`draw16bitRGBBitmap(x, y, buf, w, h)` assumes the pixel buffer has stride = `w`. But JPEGDEC's callback delivers blocks where the buffer stride is always `d->iWidth` (the full decoded width), even when we clip `w` to a smaller value. Passing a clipped `w` with the original buffer causes rows 2+ to read from the wrong offset, producing horizontal green lines across the image.

**Fix:** In `_jpegCb`, loop over each row individually and call `draw16bitRGBBitmap` with `h=1`. With a single-row call, stride never matters.

```cpp
for (int row = 0; row < d->iHeight; row++) {
    uint16_t *rowBuf = d->pPixels + (row * d->iWidth);  // correct stride
    _gfx->draw16bitRGBBitmap(x, y + row, rowBuf + ox, w, 1);
}
```

---

## config.h Settings to Change

When running on a different network or location, edit `config.h`:

```cpp
#define WIFI_SSID       "HeraPheriNet"       // ← your WiFi name
#define WIFI_PASSWORD   "Baburao123"         // ← your WiFi password
#define API_HOST        "http://192.168.0.95:8000"  // ← RPi local IP

// Observer coords for the mini radar (must match app.js)
#define OBSERVER_LAT   51.505f
#define OBSERVER_LON    7.466f
```

The RPi runs on Tailscale IP `100.91.250.109`, local network IP `192.168.0.95`, port `8000`. The ESP32 must be on the same local network — Tailscale IP won't work from an ESP32 that isn't enrolled in Tailscale.

---

## Known Remaining Issues / TODO

1. **Image cache** — the same aircraft JPEG is re-downloaded every 15 s even if the same flight is still overhead. Could cache the last URL + decoded pixels in PSRAM to skip re-fetch.

2. **HTTPS images** — Planespotters CDN serves images over HTTPS. The current code uses plain `HTTPClient` without SSL, which may fail if the CDN enforces HTTPS redirects. Fix: use `WiFiClientSecure` with `setInsecure()` for the image fetch only.

3. **Touch not implemented** — the AXS15231B touch controller (I²C addr 0x3B, SDA=4, SCL=8) is wired but not used. Could add tap-to-expand compact cards, matching the web's accordion behaviour.

4. **No OTA** — firmware updates require physical USB connection + BOOT/RESET dance. Could add ArduinoOTA for wireless updates.

5. **Observer location hardcoded** — `OBSERVER_LAT/LON` in `config.h` are hardcoded. Could be read from the backend `/health` or a new `/config` endpoint.

---

## Git Branch

All Arduino code lives on branch: `feature/esp32-display`

```bash
git checkout feature/esp32-display
```

The Kotlin backend (main branch, `main`) was not modified for this feature.
