package com.github.naofum.minstrel.plugins.playback;

import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

/**
 * Foreground MediaSessionService that owns the single active ExoPlayer used
 * for audiobook playback.
 *
 * Minstrel never plays two tracks at the same time (see docs/media-session-service-spec.md):
 * the EPUB Media Overlays renderer pre-creates one JS Media object per audio
 * file, but only one is ever the "active" track. We therefore keep a single
 * ExoPlayer here and swap its MediaItem when the active track changes.
 *
 * The Capacitor MediaPlugin talks to this service through a MediaController and
 * bridges player callbacks back to JS using the legacy status contract.
 */
@androidx.media3.common.util.UnstableApi
public class PlaybackService extends MediaSessionService {

    private MediaSession mediaSession;
    private ExoPlayer player;

    @Override
    public void onCreate() {
        super.onCreate();

        player = new ExoPlayer.Builder(this)
                .setAudioAttributes(
                        new AudioAttributes.Builder()
                                .setUsage(C.USAGE_MEDIA)
                                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                                .build(),
                        /* handleAudioFocus= */ true)
                .setHandleAudioBecomingNoisy(true)
                .build();

        mediaSession = new MediaSession.Builder(this, player).build();
    }

    @Nullable
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    /**
     * When the user removes the app from the recent-apps list while nothing is
     * playing (or the player is paused/ended), release the service. If audio is
     * actively playing we keep the service alive so playback continues.
     */
    @Override
    public void onTaskRemoved(@Nullable Intent rootIntent) {
        androidx.media3.common.Player p = mediaSession != null ? mediaSession.getPlayer() : null;
        if (p == null
                || !p.getPlayWhenReady()
                || p.getMediaItemCount() == 0
                || p.getPlaybackState() == androidx.media3.common.Player.STATE_ENDED
                || p.getPlaybackState() == androidx.media3.common.Player.STATE_IDLE) {
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        if (mediaSession != null) {
            mediaSession.getPlayer().release();
            mediaSession.release();
            mediaSession = null;
        }
        player = null;
        super.onDestroy();
    }
}
