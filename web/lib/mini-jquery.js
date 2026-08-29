/*
 * mini-jquery.js
 *
 * Phase 4: a small, self-contained, jQuery-compatible core that covers exactly
 * the jQuery API subset used by the Minstrel app (rb.*.js), the jQuery Mobile
 * shim (lib/jquery-mobile-shim.js) and the inline HTML scripts. It replaces
 * the jQuery 3.7.1 dependency without changing any application code.
 */

(function (global) {
    'use strict';

    var doc = global.document;

    // ----------------------------------------------------------------
    // data store (jQuery .data)
    // ----------------------------------------------------------------
    var dataStore = new WeakMap();

    function dataGet(el, key) {
        var m = dataStore.get(el);
        return m ? m[key] : undefined;
    }
    function dataSet(el, key, value) {
        var m = dataStore.get(el);
        if (!m) { m = {}; dataStore.set(el, m); }
        m[key] = value;
    }

    // ----------------------------------------------------------------
    // collection
    // ----------------------------------------------------------------
    function J(elems) {
        this.length = elems ? elems.length : 0;
        for (var i = 0; i < this.length; i++) { this[i] = elems[i]; }
    }

    function $(arg, ctx) {
        var elems = [];
        if (arg == null) {
            elems = [];
        } else if (typeof arg === 'function') {
            ready(arg);
            return new J([]);
        } else if (typeof arg === 'string') {
            var s = arg.trim();
            if (s.charAt(0) === '<' && s.charAt(s.length - 1) === '>') {
                elems = [htmlToNode(s)];
            } else {
                elems = Array.prototype.slice.call((ctx || doc).querySelectorAll(s));
            }
        } else if (arg.nodeType || arg === doc || arg === global || arg === global.window) {
            elems = [arg];
        } else if (arg instanceof J) {
            return arg;
        } else if (typeof arg.length === 'number') {
            elems = Array.prototype.slice.call(arg);
        }
        return new J(elems);
    }

    // ----------------------------------------------------------------
    // html creation
    // ----------------------------------------------------------------
    function htmlToNode(html) {
        var tpl = doc.createElement('template');
        tpl.innerHTML = html;
        var frag = tpl.content;
        if (frag.childNodes.length === 1) {
            return frag.firstChild;
        }
        return frag;
    }

    // ----------------------------------------------------------------
    // ready
    // ----------------------------------------------------------------
    var readyList = [];
    var readyFired = false;
    function ready(fn) {
        if (readyFired) { fn.call(doc); return; }
        readyList.push(fn);
        if (doc.readyState === 'complete' || doc.readyState === 'interactive') {
            setTimeout(fireReady, 0);
        } else {
            doc.addEventListener('DOMContentLoaded', fireReady, false);
            if (doc.readyState === 'loading') {
                global.addEventListener('load', fireReady, false);
            }
        }
    }
    function fireReady() {
        if (readyFired) { return; }
        readyFired = true;
        var list = readyList.slice();
        readyList = [];
        for (var i = 0; i < list.length; i++) { list[i].call(doc); }
    }

    // ----------------------------------------------------------------
    // helpers
    // ----------------------------------------------------------------
    function isNode(o) { return o && o.nodeType === 1; }
    function camel(prop) { return prop.replace(/-([a-z])/g, function (m, c) { return c.toUpperCase(); }); }

    var LENGTH_PROPS = /width|height|left|right|top|bottom|margin|padding|font-size|line-height|border-/;

    // ----------------------------------------------------------------
    // events
    // ----------------------------------------------------------------
    J.prototype.on = function (events, selector, handler) {
        if (typeof events === 'object' && events !== null) {
            for (var ev in events) {
                if (events.hasOwnProperty(ev)) { this.on(ev, selector, events[ev]); }
            }
            return this;
        }
        if (typeof selector === 'function') { handler = selector; selector = null; }
        if (!handler) { return this; }
        var evts = events.split(/\s+/);
        var self = this;
        for (var i = 0; i < this.length; i++) {
            (function (el) {
                for (var j = 0; j < evts.length; j++) {
                    var name = evts[j].split('.')[0];
                    if (!name) { continue; }
                    var real = handler;
                    if (selector) {
                        real = function (e) {
                            var t = e.target;
                            while (t && t !== el && !(t.nodeType === 1 && t.matches(selector))) {
                                t = t.parentNode;
                            }
                            if (t && t.nodeType === 1) { handler.call(t, e); }
                        };
                    }
                    el.addEventListener(name, real, false);
                    if (!el.__rbHandlers) { el.__rbHandlers = []; }
                    el.__rbHandlers.push({ name: name, real: real, orig: handler });
                }
            })(this[i]);
        }
        return this;
    };

    J.prototype.off = function (events, selector, handler) {
        var evts = events ? events.split(/\s+/) : null;
        for (var i = 0; i < this.length; i++) {
            var el = this[i];
            if (!el.__rbHandlers) { continue; }
            var remaining = [];
            for (var j = 0; j < el.__rbHandlers.length; j++) {
                var h = el.__rbHandlers[j];
                var remove = true;
                if (evts) {
                    remove = false;
                    for (var k = 0; k < evts.length; k++) {
                        var n = evts[k].split('.')[0];
                        if (!n || h.name === n) {
                            if (!handler || h.orig === handler) { remove = true; break; }
                        }
                    }
                }
                if (remove) {
                    el.removeEventListener(h.name, h.real, false);
                } else {
                    remaining.push(h);
                }
            }
            el.__rbHandlers = remaining;
        }
        return this;
    };

    J.prototype.trigger = function (events) {
        var evts = events.split(/\s+/);
        for (var i = 0; i < this.length; i++) {
            var el = this[i];
            for (var j = 0; j < evts.length; j++) {
                var name = evts[j].split('.')[0];
                if (!name) { continue; }
                el.dispatchEvent(new global.CustomEvent(name, { bubbles: true, cancelable: true }));
            }
        }
        return this;
    };

    // ----------------------------------------------------------------
    // traversal
    // ----------------------------------------------------------------
    J.prototype.find = function (sel) {
        var out = [];
        for (var i = 0; i < this.length; i++) {
            out = out.concat(Array.prototype.slice.call(this[i].querySelectorAll(sel)));
        }
        return new J(out);
    };

    J.prototype.children = function (sel) {
        var out = [];
        for (var i = 0; i < this.length; i++) {
            var kids = Array.prototype.slice.call(this[i].children);
            if (sel) { kids = kids.filter(function (k) { return k.matches(sel); }); }
            out = out.concat(kids);
        }
        return new J(out);
    };

    J.prototype.parent = function () {
        var out = [];
        for (var i = 0; i < this.length; i++) {
            var p = this[i].parentNode;
            if (p && p.nodeType === 1 && out.indexOf(p) === -1) { out.push(p); }
        }
        return new J(out);
    };

    J.prototype.closest = function (sel) {
        var out = [];
        for (var i = 0; i < this.length; i++) {
            var c = this[i].closest(sel);
            if (c && out.indexOf(c) === -1) { out.push(c); }
        }
        return new J(out);
    };

    J.prototype.first = function () { return new J(this.length ? [this[0]] : []); };
    J.prototype.last = function () { return new J(this.length ? [this[this.length - 1]] : []); };

    J.prototype.not = function (sel) {
        var out = [];
        for (var i = 0; i < this.length; i++) {
            if (!this[i].matches(sel)) { out.push(this[i]); }
        }
        return new J(out);
    };

    J.prototype.is = function (sel) {
        for (var i = 0; i < this.length; i++) {
            var el = this[i];
            if (sel === ':visible') {
                var cs = getComputedStyle(el);
                if (cs.display !== 'none' && cs.visibility !== 'hidden' && el.offsetWidth > 0) { return true; }
            } else if (el.matches(sel)) {
                return true;
            }
        }
        return false;
    };

    J.prototype.each = function (fn) {
        for (var i = 0; i < this.length; i++) {
            fn.call(this[i], i, this[i]);
        }
        return this;
    };

    J.prototype.get = function (idx) {
        if (idx == null) { return Array.prototype.slice.call(this); }
        return this[idx];
    };

    // ----------------------------------------------------------------
    // classes
    // ----------------------------------------------------------------
    J.prototype.addClass = function (cls) {
        var names = cls.split(/\s+/);
        for (var i = 0; i < this.length; i++) {
            for (var j = 0; j < names.length; j++) {
                if (names[j]) { this[i].classList.add(names[j]); }
            }
        }
        return this;
    };

    J.prototype.removeClass = function (cls) {
        if (cls === undefined) {
            for (var i = 0; i < this.length; i++) { this[i].className = ''; }
            return this;
        }
        var names = cls.split(/\s+/);
        for (var i = 0; i < this.length; i++) {
            for (var j = 0; j < names.length; j++) {
                if (names[j]) { this[i].classList.remove(names[j]); }
            }
        }
        return this;
    };

    J.prototype.hasClass = function (cls) {
        for (var i = 0; i < this.length; i++) {
            if (this[i].classList.contains(cls)) { return true; }
        }
        return false;
    };

    // ----------------------------------------------------------------
    // attributes / properties / values
    // ----------------------------------------------------------------
    J.prototype.attr = function (name, value) {
        if (typeof name === 'object') {
            for (var k in name) { if (name.hasOwnProperty(k)) { this.attr(k, name[k]); } }
            return this;
        }
        if (value === undefined) {
            return this.length ? this[0].getAttribute(name) : undefined;
        }
        for (var i = 0; i < this.length; i++) { this[i].setAttribute(name, value); }
        return this;
    };

    J.prototype.removeAttr = function (name) {
        for (var i = 0; i < this.length; i++) { this[i].removeAttribute(name); }
        return this;
    };

    J.prototype.prop = function (name, value) {
        if (value === undefined) { return this.length ? this[0][name] : undefined; }
        for (var i = 0; i < this.length; i++) { this[i][name] = value; }
        return this;
    };

    J.prototype.val = function (value) {
        if (value === undefined) { return this.length ? this[0].value : undefined; }
        for (var i = 0; i < this.length; i++) { this[i].value = value; }
        return this;
    };

    // ----------------------------------------------------------------
    // DOM manipulation
    // ----------------------------------------------------------------
    J.prototype.append = function (content) {
        for (var i = 0; i < this.length; i++) {
            appendContent(this[i], content);
        }
        return this;
    };

    J.prototype.appendTo = function (target) {
        var $t = $(target);
        for (var i = 0; i < this.length; i++) {
            for (var j = 0; j < $t.length; j++) {
                $t[j].appendChild(this[i]);
            }
        }
        return this;
    };

    J.prototype.prepend = function (content) {
        for (var i = 0; i < this.length; i++) {
            prependContent(this[i], content);
        }
        return this;
    };

    J.prototype.empty = function () {
        for (var i = 0; i < this.length; i++) { this[i].innerHTML = ''; }
        return this;
    };

    J.prototype.html = function (v) {
        if (v === undefined) { return this.length ? this[0].innerHTML : undefined; }
        for (var i = 0; i < this.length; i++) { this[i].innerHTML = v; }
        return this;
    };

    J.prototype.text = function (v) {
        if (v === undefined) { return this.length ? this[0].textContent : undefined; }
        for (var i = 0; i < this.length; i++) { this[i].textContent = v; }
        return this;
    };

    J.prototype.wrapInner = function (html) {
        var wrapper = htmlToNode(html);
        for (var i = 0; i < this.length; i++) {
            var el = this[i];
            var w = wrapper.cloneNode(true);
            while (el.firstChild) { w.appendChild(el.firstChild); }
            el.appendChild(w);
        }
        return this;
    };

    J.prototype.remove = function () {
        for (var i = 0; i < this.length; i++) {
            var el = this[i];
            if (el.parentNode) { el.parentNode.removeChild(el); }
        }
        return this;
    };

    function appendContent(el, content) {
        if (typeof content === 'string') {
            el.insertAdjacentHTML('beforeend', content);
        } else if (content instanceof J) {
            for (var i = 0; i < content.length; i++) { el.appendChild(content[i]); }
        } else if (content && content.nodeType) {
            el.appendChild(content);
        }
    }
    function prependContent(el, content) {
        if (typeof content === 'string') {
            el.insertAdjacentHTML('afterbegin', content);
        } else if (content instanceof J) {
            for (var i = 0; i < content.length; i++) { el.insertBefore(content[i], el.firstChild); }
        } else if (content && content.nodeType) {
            el.insertBefore(content, el.firstChild);
        }
    }

    // ----------------------------------------------------------------
    // CSS
    // ----------------------------------------------------------------
    J.prototype.css = function (name, value) {
        if (typeof name === 'object') {
            for (var k in name) { if (name.hasOwnProperty(k)) { this.css(k, name[k]); } }
            return this;
        }
        var prop = camel(name);
        if (value === undefined) {
            return this.length ? getComputedStyle(this[0])[prop] : undefined;
        }
        for (var i = 0; i < this.length; i++) {
            var v = value;
            if (typeof v === 'number' && LENGTH_PROPS.test(name)) { v = v + 'px'; }
            this[i].style[prop] = v;
        }
        return this;
    };

    J.prototype.hide = function () {
        for (var i = 0; i < this.length; i++) {
            var el = this[i];
            if (getComputedStyle(el).display !== 'none') {
                dataSet(el, '__display', el.style.display);
                el.style.display = 'none';
            }
        }
        return this;
    };

    J.prototype.show = function () {
        for (var i = 0; i < this.length; i++) {
            var el = this[i];
            if (getComputedStyle(el).display === 'none') {
                el.style.display = dataGet(el, '__display') || '';
                if (getComputedStyle(el).display === 'none') { el.style.display = 'block'; }
            }
        }
        return this;
    };

    J.prototype.toggle = function () {
        for (var i = 0; i < this.length; i++) {
            if (getComputedStyle(this[i]).display === 'none') { this.show(); }
            else { this.hide(); }
        }
        return this;
    };

    J.prototype.fadeOut = function (duration, callback) {
        var self = this;
        var delay = this.__delay || 0;
        this.__delay = 0;
        duration = duration || 400;
        var done = function () {
            for (var i = 0; i < self.length; i++) {
                var el = self[i];
                dataSet(el, '__display', el.style.display);
                el.style.display = 'none';
            }
            if (callback) {
                for (var j = 0; j < self.length; j++) { callback.call(self[j]); }
            }
        };
        if (delay > 0) {
            setTimeout(function () { fade(self, duration, done); }, delay);
        } else {
            fade(self, duration, done);
        }
        return this;
    };

    function fade(self, duration, done) {
        var start = null;
        var step = function (ts) {
            if (!start) { start = ts; }
            var p = Math.min(1, (ts - start) / duration);
            for (var i = 0; i < self.length; i++) {
                self[i].style.opacity = String(1 - p);
            }
            if (p < 1) {
                global.requestAnimationFrame(step);
            } else {
                done();
            }
        };
        global.requestAnimationFrame(step);
    }

    J.prototype.delay = function (ms) {
        this.__delay = (this.__delay || 0) + ms;
        return this;
    };

    J.prototype.offset = function () {
        if (!this.length) { return null; }
        var el = this[0];
        if (el.nodeType !== 1) { return { top: 0, left: 0 }; }
        var r = el.getBoundingClientRect();
        return {
            top: r.top + (global.pageYOffset || doc.documentElement.scrollTop),
            left: r.left + (global.pageXOffset || doc.documentElement.scrollLeft)
        };
    };

    J.prototype.width = function () {
        if (!this.length) { return undefined; }
        var el = this[0];
        if (el === global) { return global.innerWidth; }
        if (el.nodeType === 9) { return el.documentElement.clientWidth; }
        return el.getBoundingClientRect().width;
    };

    J.prototype.height = function () {
        if (!this.length) { return undefined; }
        var el = this[0];
        if (el === global) { return global.innerHeight; }
        if (el.nodeType === 9) { return el.documentElement.clientHeight; }
        return el.getBoundingClientRect().height;
    };

    J.prototype.scrollTop = function (v) {
        if (v === undefined) { return this.length ? this[0].scrollTop : 0; }
        for (var i = 0; i < this.length; i++) { this[i].scrollTop = v; }
        return this;
    };

    J.prototype.scrollTo = function (target, options) {
        options = options || {};
        var axis = options.axis || 'y';
        var duration = options.duration || 0;
        var offset = options.offset || 0;
        var isY = (axis === 'y');
        for (var i = 0; i < this.length; i++) {
            var el = this[i];
            var targetPos = computeScrollTarget(el, target, isY, offset);
            if (duration > 0) {
                animateScroll(el, isY, targetPos, duration);
            } else {
                if (isY) { el.scrollTop = targetPos; } else { el.scrollLeft = targetPos; }
            }
        }
        return this;
    };

    function computeScrollTarget(el, target, isY, offset) {
        if (el.nodeType !== 1) { return 0; }
        if (typeof target === 'number') {
            return Math.max(0, target + offset);
        }
        if (typeof target === 'string' && /^[+-]=/.test(target)) {
            var cur = isY ? el.scrollTop : el.scrollLeft;
            var m = /([+-]=)\s*([\d.]+)/.exec(target);
            var delta = m ? parseFloat(m[2]) * (m[1] === '-=' ? -1 : 1) : 0;
            return Math.max(0, cur + delta);
        }
        var t = null;
        if (typeof target === 'string') {
            t = el.querySelector(target);
        } else if (target instanceof J && target.length) {
            t = target[0];
        } else if (target && target.nodeType) {
            t = target;
        }
        if (!t) { return isY ? el.scrollTop : el.scrollLeft; }
        var cRect = el.getBoundingClientRect();
        var tRect = t.getBoundingClientRect();
        var pos = (isY ? el.scrollTop : el.scrollLeft) + (isY ? tRect.top - cRect.top : tRect.left - cRect.left) - offset;
        return Math.max(0, pos);
    }

    function animateScroll(el, isY, target, duration) {
        var startTime = null;
        var from = isY ? el.scrollTop : el.scrollLeft;
        var delta = target - from;
        var step = function (ts) {
            if (!startTime) { startTime = ts; }
            var p = Math.min(1, (ts - startTime) / duration);
            p = p < 0.5 ? 2 * p * p : -1 + (4 - 2 * p) * p; // ease
            if (isY) { el.scrollTop = from + delta * p; } else { el.scrollLeft = from + delta * p; }
            if (p < 1) { global.requestAnimationFrame(step); }
        };
        global.requestAnimationFrame(step);
    }

    // ----------------------------------------------------------------
    // data
    // ----------------------------------------------------------------
    J.prototype.data = function (name, value) {
        if (value === undefined) {
            return this.length ? dataGet(this[0], name) : undefined;
        }
        for (var i = 0; i < this.length; i++) { dataSet(this[i], name, value); }
        return this;
    };

    J.prototype.removeData = function (name) {
        for (var i = 0; i < this.length; i++) {
            var m = dataStore.get(this[i]);
            if (m) { delete m[name]; }
        }
        return this;
    };

    // ----------------------------------------------------------------
    // ready shorthand on the collection
    // ----------------------------------------------------------------
    J.prototype.ready = function (fn) {
        ready(fn);
        return this;
    };

    // ----------------------------------------------------------------
    // statics
    // ----------------------------------------------------------------
    $.fn = J.prototype;
    $.mobile = {};

    // export
    global.$ = $;
    global.jQuery = $;
    global.miniJQuery = true;

})(window);