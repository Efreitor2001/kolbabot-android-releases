const BRIDGE = window.KolbabotOzonMapBridge;
const MAP_PANEL_ID = "ozonPvzMapPanel";
const MAP_CONTAINER_ID = "ozonPvzMap";
const MAP_STATUS_ID = "ozonPvzMapStatus";
const DEFAULT_CENTER = [37.573856, 55.751574];
const DEFAULT_ZOOM = 10;

const MAPLIBRE_VERSION = "5.24.0";
const PMTILES_VERSION = "4.5.0";
const PROTOMAPS_BASEMAPS_VERSION = "5.7.2";
const JSDELIVR_NPM_BASE = "https://cdn.jsdelivr.net/npm";
const MAPLIBRE_SCRIPT_ID = "kolbabot-maplibre-js";
const MAPLIBRE_STYLE_ID = "kolbabot-maplibre-css";
const PMTILES_SCRIPT_ID = "kolbabot-pmtiles-js";
const BASEMAPS_SCRIPT_ID = "kolbabot-protomaps-basemaps-js";

let mapInstance = null;
let mapRuntimePromise = null;
let mapConfig = null;
let pointsTimer = 0;
let pointsSeq = 0;
let lastBoundsKey = "";
let clusterMarkers = [];
let pointsController = null;

function setStatus(text, isError = false) {
  const status = document.getElementById(MAP_STATUS_ID);
  if (!status) return;
  status.textContent = String(text || "");
  status.classList.toggle("is-error", Boolean(isError));
}

async function requestJson(path, signal) {
  const response = await fetchWithTimeout(path, {
    method: "GET",
    credentials: "same-origin",
    headers: { Accept: "application/json" },
    signal,
  });
  let payload = null;
  try {
    payload = await response.json();
  } catch (_error) {
    payload = null;
  }
  if (!response.ok || !payload?.ok) {
    const error = new Error(String(payload?.error || `HTTP ${response.status}`));
    error.status = response.status;
    throw error;
  }
  return payload.data || {};
}

async function fetchWithTimeout(url, options = {}) {
  const controller = new AbortController();
  const cancel = () => controller.abort();
  if (options.signal?.aborted) controller.abort();
  options.signal?.addEventListener("abort", cancel, { once: true });
  const timeout = window.setTimeout(cancel, 18000);
  try {
    return await fetch(url, { ...options, signal: controller.signal });
  } catch (error) {
    if (controller.signal.aborted && !options.signal?.aborted) {
      throw new Error("Загрузка карты заняла слишком много времени. Попробуйте открыть её ещё раз.");
    }
    throw error;
  } finally {
    window.clearTimeout(timeout);
    options.signal?.removeEventListener("abort", cancel);
  }
}

function ensurePanel() {
  const dialog = document.getElementById("ozonPvzDialog");
  if (!dialog) return null;
  const existing = document.getElementById(MAP_PANEL_ID);
  if (existing) return existing;

  const panel = document.createElement("section");
  panel.id = MAP_PANEL_ID;
  panel.className = "ozon-pvz-map-panel";
  panel.innerHTML = `
    <div class="ozon-pvz-map-panel__head">
      <div class="ozon-pvz-map-panel__title">
        <strong>ПВЗ на карте</strong>
        <span>Выберите Ваш ПВЗ на карте.</span>
      </div>
      <button type="button" class="ozon-pvz-map-location" id="ozonPvzMapLocateBtn">Моё местоположение</button>
    </div>
    <div id="${MAP_CONTAINER_ID}" class="ozon-pvz-map" aria-label="Карта пунктов выдачи Ozon"></div>
    <div id="${MAP_STATUS_ID}" class="ozon-pvz-map-status" aria-live="polite">Загрузка карты…</div>
  `;

  const searchForm = document.getElementById("ozonPvzSearchForm");
  if (searchForm?.parentNode) searchForm.parentNode.insertBefore(panel, searchForm);
  else dialog.querySelector("article")?.appendChild(panel);

  document.getElementById("ozonPvzMapLocateBtn")?.addEventListener("click", locateUser);
  return panel;
}

