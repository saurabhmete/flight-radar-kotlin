const OBSERVER_LAT = 51.505122562296975;
const OBSERVER_LON = 7.466314232256936;
const RADAR_MAX_KM = 40;

const radarCanvas = document.getElementById('radar');
const statusEl    = document.getElementById('status');
const flightsEl   = document.getElementById('flights');

let lastFlights    = [];
let currentRdrSize = 0;
let expandedIdx    = 0; // which card is expanded (mobile/tablet only)

// ── Utils ───────────────────────────────────────────

const mono = "'SF Mono','Fira Code','Consolas',monospace";

function ftStr(m)  { return m == null ? null : Math.round(m * 3.28084).toLocaleString() + ' ft'; }
function kmhStr(v) { return v == null ? null : Math.round(v * 3.6) + ' km/h'; }
function kmStr(k)  { return k == null ? '' : k.toFixed(1) + ' km'; }

function code(iata, icao) {
  return (iata && iata.trim()) || (icao && icao.trim()) || null;
}

function bearing(lat1, lon1, lat2, lon2) {
  const f1 = lat1 * Math.PI / 180, f2 = lat2 * Math.PI / 180;
  const dl = (lon2 - lon1) * Math.PI / 180;
  return (Math.atan2(
    Math.sin(dl) * Math.cos(f2),
    Math.cos(f1) * Math.sin(f2) - Math.sin(f1) * Math.cos(f2) * Math.cos(dl)
  ) * 180 / Math.PI + 360) % 360;
}

