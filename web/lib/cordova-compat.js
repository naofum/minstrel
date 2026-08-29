//
// Minimal stand-ins for the Cordova helpers used by the browser
// implementations of the Media / MediaRB plugins.
//

'use strict';

var argscheck = argscheck || {};
argscheck.checkArgs = function(spec, functionName, args) {
    // no-op: nothing to validate in the browser
};

var utils = utils || {};
utils.createUUID = function() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        var r = Math.random() * 16 | 0;
        var v = c === 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
    });
};