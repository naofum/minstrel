package com.github.naofum.minstrel;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(com.github.naofum.minstrel.plugins.CommanderPlugin.class);
        registerPlugin(com.github.naofum.minstrel.plugins.LibrarianPlugin.class);
        registerPlugin(com.github.naofum.minstrel.plugins.UnzipperPlugin.class);
        registerPlugin(com.github.naofum.minstrel.plugins.MediaPlugin.class);
        super.onCreate(savedInstanceState);

        // allow the readers to load extracted files (file://) directly:
        // XHTML via sync XHR, <img>/<audio> elements, etc.
        if (getBridge() != null && getBridge().getWebView() != null) {
            android.webkit.WebSettings s = getBridge().getWebView().getSettings();
            s.setAllowFileAccess(true);
            s.setAllowFileAccessFromFileURLs(true);
            s.setAllowUniversalAccessFromFileURLs(true);
        }
    }
}