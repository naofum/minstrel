package com.github.naofum.minstrel.plugins;

import android.content.ComponentName;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.github.naofum.minstrel.plugins.playback.PlaybackService;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.HashMap;
import java.util.Map;

/**
 * Media / MediaRB Capacitor plugin backed by a media3 {@link MediaController}
 * that drives {@link PlaybackService} (a foreground MediaSessionService).
 *
 * Both the JS "Media" and "MediaRB" shims map to this single native plugin.
 * ExoPlayer provides time-stretch playback speed (0.5x-2.0x, pitch preserved)
 * out of the box, so there is no need for the legacy Sonic .so.
 *
 * Minstrel never plays two tracks simultaneously (see the spec): the EPUB Media
 * Overlays renderer pre-creates one JS Media object per audio file, but only one
 * is ever the active track. We therefore keep a single controller/player and
 * swap the MediaItem when the active id changes. Non-active ids are served from
 * cached state (duration) without touching the real player.
 *
 * Status updates are pushed to JS via Capacitor listeners with the same
 * contract as the original Cordova plugin:
 *   { id: <string>, msgType: <int>, value: <float|{code:int}> }
 *
 * Media messages: 1=STATE 2=DURATION 3=POSITION 9=ERROR
 * Media states:   0=NONE 1=STARTING 2=RUNNING 3=PAUSED 4=STOPPED 5=COMPLETED
 */
@androidx.media3.common.util.UnstableApi
@CapacitorPlugin(name = "Media")
public class MediaPlugin extends Plugin {

    private static final int MEDIA_STATE    = 1;
    private static final int MEDIA_DURATION = 2;
    private static final int MEDIA_POSITION = 3;
    private static final int MEDIA_ERROR    = 9;

    private static final int STATE_STARTING  = 1;
    private static final int STATE_RUNNING   = 2;
    private static final int STATE_PAUSED    = 3;
    private static final int STATE_STOPPED   = 4;
    private static final int STATE_COMPLETED = 5;

    private static final int MEDIA_ERR_ABORTED = 1;

    private MediaController controller;
    private ListenableFuture<MediaController> controllerFuture;

    /** src registered per JS media id (id -> file path/uri). */
    private final Map<String, String> playerSrcs = new HashMap<>();
    /** cached duration (seconds) per id, so non-active ids can answer getDuration. */
    private final Map<String, Float> durationCache = new HashMap<>();

