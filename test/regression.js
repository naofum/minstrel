//
// Minstrel Windows - end-to-end regression tests
//
// Requires the Java server to be running (see run-tests.ps1) and a system
// browser (Edge or Chrome). Serves as the full smoke test for the web app
// after the Cordova / jQuery Mobile / jQuery removal and the modern UI work.
//
// Usage:
//   node regression.js [baseUrl] [shotsDir]
//   BASE_URL=http://127.0.0.1:8001 node regression.js
//
// Exit code is 0 when all checks pass, 1 otherwise.

'use strict';

const puppeteer = require('puppeteer-core');
const fs = require('fs');
const path = require('path');

const BASE = (process.env.BASE_URL || (process.argv[2] || 'http://127.0.0.1:8001')).replace(/\/+$/, '');
const SHOTS = process.argv[3] || path.join(__dirname, 'shots');

const BROWSERS = [
    'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
    'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
    'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
];
const EXECUTABLE = BROWSERS.find((p) => fs.existsSync(p));
if (!EXECUTABLE) {
    console.error('No supported browser (Edge/Chrome) found.');
    process.exit(1);
}

let passCount = 0;
let failCount = 0;
const failures = [];

function check(name, ok, detail) {
    if (ok) {
        passCount++;
        console.log('  PASS  ' + name + (detail ? '  (' + detail + ')' : ''));
    } else {
        failCount++;
        failures.push(name);
        console.log('  FAIL  ' + name + (detail ? '  (' + detail + ')' : ''));
    }
}

