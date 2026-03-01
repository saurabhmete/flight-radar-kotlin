# Flight Radar --- Physics‑Aware Personal Airspace Display

A production‑structured **Kotlin / Ktor backend** that transforms live
ADS‑B state vectors from **OpenSky** into a calm, observer‑centric
airspace display.

Unlike generic flight trackers, this system answers a more human
question:

> **"Which aircraft can I actually see from my window right now?"**

This backend filters aircraft using geometric line‑of‑sight physics and
returns **only flights that are physically visible from a fixed observer
location**.

Designed for: - OLED dashboards - Old Android phone near a window -
Raspberry‑style wall displays - Clean backend architecture demos for
interviews

------------------------------------------------------------------------

# Core Concept

Most trackers show everything within a radius.

This system instead models:

-   Fixed observer position
-   Geometric horizon distance
-   Minimum altitude threshold
-   Maximum practical viewing radius

It returns **zero flights** when the sky is empty.

No clutter. No noise.

------------------------------------------------------------------------

# Physics‑Aware Visibility Model

An aircraft is considered visible only if:

distance_km \<= MAX_DISTANCE_KM\
altitude_m \>= MIN_ALTITUDE_METERS\
distance_km \<= 3.57 \* sqrt(altitude_m)

Where:

-   3.57 \* √h approximates horizon distance in km (h in meters)
-   MAX_DISTANCE_KM filters extreme long‑range objects
-   MIN_ALTITUDE_METERS removes low ground clutter

This balances realism with computational simplicity.

------------------------------------------------------------------------

# Architecture

OpenSky (ADS‑B State Vectors)\
↓\
FlightService\
(Distance + Horizon Filter)\
↓\
FlightEnrichmentService\
(Route + Operator + Aircraft + Airport Mapping)\
↓\
Mongo Cache\
↓\
Ktor REST API\
↓\
OLED / Browser UI

------------------------------------------------------------------------

# Backend Features

-   Live state vectors via OpenSky OAuth
-   Physics‑based visibility filtering
-   Best‑effort route enrichment (budget‑controlled)
-   Airport ICAO → IATA + name mapping (local JSON)
-   Aircraft image resolution (time‑boxed)
-   Positive + negative caching in MongoDB
-   Budget guardrails for paid APIs
-   Clean, testable service separation

------------------------------------------------------------------------

# Airport Mapping

Uses a local airports.json file (in src/main/resources) to map:

-   ICAO → IATA
-   ICAO → airport name

If IATA is missing: - UI automatically falls back to ICAO

No external API calls required for airport display.

------------------------------------------------------------------------

# API

## Dashboard

GET /

Serves a low‑glare OLED‑friendly UI.

Refresh interval: 15 seconds.

If no flights are visible, screen remains black.

------------------------------------------------------------------------

## Health Check

GET /health

Returns:

{ "status": "ok" }

------------------------------------------------------------------------

## Nearby Visible Flights

GET /api/flights/nearby?limit=3

Query Parameters:

Parameter         Description         Default
  ----------------- ------------------- ---------
limit             Number of flights   3
max_distance_km   Max filter radius   80

Returns only flights that pass the visibility model.

------------------------------------------------------------------------

# Configuration

Environment variables:

Variable                Description               Default
  ----------------------- ------------------------- ---------------------------
PORT                    HTTP port                 8080
MONGO_URI               MongoDB URI               mongodb://localhost:27017
MONGO_DB                DB name                   flight_radar
OPENSKY_CLIENT_ID       OpenSky OAuth client id   required
OPENSKY_CLIENT_SECRET   OpenSky OAuth secret      required
BBOX_DELTA_DEG          Bounding box half‑size    1.0

Observer location is intentionally hard‑coded in FlightService to
reflect a fixed installation.

------------------------------------------------------------------------

# Running Locally

Requirements:

-   Java 17+
-   MongoDB
-   OpenSky credentials

./gradlew run

Open:

http://localhost:8080

------------------------------------------------------------------------

# Deployment

Designed for:

-   Small EC2 instances
-   systemd service
-   Low memory environments
-   Always‑on personal dashboards

Typical memory footprint: \~200--300MB JVM

------------------------------------------------------------------------

# Design Philosophy

This project demonstrates:

-   Translating a physical real‑world problem into backend logic
-   Observer‑centric modeling instead of radius spam
-   Defensive API design under unreliable external data
-   Budget‑aware enrichment logic
-   Negative caching strategy
-   Clean service separation without over‑engineering

------------------------------------------------------------------------

# Future Improvements

-   Airline logo CDN integration
-   Country flags by airport
-   WebSocket streaming instead of polling
-   Adaptive refresh intervals
-   Multi‑observer configuration support

------------------------------------------------------------------------

# License

MIT
