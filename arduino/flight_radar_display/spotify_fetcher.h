#pragma once
#include <Arduino.h>

struct SpotifyTrack {
  char     title[64];
  char     artist[64];
  char     album[64];
  char     art_url[192];
  uint32_t progress_ms;
  uint32_t duration_ms;
  bool     is_playing;
  bool     valid;
};

// Call once after WiFi is up; also called automatically when token expires.
bool spotifyRefreshToken();

// Fetch now-playing. Returns false on network error. out.valid=false means nothing playing.
bool fetchNowPlaying(SpotifyTrack &out);

void spotifyPlay();
void spotifyPause();
void spotifyNext();
void spotifyPrev();
