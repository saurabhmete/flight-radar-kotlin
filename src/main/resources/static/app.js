// Observer position (Dortmund)
const OBSERVER_LAT = 51.505122562296975;
const OBSERVER_LON = 7.466314232256936;
const RADAR_MAX_KM = 40;

const statusEl  = document.getElementById('status');
const primaryEl = document.getElementById('primary');
const secondaryEl = document.getElementById('secondary');
const radarCanvas = document.getElementById('radar');

// ── Utilities ──────────────────────────────────────────────

function metersToFeet(m) {
  if (m == null) return null;
  return Math.round(m * 3.28084);
}

function fmtKm(x) {
  if (x == null) return '';
  return `${x.toFixed(1)} km`;
}

function iataOrIcao(iata, icao) {
  return (iata && iata.trim()) ? iata.trim() : (icao && icao.trim()) ? icao.trim() : null;
}

function calcBearing(lat1, lon1, lat2, lon2) {
  const φ1 = lat1 * Math.PI / 180;
  const φ2 = lat2 * Math.PI / 180;
  const Δλ = (lon2 - lon1) * Math.PI / 180;
  const y = Math.sin(Δλ) * Math.cos(φ2);
  const x = Math.cos(φ1) * Math.sin(φ2) - Math.sin(φ1) * Math.cos(φ2) * Math.cos(Δλ);
  return (Math.atan2(y, x) * 180 / Math.PI + 360) % 360;
}

// ── Radar canvas ───────────────────────────────────────────

function initRadar() {
  const size = Math.min(window.innerWidth - 32, 260);
  radarCanvas.width  = size;
  radarCanvas.height = size;
}

function drawRadar(flights) {
  const ctx  = radarCanvas.getContext('2d');
  const size = radarCanvas.width;
  const cx   = size / 2;
  const cy   = size / 2;
  const r    = cx - 18;

  ctx.clearRect(0, 0, size, size);

  // Clip everything inside the circle
  ctx.save();
  ctx.beginPath();
  ctx.arc(cx, cy, r, 0, Math.PI * 2);
  ctx.clip();

  ctx.fillStyle = '#080808';
  ctx.fillRect(0, 0, size, size);

  // Radial lines at 45° intervals
  ctx.strokeStyle = 'rgba(255,255,255,0.04)';
  ctx.lineWidth = 0.5;
  for (let deg = 0; deg < 360; deg += 45) {
    const rad = deg * Math.PI / 180;
    ctx.beginPath();
    ctx.moveTo(cx, cy);
    ctx.lineTo(cx + r * Math.sin(rad), cy - r * Math.cos(rad));
    ctx.stroke();
  }

  // Range rings
  [10, 20, 30, 40].forEach(km => {
    const ringR = (km / RADAR_MAX_KM) * r;
    ctx.beginPath();
    ctx.arc(cx, cy, ringR, 0, Math.PI * 2);
    ctx.strokeStyle = 'rgba(255,255,255,0.07)';
    ctx.setLineDash([3, 5]);
    ctx.lineWidth = 0.7;
    ctx.stroke();
    ctx.setLineDash([]);

    // Distance label at 3 o'clock
    ctx.fillStyle = 'rgba(255,255,255,0.18)';
    ctx.font = `${Math.round(size * 0.035)}px monospace`;
    ctx.textAlign = 'left';
    ctx.textBaseline = 'bottom';
    ctx.fillText(`${km}`, cx + ringR + 2, cy - 2);
  });

  ctx.restore();

  // Outer border circle
  ctx.beginPath();
  ctx.arc(cx, cy, r, 0, Math.PI * 2);
  ctx.strokeStyle = 'rgba(255,255,255,0.1)';
  ctx.lineWidth = 1;
  ctx.stroke();

  // Compass labels just outside the circle
  const labelOffset = r + 11;
  ctx.fillStyle = 'rgba(255,255,255,0.22)';
  ctx.font = `${Math.round(size * 0.04)}px system-ui`;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  [['N', 0], ['E', 90], ['S', 180], ['W', 270]].forEach(([lbl, deg]) => {
    const rad = deg * Math.PI / 180;
    ctx.fillText(lbl, cx + labelOffset * Math.sin(rad), cy - labelOffset * Math.cos(rad));
  });

  // Observer dot
  ctx.beginPath();
  ctx.arc(cx, cy, 3, 0, Math.PI * 2);
  ctx.fillStyle = '#e0b86b';
  ctx.fill();

  // Flight icons
  flights.forEach((f, idx) => {
    const bearing = calcBearing(OBSERVER_LAT, OBSERVER_LON, f.lat, f.lon);
    const flightR = Math.min((f.distance_km / RADAR_MAX_KM) * r, r - 14);
    const bRad = bearing * Math.PI / 180;
    const fx = cx + flightR * Math.sin(bRad);
    const fy = cy - flightR * Math.cos(bRad);

    const track = f.true_track != null ? f.true_track : bearing;
    drawRadarPlane(ctx, fx, fy, track, size * 0.042, idx === 0);

    // Callsign label — anchor based on quadrant to avoid overlap with icon
    const label = (f.callsign || f.icao24 || '').trim();
    ctx.fillStyle = idx === 0 ? 'rgba(224,184,107,0.95)' : 'rgba(180,180,180,0.7)';
    ctx.font = `${Math.round(size * 0.033)}px monospace`;
    ctx.textBaseline = 'middle';
    const offset = size * 0.045;
    const rightHalf = fx >= cx;
    ctx.textAlign = rightHalf ? 'left' : 'right';
    ctx.fillText(label, fx + (rightHalf ? offset : -offset), fy);
  });
}

