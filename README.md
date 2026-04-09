# Flight Radar - Physics-Aware Personal Airspace Display

A Kotlin/Ktor backend that turns live ADS-B state vectors from OpenSky into a calm, observer-centric airspace display.
Instead of showing everything in a radius, it answers:

> "Which aircraft can I actually see from my window right now?"

It returns only flights that pass a simple visibility model based on distance and altitude.

Designed for:
- OLED dashboards
- Old Android phones near a window
- Raspberry Pi wall displays
- Clean backend architecture demos

## Core Concept

Most trackers show everything within a radius. This system models:
- Fixed observer position
- Geometric horizon distance
- Minimum altitude threshold
- Maximum practical viewing radius

It returns zero flights when the sky is empty.

## Visibility Model

An aircraft is considered visible only if:

```
distance_km <= MAX_DISTANCE_KM
altitude_m >= MIN_ALTITUDE_METERS
distance_km <= 3.57 * sqrt(altitude_m)
```

Where:
- `3.57 * sqrt(h)` approximates horizon distance in km (h in meters)
- `MAX_DISTANCE_KM` filters extreme long-range objects
- `MIN_ALTITUDE_METERS` removes low ground clutter

## Architecture

```
OpenSky (ADS-B State Vectors)
  -> FlightService (distance + horizon filter)
    -> FlightEnrichmentService (route + operator + aircraft + airports)
      -> Mongo Cache
        -> Ktor REST API
          -> OLED / Browser UI
```

## Backend Features

- Live state vectors via OpenSky OAuth
- Physics-based visibility filtering
- Best-effort route enrichment (budget-controlled)
- Airport ICAO -> IATA + name mapping (local JSON)
- Aircraft image resolution (time-boxed)
- Positive + negative caching in MongoDB
- Budget guardrails for paid APIs

## API

### Dashboard

`GET /`

Serves a low-glare OLED-friendly UI. Refresh interval: 15 seconds.

### Health Check

`GET /health`

Response:

```
{ "status": "ok" }
```

### Nearby Visible Flights

`GET /api/flights/nearby?limit=3&max_distance_km=80`

Query parameters:

| Parameter        | Description              | Default |
|-----------------|--------------------------|---------|
| `limit`         | Number of flights        | `3`     |
| `max_distance_km` | Max filter radius (km) | `80`    |

Response:

```
{
  "flights": [
    {
      "icao24": "3c6444",
      "callsign": "DLH7AB",
      "altitude": 10600.0,
      "lat": 51.52,
      "lon": 7.47,
      "velocity": 230.0,
      "distanceKm": 12.3,
      "departure": "EDDF",
      "arrival": "EDDH",
      "operatorName": "Lufthansa",
      "aircraftNameShort": "A320",
      "aircraftImageUrl": "https://...",
      "aircraftImageType": "EXACT"
    }
  ]
}
```

## Configuration

Environment variables:

| Variable                          | Description                              | Default |
|-----------------------------------|------------------------------------------|---------|
| `PORT`                            | HTTP port                                | `8080`  |
| `MONGO_URI`                       | MongoDB URI                              | `mongodb://localhost:27017` |
| `MONGO_DB`                        | DB name                                  | `flight_radar` |
| `OPENSKY_CLIENT_ID`               | OpenSky OAuth client id                  | required |
| `OPENSKY_CLIENT_SECRET`           | OpenSky OAuth secret                     | required |
| `AEROAPI_KEY`                     | FlightAware AeroAPI key                  | required for paid enrichment |
| `AEROAPI_BASE_URL`                | FlightAware base URL                     | `https://aeroapi.flightaware.com/aeroapi` |
| `FLIGHTWALL_CDN_BASE_URL`         | Public CDN for names                     | `https://cdn.theflightwall.com` |
| `AEROAPI_MAX_CALLS_PER_DAY`       | Daily paid-call budget                   | `30` |
| `AEROAPI_NEGATIVE_CACHE_SECONDS`  | Retry delay after a miss (seconds)       | `21600` |
| `AEROAPI_MAX_ATTEMPTS_PER_CALLSIGN` | Max attempts per callsign              | `1` |
| `CENTER_LAT`                      | Bounding box center latitude             | `51.5136` |
| `CENTER_LON`                      | Bounding box center longitude            | `7.4653` |
| `BBOX_DELTA_DEG`                  | Bounding box half-size in degrees        | `1.0` |

Notes:
- The distance calculation uses the hard-coded `HOME_LAT/HOME_LON` in `src/main/java/org/ssm/flightradar/service/FlightService.kt`.
- `CENTER_LAT/CENTER_LON` only affects the OpenSky bounding box filter.

## Running Locally

Requirements:
- Java 17+
- MongoDB
- OpenSky credentials

Run:

```
./gradlew run
```

Open:

`http://localhost:8080`

## Deployment

Designed for:
- Small EC2 instances
- systemd services
- Low-memory environments
- Always-on personal dashboards

Typical memory footprint: ~200-300MB JVM

## Future Improvements

- Airline logo CDN integration
- Country flags by airport
- WebSocket streaming instead of polling
- Adaptive refresh intervals
- Multi-observer configuration support

## License

MIT
