/*
 * mini-spectrum.js
 *
 * Phase 4: a minimal, vanilla replacement for the "spectrum" jQuery color
 * picker plugin, providing the small API surface the app actually uses:
 *   $(el).spectrum(options)     - attach the color picker to el
 *   $(el).spectrum('set', c)    - set the current color
 *   $(el).spectrum('hide')      - close the picker
 */

(function (global) {
    'use strict';

    var doc = global.document;
    var popup = null;
    var currentEl = null;
    var currentOpts = null;
    var currentColor = '#000000';
    var selectedColor = '#000000';

    function buildPopup() {
        popup = doc.createElement('div');
        popup.style.cssText = 'position:fixed;z-index:99999;display:none;background:#fff;color:#222;'
            + 'border:1px solid #ccc;border-radius:8px;padding:8px;box-shadow:0 4px 16px rgba(0,0,0,.25);'
            + 'font-family:inherit;font-size:12px;min-width:200px;';
        doc.body.appendChild(popup);
    }

    function hide() {
        if (popup) { popup.style.display = 'none'; }
        currentEl = null;
    }

    function confirm() {
        if (currentEl && currentOpts) {
            currentColor = selectedColor;
            if (currentOpts.change) {
                currentOpts.change({
                    toHexString: function () { return selectedColor; },
                    toString: function () { return selectedColor; }
                });
            }
        }
        hide();
    }

    function show(el, opts) {
        currentEl = el;
        currentOpts = opts;
        if (typeof opts.color === 'string') { currentColor = opts.color; }
        selectedColor = currentColor;
        if (opts.beforeShow) { opts.beforeShow(); }
        if (!popup) { buildPopup(); }
        render();
        // position the popup near the element
        var r = el.getBoundingClientRect();
        var left = Math.min(r.left, Math.max(4, global.innerWidth - 220));
        var top = r.bottom + 6;
        if (top + 260 > global.innerHeight) { top = Math.max(4, r.top - 260); }
        popup.style.left = left + 'px';
        popup.style.top = top + 'px';
        popup.style.display = 'block';
    }

    function render() {
        var opts = currentOpts;
        var html = '';
        html += '<div style="display:flex;align-items:center;gap:8px;margin-bottom:6px;">'
            + '<span class="rb-spectrum-preview" style="width:34px;height:20px;border:1px solid #aaa;border-radius:4px;background:' + selectedColor + ';"></span>'
            + '<span id="rb-spectrum-value" style="color:#444;">' + selectedColor + '</span>'
            + '</div>';
        var palette = (opts && opts.palette) || [];
        for (var r = 0; r < palette.length; r++) {
            var row = palette[r];
            html += '<div style="display:flex;gap:3px;margin-bottom:3px;">';
            for (var c = 0; c < row.length; c++) {
                html += '<span class="rb-spectrum-swatch" data-c="' + row[c]
                    + '" style="width:20px;height:20px;border:1px solid #ddd;border-radius:3px;background:' + row[c]
                    + ';cursor:pointer;display:inline-block;"></span>';
            }
            html += '</div>';
        }
        if (opts && opts.showButtons) {
            html += '<div style="display:flex;justify-content:flex-end;gap:6px;margin-top:6px;">'
                + '<button id="rb-spectrum-ok" style="cursor:pointer;border:1px solid #aaa;border-radius:4px;background:#f6f6f6;padding:4px 10px;">OK</button>'
                + '<button id="rb-spectrum-cancel" style="cursor:pointer;border:1px solid #aaa;border-radius:4px;background:#f6f6f6;padding:4px 10px;">Cancel</button>'
                + '</div>';
        }
        popup.innerHTML = html;

        var swatches = popup.querySelectorAll('.rb-spectrum-swatch');
        for (var i = 0; i < swatches.length; i++) {
            swatches[i].addEventListener('click', function (e) {
                e.stopPropagation();
                selectedColor = this.getAttribute('data-c');
                var val = popup.querySelector('#rb-spectrum-value');
                if (val) { val.textContent = selectedColor; }
                var preview = popup.querySelector('.rb-spectrum-preview');
                if (preview) { preview.style.background = selectedColor; }
                if (!currentOpts || !currentOpts.showButtons) { confirm(); }
            });
        }
        if (opts && opts.showButtons) {
            var ok = popup.querySelector('#rb-spectrum-ok');
            var cancel = popup.querySelector('#rb-spectrum-cancel');
            if (ok) { ok.addEventListener('click', function (e) { e.stopPropagation(); confirm(); }); }
            if (cancel) { cancel.addEventListener('click', function (e) { e.stopPropagation(); hide(); }); }
        }
    }

    // close on click outside
    doc.addEventListener('click', function (e) {
        if (popup && popup.style.display !== 'none' && !popup.contains(e.target)) {
            if (currentOpts && currentOpts.clickoutFiresChange !== false) {
                confirm();
            } else {
                hide();
            }
        }
    }, false);

    // ---- plugin ------------------------------------------------------
    $.fn.spectrum = function (methodOrOptions) {
        var self = this;
        if (typeof methodOrOptions === 'string') {
            if (methodOrOptions === 'set') {
                if (arguments.length > 1) { currentColor = arguments[1]; }
                return self;
            }
            if (methodOrOptions === 'hide') {
                hide();
                return self;
            }
            return self;
        }
        var opts = methodOrOptions || {};
        self.each(function () {
            var el = this;
            // capture phase: the app may add a stopImmediatePropagation click
            // handler (Android workaround) on the same element, which would
            // otherwise prevent this handler from running
            el.addEventListener('click', function (e) {
                e.stopPropagation();
                show(el, opts);
            }, true);
        });
        return self;
    };

})(window);