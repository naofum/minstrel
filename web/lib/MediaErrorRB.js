//
// MediaErrorRB (browser version, based on readbeyond-plugin-mediarb MediaErrorRB.js)
//

var _MediaErrorRB = window.MediaErrorRB;

if (!_MediaErrorRB) {
    window.MediaErrorRB = _MediaErrorRB = function(code, msg) {
        this.code = (typeof code != 'undefined') ? code : null;
        this.message = msg || "";
    };
}

_MediaErrorRB.MEDIA_ERR_NONE_ACTIVE    = _MediaErrorRB.MEDIA_ERR_NONE_ACTIVE    || 0;
_MediaErrorRB.MEDIA_ERR_ABORTED        = _MediaErrorRB.MEDIA_ERR_ABORTED        || 1;
_MediaErrorRB.MEDIA_ERR_NETWORK        = _MediaErrorRB.MEDIA_ERR_NETWORK        || 2;
_MediaErrorRB.MEDIA_ERR_DECODE         = _MediaErrorRB.MEDIA_ERR_DECODE         || 3;
_MediaErrorRB.MEDIA_ERR_NONE_SUPPORTED = _MediaErrorRB.MEDIA_ERR_NONE_SUPPORTED || 4;
_MediaErrorRB.MEDIA_ERR_SRC_NOT_SUPPORTED = _MediaErrorRB.MEDIA_ERR_SRC_NOT_SUPPORTED || 4;