// ==UserScript==
// @name         CodinGame — Capture TestSession replay
// @namespace    https://github.com/local/btk-tools
// @version      2.0
// @description  Captures TestSession/play and downloads its canonical gameResult replay.
// @author       local
// @match        *://www.codingame.com/ide/*
// @run-at       document-start
// @grant        GM_xmlhttpRequest
// @grant        unsafeWindow
// @connect      localhost
// @connect      www.codingame.com
// ==/UserScript==

(function () {
    'use strict';

    const SAVE_SERVER_URL = 'http://localhost:7777';
    const page = unsafeWindow;
    const captured = new Set();

    function isPlayUrl(value) {
        const url = String(value || '');
        return /\/services\/(?:TestSession|TestSessionRemoteService)\/play(?:[?#]|$)/i.test(url);
    }

    function timestamp() {
        const d = new Date();
        const p = n => String(n).padStart(2, '0');
        return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}_` +
            `${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`;
    }

    function findGameId(value, depth = 0) {
        if (depth > 12 || value == null || typeof value !== 'object') return null;
        if (Object.prototype.hasOwnProperty.call(value, 'gameId')) {
            const id = value.gameId;
            if (typeof id === 'number' || (typeof id === 'string' && /^\d+$/.test(id))) {
                return String(id);
            }
        }
        for (const child of Object.values(value)) {
            const id = findGameId(child, depth + 1);
            if (id) return id;
        }
        return null;
    }

    function showStatus(message, error) {
        const render = () => {
            const old = document.getElementById('cg-replay-capture-status');
            if (old) old.remove();
            const box = document.createElement('div');
            box.id = 'cg-replay-capture-status';
            box.textContent = message;
            Object.assign(box.style, {
                position: 'fixed', right: '16px', bottom: '16px', zIndex: '2147483647',
                padding: '9px 13px', borderRadius: '6px', color: '#fff',
                background: error ? '#a12622' : '#176b3a', font: '13px Consolas, monospace',
                boxShadow: '0 2px 10px rgba(0,0,0,.5)',
            });
            document.body.appendChild(box);
            setTimeout(() => box.remove(), 6000);
        };
        if (document.body) render();
        else document.addEventListener('DOMContentLoaded', render, { once: true });
    }

    function download(content, filename) {
        const blob = new Blob([content], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        a.click();
        setTimeout(() => URL.revokeObjectURL(url), 5000);
    }

    function postToSaveServer(kind, content, filename) {
        return new Promise(resolve => GM_xmlhttpRequest({
            method: 'POST', url: SAVE_SERVER_URL,
            headers: { 'Content-Type': 'application/json' },
            data: JSON.stringify({ kind, filename, content }),
            timeout: 10000,
            onload: response => {
                if (response.status < 200 || response.status >= 300) return resolve(null);
                try { const data = JSON.parse(response.responseText); resolve(data.ok ? data : null); }
                catch (_) { resolve(null); }
            },
            onerror: () => resolve(null),
            ontimeout: () => resolve(null),
        }));
    }

    function fetchCanonicalReplay(gameId) {
        return new Promise(resolve => GM_xmlhttpRequest({
            method: 'POST',
            url: 'https://www.codingame.com/services/gameResult/findByGameId',
            headers: { 'Content-Type': 'application/json' },
            data: `[${gameId},null]`,
            timeout: 15000,
            onload: response => {
                if (response.status < 200 || response.status >= 300) return resolve(null);
                try {
                    const raw = JSON.parse(response.responseText);
                    resolve(String(raw.gameId) === String(gameId) && Array.isArray(raw.frames)
                        && Array.isArray(raw.agents) ? response.responseText : null);
                } catch (_) { resolve(null); }
            },
            onerror: () => resolve(null),
            ontimeout: () => resolve(null),
        }));
    }

    async function save(content) {
        if (!content || captured.has(content)) return;
        captured.add(content);

        let gameId = null;
        try {
            gameId = findGameId(JSON.parse(content));
        } catch (e) {
            console.warn('[CG-replay] TestSession/play did not return JSON:', e);
        }

        const rawFilename = `testsession_play_${gameId || timestamp()}.json`;
        if (!gameId) {
            const rawSaved = await postToSaveServer('boss_raw', content, rawFilename);
            if (rawSaved) {
                showStatus(`Raw boss response saved: ${rawSaved.path}`, false);
            } else {
                download(content, rawFilename);
                showStatus(`Save server failed; downloaded ${rawFilename}`, true);
            }
            return;
        }

        const canonical = await fetchCanonicalReplay(gameId);
        if (canonical) {
            const filename = `${gameId}.json`;
            const saved = await postToSaveServer('boss', canonical, filename);
            if (saved) {
                console.info(`[CG-replay] Saved canonical replay ${filename}`);
                const known = saved.attribution && saved.attribution.retainedArtifactVerified;
                showStatus(`Boss replay saved: ${saved.path}${known ? '' : ' (build unknown)'}`, false);
            } else {
                download(canonical, filename);
                console.warn(`[CG-replay] Save server rejected ${filename}; used browser download`);
                showStatus(`Save server failed; downloaded ${filename}`, true);
            }
            return;
        }

        const rawSaved = await postToSaveServer('boss_raw', content, rawFilename);
        console.warn(`[CG-replay] gameResult lookup failed for ${gameId}; kept raw response`);
        if (rawSaved) {
            showStatus(`Canonical lookup failed; raw saved: ${rawSaved.path}`, true);
        } else {
            download(content, rawFilename);
            showStatus(`Both API and save server failed; downloaded ${rawFilename}`, true);
        }
    }

    if (typeof page.fetch === 'function') {
        const originalFetch = page.fetch;
        page.fetch = async function (...args) {
            const response = await originalFetch.apply(this, args);
            const requestUrl = typeof args[0] === 'string' ? args[0] : args[0] && args[0].url;
            if (isPlayUrl(requestUrl)) {
                response.clone().text().then(save).catch(error => {
                    console.warn('[CG-replay] Cannot read fetch response:', error);
                });
            }
            return response;
        };
    }

    const XHR = page.XMLHttpRequest;
    if (XHR && XHR.prototype) {
        const originalOpen = XHR.prototype.open;
        const originalSend = XHR.prototype.send;
        XHR.prototype.open = function (method, url, ...rest) {
            this.__cgReplayPlayRequest = isPlayUrl(url);
            return originalOpen.call(this, method, url, ...rest);
        };
        XHR.prototype.send = function (...args) {
            if (this.__cgReplayPlayRequest) {
                this.addEventListener('load', () => {
                    try {
                        const content = this.responseType === 'json'
                            ? JSON.stringify(this.response)
                            : this.responseText;
                        save(content);
                    } catch (error) {
                        console.warn('[CG-replay] Cannot read XHR response:', error);
                        showStatus('Replay capture failed; see browser console', true);
                    }
                }, { once: true });
            }
            return originalSend.apply(this, args);
        };
    }

    console.info('[CG-replay] TestSession/play capture is active');
})();