function esc(s) {
  return String(s ?? '')
    .replace(/&/g,'&amp;').replace(/</g,'&lt;')
    .replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

function isDesktop() { return window.innerWidth >= 1024; }

function radarSize() {
  const w = window.innerWidth;
  if (w >= 1440) return 160;
  if (w >= 1024) return 130;
  if (w >= 640)  return 110;
  return 88;
}

// ── Radar ───────────────────────────────────────────

function initRadar() {
  const size = radarSize();
  if (size === currentRdrSize) return;
  currentRdrSize = size;
  const dpr = window.devicePixelRatio || 1;
  radarCanvas.width  = size * dpr;
  radarCanvas.height = size * dpr;
  radarCanvas.style.width  = size + 'px';
  radarCanvas.style.height = size + 'px';
  radarCanvas.getContext('2d').scale(dpr, dpr);
}

function drawRadar(flights) {
  const ctx = radarCanvas.getContext('2d');
  const s   = currentRdrSize;
  const cx  = s / 2, cy = s / 2, r = cx - 6;

  ctx.clearRect(0, 0, s, s);

  // Clipped background + rings
  ctx.save();
  ctx.beginPath(); ctx.arc(cx, cy, r, 0, Math.PI * 2); ctx.clip();

  ctx.fillStyle = '#080808';
  ctx.fillRect(0, 0, s, s);

  // Range rings
  [10, 20, 30, 40].forEach(km => {
    ctx.beginPath();
    ctx.arc(cx, cy, (km / RADAR_MAX_KM) * r, 0, Math.PI * 2);
    ctx.strokeStyle = 'rgba(255,255,255,0.06)';
    ctx.setLineDash([2, 4]); ctx.lineWidth = 0.6; ctx.stroke();
    ctx.setLineDash([]);
  });

  // Crosshairs
  ctx.strokeStyle = 'rgba(255,255,255,0.04)'; ctx.lineWidth = 0.5;
  for (let d = 0; d < 360; d += 45) {
    const rad = d * Math.PI / 180;
    ctx.beginPath(); ctx.moveTo(cx, cy);
    ctx.lineTo(cx + r * Math.sin(rad), cy - r * Math.cos(rad));
    ctx.stroke();
  }
  ctx.restore();

  // Outer ring
  ctx.beginPath(); ctx.arc(cx, cy, r, 0, Math.PI * 2);
  ctx.strokeStyle = 'rgba(255,255,255,0.1)'; ctx.lineWidth = 1; ctx.stroke();

  // N label
  ctx.fillStyle = 'rgba(255,255,255,0.2)';
  ctx.font = `${Math.max(8, Math.round(s * 0.09))}px system-ui`;
  ctx.textAlign = 'center'; ctx.textBaseline = 'bottom';
  ctx.fillText('N', cx, cy - r + 2);

  // Observer
  ctx.beginPath(); ctx.arc(cx, cy, 2.5, 0, Math.PI * 2);
  ctx.fillStyle = '#f0a020'; ctx.fill();

  // Flights
  flights.forEach((f, i) => {
    const b   = bearing(OBSERVER_LAT, OBSERVER_LON, f.lat, f.lon);
    const fr  = Math.min((f.distance_km / RADAR_MAX_KM) * r, r - 9);
    const rad = b * Math.PI / 180;
    const fx  = cx + fr * Math.sin(rad);
    const fy  = cy - fr * Math.cos(rad);
    drawPlane(ctx, fx, fy, f.true_track ?? b, s * 0.055, i === 0);
  });
}

function drawPlane(ctx, x, y, deg, sz, primary) {
  ctx.save(); ctx.translate(x, y); ctx.rotate(deg * Math.PI / 180);
  ctx.fillStyle = primary ? '#f0a020' : 'rgba(200,200,200,0.75)';

  ctx.beginPath(); // fuselage
  ctx.moveTo(0, -sz); ctx.lineTo(sz*.18, sz*.25); ctx.lineTo(0, 0);
  ctx.lineTo(-sz*.18, sz*.25); ctx.closePath(); ctx.fill();

  ctx.beginPath(); // wings
  ctx.moveTo(0, -sz*.1); ctx.lineTo(-sz*.9, sz*.35);
  ctx.lineTo(-sz*.12, sz*.2); ctx.lineTo(sz*.12, sz*.2);
  ctx.lineTo(sz*.9, sz*.35); ctx.closePath(); ctx.fill();

  ctx.beginPath(); // tail
  ctx.moveTo(0, sz*.35); ctx.lineTo(-sz*.42, sz*.9);
  ctx.lineTo(sz*.42, sz*.9); ctx.closePath(); ctx.fill();

  ctx.restore();
}

// ── Card templates ──────────────────────────────────

function imgAttrs(f) {
  const isPhoto = f.aircraft_image_type === 'EXACT';
  const src = esc(f.aircraft_image_url || '/static/aircraft/plane.svg');
  const cls = isPhoto ? 'photo' : 'silhouette';
  const rot = (!isPhoto && f.true_track != null)
    ? ` style="transform:rotate(${Math.round(f.true_track)}deg)"` : '';
  return { src, cls, rot };
}

function statsHtml(f) {
  const parts = [ftStr(f.altitude), kmhStr(f.velocity), kmStr(f.distance_km)].filter(Boolean);
  return parts.map((p, i) =>
    `<span class="stat">${esc(p)}</span>${i < parts.length - 1 ? '<span class="stat-sep">·</span>' : ''}`
  ).join('');
}

// Full card (primary on all screens, all cards on desktop)
function fullCard(f) {
  const { src, cls, rot } = imgAttrs(f);
  const cs  = esc((f.callsign || f.icao24 || '').trim());
  const op  = (f.operator_name || '').trim();
  const ac  = (f.aircraft_name_short || f.aircraft_name_full || f.aircraft_type_icao || '').trim();
  const dep = code(f.departure_iata, f.departure);
  const arr = code(f.arrival_iata,   f.arrival);
  const dn  = (f.departure_name || '').trim();
  const an  = (f.arrival_name   || '').trim();
  const st  = statsHtml(f);

  return `<div class="card">
    <div class="card-img"><img class="${cls}" src="${src}" alt=""${rot}/></div>
    <div class="card-body">
      <div class="card-head">
        <span class="callsign">${cs}</span>
        ${ac ? `<span class="aircraft-type">${esc(ac)}</span>` : ''}
      </div>
      ${op ? `<div class="operator-name">${esc(op)}</div>` : ''}
      ${dep && arr ? `
        <div class="route">
          <span class="route-code">${esc(dep)}</span>
          <div class="route-line"></div>
          <div class="route-arrow"></div>
          <span class="route-code">${esc(arr)}</span>
        </div>
        ${(dn || an) ? `<div class="route-names">
          <span>${esc(dn || dep)}</span>
          <span>${esc(an || arr)}</span>
        </div>` : ''}
      ` : ''}
      ${st ? `<div class="stats">${st}</div>` : ''}
    </div>
  </div>`;
}

// Compact card (secondary on mobile/tablet)
function compactCard(f) {
  const { src, cls, rot } = imgAttrs(f);
  const cs   = esc((f.callsign || f.icao24 || '').trim());
  const dep  = code(f.departure_iata, f.departure);
  const arr  = code(f.arrival_iata,   f.arrival);
  const op   = (f.operator_name || '').trim();
  const ac   = (f.aircraft_name_short || f.aircraft_name_full || f.aircraft_type_icao || '').trim();
  const meta = [op, ac].filter(Boolean).join(' · ');

  return `<div class="card card--compact">
    <div class="thumb"><img class="${cls}" src="${src}" alt=""${rot}/></div>
    <div class="info">
      <div class="compact-top">
        <span class="cs-sm">${cs}</span>
        ${dep && arr ? `<span class="route-compact">${esc(dep)} → ${esc(arr)}</span>` : ''}
        <span class="km">${esc(kmStr(f.distance_km))}</span>
      </div>
      <div class="compact-bot">
        ${meta ? `<span class="compact-meta">${esc(meta)}</span>` : '<span></span>'}
        ${ftStr(f.altitude) ? `<span class="compact-alt">${esc(ftStr(f.altitude))}</span>` : ''}
      </div>
    </div>
  </div>`;
}

function renderFlights(flights) {
  if (!flights.length) {
    flightsEl.innerHTML = '<div class="empty">No flights overhead</div>';
    return;
  }

  if (isDesktop()) {
    flightsEl.innerHTML = flights.map(fullCard).join('');
  } else {
    // Clamp expandedIdx in case flight count changed
    if (expandedIdx >= flights.length) expandedIdx = 0;

    flightsEl.innerHTML = flights.map((f, i) => {
      if (i === expandedIdx) {
        return `<div class="card-slot" data-idx="${i}">${fullCard(f)}</div>`;
      } else {
        return `<div class="card-slot card-slot--tap" data-idx="${i}">${compactCard(f)}</div>`;
      }
    }).join('');

    // Click any non-expanded card to expand it
    flightsEl.querySelectorAll('.card-slot').forEach(slot => {
      slot.addEventListener('click', () => {
        const idx = parseInt(slot.dataset.idx);
        if (idx !== expandedIdx) {
          expandedIdx = idx;
          renderFlights(lastFlights);
        }
      });
    });
  }

  flightsEl.querySelectorAll('img').forEach(img => {
    img.onerror = function() {
      this.onerror = null;
      this.src = '/static/aircraft/plane.svg';
      this.className = 'silhouette';
      this.style.transform = '';
    };
  });
}

// ── Load & poll ─────────────────────────────────────

async function load() {
  try {
    const res = await fetch('/api/flights/nearby?limit=3&max_distance_km=120');
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    lastFlights = (data.flights || []).slice(0, 3);

    drawRadar(lastFlights);
    renderFlights(lastFlights);

    const t = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    statusEl.textContent = `${lastFlights.length} overhead · ${t}`;
  } catch (e) {
    statusEl.textContent = `error: ${e.message}`;
  }
}

// ── Resize ──────────────────────────────────────────

let resizeTimer;
window.addEventListener('resize', () => {
  clearTimeout(resizeTimer);
  resizeTimer = setTimeout(() => {
    initRadar();
    drawRadar(lastFlights);
    renderFlights(lastFlights);
  }, 120);
});

// ── Boot ────────────────────────────────────────────

initRadar();
drawRadar([]);
load();
setInterval(load, 15000);