async function loadConfig() {
  if (mapConfig) return mapConfig;
  mapConfig = await requestJson("/api/store/ozon-delivery/map-config");
  return mapConfig;
}

function loadStylesheet(id, href) {
  if (document.getElementById(id)) return;
  const link = document.createElement("link");
  link.id = id;
  link.rel = "stylesheet";
  link.href = href;
  link.crossOrigin = "anonymous";
  document.head.appendChild(link);
}

function loadScript(id, src, ready, errorMessage) {
  if (ready()) return Promise.resolve();
  return new Promise((resolve, reject) => {
    const finish = (script) => {
      script.dataset.loadComplete = "1";
      if (ready()) resolve();
      else {
        script.dataset.loadFailed = "1";
        reject(new Error(errorMessage));
      }
    };
    const fail = (script) => {
      script.dataset.loadFailed = "1";
      reject(new Error(errorMessage));
    };
    const existing = document.getElementById(id);
    if (existing) {
      if (existing.dataset.loadComplete === "1" || existing.dataset.loadFailed === "1") {
        existing.remove();
        reject(new Error(errorMessage));
        return;
      }
      existing.addEventListener("load", () => finish(existing), { once: true });
      existing.addEventListener("error", () => fail(existing), { once: true });
      return;
    }
    const script = document.createElement("script");
    script.id = id;
    script.src = src;
    script.async = true;
    script.crossOrigin = "anonymous";
    script.addEventListener("load", () => finish(script), { once: true });
    script.addEventListener("error", () => fail(script), { once: true });
    document.head.appendChild(script);
  });
}

async function loadMapRuntime() {
  if (mapRuntimePromise) return mapRuntimePromise;
  mapRuntimePromise = (async () => {
    loadStylesheet(
      MAPLIBRE_STYLE_ID,
      `${JSDELIVR_NPM_BASE}/maplibre-gl@${MAPLIBRE_VERSION}/dist/maplibre-gl.css`
    );
    await Promise.all([
      loadScript(
        MAPLIBRE_SCRIPT_ID,
        `${JSDELIVR_NPM_BASE}/maplibre-gl@${MAPLIBRE_VERSION}/dist/maplibre-gl.js`,
        () => Boolean(window.maplibregl?.Map),
        "Не удалось загрузить движок карты MapLibre."
      ),
      loadScript(
        PMTILES_SCRIPT_ID,
        `${JSDELIVR_NPM_BASE}/pmtiles@${PMTILES_VERSION}/dist/pmtiles.js`,
        () => Boolean(window.pmtiles?.Protocol),
        "Не удалось загрузить поддержку PMTiles."
      ),
      loadScript(
        BASEMAPS_SCRIPT_ID,
        `${JSDELIVR_NPM_BASE}/@protomaps/basemaps@${PROTOMAPS_BASEMAPS_VERSION}/dist/basemaps.js`,
        () => Boolean(window.basemaps?.layers && window.basemaps?.namedFlavor),
        "Не удалось загрузить стиль карты Protomaps."
      ),
    ]);

    if (!window.__kolbabotPmtilesProtocol) {
      const protocol = new window.pmtiles.Protocol({ metadata: true });
      window.maplibregl.addProtocol("pmtiles", protocol.tile);
      window.__kolbabotPmtilesProtocol = protocol;
    }
  })().catch((error) => {
    mapRuntimePromise = null;
    for (const id of [MAPLIBRE_SCRIPT_ID, PMTILES_SCRIPT_ID, BASEMAPS_SCRIPT_ID]) {
      const script = document.getElementById(id);
      if (script?.dataset.loadFailed === "1") script.remove();
    }
    throw error;
  });
  return mapRuntimePromise;
}

async function verifyPmtiles(url) {
  const response = await fetchWithTimeout(url, {
    method: "GET",
    mode: "cors",
    cache: "no-store",
    headers: { Range: "bytes=0-126" },
  });
  if (response.status !== 206) {
    throw new Error("Файл карты в CDN пока недоступен для Range-запросов.");
  }
  const contentRange = String(response.headers.get("content-range") || "");
  if (!/^bytes\s+0-126\//i.test(contentRange)) {
    throw new Error("CDN вернул некорректный Content-Range для PMTiles.");
  }
}

