// ==UserScript==
// @name         CodinGame — Export stderr log
// @namespace    https://github.com/local/snakebot-tools
// @version      2.2
// @description  Adds a button that collects [Px:IN]/[Px:OUT]/[Px:ERR] stderr
//               lines from the CodinGame debug console and downloads a single
//               combined .txt file (or POSTs to a local save-server).
// @author       local
// @match        *://www.codingame.com/ide/*
// @grant        GM_download
// @grant        GM_xmlhttpRequest
// @grant        unsafeWindow
// @connect      localhost
// ==/UserScript==

(function () {
    'use strict';

    // ── Configuration ────────────────────────────────────────────────────────
    //
    // Set to 'http://localhost:7777' to POST directly to the local save server
    // (run tools/save_server.py first).  Leave empty to download via browser.
    const SAVE_SERVER_URL = 'http://localhost:7777';

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Date without year + time, for filenames: MM-DD_HHmmss */
    function timestampNoYear() {
        const d = new Date();
        return [
            String(d.getMonth() + 1).padStart(2, '0'),
            '-',
            String(d.getDate()).padStart(2, '0'),
            '_',
            String(d.getHours()).padStart(2, '0'),
            String(d.getMinutes()).padStart(2, '0'),
            String(d.getSeconds()).padStart(2, '0'),
        ].join('');
    }

    /** Safe fragment for Windows filenames (seed, etc.). */
    function sanitizeFilenamePart(s) {
        return String(s)
            .replace(/[<>:"/\\|?*\x00-\x1f]/g, '_')
            .slice(0, 120);
    }

    /**
     * Reads seed from the IDE "game options" textarea (ng-model api.gameOptions),
     * which contains lines like seed=<value> after a test run.
     */
    function extractSeedFromGameOptions() {
        const ta =
            document.querySelector('textarea.options-text[ng-model="api.gameOptions"]') ||
            document.querySelector('textarea.options-text') ||
            document.querySelector(
                'textarea[placeholder*="Run once to display game variables"]',
            );
        if (!ta || !ta.value) return null;
        const m = String(ta.value).match(/seed\s*=\s*([^\s\n;]+)/i);
        return m ? m[1] : null;
    }

    /**
     * Collect all bot log lines visible in the DOM into a single array.
     *
     * Collects [P0:IN], [P0:OUT], [P1:IN], [P1:OUT] lines in DOM order.
     * Server errors (from .consoleError spans) are inserted as [Px:ERR],
     * attributed to the player whose [OUT] appeared most recently.
     *
     * Deduplicates only within the same DOM text node.
     *
     * Returns string[] — all lines in chronological order.
     */
    function collectLines() {
        const lines = [];

        const walker = document.createTreeWalker(
            document.body,
            NodeFilter.SHOW_TEXT,
            null,
            false,
        );

        const PREFIXES = [
            '[P0:IN] ', '[P0:OUT] ',
            '[P1:IN] ', '[P1:OUT] ',
        ];

        let lastOutPlayer = null;   // 'P0' | 'P1'

        let node;
        while ((node = walker.nextNode())) {
            const text = node.nodeValue || '';

            // Server error messages from consoleError spans.
            const parentEl = node.parentElement;
            if (parentEl && parentEl.closest && parentEl.closest('.consoleError')) {
                if (parentEl.closest('.nickname')) continue;
                const errText = text.trim();
                if (errText.length > 1) {
                    const msg = errText.startsWith(': ') ? errText.slice(2) : errText;
                    if (msg) {
                        const prefix = lastOutPlayer || 'P0';
                        lines.push(`[${prefix}:ERR] ${msg}`);
                    }
                }
                continue;
            }

            const nodeSeen = new Set();
            for (const raw of text.split('\n')) {
                const line = raw.trim();
                if (!PREFIXES.some(p => line.startsWith(p))) continue;
                if (nodeSeen.has(line)) continue;
                nodeSeen.add(line);

                lines.push(line);
                if (line.startsWith('[P0:OUT]')) lastOutPlayer = 'P0';
                else if (line.startsWith('[P1:OUT]')) lastOutPlayer = 'P1';
            }
        }
        return lines;
    }

    function downloadText(content, filename) {
        const blob = new Blob([content], { type: 'text/plain' });
        const url  = URL.createObjectURL(blob);
        const a    = document.createElement('a');
        a.href     = url;
        a.download = filename;
        a.click();
        setTimeout(() => URL.revokeObjectURL(url), 5000);
    }

    function postToServer(content, filename) {
        // Use GM_xmlhttpRequest to bypass the page's Content Security Policy,
        // which blocks fetch() to http://localhost.
        return new Promise((resolve) => {
            GM_xmlhttpRequest({
                method:  'POST',
                url:     SAVE_SERVER_URL,
                headers: { 'Content-Type': 'application/json' },
                data:    JSON.stringify({ filename, content }),
                onload:  (resp) => {
                    if (resp.status >= 200 && resp.status < 300) {
                        resolve(true);
                    } else {
                        console.warn('[CG-export] Save server returned', resp.status);
                        resolve(false);
                    }
                },
                onerror: (e) => {
                    console.warn('[CG-export] Save server unreachable:', e);
                    resolve(false);
                },
                ontimeout: () => {
                    console.warn('[CG-export] Save server timed out.');
                    resolve(false);
                },
            });
        });
    }

    // ── Main button ──────────────────────────────────────────────────────────

    function createButton() {
        const btn = document.createElement('button');
        btn.id          = 'cg-stderr-export-btn';
        btn.textContent = '⬇ Export stderr';
        Object.assign(btn.style, {
            position:     'fixed',
            bottom:       '16px',
            left:         '12%',
            zIndex:       '99999',
            padding:      '8px 14px',
            background:   '#1a1a2e',
            color:        '#79c0ff',
            border:       '1px solid #79c0ff',
            borderRadius: '6px',
            cursor:       'pointer',
            fontSize:     '13px',
            fontFamily:   'Consolas, monospace',
            boxShadow:    '0 2px 8px rgba(0,0,0,0.5)',
        });

        btn.addEventListener('mouseenter', () => {
            btn.style.background = '#16213e';
        });
        btn.addEventListener('mouseleave', () => {
            btn.style.background = '#1a1a2e';
        });

        btn.addEventListener('click', async () => {
            const allLines = collectLines();
            const ts = timestampNoYear();
            const rawSeed = extractSeedFromGameOptions();
            const seedPart = rawSeed ? sanitizeFilenamePart(rawSeed) : 'unknown';

            if (allLines.length === 0) {
                btn.textContent = '⚠ No [P0/P1:IN/OUT] lines found';
                setTimeout(() => { btn.textContent = '⬇ Export stderr'; }, 3000);
                console.warn(
                    '[CG-export] No [Px:IN]/[Px:OUT] lines detected.\n' +
                    'Make sure LOG_IO = True is set in bot.py and the game has finished.',
                );
                return;
            }

            const content  = allLines.join('\n') + '\n';
            const filename = `stderr_${ts}_${seedPart}.txt`;

            if (SAVE_SERVER_URL) {
                const ok = await postToServer(content, filename);
                if (ok) {
                    btn.textContent = `✓ Saved: ${filename}`;
                    setTimeout(() => { btn.textContent = '⬇ Export stderr'; }, 4000);
                    return;
                }
                console.warn('[CG-export] Falling back to browser download.');
            }

            downloadText(content, filename);
            btn.textContent = `✓ ${filename} (${allLines.length} lines)`;
            setTimeout(() => { btn.textContent = '⬇ Export stderr'; }, 3000);
        });

        return btn;
    }

    // ── Inject ───────────────────────────────────────────────────────────────
    // CodinGame is a SPA — wait until the body is ready.

    function inject() {
        if (document.getElementById('cg-stderr-export-btn')) return;
        document.body.appendChild(createButton());
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', inject);
    } else {
        inject();
    }

    // Re-inject after SPA navigation (CodinGame uses History API).
    const _pushState    = history.pushState.bind(history);
    const _replaceState = history.replaceState.bind(history);
    history.pushState = function (...args) {
        _pushState(...args);
        setTimeout(inject, 500);
    };
    history.replaceState = function (...args) {
        _replaceState(...args);
        setTimeout(inject, 500);
    };

})();
