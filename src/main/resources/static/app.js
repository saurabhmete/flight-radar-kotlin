const statusEl = document.getElementById('status');
const primaryEl = document.getElementById('primary');
const secondaryEl = document.getElementById('secondary');

function fmtKm(x) {
  if (x === null || x === undefined) return '';
  return `${x.toFixed(1)} km`;
}

function routeText(f) {
  const dep = f.departure ?? 'Unknown';
  const arr = f.arrival ?? 'Unknown';
  return `${dep} → ${arr}`;
}

function safeCallsign(f) {
  return (f.callsign || '').trim() || 'UNKNOWN';
}

function renderPrimary(f) {
  const imgUrl = f.aircraft_image_url;
  const callsign = safeCallsign(f);
  const route = routeText(f);

  const alt = (f.altitude === null || f.altitude === undefined)
    ? null
    : Math.round(f.altitude);

  const velocity = (f.velocity === null || f.velocity === undefined)
    ? null
    : Math.round(f.velocity * 3.6); // m/s -> km/h

  const metaParts = [];
  if (alt !== null) metaParts.push(`${alt} m`);
  if (velocity !== null) metaParts.push(`${velocity} km/h`);
  metaParts.push(fmtKm(f.distance_km));

  const meta = metaParts.filter(Boolean).join(' • ');

  primaryEl.innerHTML = `
    <div class="primary-card">
      <img class="plane" src="${imgUrl}" alt="aircraft" loading="lazy" />
      <div class="primary-main">
        <div class="callsign">${callsign}</div>
        <div class="route">${route}</div>
        <div class="meta">${meta}</div>
      </div>
    </div>
  `;
}

function renderSecondary(flights) {
  if (!flights || flights.length === 0) {
    secondaryEl.innerHTML = '';
    return;
  }

  const rows = flights.map(f => {
    const callsign = safeCallsign(f);
    const route = routeText(f);
    return `
      <div class="row">
        <div class="cs">${callsign}</div>
        <div class="rt">${route}</div>
        <div class="dist">${fmtKm(f.distance_km)}</div>
      </div>
    `;
  }).join('');

  secondaryEl.innerHTML = `
    <div class="secondary">
      ${rows}
    </div>
  `;
}

async function load() {
  try {
    statusEl.textContent = 'fetching flights…';

    const res = await fetch('/api/flights/nearby?limit=3&max_distance_km=120');
    if (!res.ok) throw new Error(`HTTP ${res.status}`);

    const data = await res.json();
    const flights = data.flights || [];

    if (flights.length === 0) {
      primaryEl.innerHTML = `<div class="empty">No flights nearby</div>`;
      secondaryEl.innerHTML = '';
      statusEl.textContent = `updated ${new Date().toLocaleTimeString()}`;
      return;
    }

    renderPrimary(flights[0]);
    renderSecondary(flights.slice(1));

    statusEl.textContent = `updated ${new Date().toLocaleTimeString()}`;
  } catch (e) {
    statusEl.textContent = `error: ${e.message}`;
  }
}

// Refresh periodically, but no rotation (you asked no rotation).
load();
setInterval(load, 15000);