function drawRadarPlane(ctx, x, y, trackDeg, size, isPrimary) {
  ctx.save();
  ctx.translate(x, y);
  ctx.rotate(trackDeg * Math.PI / 180);

  ctx.fillStyle = isPrimary ? '#e0b86b' : 'rgba(180,180,180,0.85)';

  // Fuselage
  ctx.beginPath();
  ctx.moveTo(0, -size);
  ctx.lineTo(size * 0.18, size * 0.2);
  ctx.lineTo(0, 0);
  ctx.lineTo(-size * 0.18, size * 0.2);
  ctx.closePath();
  ctx.fill();

  // Wings
  ctx.beginPath();
  ctx.moveTo(0, -size * 0.05);
  ctx.lineTo(-size * 0.85, size * 0.35);
  ctx.lineTo(-size * 0.12, size * 0.2);
  ctx.lineTo(size * 0.12, size * 0.2);
  ctx.lineTo(size * 0.85, size * 0.35);
  ctx.closePath();
  ctx.fill();

  // Tail
  ctx.beginPath();
  ctx.moveTo(0, size * 0.3);
  ctx.lineTo(-size * 0.38, size * 0.85);
  ctx.lineTo(size * 0.38, size * 0.85);
  ctx.closePath();
  ctx.fill();

  ctx.restore();
}

// ── Primary flight card ────────────────────────────────────