    /** the id currently loaded into the single ExoPlayer, or null. */
    private String activeId = null;

    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    public void load() {
        SessionToken token = new SessionToken(
                getContext(),
                new ComponentName(getContext(), PlaybackService.class));
        controllerFuture = new MediaController.Builder(getContext(), token).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                controller = controllerFuture.get();
                controller.addListener(new ControllerListener());
            } catch (Exception e) {
                controller = null;
            }
        }, MoreExecutors.directExecutor());
    }

    // -------------------------------------------------------------------------
    // Plugin methods (JS contract preserved)
    // -------------------------------------------------------------------------

    @PluginMethod
    public void create(final PluginCall call) {
        final String id  = call.getString("id", "");
        final String src = stripFileProtocol(call.getString("src", ""));
        playerSrcs.put(id, src);
        call.resolve();
    }

    @PluginMethod
    public void startPlayingAudio(final PluginCall call) {
        final String id  = call.getString("id", "");
        final String src = stripFileProtocol(call.getString("src", ""));
        if ((src != null) && (!src.isEmpty())) {
            playerSrcs.put(id, src);
        }
        runOnMain(() -> {
            if (controller == null) {
                sendError(id, MEDIA_ERR_ABORTED);
                return;
            }
            ensureActive(id);
            controller.setPlayWhenReady(true);
            controller.prepare();
            sendStatus(id, MEDIA_STATE, STATE_RUNNING);
        });
        call.resolve();
    }

    @PluginMethod
    public void stopPlayingAudio(final PluginCall call) {
        final String id = call.getString("id", "");
        runOnMain(() -> {
            if ((controller != null) && id.equals(activeId)) {
                controller.pause();
                controller.seekTo(0);
            }
            sendStatus(id, MEDIA_STATE, STATE_STOPPED);
        });
        call.resolve();
    }

    @PluginMethod
    public void pausePlayingAudio(final PluginCall call) {
        final String id = call.getString("id", "");
        runOnMain(() -> {
            if ((controller != null) && id.equals(activeId)) {
                controller.pause();
            }
            sendStatus(id, MEDIA_STATE, STATE_PAUSED);
        });
        call.resolve();
    }

    @PluginMethod
    public void seekToAudio(final PluginCall call) {
        final String id = call.getString("id", "");
        final long ms = (long) (double) call.getDouble("milliseconds", 0d);
        runOnMain(() -> {
            if ((controller != null) && id.equals(activeId)) {
                controller.seekTo(ms);
                sendStatus(id, MEDIA_POSITION, ms / 1000.0f);
            }
        });
        call.resolve();
    }

    @PluginMethod
    public void getCurrentPositionAudio(final PluginCall call) {
        final String id = call.getString("id", "");
        runOnMain(() -> {
            float pos = -1;
            if ((controller != null) && id.equals(activeId)) {
                int state = controller.getPlaybackState();
                if ((state == Player.STATE_READY) || (state == Player.STATE_BUFFERING)) {
                    pos = controller.getCurrentPosition() / 1000.0f;
                    sendStatus(id, MEDIA_POSITION, pos);
                }
            }
            JSObject ret = new JSObject();
            ret.put("value", pos);
            call.resolve(ret);
        });
    }

    @PluginMethod
    public void getDurationAudio(final PluginCall call) {
        final String id = call.getString("id", "");
        runOnMain(() -> {
            float duration = -1;
            if ((controller != null) && id.equals(activeId)) {
                long d = controller.getDuration();
                duration = (d != androidx.media3.common.C.TIME_UNSET) ? d / 1000.0f : -1;
                if (duration > 0) {
                    durationCache.put(id, duration);
                }
            } else if (durationCache.containsKey(id)) {
                duration = durationCache.get(id);
            }
            JSObject ret = new JSObject();
            ret.put("value", duration);
            call.resolve(ret);
        });
    }

    @PluginMethod
    public void setVolume(final PluginCall call) {
        final String id = call.getString("id", "");
        final float volume = (float) (double) call.getDouble("volume", 1d);
        runOnMain(() -> {
            if ((controller != null) && id.equals(activeId)) {
                controller.setVolume(volume);
            }
        });
        call.resolve();
    }

    @PluginMethod
    public void setPlaybackSpeed(final PluginCall call) {
        final String id = call.getString("id", "");
        final float speed = (float) (double) call.getDouble("speed", 1d);
        runOnMain(() -> {
            if ((controller != null) && id.equals(activeId)
                    && (speed >= 0.5f) && (speed <= 2.0f)) {
                controller.setPlaybackSpeed(speed);
            }
        });
        call.resolve();
    }

    @PluginMethod
    public void release(final PluginCall call) {
        final String id = call.getString("id", "");
        playerSrcs.remove(id);
        durationCache.remove(id);
        runOnMain(() -> {
            if (id.equals(activeId) && (controller != null)) {
                controller.stop();
                controller.clearMediaItems();
                activeId = null;
            }
        });
        call.resolve();
    }

    @Override
    protected void handleOnDestroy() {
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
        }
        controller = null;
        super.handleOnDestroy();
    }

    // -------------------------------------------------------------------------
    // internals
    // -------------------------------------------------------------------------

    /** Load {@code id}'s src into the single player if it is not already active. */
    private void ensureActive(String id) {
        if (id.equals(activeId)) {
            return;
        }
        String src = playerSrcs.get(id);
        if ((src == null) || src.isEmpty()) {
            return;
        }
        controller.setMediaItem(
                new MediaItem.Builder()
                        .setMediaId(id)
                        .setUri(toUri(src))
                        .build());
        controller.setPlayWhenReady(false);
        controller.prepare();
        activeId = id;
    }

    private void runOnMain(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            main.post(r);
        }
    }

    private void sendStatus(String id, int msgType, float value) {
        JSObject status = new JSObject();
        status.put("id", id);
        status.put("msgType", msgType);
        status.put("value", value);
        notifyListeners("status", status);
    }

    private void sendError(String id, int code) {
        JSObject codeObj = new JSObject();
        codeObj.put("code", code);
        JSObject status = new JSObject();
        status.put("id", id);
        status.put("msgType", MEDIA_ERROR);
        status.put("value", codeObj);
        notifyListeners("status", status);
    }

    private String stripFileProtocol(String src) {
        if ((src != null) && (src.startsWith("file://"))) {
            return src.substring(7);
        }
        return src;
    }

    private Uri toUri(String src) {
        if (src == null) {
            return Uri.EMPTY;
        }
        Uri uri = Uri.parse(src);
        if (uri.getScheme() != null) {
            return uri;
        }
        // collapse leading slashes so "//storage/..." resolves like "/storage/..."
        String path = src;
        while (path.startsWith("//")) {
            path = path.substring(1);
        }
        return Uri.fromFile(new java.io.File(path));
    }

    // -------------------------------------------------------------------------
    // controller callbacks -> JS status contract (for the active track only)
    // -------------------------------------------------------------------------

    private final class ControllerListener implements Player.Listener {
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            if (activeId == null) {
                return;
            }
            if (playbackState == Player.STATE_READY) {
                long d = controller.getDuration();
                float duration = (d != androidx.media3.common.C.TIME_UNSET) ? d / 1000.0f : -1;
                if (duration > 0) {
                    durationCache.put(activeId, duration);
                }
                sendStatus(activeId, MEDIA_DURATION, duration);
                sendStatus(activeId, MEDIA_STATE, STATE_STARTING);
            } else if (playbackState == Player.STATE_ENDED) {
                sendStatus(activeId, MEDIA_STATE, STATE_COMPLETED);
            }
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            if ((activeId != null) && isPlaying) {
                sendStatus(activeId, MEDIA_STATE, STATE_RUNNING);
            }
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            if (activeId != null) {
                sendError(activeId, MEDIA_ERR_ABORTED);
            }
        }
    }
}
