// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 irvan-putra
package com.vanipy.cabang;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.io.File;

/**
 * Hosts the bundled threaded view. Cabang keeps its OWN accounts directory; it never reads another
 * app's data. It creates a new identity only after the person taps the setup button. Network is used only by the Delta Chat core, after that explicit setup.
 */
public class MainActivity extends Activity {
    private ValueCallback<Uri[]> pending;
    private WebView web;
    private Bridge bridge;
    private int backupAccount = -1;
    private String backupCode;   // memory only; dropped when the flow ends or the activity is destroyed
    private static final int MAX_PICK_BYTES = 20 * 1024 * 1024;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setDomStorageEnabled(true);   // link overlay is saved in this app only (ids, no message text)
        s.setBlockNetworkLoads(true);
        bridge = new Bridge(new PythonCore(), js -> runOnUiThread(() -> { if (web != null) web.evaluateJavascript(js, null); }));
        web.addJavascriptInterface(new Host(), "CabangHost");
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        web.setWebViewClient(new WebViewClient() {
            // Page never navigates away and never opens another app or the network.
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return true; }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (isBundledAsset(u)) return null;
                return new WebResourceResponse("text/plain", "utf-8", null);
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (pending != null) pending.onReceiveValue(null);
                pending = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("*/*");
                i.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(i, 1);
                return true;
            }
        });
        web.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);   // never offered to autofill services
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    /** Python side. Created lazily on the bridge thread; owns the accounts dir inside this app. */
    private class PythonCore implements Bridge.Core {
        private PyObject core, rpc;
        private synchronized PyObject core() {
            if (core == null) {
                if (!Python.isStarted()) Python.start(new AndroidPlatform(MainActivity.this));
                core = Python.getInstance().getModule("cabang_core");
                File dir = new File(getFilesDir(), "accounts");
                String server = new File(getApplicationInfo().nativeLibraryDir, "librpcsrv.so").getPath();
                rpc = core.callAttr("open_rpc", dir.getPath(), server);
                try { core.callAttr("start_configured_io", rpc); } catch (Exception ignored) { }
            }
            return core;
        }
        public String chatJson(int a, int c, int n) { return core().callAttr("chat_json", rpc, a, c, n).toString(); }
        public String snapshotJson() { return core().callAttr("snapshot_json", rpc).toString(); }
        public void sendReply(int a, int c, int q, String t) { core().callAttr("send_reply", rpc, a, c, q, t); }
        public String exportBackup(int a, String dir, String code) { return core().callAttr("export_backup", rpc, a, dir, code).toString(); }
        public void createIdentity() { core().callAttr("create_identity", rpc, "Cabang"); }
        public synchronized void close() { if (core != null && rpc != null) { core.callAttr("close_rpc", rpc); rpc = null; } }
    }

    /** Only plain file:///android_asset/<name> paths; no "..", no encoded separators, no query or fragment tricks. */
    static boolean isBundledAsset(Uri u) {
        if (!"file".equals(u.getScheme())) return false;
        String p = u.getPath();
        if (p == null || !p.startsWith("/android_asset/")) return false;
        String raw = String.valueOf(u.getEncodedPath()).toLowerCase(java.util.Locale.ROOT);
        return !p.contains("..") && !p.contains("//") && !raw.contains("%2e") && !raw.contains("%2f") && !raw.contains("%5c") && !p.contains("\\");
    }

    /** FLAG_SECURE blocks screenshots, screen recording and the recents thumbnail. Safe to call from any thread. */
    void setSecure(boolean on) {
        runOnUiThread(() -> {
            if (on) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
            else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        });
    }

    private class Host {
        @JavascriptInterface public void refresh() { bridge.refresh(); }
        @JavascriptInterface public void loadChat(int account, int chat, int limit) { bridge.loadChat(account, chat, limit); }
        @JavascriptInterface public void sendReply(int account, int chat, int quoted, String text) { bridge.sendReply(account, chat, quoted, text); }
        /** Step 1: makes the code on this device and returns it to the page, which shows it before anything is saved. */
        @JavascriptInterface public String backupPrepare(int account) {
            if (account < 0) return "";
            backupAccount = account; backupCode = Bridge.newCode();
            setSecure(true);   // no screenshots or recent-apps thumbnail while the code is on screen
            return backupCode;
        }
        /** Step 2 (after the person confirmed the code): the system "save as" dialog; no storage permission involved. */
        @JavascriptInterface public void backupStart() {
            runOnUiThread(() -> {
                if (backupCode == null || backupAccount < 0) return;
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/x-tar");
                i.putExtra(Intent.EXTRA_TITLE, "cabang-backup.tar");
                startActivityForResult(i, 2);
            });
        }
        @JavascriptInterface public void createIdentity() { bridge.createIdentity(); }
        @JavascriptInterface public void backupCancel() { backupCode = null; backupAccount = -1; setSecure(false); }
        @JavascriptInterface public int maxPickBytes() { return MAX_PICK_BYTES; }
    }

    @Override protected void onDestroy() {
        backupCode = null;
        if (bridge != null) bridge.close();
        if (web != null) { web.removeJavascriptInterface("CabangHost"); web.destroy(); web = null; }
        super.onDestroy();
    }

    @Override protected void onActivityResult(int code, int res, Intent data) {
        if (code == 2) {
            Uri u = (res == RESULT_OK && data != null) ? data.getData() : null;
            final String c = backupCode; final int acc = backupAccount;
            backupCode = null; backupAccount = -1; setSecure(false);
            if (u == null || c == null || acc < 0 || bridge == null) { if (web != null) web.evaluateJavascript("window.cabangBackup({cancelled:true})", null); return; }
            final Uri dest = u;
            bridge.backup(acc, c, new File(getCacheDir(), "backup-tmp"),
                () -> getContentResolver().openOutputStream(dest, "wt"),
                () -> getContentResolver().openInputStream(dest));
            return;
        }
        if (code == 1 && pending != null) {
            Uri[] r = (res == RESULT_OK && data != null && data.getData() != null) ? new Uri[]{data.getData()} : null;
            pending.onReceiveValue(r);
            pending = null;
        }
    }
}
