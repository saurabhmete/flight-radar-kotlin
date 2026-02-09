# Flight Radar — Physics‑Aware Personal Airspace Display

A **Kotlin / Ktor backend** that turns live ADS‑B data from **OpenSky** into a calm, night‑friendly airspace display.
Unlike generic flight trackers, this system is **observer‑centric**: it shows **only aircraft that are physically visible from a fixed location**, based on distance, altitude, and line‑of‑sight physics.

The primary use case is a **low‑glare OLED dashboard** (e.g. an old Android phone near a window), but the backend is cleanly structured and interview‑ready.

---

## Why this project exists

Most flight trackers show *everything* in a radius.
From a human perspective, that creates noise:

- aircraft too low to see
- aircraft technically nearby but below the horizon
- constant clutter, especially at night

This project answers a different question:

> **“Which aircraft can I actually see from my window right now?”**

The backend models this explicitly and returns **zero flights** when the sky is empty.

---

## Key ideas

### 1. Fixed observer model
The system is anchored to a **single observer location** (latitude / longitude).
All visibility calculations are relative to this point.

### 2. Physics‑based visibility
An aircraft is considered *visible* only if **all** of the following hold:

- distance ≤ configured maximum radius
- altitude ≥ minimum altitude
- distance ≤ geometric horizon distance

Horizon distance is approximated by:

```
horizon_km ≈ 3.57 × √(altitude_m)
```

This eliminates false positives while remaining computationally cheap.

### 3. Honest data handling
If OpenSky cannot reliably provide route information for a live flight:
- the backend returns `Unknown`
- the UI shows it transparently
- negative results are cached to avoid hammering external APIs

No guessing, no fake certainty.

---

## Features

- **Live nearby flights** from OpenSky state vectors
- **Physics‑aware visibility filtering**
- **Best‑effort enrichment** (routes, aircraft images)
- **MongoDB cache** for positive and negative enrichment results
- **OLED‑friendly web dashboard** (pure black when no flights are visible)
- **Systemd‑ready deployment** on small EC2 instances

---

## High‑level architecture

```
             ┌───────────────┐
             │   OpenSky     │
             │  Live States  │
             └───────┬───────┘
                     │
                     v
            ┌──────────────────┐
            │  FlightService   │
            │  (visibility +  │
            │   distance +    │
            │   horizon)      │
            └───────┬─────────┘
                    │ visible flights only
                    v
        ┌──────────────────────────┐
        │ FlightEnrichmentService  │
        │  - route (best effort)   │
        │  - aircraft image        │
        │  - time‑boxed calls      │
        └───────┬──────────────────┘
                │
                v
          ┌──────────────┐
          │  MongoDB     │
          │  Cache       │
          └──────────────┘
                │
                v
        ┌────────────────────┐
        │  Ktor REST API     │
        └─────────┬──────────┘
                  │
                  v
        ┌────────────────────┐
        │ OLED / Browser UI  │
        └────────────────────┘
```

---

## Visibility model (core logic)

The backend only returns flights that satisfy:

```
distance_km <= MAX_DISTANCE_KM
altitude_m  >= MIN_ALTITUDE_METERS
distance_km <= 3.57 * sqrt(altitude_m)
```

Typical configuration (example):

| Parameter | Value |
|---------|------|
| Max distance | 40 km |
| Min altitude | 500 m |

This keeps:
- high‑altitude jets clearly visible
- some lower aircraft when genuinely observable
- everything else filtered out

---

## API

### Dashboard
```
GET /
```
Serves a dark, low‑glare dashboard that refreshes every 15 seconds.

If **no visible aircraft** exist, the screen stays completely black.

### Health
```
GET /health
```

### Nearby visible flights
```
GET /api/flights/nearby?limit=3
```

Returns only **visible** flights (after all filtering).

---

## Aircraft images

- Uses a **local silhouette** by default
- Attempts best‑effort lookup (time‑boxed HTTP HEAD checks)
- Results are cached (including "not found")
- No images are stored locally or downloaded

---

## Configuration

Environment variables:

| Variable | Description | Default |
|-------|------------|--------|
| PORT | HTTP port | 8080 |
| MONGO_URI | MongoDB connection | mongodb://localhost:27017 |
| MONGO_DB | Database name | flight_radar |
| OPENSKY_CLIENT_ID | OpenSky OAuth client id | required |
| OPENSKY_CLIENT_SECRET | OpenSky OAuth secret | required |
| BBOX_DELTA_DEG | Bounding box half‑size | 1.0 |

Observer location is intentionally **hard‑coded** in `FlightService` to reflect a fixed physical installation.

---

## Running locally

Requirements:
- Java 17+
- MongoDB
- OpenSky API credentials

```bash
./gradlew run
```

Open in browser:

```
http://localhost:8080
```

---

## Deployment

Designed for:
- small EC2 instances
- systemd services
- memory‑constrained environments

---

## What this demonstrates (for interviews)

- translating a **real‑world physical problem** into backend logic
- clear separation of concerns
- defensive API design under unreliable external data
- cache design with negative caching
- pragmatic trade‑offs over over‑engineering

---

## License

MIT
