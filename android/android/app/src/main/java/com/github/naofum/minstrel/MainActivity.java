package com.github.naofum.minstrel;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.getcapacitor.BridgeActivity;
import com.getcapacitor.PluginHandle;
import com.github.naofum.minstrel.plugins.ImporterPlugin;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(com.github.naofum.minstrel.plugins.CommanderPlugin.class);
        registerPlugin(com.github.naofum.minstrel.plugins.LibrarianPlugin.class);
        registerPlugin(com.github.naofum.minstrel.plugins.UnzipperPlugin.class);
        registerPlugin(com.github.naofum.minstrel.plugins.MediaPlugin.class);
        registerPlugin(com.github.naofum.minstrel.plugins.ImporterPlugin.class);
        super.onCreate(savedInstanceState);

        // allow the readers to load extracted files (file://) directly:
        // XHTML via sync XHR, <img>/<audio> elements, etc.
        if (getBridge() != null && getBridge().getWebView() != null) {
            android.webkit.WebSettings s = getBridge().getWebView().getSettings();
            s.setAllowFileAccess(true);
            s.setAllowFileAccessFromFileURLs(true);
            s.setAllowUniversalAccessFromFileURLs(true);
        }

        // "Open with Minstrel" from a file manager (cold start)
        handleOpenWith(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // "Open with Minstrel" while the app is already running
        handleOpenWith(intent);
    }

    private void handleOpenWith(Intent intent) {
        if ((intent == null) || (!Intent.ACTION_VIEW.equals(intent.getAction()))) {
            return;
        }
        Uri uri = intent.getData();
        if ((uri == null) || (getBridge() == null)) {
            return;
        }
        PluginHandle handle = getBridge().getPlugin("Importer");
        if ((handle != null) && (handle.getInstance() instanceof ImporterPlugin)) {
            ((ImporterPlugin) handle.getInstance()).handleOpenWithIntent(uri);
        }
    }
}