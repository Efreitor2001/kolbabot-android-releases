"use strict";

// The same paid calculator runs headlessly. This adapter exposes its form data,
// generated sections and actions to native controls without a browser or WebView.
const nativeNodes = Object.create(null);
const nativeListeners = Object.create(null);
let nativeFile = null;
function nativeNode(id) {
    if (!Object.hasOwn(nativeNodes, id)) {
        nativeNodes[id] = {
            innerHTML: "", textContent: "", style: {}, dataset: {},
            classList: {add() {}, remove() {}, toggle() {}},
            addEventListener(name, fn) { this[name] = fn; },
            setAttribute() {}, scrollIntoView() {}, remove() {},
        };
    }
    return nativeNodes[id];
}
const document = {
    body: {dataset: {}, appendChild() {}},
    getElementById: nativeNode,
    querySelector(selector) {
        if (selector.includes('[data-key="rawKg"]')) {
            return {closest() { return {querySelector() { return nativeNode(selector + ":label"); }}; }};
        }
        return nativeNode(selector);
    },
    querySelectorAll() { return []; },
    addEventListener(name, fn) { (nativeListeners[name] ??= []).push(fn); },
    createElement() {
        return {remove() {}, click() { if (nativeFile) nativeFile.name = this.download; }};
    },
};
const window = {print() {}};
const URL = {
    createObjectURL(blob) { nativeFile = blob; return "blob:local"; },
    revokeObjectURL() {},
};
function Blob(parts, options) { this.parts = parts; this.type = options.type; }
function setTimeout() {}
function clearTimeout() {}
function requestAnimationFrame(fn) { fn(); }
function confirm() { return true; }
function TextEncoder() {}
TextEncoder.prototype.encode = function(value) {
        const escaped = encodeURIComponent(value);
        const bytes = [];
        for (let i = 0; i < escaped.length; i++) {
            if (escaped[i] === "%") {
                bytes.push(parseInt(escaped.slice(i + 1, i + 3), 16));
                i += 2;
            } else bytes.push(escaped.charCodeAt(i));
        }
        return new Uint8Array(bytes);
};
Number.prototype.toLocaleString = function(locale, options) {
    const precision = options?.maximumFractionDigits ?? 2;
    const [whole, fraction = ""] = Number(this).toFixed(precision).split(".");
    const decimals = fraction.replace(/0+$/, "");
    return whole.replace(/\B(?=(\d{3})+(?!\d))/g, "\u00a0") + (decimals ? "," + decimals : "");
};
Date.prototype.toLocaleDateString = function() {
    return [this.getDate(), this.getMonth() + 1, this.getFullYear()].map(n => String(n).padStart(2, "0")).join(".");
};
function nativeEvent(name, target) {
    for (const fn of (nativeListeners[name] ?? [])) fn({target, preventDefault() {}});
}
