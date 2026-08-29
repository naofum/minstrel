//
// Minstrel runtime environment
//
// Dual backend:
//  - Browser (Windows/PC development): HTTP calls to the local Java server,
//    which mirrors the original plugin interface:
//      Commander.createEvent(command, option, successCallback, errorCallback)
//      Librarian.createEvent(mode, path, args, successCallback, errorCallback)
//      Unzipper.createEvent(mode, srcPath, dstPath, args, successCallback, errorCallback)
//    The server answers {"success": "..."} or {"error": "..."}, exactly like the
//    original callbackContext.success()/error() did.
//  - Android (Capacitor): native bridge to the Commander / Librarian / Unzipper
//    Capacitor plugins. Results are unwrapped from { value: ... }.

'use strict';

// true when running inside the Capacitor WebView on a native platform
var RB_NATIVE = (typeof window !== 'undefined') && (window.Capacitor) && (typeof Capacitor.isNativePlatform === 'function') && (Capacitor.isNativePlatform());

// invoke a native Capacitor plugin method and unwrap { value: ... }
function rb_capCall(plugin, method, args) {
    return Capacitor.Plugins[plugin][method](args).then(
        function(result) {
            return (result && (result.value !== undefined)) ? result.value : result;
        },
        function(error) {
            if (error && error.message) {
                throw error.message;
            }
            throw '' + error;
        }
    );
}

// --- HTTP (browser) backend -------------------------------------------

function rb_postApi(endpoint, payload, successCallback, errorCallback) {
    try {
        var xhr = new XMLHttpRequest();
        xhr.open('POST', endpoint, true);
        xhr.setRequestHeader('Content-Type', 'application/json');
        xhr.onreadystatechange = function() {
            if (xhr.readyState === 4) {
                if (xhr.status === 200) {
                    var resp = null;
                    try {
                        resp = JSON.parse(xhr.responseText);
                    } catch (e) {
                        resp = null;
                    }
                    if ((resp) && ('success' in resp)) {
                        if (successCallback) {
                            successCallback(resp.success);
                        }
                    } else if ((resp) && ('error' in resp)) {
                        if (errorCallback) {
                            errorCallback(resp.error);
                        }
                    } else {
                        if (errorCallback) {
                            errorCallback('Unexpected server response');
                        }
                    }
                } else {
                    if (errorCallback) {
                        errorCallback('HTTP error ' + xhr.status);
                    }
                }
            }
        };
        xhr.send(JSON.stringify(payload));
    } catch (e) {
        if (errorCallback) {
            errorCallback('' + e);
        }
    }
}

// --- plugin shims ------------------------------------------------------

var Commander = Commander || {};
Commander.createEvent = function(command, option, successCallback, errorCallback) {
    if (RB_NATIVE) {
        rb_capCall('Commander', 'command', { 'command': command, 'option': option || '{}' })
            .then(successCallback, errorCallback);
    } else {
        rb_postApi('api/commander', { 'command': command, 'option': option || '{}' }, successCallback, errorCallback);
    }
};

var Librarian = Librarian || {};
Librarian.createEvent = function(mode, path, args, successCallback, errorCallback) {
    if (RB_NATIVE) {
        rb_capCall('Librarian', 'librarian', { 'mode': mode, 'path': path || '', 'args': args || '{}' })
            .then(successCallback, errorCallback);
    } else {
        rb_postApi('api/librarian', { 'mode': mode, 'path': path || '', 'args': args || '{}' }, successCallback, errorCallback);
    }
};

var Unzipper = Unzipper || {};
Unzipper.createEvent = function(mode, srcPath, dstPath, args, successCallback, errorCallback) {
    if (RB_NATIVE) {
        rb_capCall('Unzipper', 'unzip', { 'mode': mode, 'srcPath': srcPath || '', 'dstPath': dstPath || '', 'args': args || '{}' })
            .then(successCallback, errorCallback);
    } else {
        rb_postApi('api/unzipper', { 'mode': mode, 'srcPath': srcPath || '', 'dstPath': dstPath || '', 'args': args || '{}' }, successCallback, errorCallback);
    }
};

// navigator.app.exitApp() is Android-only; use the Capacitor App plugin there.
if (!navigator.app) {
    navigator.app = {};
}
navigator.app.exitApp = function() {
    if (RB_NATIVE) {
        try {
            Capacitor.Plugins.App.exitApp();
        } catch (e) {
            console.log('windows.js: exitApp() failed: ' + e);
        }
    } else {
        console.log('windows.js: exitApp() is a no-op on Windows');
    }
};