function selectedPointCenter() {
  const point = BRIDGE?.getSelectedPoint?.();
  const lat = Number(point?.coordinates?.latitude);
  const lon = Number(point?.coordinates?.longitude);
  if (Number.isFinite(lat) && Number.isFinite(lon)) return [lon, lat];
  return DEFAULT_CENTER;
}

function makeBasemapStyle(pmtilesUrl) {
  return {
    version: 8,
    glyphs: "https://protomaps.github.io/basemaps-assets/fonts/{fontstack}/{range}.pbf",
    sprite: "https://protomaps.github.io/basemaps-assets/sprites/v4/light",
    sources: {
      protomaps: {
        type: "vector",
        url: `pmtiles://${pmtilesUrl}`,
        attribution: '<a href="https://protomaps.com">Protomaps</a> © <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>',
      },
    },
    layers: window.basemaps.layers(
      "protomaps",
      window.basemaps.namedFlavor("light"),
      { lang: "ru" }
    ),
  };
}

async function ensureMap() {
  ensurePanel();
  if (mapInstance) {
    window.setTimeout(() => mapInstance.resize(), 0);
    scheduleViewportLoad(true);
    return mapInstance;
  }

  try {
    const config = await loadConfig();
    if (!config.pmtiles_url) {
      setStatus("Файл базовой карты пока не настроен. Поиск ПВЗ ниже продолжает работать.", true);
      return null;
    }
    await Promise.all([loadMapRuntime(), verifyPmtiles(config.pmtiles_url)]);
  } catch (error) {
    setStatus(`${error?.message || "Не удалось открыть карту."} Поиск ПВЗ ниже продолжает работать.`, true);
    return null;
  }

  const container = document.getElementById(MAP_CONTAINER_ID);
  if (!container) return null;

  try {
    mapInstance = new window.maplibregl.Map({
      container,
      style: makeBasemapStyle(mapConfig.pmtiles_url),
      center: selectedPointCenter(),
      zoom: DEFAULT_ZOOM,
      minZoom: 2,
      maxZoom: 19,
      attributionControl: true,
    });
    mapInstance.addControl(new window.maplibregl.NavigationControl({ showCompass: false }), "top-right");
    mapInstance.addControl(new window.maplibregl.FullscreenControl({ container }), "top-right");
    mapInstance.on("load", () => {
      window.setTimeout(() => mapInstance?.resize(), 0);
      scheduleViewportLoad(true);
    });
    mapInstance.on("moveend", () => scheduleViewportLoad(false));
    mapInstance.on("error", (event) => {
      const message = String(event?.error?.message || "");
      if (/pmtiles|failed to fetch|network/i.test(message)) {
        setStatus("Не удалось прочитать базовую карту из CDN. Поиск ПВЗ ниже продолжает работать.", true);
      }
    });
    return mapInstance;
  } catch (error) {
    mapInstance?.remove?.();
    mapInstance = null;
    setStatus(`${error?.message || "Не удалось запустить карту."} Поиск ПВЗ ниже продолжает работать.`, true);
    return null;
  }
}

function boundsParams() {
  if (!mapInstance) return null;
  const bounds = mapInstance.getBounds();
  const minLat = Number(bounds?.getSouth?.());
  const maxLat = Number(bounds?.getNorth?.());
  const minLon = Number(bounds?.getWest?.());
  const maxLon = Number(bounds?.getEast?.());
  if (![minLat, maxLat, minLon, maxLon].every(Number.isFinite)) return null;
  return {
    minLat: Math.max(-90, minLat),
    maxLat: Math.min(90, maxLat),
    minLon: Math.max(-180, minLon),
    maxLon: Math.min(180, maxLon),
  };
}

function scheduleViewportLoad(immediate) {
  window.clearTimeout(pointsTimer);
  pointsTimer = window.setTimeout(loadViewportClusters, immediate ? 0 : 280);
}

