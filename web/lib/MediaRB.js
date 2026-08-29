//
// MediaRB (browser version)
// based on readbeyond-plugin-mediarb www/browser/MediaRB.js
// Uses the HTML5 <audio> element instead of a native player.
//

var argscheck = window.argscheck,
    utils = window.utils;

var RB_NATIVE = (typeof window !== 'undefined') && (window.Capacitor) && (typeof Capacitor.isNativePlatform === 'function') && (Capacitor.isNativePlatform());

var mediaObjects = {};

// --- native (Capacitor Media3 plugin) bridge ----------------------------
var _nativeStatusInstalled = false;

function rbMediaRBEnsureNativeStatusListener() {
    if (_nativeStatusInstalled) {
        return;
    }
    _nativeStatusInstalled = true;
    Capacitor.Plugins.Media.addListener('status', function(status) {
        MediaRB.onStatus(status.id, status.msgType, status.value);
    });
}

function rbMediaRBNativeCreate(media) {
    rbMediaRBEnsureNativeStatusListener();
    return Capacitor.Plugins.Media.create({ 'id': media.id, 'src': media.src || '', 'mode': media.mode || 'mode1' });
}

// media whose play() was blocked by the browser autoplay policy
var blockedPlayMedia = [];
var blockedPlayResumeInstalled = false;

function addBlockedPlayResume() {
    if (blockedPlayResumeInstalled) {
        return;
    }
    blockedPlayResumeInstalled = true;

    function cleanup() {
        document.removeEventListener('pointerdown', resume, true);
        document.removeEventListener('touchstart', resume, true);
        document.removeEventListener('keydown', resume, true);
    }

    function resume() {
        cleanup();
        blockedPlayResumeInstalled = false;
        var pending = blockedPlayMedia.slice();
        blockedPlayMedia = [];
        for (var i = 0; i < pending.length; i++) {
            var m = pending[i];
            m.clearBlockedPlay();
            m.play();
        }
    }

    document.addEventListener('pointerdown', resume, true);
    document.addEventListener('touchstart', resume, true);
    document.addEventListener('keydown', resume, true);
}

function clearBlockedPlayFromList(media) {
    var idx = blockedPlayMedia.indexOf(media);
    if (idx >= 0) {
        blockedPlayMedia.splice(idx, 1);
    }
    media._blockedPlay = false;
}

/**
 * Creates new Audio node and with necessary event listeners attached
 * @param  {MediaRB} media MediaRB object
 * @return {Audio}       Audio element 
 */
function createNode (media) {
    var node = new Audio();

    node.onloadstart = function () {
        MediaRB.onStatus(media.id, MediaRB.MEDIA_STATE, MediaRB.MEDIA_STARTING);
    };

    node.onplaying = function () {
        MediaRB.onStatus(media.id, MediaRB.MEDIA_STATE, MediaRB.MEDIA_RUNNING);
    };

    node.ondurationchange = function (e) {
        MediaRB.onStatus(media.id, MediaRB.MEDIA_DURATION, e.target.duration || -1);
    };

    node.onerror = function (e) {
        var err = e.target.error.code === MediaErrorRB.MEDIA_ERR_SRC_NOT_SUPPORTED ?
            { code: MediaErrorRB.MEDIA_ERR_ABORTED } :
            e.target.error;

        MediaRB.onStatus(media.id, MediaRB.MEDIA_ERROR, err);
    };

    node.onended = function () {
        MediaRB.onStatus(media.id, MediaRB.MEDIA_STATE, MediaRB.MEDIA_STOPPED);
    };

    if (media.src) {
        node.src = media.src;
    }

    return node;
}

/**
 * @constructor
 * @param src                   The file name or url to play
 * @param successCallback       The callback to be called when the file is done playing or recording.
 * @param errorCallback         The callback to be called if there is an error.
 * @param statusCallback        The callback to be called when media status has changed.
 * @param mode                  Unused on Windows (kept for API compatibility).
 */
