const OBSERVER_LAT = 51.505122562296975;
const OBSERVER_LON = 7.466314232256936;
const RADAR_MAX_KM  = 40;
const RADAR_SIZE    = 88; // px — compact header radar

// ── Utilities ──────────────────────────────────────

function ftStr(m) {
  if (m == null) return null;
  return Math.round(m * 3.28084).toLocaleString() + ' ft';
}

function kmhStr(ms) {
  if (ms == null) return null;
  return Math.round(ms * 3.6) + ' km/h';
}

function fmtKm(km) {
  if (km == null) return '';
  return km.toFixed(1) + ' km';
}

function iata(code, icao) {
  const v = (code && code.trim()) || (icao && icao.trim());
  return v || null;
}

function bearing(lat1, lon1, lat2, lon2) {
  const f1 = lat1 * Math.PI / 180, f2 = lat2 * Math.PI / 180;
  const dl = (lon2 - lon1) * Math.PI / 180;
  const y  = Math.sin(dl) * Math.cos(f2);
  const x  = Math.cos(f1) * Math.sin(f2) - Math.sin(f1) * Math.cos(f2) * Math.cos(dl);
  return (Math.atan2(y, x) * 180 / Math.PI + 360) % 360;
}

function esc(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

// ── Radar ───────────────────────────────────────────

const radarCanvas = document.getElementById('radar');

function initRadar() {
  const dpr = window.devicePixelRatio || 1;
  radarCanvas.width  = RADAR_SIZE * dpr;
  radarCanvas.height = RADAR_SIZE * dpr;
  radarCanvas.style.width  = RADAR_SIZE + 'px';
  radarCanvas.style.height = RADAR_SIZE + 'px';
  const ctx = radarCanvas.getContext('2d');
  ctx.scale(dpr, dpr);
}

function drawRadar(flights) {
  const ctx  = radarCanvas.getContext('2d');
  const s    = RADAR_SIZE;
  const cx   = s / 2, cy = s / 2;
  const r    = cx - 6;

  ctx.clearRect(0, 0, s, s);

  // Clip
  ctx.save();
  ctx.beginPath();
  ctx.arc(cx, cy, r, 0, Math.PI * 2);
  ctx.clip();

  // Background
  ctx.fillStyle = '#080808';
  ctx.fillRect(0, 0, s, s);

  // Range rings
  [10, 20, 30, 40].forEach(km => {
    const rr = (km / RADAR_MAX_KM) * r;
    ctx.beginPath();
    ctx.arc(cx, cy, rr, 0, Math.PI * 2);
    ctx.strokeStyle = 'rgba(255,255,255,0.06)';
    ctx.setLineDash([2, 4]);
    ctx.lineWidth = 0.5;
    ctx.stroke();
    ctx.setLineDash([]);
  });

  // Crosshairs
  ctx.strokeStyle = 'rgba(255,255,255,0.04)';
  ctx.lineWidth = 0.5;
  for (let deg = 0; deg < 360; deg += 45) {
    const rad = deg * Math.PI / 180;
    ctx.beginPath();
    ctx.moveTo(cx, cy);
    ctx.lineTo(cx + r * Math.sin(rad), cy - r * Math.cos(rad));
    ctx.stroke();
  }

  ctx.restore();

  // Border
  ctx.beginPath();
  ctx.arc(cx, cy, r, 0, Math.PI * 2);
  ctx.strokeStyle = 'rgba(255,255,255,0.1)';
  ctx.lineWidth = 1;
  ctx.stroke();

  // N label
  ctx.fillStyle = 'rgba(255,255,255,0.2)';
  ctx.font = '8px system-ui';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'bottom';
  ctx.fillText('N', cx, cy - r + 1);

  // Observer dot
  ctx.beginPath();
  ctx.arc(cx, cy, 2.5, 0, Math.PI * 2);
  ctx.fillStyle = '#f0a020';
  ctx.fill();

  // Flights
  flights.forEach((f, i) => {
    const b  = bearing(OBSERVER_LAT, OBSERVER_LON, f.lat, f.lon);
    const fr = Math.min((f.distance_km / RADAR_MAX_KM) * r, r - 8);
    const br = b * Math.PI / 180;
    const fx = cx + fr * Math.sin(br);
    const fy = cy - fr * Math.cos(br);
    const track = f.true_track != null ? f.true_track : b;

    drawPlaneIcon(ctx, fx, fy, track, i === 0 ? 5.5 : 4.5, i === 0);
  });
}

function drawPlaneIcon(ctx, x, y, trackDeg, size, isPrimary) {
  ctx.save();
  ctx.translate(x, y);
  ctx.rotate(trackDeg * Math.PI / 180);
  ctx.fillStyle = isPrimary ? '#f0a020' : 'rgba(200,200,200,0.75)';

  // Fuselage
  ctx.beginPath();
  ctx.moveTo(0, -size);
  ctx.lineTo(size * 0.18, size * 0.25);
  ctx.lineTo(0, 0);
  ctx.lineTo(-size * 0.18, size * 0.25);
  ctx.closePath();
  ctx.fill();

  // Wings
  ctx.beginPath();
  ctx.moveTo(0, -size * 0.1);
  ctx.lineTo(-size * 0.9, size * 0.35);
  ctx.lineTo(-size * 0.12, size * 0.2);
  ctx.lineTo(size * 0.12, size * 0.2);
  ctx.lineTo(size * 0.9, size * 0.35);
  ctx.closePath();
  ctx.fill();

  // Tail
  ctx.beginPath();
  ctx.moveTo(0, size * 0.35);
  ctx.lineTo(-size * 0.42, size * 0.9);
  ctx.lineTo(size * 0.42, size * 0.9);
  ctx.closePath();
  ctx.fill();

  ctx.restore();
}

// ── Card rendering ──────────────────────────────────

function renderPrimaryCard(f) {
  const cs       = esc((f.callsign || f.icao24 || '').trim());
  const operator = (f.operator_name || '').trim();
  const aircraft = (f.aircraft_name_short || f.aircraft_name_full || f.aircraft_type_icao || '').trim();
  const dep      = iata(f.departure_iata, f.departure);
  const arr      = iata(f.arrival_iata,   f.arrival);
  const depName  = (f.departure_name || '').trim();
  const arrName  = (f.arrival_name   || '').trim();

  const isPhoto  = f.aircraft_image_type === 'EXACT';
  const imgSrc   = f.aircraft_image_url || '/static/aircraft/plane.svg';
  const imgCls   = isPhoto ? 'photo' : 'silhouette';
  const rotation = (!isPhoto && f.true_track != null)
    ? ` style="transform:rotate(${Math.round(f.true_track)}deg)"`
    : '';

  const statParts = [ftStr(f.altitude), kmhStr(f.velocity), fmtKm(f.distance_km)].filter(Boolean);
  const statsHtml = statParts.map((s, i) =>
    `<span class="stat">${esc(s)}</span>${i < statParts.length - 1 ? '<span class="stat-sep">·</span>' : ''}`
  ).join('');

  return `
    <div class="card card--primary">
      <div class="card-img">
        <img class="${imgCls}" src="${esc(imgSrc)}" alt=""${rotation}/>
      </div>
      <div class="card-body">
        <div class="card-head">
          <span class="callsign">${cs}</span>
          ${aircraft ? `<span class="aircraft-type">${esc(aircraft)}</span>` : ''}
        </div>
        ${operator ? `<div class="operator-name">${esc(operator)}</div>` : ''}
        ${dep && arr ? `
          <div class="route">
            <span class="route-code">${esc(dep)}</span>
            <div class="route-line"></div>
            <div class="route-arrow"></div>
            <span class="route-code">${esc(arr)}</span>
          </div>
          ${(depName || arrName) ? `
            <div class="route-names">
              <span>${esc(depName || dep)}</span>
              <span>${esc(arrName || arr)}</span>
            </div>` : ''}
        ` : ''}
        ${statsHtml ? `<div class="stats">${statsHtml}</div>` : ''}
      </div>
    </div>`;
}

function renderSecondaryCard(f) {
  const cs       = esc((f.callsign || f.icao24 || '').trim());
  const operator = (f.operator_name || '').trim();
  const aircraft = (f.aircraft_name_short || f.aircraft_name_full || f.aircraft_type_icao || '').trim();
  const dep      = iata(f.departure_iata, f.departure);
  const arr      = iata(f.arrival_iata,   f.arrival);

  const isPhoto  = f.aircraft_image_type === 'EXACT';
  const imgSrc   = f.aircraft_image_url || '/static/aircraft/plane.svg';
  const imgCls   = isPhoto ? 'photo' : 'silhouette';
  const rotation = (!isPhoto && f.true_track != null)
    ? ` style="transform:rotate(${Math.round(f.true_track)}deg)"`
    : '';

  const meta = [operator, aircraft].filter(Boolean).join(' · ');

  return `
    <div class="card card--secondary">
      <div class="card-img">
        <img class="${imgCls}" src="${esc(imgSrc)}" alt=""${rotation}/>
      </div>
      <div class="card-body">
        <div class="sec-row-a">
          <span class="callsign--sm">${cs}</span>
          ${dep && arr ? `<span class="route-sm">${esc(dep)} → ${esc(arr)}</span>` : '<span class="route-sm"></span>'}
          <span class="dist">${esc(fmtKm(f.distance_km))}</span>
        </div>
        <div class="sec-row-b">
          ${meta ? `<span class="sec-meta">${esc(meta)}</span>` : '<span></span>'}
          ${ftStr(f.altitude) ? `<span class="sec-alt">${esc(ftStr(f.altitude))}</span>` : ''}
        </div>
      </div>
    </div>`;
}

function attachImageFallbacks(container) {
  container.querySelectorAll('img').forEach(img => {
    img.onerror = function () {
      this.onerror = null;
      this.src = '/static/aircraft/plane.svg';
      this.className = 'silhouette';
      this.style.transform = '';
    };
  });
}

// ── Load & poll ─────────────────────────────────────

const statusEl  = document.getElementById('status');
const flightsEl = document.getElementById('flights');

async function load() {
  try {
    const res = await fetch('/api/flights/nearby?limit=3&max_distance_km=120');
    if (!res.ok) throw new Error(`HTTP ${res.status}`);

    const data    = await res.json();
    const flights = (data.flights || []).slice(0, 3);

    drawRadar(flights);

    if (!flights.length) {
      flightsEl.innerHTML = '<div class="empty">No flights overhead</div>';
    } else {
      const [first, ...rest] = flights;
      flightsEl.innerHTML = renderPrimaryCard(first)
        + rest.map(renderSecondaryCard).join('');
      attachImageFallbacks(flightsEl);
    }

    const t = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    statusEl.textContent = `${flights.length} overhead · ${t}`;
  } catch (e) {
    statusEl.textContent = `error: ${e.message}`;
  }
}

initRadar();
drawRadar([]);
load();
setInterval(load, 15000);