async function loadViewportClusters(retryAttempt = 0) {
  if (!mapInstance) return;
  const bounds = boundsParams();
  if (!bounds || bounds.minLat >= bounds.maxLat || bounds.minLon >= bounds.maxLon) return;
  const zoom = Math.max(0, Math.min(19, Math.round(Number(mapInstance.getZoom()) || DEFAULT_ZOOM)));
  const key = [bounds.minLat, bounds.maxLat, bounds.minLon, bounds.maxLon]
    .map((value) => value.toFixed(4)).join(":") + `:${zoom}`;
  if (key === lastBoundsKey) return;
  lastBoundsKey = key;
  const seq = ++pointsSeq;
  pointsController?.abort();
  pointsController = new AbortController();
  setStatus("Загружаем ПВЗ Ozon в этой области…");

  const params = new URLSearchParams({
    min_lat: String(bounds.minLat),
    max_lat: String(bounds.maxLat),
    min_lon: String(bounds.minLon),
    max_lon: String(bounds.maxLon),
    zoom: String(zoom),
  });

  try {
    const data = await requestJson(`/api/store/ozon-delivery/map-points?${params.toString()}`, pointsController.signal);
    if (seq !== pointsSeq) return;
    const clusters = (Array.isArray(data.clusters) ? data.clusters : [])
      .map(cluster => ({ ...cluster, source: cluster.source || data.source || mapConfig?.points_source }));
    renderClusters(clusters);
    const total = clusters.reduce((sum, cluster) => sum + Math.max(0, Number(cluster?.points_count) || 0), 0);
    if (total) {
      setStatus(data.truncated
        ? `Показано ${total} ПВЗ Ozon. Приблизьте карту, чтобы увидеть остальные.`
        : `В этой области ${total} ПВЗ Ozon. Приближайте карту или нажмите на метку.`);
    } else {
      setStatus("В этой области ПВЗ Ozon не найдено. Переместите карту или измените масштаб.");
    }
  } catch (error) {
    if (seq !== pointsSeq) return;
    lastBoundsKey = "";
    const transient = !error?.status || error.status === 429 || error.status >= 500;
    if (transient && error?.name !== "AbortError" && retryAttempt < 2) {
      setStatus("Повторяем загрузку ПВЗ Ozon…");
      window.clearTimeout(pointsTimer);
      pointsTimer = window.setTimeout(() => {
        if (seq === pointsSeq && document.getElementById("ozonPvzDialog")?.open) {
          void loadViewportClusters(retryAttempt + 1);
        }
      }, 750 * (retryAttempt + 1));
      return;
    }
    setStatus(error?.message || "Не удалось загрузить ПВЗ Ozon на карте.", true);
  }
}

function clearClusterMarkers() {
  for (const marker of clusterMarkers) marker.remove();
  clusterMarkers = [];
}

function markerElement(count) {
  const single = count === 1;
  const button = document.createElement("button");
  button.type = "button";
  button.className = `ozon-pvz-map-marker ${single ? "is-point" : "is-cluster"}`;
  if (!single) button.textContent = String(count);
  button.title = single ? "Выбрать ПВЗ Ozon" : `ПВЗ Ozon: ${count}. Нажмите, чтобы приблизить`;
  button.setAttribute("aria-label", button.title);
  return button;
}

function renderClusters(clusters) {
  if (!mapInstance || !window.maplibregl) return;
  clearClusterMarkers();
  for (const cluster of clusters) {
    const lat = Number(cluster?.coordinate?.lat);
    const lon = Number(cluster?.coordinate?.long);
    if (!Number.isFinite(lat) || !Number.isFinite(lon)) continue;
    const count = Math.max(1, Number(cluster?.points_count) || 1);
    const element = markerElement(count);
    element.addEventListener("click", (event) => {
      event.preventDefault();
      event.stopPropagation();
      void handleClusterClick(cluster);
    });
    const marker = new window.maplibregl.Marker({ element, anchor: "center" })
      .setLngLat([lon, lat])
      .addTo(mapInstance);
    clusterMarkers.push(marker);
  }
}

