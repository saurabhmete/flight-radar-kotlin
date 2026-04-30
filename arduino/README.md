# Arduino – JC3248W535C Flight Display

ESP32-S3 3.5″ QSPI display that fetches nearby flights from the Kotlin backend and shows them as a live table.

## Hardware

| Spec | Value |
|------|-------|
| Module | JC3248W535C_I_Y (ESP32-S3-WROOM-1) |
| Display | 3.5″ IPS, 320 × 480, AXS15231B driver via QSPI |
| Touch | AXS15231B I²C (addr 0x3B) |
| Flash | 16 MB | PSRAM | 8 MB OPI |

### Pinout used

| Function | GPIO |
|----------|------|
| LCD CS | 45 |
| LCD CLK | 47 |
| LCD D0–D3 | 21, 48, 40, 39 |
| Backlight | 1 |
| Touch SDA | 4 |
| Touch SCL | 8 |
| Touch INT | 11 |
| Touch RST | 12 |

## Arduino IDE setup

1. **Add ESP32 board package**
   Preferences → Additional boards URL:
   ```
   https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json
   ```
   Boards Manager → install **esp32 by Espressif Systems** (≥ 3.x).

2. **Board settings** (Tools menu)
   | Setting | Value |
   |---------|-------|
   | Board | ESP32S3 Dev Module |
   | PSRAM | OPI PSRAM |
   | Flash Size | 16MB |
   | Partition Scheme | 16M Flash (3MB APP/9.9MB FATFS) |
   | USB CDC On Boot | Enabled |
   | Upload Speed | 921600 |

3. **Install libraries** (Library Manager)
   - `Arduino_GFX` by moononournation ≥ 1.5.0
   - `ArduinoJson` by Benoit Blanchon ≥ 7.x

## Flashing

### Normal upload
1. Open `flight_radar_display/flight_radar_display.ino` in Arduino IDE.
2. Edit `config.h` — set `WIFI_SSID`, `WIFI_PASSWORD`, and `API_HOST` (your RPi IP or Tailscale hostname).
3. Connect the display via USB-C.
4. Click **Upload**.

### Stuck / blank screen — enter bootloader manually
1. Hold **BOOT** button.
2. Press and release **RESET**.
3. Release **BOOT**.
4. Upload from Arduino IDE or via esptool:
   ```bash
   pip install esptool
   esptool.py --chip esp32s3 erase_flash   # only if you want a clean slate
   ```

## Project structure

```
arduino/
└── flight_radar_display/
    ├── flight_radar_display.ino   # main sketch
    ├── config.h                   # WiFi, API, pin definitions
    ├── display.h                  # GFX init + rendering
    ├── wifi_client.h              # WiFi connect helper
    ├── flight_fetcher.h           # Flight struct
    └── flight_fetcher.cpp         # HTTP + JSON parsing
```

## API used

```
GET /api/flights/nearby?limit=8
```
Returns `{ "flights": [ { callsign, altitude (m), velocity (m/s), ... } ] }`.
The sketch converts altitude → feet and velocity → knots automatically.
