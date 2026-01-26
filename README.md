# ✈️ Flight Radar – Kotlin Backend

A lightweight Kotlin backend that fetches live flight data from **OpenSky**, enriches it with cached arrival/departure information, and exposes clean APIs for displaying flight information on external displays (ESP32 / e‑ink / Android / web widgets).

This project is designed to be **simple, cheap, and reliable** — avoiding heavy UI stacks while keeping logic solid and testable.

---

## 🚀 What this project does

- Fetches **live overhead flights** using the OpenSky API
- Caches flight metadata (callsign → origin/destination)
- Uses **MongoDB** as a lightweight cache store
- Stores secrets securely using **AWS SSM Parameter Store**
- Runs a **nightly arrival batch job** to resolve missing destinations
- Exposes REST endpoints for consumption by:
  - ESP32 / microcontrollers
  - Raspberry Pi displays
  - Android / web dashboards

---

## 🧱 Architecture (High level)

```
┌─────────────┐      ┌─────────────┐
│  OpenSky    │ ---> │ Kotlin API  │
│   API       │      │ (Ktor)      │
└─────────────┘      ├─────────────┤
                     │ MongoDB     │
                     │ (Cache)     │
                     ├─────────────┤
                     │ Arrival Job │
                     └─────────────┘
                           │
                           ▼
                     External Displays
                  (ESP32 / Android / Pi)
```

---

## 📂 Project structure

```
src/main/java/org/ssm/flightradar
│
├── Application.kt          # Main Ktor application
├── ArrivalJobMain.kt       # Entry point for batch job
│
├── config/
│   └── AppConfig.kt        # App & environment configuration
│
├── datasource/
│   ├── AwsParameterStore.kt  # AWS SSM integration
│   ├── MongoProvider.kt      # MongoDB client
│   └── OpenSkyClient.kt      # OpenSky API client
│
├── model/
│   ├── FlightCacheDocument.kt
│   └── Models.kt
│
├── routes/
│   └── Routes.kt           # REST endpoints
│
├── service/
│   ├── FlightService.kt     # Core business logic
│   └── ArrivalBatchJob.kt   # Nightly arrival resolver
│
├── util/
│   └── Geo.kt              # Geo helpers (distance, bounding box)
```

---

## 🔌 API Endpoints (example)

> Exact routes may evolve — keep backend flexible for display clients.

```
GET /flights/nearby
```
Returns overhead flights enriched with cached data.

```json
[
  {
    "callsign": "LH123",
    "from": "FRA",
    "to": "DEL",
    "altitude": 10300,
    "lat": 51.4,
    "lon": 7.4
  }
]
```

---

## 🌙 Arrival Batch Job

Some OpenSky flights **don’t have arrival info in real time**.

The batch job:
- Runs on **previous day data only** (OpenSky limitation)
- Retries unresolved flights up to **2 days**
- Marks flights as `no_data` after final failure
- Updates MongoDB cache

This keeps live queries fast and cheap.

---

## 🔐 Configuration & Secrets

Secrets are **not hardcoded**.

Stored in **AWS SSM Parameter Store**:

- `OPENSKY_USERNAME`
- `OPENSKY_PASSWORD`
- `MONGODB_URI`

Loaded at runtime via `AwsParameterStore`.

---

## ▶️ Running the project

### Prerequisites

- Java 17+
- MongoDB (local or remote)
- AWS credentials (for SSM)

### Run API server

```bash
./gradlew run
```

### Run arrival batch job

```bash
./gradlew runArrivalJob
```

---

## 🧪 Why Kotlin + Ktor?

- Extremely **low memory footprint**
- Fast startup (great for EC2 free tier)
- Strong typing for long‑running background jobs
- Easy to consume from microcontrollers

---

## 🖥️ Display ideas (intended use)

This backend is intentionally UI‑agnostic.

Works well with:

- ESP32 / e‑ink displays (HTTP polling)
- Raspberry Pi (fullscreen browser / Python client)
- Old Android phones (single‑activity kiosk app)
- Desktop widgets

---

## 🧠 Design philosophy

> **"Do the hard thinking once, keep devices dumb."**

All complexity lives here:
- Caching
- Rate‑limit handling
- Arrival inference

Displays only render JSON.

---

## 📜 License

MIT License — build cool stuff.

---

## ✨ Future ideas

- WebSocket push for displays
- E‑ink optimized endpoint
- City‑based filtering
- Historical stats

---

