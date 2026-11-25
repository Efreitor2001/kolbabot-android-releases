"use strict";

function nativeCommand(action, raw) {
    const payload = JSON.parse(raw);
    nativeNode("toast").textContent = "";
    nativeFile = null;
    if (action === "field") {
        const target = Object.assign(nativeNode("form-event"), payload);
        nativeEvent(payload.event, target);
        // The web keeps tax comparison inputs pending until its recalculate button.
        // Other fields may change which products, costs or validations are visible.
        if (target.dataset.pid) buildProducts();
    } else if (action === "click") {
        if (payload.id && typeof nativeNode(payload.id).onclick === "function") nativeNode(payload.id).onclick();
        else {
            const target = {dataset: payload.dataset, closest(selector) {
                return selector === "button" || (selector === "[data-tseh-step]" && Object.hasOwn(this.dataset, "tsehStep"))
                    ? this : null;
            }};
            nativeEvent("click", target);
        }
    } else if (action === "load") {
        return importJSON({size: payload.length, text: () => Promise.resolve(payload)}).then(nativeSnapshot);
    } else if (action === "step") {
        go(payload);
    }
    return nativeSnapshot();
}
function nativeSnapshot() {
    const parts = Object.create(null);
    for (const [key, node] of Object.entries(nativeNodes)) {
        if (node.innerHTML || node.textContent || node.style.display !== undefined) {
            parts[key] = {html: node.innerHTML, text: node.textContent, hidden: node.style.display === "none"};
        }
    }
    return JSON.stringify({
        step, data, parts,
        file: nativeFile ? {name: nativeFile.name, type: nativeFile.type,
            parts: nativeFile.parts.map(part => typeof part === "string" ? part : Array.from(part))} : null,
    });
}
