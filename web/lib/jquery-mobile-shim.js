//
// jQuery Mobile replacement shim
//
// Implements the subset of the jQuery Mobile 1.3.2 API actually used by
// Minstrel, so the (deprecated) jquery.mobile JS dependency can be dropped.
// The retained jQuery Mobile CSS (css/nd/*) keeps providing the theme.
//
// Covered API:
//   $.mobile.pageContainer / buttonMarkup / defaultDialogTransition /
//   defaultPageTransition / loader / loading() / changePage() / silentScroll()
//   $.fn.listview() / checkboxradio() / slider() / dialog() / popup()
//   events: mobileinit, pagebeforeshow, pagehide, popupafterclose,
//           orientationchange (mapped to window resize)
//   data-role enhancement: page, dialog, header, footer, content, fieldcontain,
//   button, controlgroup, navbar, listview, collapsible, popup, checkbox/radio
//

(function ($) {
    'use strict';

    // --- html.ui-mobile is required by the retained CSS -------------------
    var htmlEl = document.documentElement;
    if (!htmlEl.className.match(/(?:^|\s)ui-mobile(?:\s|$)/)) {
        htmlEl.className += ' ui-mobile';
    }

    // --- $.mobile namespace ------------------------------------------------
    var mobile = $.mobile = {
        pageContainer: document.body,
        defaultPageTransition: 'none',
        defaultDialogTransition: 'none',
        buttonMarkup: { hoverDelay: 0 },
        loader: {
            prototype: {
                options: {
                    html: '',
                    theme: 'b'
                }
            }
        },
        loading: function (state) {
            var s = getSpinner();
            if (state === 'show') {
                s.innerHTML = mobile.loader.prototype.options.html || '<span>Loading...</span>';
                s.style.display = 'block';
            } else {
                s.style.display = 'none';
            }
        },
        changePage: function (selector, options) {
            var $t = $(selector);
            if (!$t.length) {
                return false;
            }
            var el = $t[0];
            enhance(el);
            showPage(el, options && options.role === 'dialog');
            return false;
        },
        silentScroll: function (y) {
            window.scrollTo(0, y || 0);
        }
    };

    function getSpinner() {
        var el = document.getElementById('rb-loading');
        if (!el) {
            el = document.createElement('div');
            el.id = 'rb-loading';
            el.className = 'ui-loader ui-loader-textonly ui-corner-all';
            el.style.cssText = 'position:fixed;top:50%;left:50%;z-index:99999;' +
                'transform:translate(-50%,-50%);display:none;';
            document.body.appendChild(el);
        }
        return el;
    }

    // --- page management -----------------------------------------------------
    var activePage = null;
    var pageStack = [];

    function ensurePage(el) {
        var $el = $(el);
        if (!$el.hasClass('ui-page')) {
            $el.addClass('ui-page');
        }
        if ($el.attr('data-role') === 'dialog') {
            $el.addClass('ui-dialog');
        }
        var theme = $el.attr('data-theme') || 'b';
        $el.addClass('ui-page-' + theme);
        return $el;
    }

    function showPage(el, isDialog) {
        el = ensurePage(el);
        if (activePage && activePage !== el) {
            $(activePage).removeClass('ui-page-active');
            $(activePage).trigger('pagehide');
            pageStack.push(activePage);
        }
        el.addClass('ui-page-active');
        activePage = el;
        $(el).trigger('pagebeforeshow');
        $(el).trigger('pageshow');
    }

    function closePage(el) {
        var $el = $(el);
        $el.removeClass('ui-page-active');
        $el.trigger('pagehide');
        if (pageStack.length > 0) {
            var prev = pageStack.pop();
            $(prev).addClass('ui-page-active');
            activePage = prev;
            $(prev).trigger('pageshow');
        } else {
            activePage = null;
        }
    }

    // --- $.fn widget methods -------------------------------------------------

    $.fn.listview = function (action) {
        return this.each(function () {
            enhanceListview(this);
        });
    };

    $.fn.buttonMarkup = function (options) {
        return this.each(function () {
            enhanceButton(this);
            if (options && typeof options.icon === 'string') {
                var $el = $(this);
                var $icon = $el.find('.ui-icon').first();
                if ($icon.length) {
                    $icon.attr('class', 'ui-icon ui-icon-' + options.icon + ' ui-icon-shadow');
                } else {
                    var span = document.createElement('span');
                    span.className = 'ui-icon ui-icon-' + options.icon + ' ui-icon-shadow';
                    span.innerHTML = '&nbsp;';
                    var inner = $el.find('.ui-btn-inner');
                    if (inner.length) {
                        inner.prepend(span);
                    } else {
                        this.appendChild(span);
                    }
                }
            }
        });
    };

    $.fn.checkboxradio = function (action) {
        return this.each(function () {
            if (action === 'refresh') {
                syncCheckbox(this);
            } else {
                enhanceCheckbox(this);
            }
        });
    };

    $.fn.slider = function (options) {
        return this.each(function () {
            var input = this;
            if (typeof options === 'string' && options === 'refresh') {
                syncSlider(input);
            } else {
                buildSlider(input, (options && typeof options === 'object') ? options : null);
            }
        });
    };

    $.fn.dialog = function (action) {
        return this.each(function () {
            var el = this;
            if (action === 'close') {
                if ($(el).hasClass('ui-page') && $(el).hasClass('ui-page-active')) {
                    closePage(el);
                } else {
                    el.style.display = 'none';
                    $(el).trigger('pagehide');
                }
            }
        });
    };

    $.fn.popup = function (action, opts) {
        return this.each(function () {
            var el = this;
            enhance(el);
            if (action === 'open') {
                el.classList.add('ui-popup-active');
                el.style.cssText = el.style.cssText + ';position:fixed;z-index:9999;display:block;' +
                    'left:50%;top:50%;transform:translate(-50%,-50%);';
            } else if (action === 'close') {
                el.classList.remove('ui-popup-active');
                el.style.display = 'none';
                $(el).trigger('popupafterclose');
            }
        });
    };

    // --- orientationchange alias --------------------------------------------
    var resizeTimer = null;
    $(window).on('resize', function () {
        if (resizeTimer) {
            clearTimeout(resizeTimer);
        }
        resizeTimer = setTimeout(function () {
            $(window).trigger('orientationchange');
        }, 300);
    });

    // --- slider implementation ----------------------------------------------

    function buildSlider(input, options) {
        if (input._rbSlider) {
            return;
        }
        input._rbSlider = true;

        var theme = 'b';
        var wrap = document.createElement('div');
        wrap.className = 'ui-slider';
        var track = document.createElement('div');
        track.className = 'ui-slider-track';
        var handle = document.createElement('a');
        handle.href = '#';
        handle.className = 'ui-btn ui-btn-' + theme + ' ui-slider-handle';
        handle.innerHTML = '<span class="ui-btn-inner"><span class="ui-btn-text">&nbsp;</span></span>';
        track.appendChild(handle);

        input.parentNode.insertBefore(wrap, input);
        wrap.appendChild(input);
        wrap.appendChild(track);

        syncSlider(input);

        // input change => update handle
        $(input).on('input change', function () {
            syncSlider(input);
            $(input).trigger('slide');
            $(input).trigger('slidedrag');
        });

        // drag on track / handle => move value
        $(track).on('mousedown touchstart', function (e) {
            e.preventDefault();
            $(input).trigger('slidestart');
            function move(ev) {
                var rect = track.getBoundingClientRect();
                var clientX = (ev.clientX != null) ? ev.clientX : ev.touches[0].clientX;
                var pct = (rect.width > 0) ? (clientX - rect.left) / rect.width : 0;
                pct = Math.max(0, Math.min(1, pct));
                var min = parseFloat(input.getAttribute('min') || '0');
                var max = parseFloat(input.getAttribute('max') || '100');
                input.value = String(Math.round(min + pct * (max - min)));
                syncSlider(input);
                $(input).trigger('slide');
                $(input).trigger('slidedrag');
            }
            function up() {
                $(document).off('mousemove touchmove', move);
                $(document).off('mouseup touchend', up);
                $(input).trigger('slidestop');
            }
            $(document).on('mousemove touchmove', move).on('mouseup touchend', up);
        });

        if (options && typeof options.create === 'function') {
            options.create.call(input, null, null);
        }
    }

    function syncSlider(input) {
        var wrap = input.closest('.ui-slider');
        if (!wrap) {
            return;
        }
        var track = wrap.querySelector('.ui-slider-track');
        var handle = track ? track.querySelector('.ui-slider-handle') : null;
        var min = parseFloat(input.getAttribute('min') || '0');
        var max = parseFloat(input.getAttribute('max') || '100');
        var val = parseFloat(input.value);
        if (isNaN(val)) {
            val = min;
        }
        var pct = (max > min) ? ((val - min) / (max - min)) * 100 : 0;
        if (handle) {
            handle.style.left = pct + '%';
        }
    }

    // --- checkbox/radio -------------------------------------------------------

    function enhanceCheckbox(input) {
        if (input._rbCheckbox) {
            return;
        }
        input._rbCheckbox = true;
        var isRadio = input.type === 'radio';

        // find the associated label BEFORE moving the input
        var label = input.nextElementSibling;
        if (!label || label.tagName.toLowerCase() !== 'label') {
            label = null;
        }

        var wrap = document.createElement('div');
        wrap.className = isRadio ? 'ui-radio' : 'ui-checkbox';
        input.parentNode.insertBefore(wrap, input);
        wrap.appendChild(input);
        if (label) {
            wrap.appendChild(label);
        }

        if (!label) {
            return;
        }

        var $label = $(label);
        $label.addClass('ui-btn ui-corner-all ui-btn-icon-left');
        // build the jQM button structure: label > .ui-btn-inner > [.ui-btn-text][.ui-icon]
        if (!$label.find('.ui-btn-inner').length) {
            var inner = document.createElement('span');
            inner.className = 'ui-btn-inner';
            var text = document.createElement('span');
            text.className = 'ui-btn-text';
            // move all of the label's content (text / elements) into .ui-btn-text
            while (label.firstChild) {
                text.appendChild(label.firstChild);
            }
            inner.appendChild(text);
            var icon = document.createElement('span');
            icon.className = 'ui-icon ui-icon-' + (isRadio ? 'radio-on' : 'check') +
                ' ui-icon-shadow ui-icon-off';
            icon.innerHTML = '&nbsp;';
            inner.appendChild(icon);
            label.appendChild(inner);
        }
        // hide the native input, the styled label drives it
        input.style.position = 'absolute';
        input.style.left = '-9999px';

        syncCheckbox(input);
        $(input).on('change', function () {
            syncCheckbox(this);
        });
    }

    function syncCheckbox(input) {
        var $inp = $(input);
        var wrap = $inp.closest('.ui-checkbox, .ui-radio');
        if (!wrap.length) {
            return;
        }
        var label = wrap.find('label').first();
        if (!label.length) {
            return;
        }
        var onClass = input.type === 'radio' ? 'ui-radio-on' : 'ui-checkbox-on';
        var offClass = input.type === 'radio' ? 'ui-radio-off' : 'ui-checkbox-off';
        if (input.checked) {
            label.addClass(onClass).removeClass(offClass);
            label.find('.ui-icon').addClass('ui-icon-on').removeClass('ui-icon-off');
        } else {
            label.addClass(offClass).removeClass(onClass);
            label.find('.ui-icon').removeClass('ui-icon-on').addClass('ui-icon-off');
        }
    }

    // --- data-role enhancement ------------------------------------------------

    function enhance(root) {
        var $root = $(root);
        if (root && root.nodeType === 9) {
            $root = $('body');
        }
        $root.find('[data-role="page"], [data-role="dialog"]').each(function () {
            ensurePage(this);
        });
        $root.find('[data-role="header"]').each(function () {
            enhanceHeader(this);
        });
        $root.find('[data-role="footer"]').each(function () {
            enhanceFooter(this);
        });
        $root.find('[data-role="content"]').each(function () {
            $(this).addClass('ui-content');
        });
        $root.find('[data-role="fieldcontain"]').each(function () {
            $(this).addClass('ui-field-contain');
        });
        $root.find('[data-role="button"]').each(function () {
            enhanceButton(this);
        });
        $root.find('[data-role="controlgroup"]').each(function () {
            enhanceControlgroup(this);
        });
        $root.find('[data-role="navbar"]').each(function () {
            enhanceNavbar(this);
        });
        $root.find('[data-role="listview"]').each(function () {
            enhanceListview(this);
        });
        $root.find('[data-role="collapsible-set"]').each(function () {
            $(this).addClass('ui-collapsible-set');
        });
        $root.find('[data-role="collapsible"]').each(function () {
            enhanceCollapsible(this);
        });
        $root.find('[data-role="popup"]').each(function () {
            $(this).addClass('ui-popup');
            this.style.display = 'none';
        });
        $root.find('input[type="checkbox"], input[type="radio"]').each(function () {
            enhanceCheckbox(this);
        });
        // buttons inside headers/footers/navbars/controlgroups that were not enhanced
        $root.find('[data-role="header"] a, [data-role="footer"] a, [data-role="navbar"] a,'
                + ' .ui-navbar a, .ui-controlgroup a, [data-role="controlgroup"] a')
            .each(function () {
                enhanceButton(this);
            });
    }

    function enhanceHeader(el) {
        enhanceBar(el, true);
    }

    function enhanceFooter(el) {
        enhanceBar(el, false);
    }

    function enhanceBar(el, isHeader) {
        var $el = $(el);
        var cls = isHeader ? 'ui-header ui-bar-' : 'ui-footer ui-bar-';
        var theme = $el.attr('data-theme') || 'b';
        $el.addClass(cls + theme);
        if ($el.attr('data-position') === 'fixed') {
            $el.addClass('ui-fixed ' + (isHeader ? 'ui-header-fixed' : 'ui-footer-fixed'));
        }
        // heading becomes the toolbar title
        $el.find('h1,h2,h3,h4,h5,h6').not('a h1,a h2,a h3,a h4,a h5,a h6').each(function () {
            if (!$(this).hasClass('ui-title')) {
                $(this).addClass('ui-title');
            }
        });
        // direct buttons: first = left, last = right (like jQuery Mobile)
        var $buttons = $el.children('a');
        if ($buttons.length === 1) {
            $buttons.first().addClass('ui-btn-left');
        } else if ($buttons.length > 1) {
            $buttons.first().addClass('ui-btn-left');
            $buttons.last().addClass('ui-btn-right');
        }
        if ($buttons.length > 0) {
            $buttons.each(function () {
                enhanceButton(this);
            });
        }
    }

    function enhanceButton(el) {
        var $el = $(el);
        if ($el.data('rb-btn')) {
            return;
        }
        $el.data('rb-btn', true);
        var theme = $el.attr('data-theme') ||
            ($el.closest('[data-theme]').attr('data-theme')) || 'b';
        $el.addClass('ui-btn ui-btn-' + theme + ' ui-corner-all ui-shadow');

        var icon = $el.attr('data-icon');
        var iconPos = $el.attr('data-iconpos') || 'left';
        if (icon) {
            $el.addClass('ui-btn-icon-' + iconPos);
        }
        // wrap buttons in the jQM button structure (icon + inner + text).
        // Only buttons that contain an <img> are left untouched (listview thumbs).
        // Empty buttons are wrapped too: the app later sets their label via
        // RB.UI.setText, which targets the ".ui-btn-text" inner element.
        var hasImageChild = ($el.find('img').length > 0);
        if (!hasImageChild && !$el.find('.ui-btn-inner').length) {
            var inner = document.createElement('span');
            inner.className = 'ui-btn-inner';
            var text = document.createElement('span');
            text.className = 'ui-btn-text';
            while (el.firstChild) {
                text.appendChild(el.firstChild);
            }
            inner.appendChild(text);
            if (icon) {
                var ispan = document.createElement('span');
                ispan.className = 'ui-icon ui-icon-' + icon + ' ui-icon-shadow';
                ispan.innerHTML = '&nbsp;';
                inner.appendChild(ispan);
            }
            el.appendChild(inner);
        }
    }

    function enhanceControlgroup(el) {
        var $el = $(el);
        if ($el.hasClass('ui-controlgroup')) {
            return;
        }
        var type = $el.attr('data-type') || 'vertical';
        $el.addClass('ui-controlgroup ui-controlgroup-' + type + ' ui-corner-all');
        $el.find('a, button').each(function () {
            enhanceButton(this);
        });
    }

    function enhanceNavbar(el) {
        var $el = $(el);
        if ($el.hasClass('ui-navbar')) {
            return;
        }
        $el.addClass('ui-navbar');
        var $ul = $el.children('ul').first();
        if (!$ul.length) {
            $ul = $el.find('ul').first();
        }
        var $items = $ul.children('li');
        var n = $items.length;
        if (n > 0) {
            if (n === 1) {
                $ul.addClass('ui-grid-solo');
                $items.first().addClass('ui-block-a');
            } else {
                // grid class: a=2, b=3, c=4, d=5 columns (like jQuery Mobile)
                var letters = 'abcde';
                var gridIdx = Math.max(0, Math.min(letters.length - 1, n - 2));
                $ul.addClass('ui-grid-' + letters.charAt(gridIdx));
                $items.each(function (i) {
                    var b = letters.charAt(Math.min(i, letters.length - 1));
                    $(this).addClass('ui-block-' + b);
                });
            }
        }
        // navbar buttons always show the icon on top
        $el.find('a').each(function () {
            $(this).attr('data-iconpos', 'top');
            enhanceButton(this);
        });
    }

    function buildListviewFilter($ul) {
        var placeholder = $ul.attr('data-filter-placeholder') || '';
        var theme = $ul.attr('data-filter-theme') || 'b';
        var $li = $('<li class="ui-listview-filter ui-li-divider">'
                + '<form class="ui-listview-filter">'
                + '<div class="ui-input-search">'
                + '<input type="search" class="ui-input-text ui-body-' + theme
                + ' ui-corner-all ui-shadow-inset" placeholder="' + placeholder + '"/>'
                + '</div></form></li>');
        $ul.prepend($li);

        $li.find('input').on('input change', function () {
            var q = (this.value || '').toLowerCase();
            $ul.children('li').not('.ui-listview-filter').each(function () {
                var $it = $(this);
                if (!q || ($it.text().toLowerCase().indexOf(q) >= 0)) {
                    $it.show();
                } else {
                    $it.hide();
                }
            });
        });
    }

    function enhanceListview(ul) {
        var $ul = $(ul);
        $ul.addClass('ui-listview');
        if ($ul.attr('data-inset')) {
            $ul.addClass('ui-listview-inset');
        }
        // build the search box (data-filter), but only if it is not present
        // (the app clears the listview and re-adds items on refresh)
        if ($ul.attr('data-filter') === 'true') {
            var hasFilter = $ul.children('li.ui-listview-filter').length > 0;
            if (!hasFilter) {
                buildListviewFilter($ul);
            }
        }
        var splitIcon = $ul.attr('data-split-icon');
        $ul.children('li').each(function () {
            var $li = $(this);
            if ($li.hasClass('ui-listview-filter')) {
                return;
            }
            if (!$li.hasClass('ui-li')) {
                $li.addClass('ui-li');
            }
            if ($li.find('img').length > 0) {
                $li.addClass('ui-li-has-thumb');
                $li.find('img').addClass('ui-li-thumb');
            }
            $li.children('a').each(function (i) {
                var $a = $(this);
                if (!$a.hasClass('ui-btn')) {
                    $a.addClass('ui-btn');
                }
                var iconName = $a.attr('data-icon');
                if (!iconName && (i === 1) && (splitIcon)) {
                    // second anchor is a split button: use the listview split icon
                    iconName = splitIcon;
                }
                if (iconName && !$a.find('.ui-icon').length) {
                    var icon = document.createElement('span');
                    icon.className = 'ui-icon ui-icon-' + iconName;
                    icon.innerHTML = '&nbsp;';
                    this.appendChild(icon);
                }
            });
        });
    }

    function enhanceCollapsible(el) {
        var $el = $(el);
        if ($el.hasClass('ui-collapsible')) {
            return;
        }
        var theme = $el.attr('data-theme') || 'b';
        $el.addClass('ui-collapsible ui-collapsible-collapsed ui-corner-all ui-shadow');
        if ($el.attr('data-inset')) {
            $el.addClass('ui-collapsible-inset');
        }

        var $h = $el.children('h1,h2,h3,h4,h5,h6').first();
        var $content = $el.children('div').first();
        if (!$h.length || !$content.length) {
            return;
        }

        $h.addClass('ui-collapsible-heading ui-collapsible-heading-collapsed');
        var a = document.createElement('a');
        a.href = '#';
        a.className = 'ui-btn ui-btn-' + theme + ' ui-corner-all ui-shadow ui-btn-icon-right';
        var inner = document.createElement('span');
        inner.className = 'ui-btn-inner';
        var txt = document.createElement('span');
        txt.className = 'ui-btn-text';
        txt.appendChild(document.createTextNode($h.text()));
        inner.appendChild(txt);
        var icon = document.createElement('span');
        icon.className = 'ui-icon ui-icon-' + ($el.attr('data-collapsed-icon') || 'chevron-right') +
            ' ui-icon-shadow';
        icon.innerHTML = '&nbsp;';
        inner.appendChild(icon);
        a.appendChild(inner);
        $h.empty().append(a);

        $content.addClass('ui-collapsible-content ui-collapsible-content-collapsed');
        if (!$content.find('.ui-collapsible-content-inset').length) {
            $content.wrapInner('<div class="ui-collapsible-content-inset"></div>');
        }
        $content.hide();

        $h.on('click', function (e) {
            e.preventDefault();
            toggleCollapsible(el);
        });
    }

    function toggleCollapsible(el) {
        var $el = $(el);
        var $h = $el.children('h1,h2,h3,h4,h5,h6').first();
        var $content = $el.children('div').first();
        var $icon = $h.find('.ui-icon');
        if ($el.hasClass('ui-collapsible-collapsed')) {
            $el.removeClass('ui-collapsible-collapsed');
            $h.removeClass('ui-collapsible-heading-collapsed');
            $content.removeClass('ui-collapsible-content-collapsed').show();
            $icon.attr('class', 'ui-icon ui-icon-' + ($el.attr('data-expanded-icon') || 'chevron-down') + ' ui-icon-shadow');
        } else {
            $el.addClass('ui-collapsible-collapsed');
            $h.addClass('ui-collapsible-heading-collapsed');
            $content.addClass('ui-collapsible-content-collapsed').hide();
            $icon.attr('class', 'ui-icon ui-icon-' + ($el.attr('data-collapsed-icon') || 'chevron-right') + ' ui-icon-shadow');
        }
    }

    // --- fixed toolbar compensation -------------------------------------------
    function adjustFixedToolbars() {
        $('[data-role="header"][data-position="fixed"], [data-role="footer"][data-position="fixed"]')
            .each(function () {
                var $bar = $(this);
                var page = $bar.closest('[data-role="page"]');
                if (!page.length) {
                    return;
                }
                var $content = page.find('[data-role="content"]').first();
                if (!$content.length) {
                    return;
                }
                var isHeader = $bar.attr('data-role') === 'header';
                var h = $bar[0].offsetHeight || 50;
                if (isHeader) {
                    $content.css('paddingTop', h);
                } else {
                    $content.css('paddingBottom', h);
                }
            });
    }

    // --- boot -----------------------------------------------------------------

    // fire mobileinit (bound earlier in the HTML), like jQuery Mobile did
    $(document).trigger('mobileinit');

    $(function () {
        enhance(document);
        adjustFixedToolbars();

        // activate the first page in this document
        var $first = $('[data-role="page"]').first();
        if ($first.length) {
            showPage($first[0], false);
        }
    });

    // data-rel="back" closes the enclosing popup/dialog (jQuery Mobile behaviour)
    $(document).on('click', '[data-rel="back"]', function (e) {
        e.preventDefault();
        var $el = $(this);
        var $popup = $el.closest('[data-role="popup"]');
        if ($popup.length) {
            $popup.popup('close');
            return;
        }
        var $dialog = $el.closest('[data-role="dialog"]');
        if ($dialog.length) {
            $dialog.dialog('close');
            return;
        }
        window.history.back();
    });

})(jQuery);