async function main() {
    if (!fs.existsSync(SHOTS)) { fs.mkdirSync(SHOTS, { recursive: true }); }
    const profile = path.join(require('os').tmpdir(), 'rb-test-' + Date.now());

    const browser = await puppeteer.launch({
        executablePath: EXECUTABLE,
        headless: 'new',
        args: ['--no-sandbox', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
            '--autoplay-policy=no-user-gesture-required', '--user-data-dir=' + profile],
    });

    const page = await browser.newPage();
    await page.setViewport({ width: 390, height: 844 });
    page.setDefaultTimeout(20000);

    const consoleErrors = [];
    const notFound = [];
    page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text()); });
    page.on('pageerror', (e) => consoleErrors.push(e.message));
    page.on('dialog', (d) => d.dismiss().catch(() => {}));
    page.on('response', (r) => { if (r.status() >= 400) notFound.push(r.status() + ' ' + r.url()); });

    const safe = (fn, ...args) => page.evaluate(fn, ...args).catch((e) => ({ __err: '' + e }));

    const openLibrary = async () => {
        // cache-busting URL forces a real server load (no back-forward cache)
        await page.goto(BASE + '/index.html?t=' + Date.now(), { waitUntil: 'networkidle2' });
        await new Promise((r) => setTimeout(r, 1500));
        await page.evaluate(() => {
            localStorage.setItem('app_plugins_loaded_key', JSON.stringify({ epub: true, cbz: true, abz: true }));
            localStorage.setItem('app_plugins_loaded_changed_key', 'true');
        });
        await page.reload({ waitUntil: 'networkidle2' });
        await new Promise((r) => setTimeout(r, 1500));
        // wait for the library to populate (auto-scan or refresh);
        // note: #lstItems also contains the filter row, so exclude it
        for (let i = 0; i < 15; i++) {
            const n = await safe(() => document.querySelectorAll('#lstItems li:not(.ui-listview-filter)').length);
            if (typeof n === 'number' && n > 0) { break; }
            await safe(() => { const b = document.getElementById('btnRefresh'); if (b) b.click(); });
            await new Promise((r) => setTimeout(r, 1500));
        }
        return page.evaluate(() => Array.from(document.querySelectorAll('#lstItems li:not(.ui-listview-filter)'))
            .map((li) => li.textContent.replace(/\s+/g, ' ').trim()));
    };

    const openBook = async (needle) => {
        await openLibrary();
        // wait for the target book to be rendered, then click it
        for (let i = 0; i < 15; i++) {
            const found = await safe((needle) => {
                let f = false;
                document.querySelectorAll('#lstItems li').forEach((li) => {
                    if (li.textContent.indexOf(needle) >= 0) f = true;
                });
                return f;
            }, needle);
            if (found) { break; }
            await new Promise((r) => setTimeout(r, 1000));
        }
        await safe((needle) => {
            document.querySelectorAll('#lstItems li').forEach((li) => {
                if (li.textContent.indexOf(needle) >= 0) { const a = li.querySelector('a[onclick]'); if (a) a.click(); }
            });
        }, needle);
        await new Promise((r) => setTimeout(r, 5000));
    };

    console.log('== Regression: ' + BASE + ' (' + EXECUTABLE + ') ==');
    try {

        // ---- library -------------------------------------------------
        console.log('[library]');
        const library = await openLibrary();
        check('library has items', library.length >= 1, library.length + ' item(s)');
        check('library has EPUB', library.some((t) => t.indexOf('Menestrello') >= 0 || t.indexOf('Guide') >= 0));
        check('library has CBZ', library.some((t) => t.indexOf('Comic') >= 0));
        check('library has ABZ', library.some((t) => t.indexOf('Audiobook') >= 0));

        // filter search box
        const filter = await safe(() => {
            const input = document.querySelector('#lstItems li.ui-listview-filter input');
            if (!input) return { exists: false };
            const setV = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
            setV.call(input, 'Comic');
            input.dispatchEvent(new Event('input', { bubbles: true }));
            const visible = Array.from(document.querySelectorAll('#lstItems li')).filter((li) => getComputedStyle(li).display !== 'none').length;
            return { exists: true, visibleAfterFilter: visible };
        });
        check('search box exists', filter.exists);
        check('search filters items', filter.exists && filter.visibleAfterFilter >= 1 && filter.visibleAfterFilter < library.length, 'visible=' + filter.visibleAfterFilter);

        // ---- footer / split icons ------------------------------------
        console.log('[icons]');
        const icons = await safe(() => {
            const glyph = (id) => { const el = document.getElementById(id); if (!el) return null; const i = el.querySelector('.ui-icon'); return i ? getComputedStyle(i, '::before').content : null; };
            const li = document.querySelector('#lstItems li:not(.ui-listview-filter)');
            const split = li ? li.querySelectorAll(':scope > a')[1] : null;
            return {
                refresh: glyph('btnRefresh'),
                settings: glyph('btnSettings'),
                sort: glyph('btnSort'),
                help: glyph('btnHelp'),
                split: split && split.querySelector('.ui-icon') ? getComputedStyle(split.querySelector('.ui-icon'), '::before').content : null,
            };
        });
        check('footer icons render', !!icons.refresh && !!icons.settings && !!icons.sort && !!icons.help);
        check('split button icon renders', !!icons.split);
        await page.screenshot({ path: SHOTS + '/01-library.png' });

        // ---- settings -------------------------------------------------
        console.log('[settings]');
        await page.goto(BASE + '/settings.html', { waitUntil: 'networkidle2' });
        await new Promise((r) => setTimeout(r, 2500));
        const settings = await safe(() => {
            const coll = document.getElementById('colSettingsScreen');
            const heading = coll ? coll.querySelector('.ui-collapsible-heading .ui-btn .ui-btn-inner .ui-btn-text') : null;
            const cb = document.querySelector('#inpchkToggleShowHiddenBooks');
            return {
                collapsible: !!coll,
                collapsed: coll ? coll.classList.contains('ui-collapsible-collapsed') : null,
                headingText: heading ? heading.textContent : null,
                checkboxEnhanced: cb ? !!cb.closest('.ui-checkbox') : false,
                activePage: document.querySelector('.ui-page-active') ? document.querySelector('.ui-page-active').id : null,
            };
        });
        check('settings collapsible found', settings.collapsible && settings.collapsed === true);
        check('settings heading localized (ja)', settings.headingText && settings.headingText.length > 0 && settings.headingText !== 'Screen', settings.headingText);
        check('settings checkbox enhanced', settings.checkboxEnhanced);
        check('settings page active', settings.activePage === 'settings');

        // toggle collapsible
        await safe(() => { const h = document.querySelector('#colSettingsScreen h3'); if (h) h.click(); });
        await new Promise((r) => setTimeout(r, 500));
        const expanded = await safe(() => {
            const coll = document.getElementById('colSettingsScreen');
            return coll ? !coll.classList.contains('ui-collapsible-collapsed') : false;
        });
        check('collapsible expands', expanded);
        await page.screenshot({ path: SHOTS + '/02-settings.png' });

        // footer back button navigates back
        const backBtn = await safe(() => {
            const btn = document.getElementById('btnSettingsBack');
            const b = btn ? btn.getBoundingClientRect() : null;
            return b ? { x: b.x + b.width / 2, y: b.y + b.height / 2, w: b.width, h: b.height } : null;
        });
        check('settings back button clickable', backBtn && backBtn.h >= 30, backBtn ? 'h=' + Math.round(backBtn.h) : 'missing');
        if (backBtn) {
            await page.mouse.click(backBtn.x, backBtn.y);
            await new Promise((r) => setTimeout(r, 2000));
            check('settings back navigates to library', page.url().indexOf('index.html') >= 0, page.url());
        }

        // ---- epub reader ----------------------------------------------
        console.log('[epub reader]');
        await openBook('Menestrello');
        const epub = await safe(() => ({
            url: location.pathname,
            slider: !!document.querySelector('.ui-slider-track'),
            activePage: document.querySelector('.ui-page-active') ? document.querySelector('.ui-page-active').id : null,
            text: document.body.innerText.replace(/\s+/g, ' ').trim().substring(0, 60),
        }));
        check('epub reader opens', epub.url.indexOf('epub.reader') >= 0);
        check('epub content renders', epub.text.length > 0);
        check('epub reader active page', epub.activePage === 'divReader');
        check('epub slider present', epub.slider);
        await page.screenshot({ path: SHOTS + '/03-epub-reader.png' });

        // quickbar icons + lock toggle
        await safe(() => { RB.UI.show('divFooterAll'); });
        await new Promise((r) => setTimeout(r, 300));
        const quickbar = await safe(() => {
            const g = (id) => { const el = document.getElementById(id); if (!el) return null; const i = el.querySelector('.ui-icon'); return i ? getComputedStyle(i, '::before').content : null; };
            const before = (() => { const i = document.querySelector('#btnQuickBar1 .ui-icon'); return i ? i.className : null; })();
            const btn = document.getElementById('btnQuickBar1');
            if (btn) btn.click();
            const after = (() => { const i = document.querySelector('#btnQuickBar1 .ui-icon'); return i ? i.className : null; })();
            return {
                lock: g('btnQuickBar1'),
                backward: g('btnQuickBar2'),
                listol: g('btnQuickBar3'),
                forward: g('btnQuickBar4'),
                reorder: g('btnQuickBar5'),
                lockIconBefore: before,
                lockIconAfter: after,
            };
        });
        check('quickbar 5 icons render', quickbar && quickbar.lock && quickbar.backward && quickbar.listol && quickbar.forward && quickbar.reorder);
        check('lock button toggles icon', quickbar && quickbar.lockIconBefore !== quickbar.lockIconAfter, quickbar.lockIconBefore + ' -> ' + quickbar.lockIconAfter);

        // ---- cbz ------------------------------------------------------
        console.log('[cbz reader]');
        await openBook('Sample Comic');
        const cbz = await safe(() => {
            const img = document.querySelector('#divPanzoomInner img');
            return { found: !!img, w: img ? img.naturalWidth : 0 };
        });
        check('cbz image loads', cbz.found && cbz.w > 0, 'w=' + cbz.w);
        await page.screenshot({ path: SHOTS + '/04-cbz.png' });

        // ---- abz ------------------------------------------------------
        console.log('[abz reader]');
        await openBook('Sample Audiobook');
        const abz = await safe(() => document.querySelectorAll('#lstTracks li').length);
        check('abz tracks listed', abz >= 2, abz + ' tracks');
        await page.screenshot({ path: SHOTS + '/05-abz.png' });

        // ---- color picker (spectrum) ----------------------------------
        console.log('[color picker]');
        await page.goto(BASE + '/epub.settings.html', { waitUntil: 'networkidle2' });
        await new Promise((r) => setTimeout(r, 2500));
        await safe(() => { const b = document.getElementById('optPreferencesDialogBackColor'); if (b) b.click(); });
        await new Promise((r) => setTimeout(r, 400));
        const spectrum = await safe(() => ({
            open: !!document.querySelector('.rb-spectrum-swatch'),
            swatches: document.querySelectorAll('.rb-spectrum-swatch').length,
            hasOk: !!document.getElementById('rb-spectrum-ok'),
        }));
        check('color picker opens', spectrum.open && spectrum.swatches > 0, spectrum.swatches + ' swatches');
        check('color picker OK button', spectrum.hasOk);
        if (spectrum.open) {
            await safe(() => { const sw = document.querySelector('.rb-spectrum-swatch[data-c="#ff0000"]'); if (sw) sw.click(); });
            await safe(() => { const ok = document.getElementById('rb-spectrum-ok'); if (ok) ok.click(); });
            await new Promise((r) => setTimeout(r, 300));
            const closed = await safe(() => { const v = document.getElementById('rb-spectrum-value'); return v ? getComputedStyle(v.parentElement.parentElement).display : null; });
            check('color picker closes after OK', closed === 'none', closed);
        }
        await page.screenshot({ path: SHOTS + '/06-settings.png' });

    } catch (e) {
        console.error('FATAL: ' + e);
        failCount++;
        failures.push('fatal: ' + e.message);
    }

    const consoleErrorsFiltered = consoleErrors.filter((e) => e.indexOf('encryption.xml') === -1);
    check('no console/page errors', consoleErrorsFiltered.length === 0, consoleErrorsFiltered.length + ' error(s)');
    if (consoleErrorsFiltered.length) {
        console.log('Console errors:');
        consoleErrorsFiltered.slice(0, 15).forEach((e) => console.log('  - ' + e.substring(0, 200)));
    }
    const unique404 = Array.from(new Set(notFound));
    if (unique404.length) {
        console.log('HTTP 4xx:');
        unique404.slice(0, 15).forEach((u) => console.log('  - ' + u));
    }

    console.log('');
    console.log('==========================================');
    console.log('Result: ' + passCount + ' passed, ' + failCount + ' failed');
    if (failures.length) {
        console.log('Failed: ' + failures.join(', '));
    }
    console.log('Screenshots: ' + SHOTS);

    await browser.close().catch(() => {});
    process.exit(failCount ? 1 : 0);
}

main().catch((e) => { console.error(e); process.exit(1); });