var MediaRB = function(src, successCallback, errorCallback, statusCallback, mode) {
    argscheck.checkArgs('SFFFS', 'MediaRB', arguments);
    this.id = utils.createUUID();
    mediaObjects[this.id] = this;
    this.src = src;
    this.successCallback = successCallback;
    this.errorCallback = errorCallback;
    this.statusCallback = statusCallback;
    this.mode = mode;
    this._duration = -1;
    this._position = -1;

    MediaRB.onStatus(this.id, MediaRB.MEDIA_STATE, MediaRB.MEDIA_STARTING);

    if (RB_NATIVE) {
        rbMediaRBNativeCreate(this).catch(function(err) {
            MediaRB.onStatus(this.id, MediaRB.MEDIA_ERROR, { code: MediaErrorRB.MEDIA_ERR_ABORTED });
        });
        return;
    }

    try {
        this.node = createNode(this);
    } catch (err) {
        MediaRB.onStatus(this.id, MediaRB.MEDIA_ERROR, { code: MediaErrorRB.MEDIA_ERR_ABORTED });
    }
};

// MediaRB messages
MediaRB.MEDIA_STATE = 1;
MediaRB.MEDIA_DURATION = 2;
MediaRB.MEDIA_POSITION = 3;
MediaRB.MEDIA_ERROR = 9;

// MediaRB states
MediaRB.MEDIA_NONE = 0;
MediaRB.MEDIA_STARTING = 1;
MediaRB.MEDIA_RUNNING = 2;
MediaRB.MEDIA_PAUSED = 3;
MediaRB.MEDIA_STOPPED = 4;
MediaRB.MEDIA_COMPLETED = 5;
MediaRB.MEDIA_MSG = ["None", "Starting", "Running", "Paused", "Stopped", "Completed"];

// "static" function to return existing objs.
MediaRB.get = function(id) {
    return mediaObjects[id];
};

/**
 * Start or resume playing audio file.
 */
MediaRB.prototype.play = function() {
    if (RB_NATIVE) {
        var self = this;
        return Capacitor.Plugins.Media.startPlayingAudio({ 'id': this.id, 'src': this.src || '' }).then(function() {
            return self;
        }, function(err) {
            MediaRB.onStatus(self.id, MediaRB.MEDIA_ERROR, err);
        });
    }
    if (!this.node) {
        try {
            this.node = createNode(this);
        } catch (err) {
            MediaRB.onStatus(this.id, MediaRB.MEDIA_ERROR, { code: MediaErrorRB.MEDIA_ERR_ABORTED });
        }
    }
    var self = this;
    var p = this.node.play();
    if (p && typeof p.catch === 'function') {
        p.catch(function(err) {
            if (err && err.name === 'NotAllowedError') {
                // The browser autoplay policy blocked this play() because there
                // was no user gesture. Retry automatically on the next gesture.
                if (!self._blockedPlay) {
                    self._blockedPlay = true;
                    blockedPlayMedia.push(self);
                }
                addBlockedPlayResume();
            } else if (!(err && err.name === 'AbortError')) {
                // AbortError is harmless (a newer play() superseded this one)
                MediaRB.onStatus(self.id, MediaRB.MEDIA_ERROR, err);
            }
        });
    }
    return p;
};

/**
 * Stop playing audio file.
 */
MediaRB.prototype.stop = function() {
    if (RB_NATIVE) {
        try {
            // the native plugin emits the MEDIA_STOPPED status event
            Capacitor.Plugins.Media.stopPlayingAudio({ 'id': this.id });
        } catch (err) {
            MediaRB.onStatus(this.id, MediaRB.MEDIA_ERROR, err);
        }
        return;
    }
    try {
        this.clearBlockedPlay();
        this.pause();
        this.seekTo(0);
        MediaRB.onStatus(this.id, MediaRB.MEDIA_STATE, MediaRB.MEDIA_STOPPED);
    } catch (err) {
        MediaRB.onStatus(this.id, MediaRB.MEDIA_ERROR, err);
    }
};

/**
 * Seek or jump to a new time in the track..
 */
