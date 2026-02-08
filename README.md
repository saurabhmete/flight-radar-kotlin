# Flight Radar (Kotlin / Ktor)

A lightweight Kotlin backend that fetches live flight states from OpenSky, enriches them best-effort with cached context, and exposes:

- a small REST API for low-power clients (ESP32 displays, Raspberry Pi dashboards, Android widgets)
- a simple OLED-friendly web dashboard usable from an old Android phone browser

The project also includes a batch job that resolves missing arrival airports using OpenSky historical endpoints (limited to previous-day data).

## Features

- Live "nearby flights" endpoint via OpenSky state vectors
- MongoDB cache for departure/arrival enrichment
- AWS SSM Parameter Store support for secrets (optional)
- Batch job to resolve missing arrivals using yesterday's flight history
- Structured error responses and basic query validation

## High-level architecture

```
OpenSky (live states)  --->  Ktor API  --->  Client display
                    \         |
                     \        v
OpenSky (history)  --->  Arrival batch job  --->  MongoDB cache
```

## API

### OLED dashboard

```
GET /
```

Serves a dark, low-glare dashboard that refreshes every 15 seconds and shows up to 3 closest flights.

### Health

```
GET /health
```

### Nearby flights

```
GET /api/flights/nearby?limit=3&max_distance_km=80
```

Query params:

- `limit` (1..20, default 3)
- `max_distance_km` (1..500, default 80)

Example response:

```json
{
  "flights": [
    {
      "icao24": "3c4b45",
      "callsign": "DLH1234",
      "altitude": 10300.0,
      "lat": 51.52,
      "lon": 7.42,
      "velocity": 230.1,
      "distance_km": 12.7,
      "departure": "EDDF",
      "departure_name": "Frankfurt Main",
      "arrival": "EDDM",
      "arrival_name": "Munich",
      "aircraft_image_url": "/static/aircraft/plane.svg",
      "aircraft_image_type": "SILHOUETTE"
    }
  ]
}
```

Error response shape:

```json
{ "error": "bad_request", "details": "limit must be between 1 and 20" }
```

## Batch job

The arrival batch job resolves flights where `arrival` is missing in MongoDB.

Important: OpenSky arrival data is most reliable for completed flights, so the job queries only yesterday's flight history.

Run once:

```bash
./gradlew runArrivalJob
```

## Configuration

Environment variables (all can be provided via env; some can also be loaded from AWS SSM):

| Variable | Purpose | Default |
|---|---|---|
| PORT | HTTP port | 8080 |
| MONGO_URI | Mongo connection string | mongodb://localhost:27017 |
| MONGO_DB | Mongo database name | flight_radar |
| OPENSKY_CLIENT_ID | OpenSky OAuth client id | (required) |
| OPENSKY_CLIENT_SECRET | OpenSky OAuth client secret | (required) |
| CENTER_LAT | Center latitude for nearby search | 51.5136 |
| CENTER_LON | Center longitude for nearby search | 7.4653 |
| BBOX_DELTA_DEG | Bounding box half-size (degrees) | 1.0 |

## Run locally

Prereqs:

- Java 17+
- MongoDB

Start API:

```bash
./gradlew run
```

Call API:

```bash
curl "http://localhost:8080/api/flights/nearby?limit=3&max_distance_km=80"
```

## Project structure

```
src/main/java/org/ssm/flightradar
  Application.kt
  ArrivalJobMain.kt

  api/
    dto/
    mapper/

  config/
  datasource/
  domain/
  persistence/
  routes/
  service/
  util/
```

## Notes

- The OpenSky "states" endpoint returns callsigns padded with whitespace. The client trims callsigns before use.
- The batch job sleeps between requests to be polite to rate limits.

## License

MIT
