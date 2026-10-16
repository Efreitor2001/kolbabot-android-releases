// This document renders only the map. Authentication and checkout remain native.
(() => {
  let sequence = 0;
  const pending = new Map();
  window.OzonNativeReply = (id, data, error) => {
    const request = pending.get(id);
    if (!request) return;
    request.finish();
    if (error) request.reject(Object.assign(new Error(error.message), { status: error.status }));
    else request.resolve(data);
  };
  window.OzonNativeRequest = (path, signal) => new Promise((resolve, reject) => {
    const id = String(++sequence);
    const abort = () => {
      const request = pending.get(id);
      if (!request) return;
      request.finish();
      AndroidOzon.cancel(id);
      reject(new DOMException("Запрос отменён", "AbortError"));
    };
    const timeout = window.setTimeout(() => {
      const request = pending.get(id);
      if (!request) return;
      request.finish();
      AndroidOzon.cancel(id);
      reject(new Error("Загрузка карты заняла слишком много времени. Повторите попытку."));
    }, 18000);
    pending.set(id, {
      resolve, reject,
      finish() {
        pending.delete(id);
        clearTimeout(timeout);
        signal?.removeEventListener("abort", abort);
      },
    });
    if (signal?.aborted) { abort(); return; }
    signal?.addEventListener("abort", abort, { once: true });
    AndroidOzon.request(id, path);
  });
  const selectedPoint = JSON.parse(AndroidOzon.initialPoint());
  window.KolbabotOzonMapBridge = {
    getSelectedPoint: () => selectedPoint,
    selectPoint: point => AndroidOzon.selectPoint(JSON.stringify(point)),
    renderPoints: points => AndroidOzon.renderPoints(JSON.stringify(points)),
  };
})();