MediaRB.prototype.seekTo = function(milliseconds) {
    if (RB_NATIVE) {
        try {
            Capacitor.Plugins.Media.seekToAudio({ 'id': this.id, 'milliseconds': milliseconds });
        } catch (err) {
            MediaRB.onStatus(this.id, MediaRB.MEDIA_ERROR, err);
        }
        return;
    }
    try {
        this.node.currentTime = milliseconds / 1000;
    } catch (err) {
        MediaRB.onStatus(this.id, MediaRB.MEDIA_ERROR, err);
    }
};

/**
 * Pause playing audio file.
 */
MediaRB.prototype.pause = function() {
    if (RB_NATIVE) {
        try {
            // the native plugin emits the MEDIA_PAUSED status event
            Capacitor.Plugins.Media.pausePlayingAudio({ 'id': this.id });
        } catch (err) {
            MediaRB.onStatus(this.id, MediaRB.MEDIA_ERROR, err);
        }
        return;
    }
    try {
        this.node.pause();
        MediaRB.onStatus(this.id, MediaRB.MEDIA_STATE, MediaRB.MEDIA_PAUSED);
    } catch (err) {
        MediaRB.onStatus(this.id, MediaRB.MEDIA_ERROR, err);
    }};

/**
 * Get duration of an audio file.
 */
MediaRB.prototype.getDuration = function() {
    return this._duration;
};

/**
 * Get position of audio.
 */
MediaRB.prototype.getCurrentPosition = function(success, fail) {
    if (RB_NATIVE) {
        try {
            var self = this;
            Capacitor.Plugins.Media.getCurrentPositionAudio({ 'id': this.id }).then(function(p) {
                var value = (p && (p.value !== undefined)) ? p.value : -1;
                self._position = value;
                success(value);
            }, function(err) {
                fail(err);
            });
        } catch (err) {
            fail(err);
        }
        return;
    }
    try {
        var p = this.node.currentTime;
        MediaRB.onStatus(this.id, MediaRB.MEDIA_POSITION, p);
        success(p);
    } catch (err) {
        fail(err);
    }
};

/**
 * Release the resources.
 */
MediaRB.prototype.release = function() {
    if (RB_NATIVE) {
        try {
            Capacitor.Plugins.Media.release({ 'id': this.id });
        } catch (err) {
            MediaRB.onStatus(this.id, MediaRB.MEDIA_ERROR, err);
        }
        return;
    }
    try {
        clearBlockedPlayFromList(this);
        delete this.node;
    } catch (err) {
        MediaRB.onStatus(this.id, MediaRB.MEDIA_ERROR, err);
    }};

/**
 * Forget that playback was blocked by the autoplay policy.
 */
MediaRB.prototype.clearBlockedPlay = function() {
    clearBlockedPlayFromList(this);
};

/**
 * Adjust the volume.
 */
MediaRB.prototype.setVolume = function(volume) {
    if (RB_NATIVE) {
        Capacitor.Plugins.Media.setVolume({ 'id': this.id, 'volume': volume });
        return;
    }
    this.node.volume = volume;
};

/**
 * Adjust the playback speed.
 */
MediaRB.prototype.setPlaybackSpeed = function(speed) {
    if (RB_NATIVE) {
        Capacitor.Plugins.Media.setPlaybackSpeed({ 'id': this.id, 'speed': speed });
        return;
    }
    this.node.playbackRate = speed;
};

/**
 * Audio has status update.
 * PRIVATE
 */
MediaRB.onStatus = function(id, msgType, value) {
    var media = mediaObjects[id];
    if (media) {
        switch (msgType) {
            case MediaRB.MEDIA_STATE :
                media.statusCallback && media.statusCallback(value);
                if (value === MediaRB.MEDIA_COMPLETED) {
                    media.successCallback && media.successCallback();
                }
                break;
            case MediaRB.MEDIA_DURATION :
                media._duration = value;
                break;
            case MediaRB.MEDIA_ERROR :
                media.errorCallback && media.errorCallback(value);
                break;
            case MediaRB.MEDIA_POSITION :
                media._position = Number(value);
                break;
            default :
                console.error && console.error("Unhandled MediaRB.onStatus :: " + msgType);
                break;
        }
    } else {
         console.error && console.error("Received MediaRB.onStatus callback for unknown media :: " + id);
    }
};