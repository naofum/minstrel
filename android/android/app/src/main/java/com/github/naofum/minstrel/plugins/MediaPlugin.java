package com.github.naofum.minstrel.plugins;

import android.net.Uri;
import android.util.Log;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.HashMap;
import java.util.Map;

/**
 * Media / MediaRB Capacitor plugin built on androidx.media3 (ExoPlayer).
 *
 * Both the JS "Media" and "MediaRB" shims map to this single native plugin.
 * ExoPlayer provides time-stretch playback speed (0.5x-2.0x, pitch preserved)
 * out of the box, so there is no need for the legacy Sonic .so.
 *
 * Status updates are pushed to JS via Capacitor listeners with the same
 * contract as the original Cordova plugin:
 *   { id: <string>, msgType: <int>, value: <float|{code:int}> }
 *
 * Media messages: 1=STATE 2=DURATION 3=POSITION 9=ERROR
 * Media states:   0=NONE 1=STARTING 2=RUNNING 3=PAUSED 4=STOPPED 5=COMPLETED
 */
@CapacitorPlugin(name = "Media")
public class MediaPlugin extends Plugin {

    private static final String TAG = "MediaPlugin";

    private static final int MEDIA_STATE    = 1;
    private static final int MEDIA_DURATION = 2;
    private static final int MEDIA_POSITION = 3;
    private static final int MEDIA_ERROR    = 9;

    private static final int STATE_STARTING  = 1;
    private static final int STATE_RUNNING   = 2;
    private static final int STATE_PAUSED    = 3;
    private static final int STATE_STOPPED   = 4;
    private static final int STATE_COMPLETED = 5;

    private static final int MEDIA_ERR_NONE_ACTIVE = 0;
    private static final int MEDIA_ERR_ABORTED     = 1;

    private final Map<String, ExoPlayer> players = new HashMap<String, ExoPlayer>();
    private final Map<String, String> playerSrcs = new HashMap<String, String>();

    @PluginMethod
    public void create(final PluginCall call) {
        String id  = call.getString("id", "");
        String src = stripFileProtocol(call.getString("src", ""));
        ExoPlayer player = players.get(id);
        if (player == null) {
            player = buildPlayer();
            players.put(id, player);
            playerSrcs.put(id, src);
            prepare(player, id, src);
        }
        call.resolve();
    }

    @PluginMethod
    public void startPlayingAudio(final PluginCall call) {
        String id  = call.getString("id", "");
        String src = stripFileProtocol(call.getString("src", ""));
        ExoPlayer player = players.get(id);
        if (player == null) {
            player = buildPlayer();
            players.put(id, player);
            playerSrcs.put(id, src);
            prepare(player, id, src);
        } else {
            String current = playerSrcs.get(id);
            if ((src != null) && (!src.isEmpty()) && (!src.equals(current))) {
                playerSrcs.put(id, src);
                prepare(player, id, src);
            }
        }
        player.setPlayWhenReady(true);
        player.prepare();
        sendStatus(id, MEDIA_STATE, STATE_RUNNING);
        call.resolve();
    }

    @PluginMethod
    public void stopPlayingAudio(final PluginCall call) {
        ExoPlayer player = players.get(call.getString("id", ""));
        if (player != null) {
            player.pause();
            player.seekTo(0);
            sendStatus(call.getString("id", ""), MEDIA_STATE, STATE_STOPPED);
        }
        call.resolve();
    }

    @PluginMethod
    public void seekToAudio(final PluginCall call) {
        ExoPlayer player = players.get(call.getString("id", ""));
        if (player != null) {
            long ms = (long) (double) call.getDouble("milliseconds", 0d);
            player.seekTo(ms);
            sendStatus(call.getString("id", ""), MEDIA_POSITION, ms / 1000.0f);
        }
        call.resolve();
    }

    @PluginMethod
    public void pausePlayingAudio(final PluginCall call) {
        String id = call.getString("id", "");
        ExoPlayer player = players.get(id);
        if (player != null) {
            player.pause();
            sendStatus(id, MEDIA_STATE, STATE_PAUSED);
        }
        call.resolve();
    }

    @PluginMethod
    public void getCurrentPositionAudio(final PluginCall call) {
        String id = call.getString("id", "");
        ExoPlayer player = players.get(id);
        float pos = -1;
        if (player != null) {
            int state = player.getPlaybackState();
            if ((state == Player.STATE_READY) || (state == Player.STATE_BUFFERING)) {
                pos = player.getCurrentPosition() / 1000.0f;
                sendStatus(id, MEDIA_POSITION, pos);
            }
        }
        JSObject ret = new JSObject();
        ret.put("value", pos);
        call.resolve(ret);
    }

    @PluginMethod
    public void getDurationAudio(final PluginCall call) {
        String id = call.getString("id", "");
        ExoPlayer player = players.get(id);
        float duration = -1;
        if (player != null) {
            duration = player.getDuration() != androidx.media3.common.C.TIME_UNSET
                    ? player.getDuration() / 1000.0f : -1;
        }
        JSObject ret = new JSObject();
        ret.put("value", duration);
        call.resolve(ret);
    }

    @PluginMethod
    public void setVolume(final PluginCall call) {
        ExoPlayer player = players.get(call.getString("id", ""));
        if (player != null) {
            float volume = (float) (double) call.getDouble("volume", 1d);
            player.setVolume(volume);
        }
        call.resolve();
    }

    @PluginMethod
    public void setPlaybackSpeed(final PluginCall call) {
        ExoPlayer player = players.get(call.getString("id", ""));
        if (player != null) {
            float speed = (float) (double) call.getDouble("speed", 1d);
            if ((speed >= 0.5f) && (speed <= 2.0f)) {
                player.setPlaybackSpeed(speed);
            }
        }
        call.resolve();
    }

    @PluginMethod
    public void release(final PluginCall call) {
        String id = call.getString("id", "");
        ExoPlayer player = players.remove(id);
        playerSrcs.remove(id);
        if (player != null) {
            player.release();
        }
        call.resolve();
    }

    //--------------------------------------------------------------------------
    // internals
    //--------------------------------------------------------------------------

    private ExoPlayer buildPlayer() {
        return new ExoPlayer.Builder(getContext())
                .setAudioAttributes(
                        new androidx.media3.common.AudioAttributes.Builder()
                                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                                .build(),
                        true)
                .build();
    }

    private void prepare(final ExoPlayer player, final String id, final String src) {
        player.clearMediaItems();
        player.setMediaItem(MediaItem.fromUri(toUri(src)));
        player.setPlayWhenReady(false);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    float duration = player.getDuration() != androidx.media3.common.C.TIME_UNSET
                            ? player.getDuration() / 1000.0f : -1;
                    sendStatus(id, MEDIA_DURATION, duration);
                    sendStatus(id, MEDIA_STATE, STATE_STARTING);
                } else if (playbackState == Player.STATE_ENDED) {
                    sendStatus(id, MEDIA_STATE, STATE_COMPLETED);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                sendError(id, MEDIA_ERR_ABORTED);
            }
        });
        player.prepare();
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
}