function renderPrimary(f) {
  const imgUrl   = f.aircraft_image_url || '/static/aircraft/plane.svg';
  const imgClass = f.aircraft_image_type === 'EXACT' ? 'photo' : 'silhouette';

  // Rotate silhouette by heading; photos stay as-is
  const rotation = (imgClass === 'silhouette' && f.true_track != null)
    ? `transform: rotate(${Math.round(f.true_track)}deg);`
    : '';

  const callsign = (f.callsign || f.icao24 || 'UNKNOWN').trim();
  const operator = (f.operator_name || f.operator_icao || '').trim();
  const aircraft = (f.aircraft_name_short || f.aircraft_name_full || f.aircraft_type_icao || '').trim();
  const opLine   = [operator, aircraft].filter(Boolean).join(' • ');

  const depCode = iataOrIcao(f.departure_iata, f.departure);
  const arrCode = iataOrIcao(f.arrival_iata, f.arrival);
  const routeCodes = (depCode && arrCode) ? `${depCode} → ${arrCode}` : (depCode || arrCode || '');

  const depName = f.departure_name ? f.departure_name.trim() : null;
  const arrName = f.arrival_name   ? f.arrival_name.trim()   : null;
  const routeNames = (depName || arrName)
    ? `${depName || depCode || '?'} → ${arrName || arrCode || '?'}`
    : null;

  const altFt   = metersToFeet(f.altitude);
  const speedKh = f.velocity != null ? Math.round(f.velocity * 3.6) : null;

  const pills = [];
  if (altFt   != null) pills.push(`${altFt.toLocaleString()} ft`);
  if (speedKh != null) pills.push(`${speedKh} km/h`);
  if (f.distance_km != null) pills.push(fmtKm(f.distance_km));

  primaryEl.innerHTML = `
    <div class="primary-grid">
      <div class="aircraft-img-wrap">
        <img class="${imgClass}" src="${imgUrl}" alt="aircraft" loading="lazy" style="${rotation}" />
      </div>
      <div class="primary-details">
        <div class="callsign">${callsign}</div>
        ${opLine   ? `<div class="op-aircraft">${opLine}</div>` : ''}
        ${routeCodes ? `
          <div class="route-block">
            <div class="route-codes">${routeCodes}</div>
            ${routeNames ? `<div class="route-names">${routeNames}</div>` : ''}
          </div>` : ''}
        ${pills.length ? `
          <div class="meta-pills">
            ${pills.map(p => `<span class="pill">${p}</span>`).join('')}
          </div>` : ''}
      </div>
    </div>
  `;

  // onerror fallback after innerHTML is set
  const img = primaryEl.querySelector('img');
  if (img) {
    img.onerror = function () {
      this.onerror = null;
      this.src = '/static/aircraft/plane.svg';
      this.className = 'silhouette';
      if (f.true_track != null) this.style.transform = `rotate(${Math.round(f.true_track)}deg)`;
    };
  }
}

// ── Secondary flight cards ─────────────────────────────────

function renderSecondary(flights) {
  if (!flights || flights.length === 0) {
    secondaryEl.innerHTML = '';
    return;
  }

  const cards = flights.map(f => {
    const callsign = (f.callsign || f.icao24 || 'UNKNOWN').trim();
    const depCode  = iataOrIcao(f.departure_iata, f.departure);
    const arrCode  = iataOrIcao(f.arrival_iata, f.arrival);
    const route    = (depCode && arrCode) ? `${depCode} → ${arrCode}` : (depCode || arrCode || '');

    const operator = (f.operator_name || f.operator_icao || '').trim();
    const aircraft = (f.aircraft_name_short || f.aircraft_name_full || f.aircraft_type_icao || '').trim();
    const meta     = [operator, aircraft].filter(Boolean).join(' • ');

    const altFt = metersToFeet(f.altitude);
    const metaParts = [];
    if (altFt != null) metaParts.push(`${altFt.toLocaleString()} ft`);
    if (meta)          metaParts.push(meta);

    return `
      <div class="sec-card">
        <div class="sec-cs">${callsign}</div>
        <div class="sec-right">
          ${route ? `<div class="sec-route">${route}</div>` : ''}
          ${metaParts.length ? `<div class="sec-meta">${metaParts.join(' · ')}</div>` : ''}
        </div>
        <div class="sec-dist">${fmtKm(f.distance_km)}</div>
      </div>
    `;
  }).join('');

  secondaryEl.innerHTML = cards;
}

// ── Data loading ───────────────────────────────────────────

let firstLoad = true;

async function load() {
  if (!firstLoad) {
    primaryEl.classList.add('fading');
  }

  try {
    statusEl.textContent = 'fetching…';

    const res = await fetch('/api/flights/nearby?limit=5&max_distance_km=120');
    if (!res.ok) throw new Error(`HTTP ${res.status}`);

    const data    = await res.json();
    const flights = data.flights || [];

    primaryEl.classList.remove('fading');

    if (flights.length === 0) {
      primaryEl.innerHTML = `<div class="empty">No flights nearby</div>`;
      secondaryEl.innerHTML = '';
      drawRadar([]);
    } else {
      renderPrimary(flights[0]);
      renderSecondary(flights.slice(1));
      drawRadar(flights);
    }

    statusEl.textContent = `updated ${new Date().toLocaleTimeString()}`;
    firstLoad = false;
  } catch (e) {
    primaryEl.classList.remove('fading');
    statusEl.textContent = `error: ${e.message}`;
  }
}

// ── Init ───────────────────────────────────────────────────

initRadar();
drawRadar([]);
load();
setInterval(load, 15000);