function clusterBounds(cluster) {
  const left = cluster?.viewport?.left_bottom;
  const right = cluster?.viewport?.right_top;
  const minLat = Number(left?.lat);
  const minLon = Number(left?.long);
  const maxLat = Number(right?.lat);
  const maxLon = Number(right?.long);
  if (![minLat, minLon, maxLat, maxLon].every(Number.isFinite)) return null;
  if (minLat >= maxLat || minLon >= maxLon) return null;
  return [[minLon, minLat], [maxLon, maxLat]];
}

async function loadClusterPoints(cluster) {
  const ids = Array.isArray(cluster?.map_point_ids) ? cluster.map_point_ids.filter(Boolean).slice(0, 100) : [];
  if (!ids.length) {
    setStatus("Ozon не вернул идентификаторы ПВЗ для этой метки. Попробуйте приблизить карту.", true);
    return;
  }
  setStatus("Загружаем данные выбранного ПВЗ…");
  try {
    const params = new URLSearchParams({ ids: ids.join(","), source: cluster.source || mapConfig?.points_source || "seller" });
    const data = await requestJson(`/api/store/ozon-delivery/map-point-info?${params.toString()}`);
    const items = Array.isArray(data.items) ? data.items : [];
    if (items.length === 1) {
      await BRIDGE?.selectPoint?.(items[0]);
      return;
    }
    if (items.length > 1) {
      BRIDGE?.renderPoints?.(items);
      setStatus(`В этом месте ${items.length} ПВЗ. Выберите нужный в списке под картой.`);
      document.getElementById("ozonPvzResults")?.scrollIntoView({ behavior: "smooth", block: "nearest" });
      return;
    }
    setStatus("Активных ПВЗ в этой точке не найдено. Выберите другую метку.", true);
  } catch (error) {
    setStatus(error?.message || "Не удалось загрузить выбранный ПВЗ.", true);
  }
}

async function handleClusterClick(cluster) {
  if (!mapInstance) return;
  const count = Math.max(1, Number(cluster?.points_count) || 1);
  const zoom = Number(mapInstance.getZoom()) || DEFAULT_ZOOM;
  if (cluster?.requires_zoom) {
    const bounds = clusterBounds(cluster);
    if (bounds) {
      lastBoundsKey = "";
      mapInstance.fitBounds(bounds, { padding: 46, duration: 250, maxZoom: 19 });
      return;
    }
    setStatus("В этой области много ПВЗ. Приблизьте карту, чтобы выбрать точку.", true);
    return;
  }
  if (count === 1 || cluster?.is_same_building || zoom >= 18) {
    await loadClusterPoints(cluster);
    return;
  }
  const bounds = clusterBounds(cluster);
  if (bounds) {
    lastBoundsKey = "";
    mapInstance.fitBounds(bounds, { padding: 46, duration: 250, maxZoom: 19 });
    return;
  }
  const lat = Number(cluster?.coordinate?.lat);
  const lon = Number(cluster?.coordinate?.long);
  if (Number.isFinite(lat) && Number.isFinite(lon)) {
    lastBoundsKey = "";
    mapInstance.easeTo({ center: [lon, lat], zoom: Math.min(19, zoom + 2), duration: 250 });
  }
}

function locateUser() {
  if (!mapInstance) return;
  if (!navigator.geolocation) {
    setStatus("Браузер не поддерживает определение местоположения.", true);
    return;
  }
  setStatus("Определяем ваше местоположение…");
  navigator.geolocation.getCurrentPosition(
    (position) => {
      const lat = Number(position.coords?.latitude);
      const lon = Number(position.coords?.longitude);
      if (!Number.isFinite(lat) || !Number.isFinite(lon)) return;
      lastBoundsKey = "";
      mapInstance.easeTo({ center: [lon, lat], zoom: 12, duration: 250 });
    },
    () => setStatus("Не удалось получить местоположение. Можно переместить карту вручную.", true),
    { enableHighAccuracy: false, timeout: 8000, maximumAge: 300000 }
  );
}

function onOpenClick() {
  window.setTimeout(() => ensureMap(), 0);
}

if (BRIDGE) {
  document.getElementById("ozonPvzOpenBtn")?.addEventListener("click", onOpenClick);
  const dialog = document.getElementById("ozonPvzDialog");
  if (dialog?.open) ensureMap();
}
