# Flight Radar — Physics-Aware Personal Airspace Display

A **Kotlin / Ktor** backend that transforms live ADS-B state vectors from **OpenSky** into a calm, observer-centric airspace display — deployed on a Raspberry Pi 4 as a always-on wall display.

> **"Which aircraft can I actually see from my window right now?"**

Unlike generic flight trackers that show everything within a radius, this system filters aircraft using geometric line-of-sight physics and enriches each flight with route, operator, aircraft type, and a real photo — returning only what's physically visible from your location.

---

## What It Looks Like

- **Phone/tablet**: primary flight card expanded, two compact cards below with tap-to-expand accordion
- **Desktop/TV**: three equal columns, each with a full aircraft photo, route, and stats
- **OLED-optimised**: true black background, amber accent colour, minimal chrome
- **ESP32 display**: standalone 3.5" touchscreen device showing live flights and weather with no browser needed

---

## ESP32 Hardware Display

The `arduino/` directory contains a standalone ESP32 sketch for the **Waveshare JC3248W535C** (3.5", 320×480, AXS15231B QSPI). It connects to the same RPi backend over WiFi and renders the same data on a physical display.

### What it shows
- **Flights overhead**: primary card (callsign, route, operator, aircraft photo, altitude/speed/distance) + two compact cards
- **No flights**: full-screen weather from open-meteo.com (temperature, feels like, condition icon, wind, humidity)
- Mini radar in the header with live flight dot positions

### Hardware
| Pin function | GPIO |
|---|---|
| LCD CS/CLK/D0–D3 | 45, 47, 21, 48, 40, 39 |
| Backlight | 1 |
| Touch SDA/SCL/INT/RST | 4, 8, 11, 12 |

> **Requirements:** OPI PSRAM must be enabled in Arduino IDE board settings. The AXS15231B display requires a full-screen canvas (frame buffer) in PSRAM — direct pixel writes to the bus don't work.

### Setup
1. Copy `arduino/flight_radar_display/config.h.example` → `config.h`
2. Fill in your WiFi credentials and RPi IP
3. Flash with Arduino IDE (ESP32 board package, Arduino_GFX_Library, ArduinoJson, JPEGDEC)

```cpp
#define WIFI_SSID      "YourNetwork"
#define WIFI_PASSWORD  "YourPassword"
#define API_HOST       "http://192.168.x.x:8000"
```

---

## Core Concept

An aircraft is considered visible only if all three conditions hold:

```
altitude_m      >= 500
distance_km     <= MAX_DISTANCE_KM          (configurable, default 20 km)
distance_km     <= 3.57 × √altitude_m       (geometric horizon)
```

`3.57 × √h` approximates the horizon distance in km (h in metres). At 10 000 m cruise altitude this gives ~89 km theoretical line-of-sight; capped by `MAX_DISTANCE_KM` for practical "overhead" viewing (20 km ≈ 28° elevation — clearly visible from a window).

Private and general aviation flights (non-ICAO-airline callsigns) are filtered out entirely.

---

## Architecture

```
OpenSky Network (ADS-B state vectors, OAuth 2.0)
        ↓
FlightService — physics filter, GA filter, observer distance
        ↓  (parallel coroutines per flight)
FlightEnrichmentService
  ├── FlightWall CDN  — operator name (free)
  ├── FlightAware AeroAPI  — route + aircraft type (budget-capped)
  ├── AirportLookupService — ICAO → IATA + name (local JSON)
  └── planespotters.net   — aircraft photo by ICAO24 (free)
        ↓
FerretDB (MongoDB-compatible) — positive + negative cache
        ↓
Ktor REST API  →  Responsive HTML/CSS/JS UI
```

---

## Features

**Backend**
- Live ADS-B via OpenSky OAuth 2.0
- Physics-based line-of-sight visibility filter
- Airline-only filter (excludes GA registrations used as callsigns)
- Parallel enrichment — all flights enriched concurrently
- Aircraft photos from planespotters.net (real photos by ICAO24 hex)
- Route + aircraft type from FlightAware AeroAPI with daily budget cap and negative caching
- Airport ICAO → IATA + name from local `airports.json` (no API call)
- All observer parameters configurable via environment variables
- FerretDB/MongoDB caching — positive and negative results cached

**Frontend**
- Responsive layout: single column (mobile) → 3-column grid (desktop/TV)
- Fluid typography via CSS `clamp()` — scales smoothly at every size
- Accordion expand/collapse on mobile (tap any card to expand it)
- Mini radar canvas in header showing live flight positions and headings
- Aircraft silhouette SVG rotates by `true_track` heading
- Aircraft photos displayed full-width without cropping (`object-fit: contain`)
- Callsign + ICAO24 shown side by side
- 15-second polling with `onerror` image fallback

---

## API

### `GET /`
Redirects to the dashboard UI.

### `GET /health`
```json
{ "status": "ok" }
```

### `GET /api/flights/nearby`
| Parameter | Description | Default |
|---|---|---|
| `limit` | Max flights to return | `3` |
| `max_distance_km` | Override distance cap | from config |

Returns flights passing the visibility model, fully enriched.

---

## Configuration

All values are read from environment variables (or AWS SSM Parameter Store if env is absent).

| Variable | Description | Default |
|---|---|---|
| `PORT` | HTTP port | `8080` |
| `MONGO_URI` | FerretDB / MongoDB URI | `mongodb://localhost:27017` |
| `MONGO_DB` | Database name | `flight_radar` |
| `OPENSKY_CLIENT_ID` | OpenSky OAuth client ID | required |
| `OPENSKY_CLIENT_SECRET` | OpenSky OAuth secret | required |
| `AEROAPI_KEY` | FlightAware AeroAPI key | required |
| `AEROAPI_BASE_URL` | AeroAPI base URL | `https://aeroapi.flightaware.com/aeroapi` |
| `FLIGHTWALL_CDN_BASE_URL` | FlightWall CDN base | `https://cdn.theflightwall.com` |
| `CENTER_LAT` | Observer latitude | `51.5136` |
| `CENTER_LON` | Observer longitude | `7.4653` |
| `BBOX_DELTA_DEG` | OpenSky bounding box half-size (°) | `1.0` |
| `MAX_DISTANCE_KM` | Max distance to show a flight | `20.0` |
| `AEROAPI_MAX_CALLS_PER_DAY` | Daily AeroAPI budget | `15` |
| `AEROAPI_NEGATIVE_CACHE_SECONDS` | Don't retry failed lookups for N seconds | `86400` |
| `AEROAPI_MAX_ATTEMPTS_PER_CALLSIGN` | Lifetime AeroAPI attempts per callsign | `2` |

**Setting your location:** drop a pin at your address in Google Maps, copy the coordinates into `CENTER_LAT` / `CENTER_LON`.

---

## Deployment (Raspberry Pi)

OpenSky Network blocks cloud provider IPs (AWS, GCP). A Raspberry Pi on a residential ISP connection works reliably.

```bash
# Build
./gradlew installDist

# Run
PORT=8000 ./build/install/flight-radar/bin/flight-radar
```

**systemd service** (`/etc/systemd/system/flight-radar.service`):
```ini
[Unit]
Description=Flight Radar Kotlin
After=network.target ferretdb.service
Wants=ferretdb.service

[Service]
User=mete
WorkingDirectory=/home/mete/flight-radar-kotlin
EnvironmentFile=/etc/flight-radar.env
ExecStart=/home/mete/flight-radar-kotlin/build/install/flight-radar/bin/flight-radar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

**Deploy a new branch:**
```bash
sudo systemctl stop flight-radar
git pull origin <branch>
./gradlew installDist
sudo systemctl start flight-radar
```

---

## Running Locally

Requirements: Java 17+, FerretDB or MongoDB, OpenSky + AeroAPI credentials.

```bash
./gradlew run
# Open http://localhost:8080
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.0 |
| HTTP server | Ktor 2.3 |
| HTTP client | Ktor CIO |
| Database | FerretDB (MongoDB-compatible) via KMongo |
| Serialisation | kotlinx.serialization |
| Build | Gradle 8 |
| Frontend | Vanilla HTML / CSS / JS (no framework) |
| ESP32 display | Arduino_GFX_Library, ArduinoJson, JPEGDEC |

---

## License

MIT
