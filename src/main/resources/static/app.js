const statusEl = document.getElementById('status');
const primaryEl = document.getElementById('primary');
const secondaryEl = document.getElementById('secondary');

function fmtKm(x) {
  if (x === null || x === undefined) return '';
  return `${x.toFixed(1)} km`;
}

function formatAirport(code, iata, name) {
  const displayCode = (iata && iata.trim())
      ? iata.trim()
      : (code && code.trim())
          ? code.trim()
          : 'Unknown';

  const cleanName = (name && name.trim())
      ? name.trim()
      : null;

  return cleanName
      ? `${displayCode} (${cleanName})`
      : displayCode;
}

function routeText(f) {
  function code(code, iata) {
    return (iata && iata.trim())
        ? iata.trim()
        : (code && code.trim())
            ? code.trim()
            : 'Unknown';
  }

  function name(n) {
    return (n && n.trim()) ? n.trim() : null;
  }

  const depCode = code(f.departure, f.departure_iata);
  const arrCode = code(f.arrival, f.arrival_iata);

  const depName = name(f.departure_name);
  const arrName = name(f.arrival_name);

  const codesLine = `${depCode} → ${arrCode}`;

  if (depName || arrName) {
    const namesLine = `${depName || depCode} → ${arrName || arrCode}`;
    return `
      <div class="route-codes">${codesLine}</div>
      <div class="route-names">${namesLine}</div>
    `;
  }

  return `<div class="route-codes">${codesLine}</div>`;
}

function safeCallsignWithIcao(f) {
  const cs = (f.callsign || '').trim();
  const icao = (f.icao24 || '').trim();

  if (cs && icao) return `${cs} · ${icao}`;
  if (cs) return cs;
  if (icao) return icao;

  return 'UNKNOWN';
}

function renderPrimary(f) {
  const imgUrl = f.aircraft_image_url;
  const callsign = safeCallsignWithIcao(f);
  const route = routeText(f);

  const operator = (f.operator_name || f.operator_icao || '').trim();
  const aircraft = (f.aircraft_name_short || f.aircraft_name_full || f.aircraft_type_icao || '').trim();
  const labelParts = [operator, aircraft].filter(Boolean);
  const labels = labelParts.length ? labelParts.join(' • ') : '';

  const altFt = metersToFeet(f.altitude);

  const velocity = (f.velocity === null || f.velocity === undefined)
    ? null
    : Math.round(f.velocity * 3.6);

  const metaParts = [];
  if (altFt !== null) metaParts.push(`${altFt.toLocaleString()} ft`);
  if (velocity !== null) metaParts.push(`${velocity} km/h`);
  metaParts.push(fmtKm(f.distance_km));

  const meta = metaParts.filter(Boolean).join(' • ');

  primaryEl.innerHTML = `
    <div class="flight-card">
      <div class="img"><img src="${imgUrl}" alt="aircraft" loading="lazy" /></div>
      <div class="primary-main">
        <div class="callsign">${callsign}</div>
        ${labels ? `<div class="labels">${labels}</div>` : ''}
        <div class="route">${route}</div>
        <div class="meta">${meta}</div>
      </div>
    </div>
  `;
}

function metersToFeet(m) {
  if (m === null || m === undefined) return null;
  return Math.round(m * 3.28084);
}

function renderSecondary(flights) {
  if (!flights || flights.length === 0) {
    secondaryEl.innerHTML = '';
    return;
  }

  const rows = flights.map(f => {
    const callsign = safeCallsignWithIcao(f);
    const route = routeText(f);
    const operator = (f.operator_name || f.operator_icao || '').trim();
    const aircraft = (f.aircraft_name_short || f.aircraft_name_full || f.aircraft_type_icao || '').trim();
    const labels = [operator, aircraft].filter(Boolean).join(' • ');
    return `
      <div class="row">
        <div class="cs">${callsign}</div>
        <div class="rt">
          <div>${route}</div>
          ${labels ? `<div class="lbl">${labels}</div>` : ''}
        </div>
        <div class="dist">${fmtKm(f.distance_km)}</div>
      </div>
    `;
  }).join('');

  secondaryEl.innerHTML = `<div class="secondary">${rows}</div>`;
}

function renderError(message) {
  primaryEl.innerHTML = `
    <div class="error-card">
      <div class="error-msg">No data available</div>
      <div class="error-detail">${message}</div>
      <button class="retry-btn" onclick="load()">Retry</button>
    </div>
  `;
}

async function load() {
  statusEl.classList.add('loading');
  statusEl.textContent = 'fetching…';

  try {
    const res = await fetch('/api/flights/nearby?limit=3&max_distance_km=120');

    if (!res.ok) {
      let detail = `HTTP ${res.status}`;
      try {
        const body = await res.json();
        if (body.details) detail = body.details;
        else if (body.error) detail = body.error;
      } catch (_) {}
      throw new Error(detail);
    }

    const data = await res.json();
    const flights = data.flights || [];

    if (flights.length === 0) {
      primaryEl.innerHTML = `<div class="empty">No flights nearby</div>`;
      secondaryEl.innerHTML = '';
    } else {
      renderPrimary(flights[0]);
      renderSecondary(flights.slice(1));
    }

    statusEl.textContent = `updated ${new Date().toLocaleTimeString()}`;
  } catch (e) {
    renderError(e.message);
    statusEl.textContent = `unavailable · ${new Date().toLocaleTimeString()}`;
    // Secondary data is preserved — don't clear it on transient errors
  } finally {
    statusEl.classList.remove('loading');
  }
}

load();
setInterval(load, 15000);
