// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 irvan-putra
package com.vanipy.cabang;

import static org.junit.Assert.*;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MainActivityTest {
    @Test public void launcherLabelIsCabangWithoutVersion() throws Exception {
        android.content.Context c = ApplicationProvider.getApplicationContext();
        PackageManager pm = c.getPackageManager();
        assertEquals("Cabang", pm.getApplicationLabel(c.getApplicationInfo()).toString());
        android.content.Intent launcher = pm.getLaunchIntentForPackage(c.getPackageName());
        assertNotNull(launcher);
        assertEquals("Cabang", pm.resolveActivity(launcher, 0).loadLabel(pm).toString());
        assertEquals("com.vanipy.cabang", c.getPackageName());
    }

    @Test public void manifestRequestsOnlyInternet() throws Exception {
        android.content.Context c = ApplicationProvider.getApplicationContext();
        PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), PackageManager.GET_PERMISSIONS);
        assertArrayEquals(new String[]{"android.permission.INTERNET"}, p.requestedPermissions);
    }

    @Test public void webViewIsLockedDown() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).create().get();
        WebView w = (WebView) ((ViewGroup) a.findViewById(android.R.id.content)).getChildAt(0);
        assertTrue(w.getSettings().getJavaScriptEnabled());
        assertFalse(w.getSettings().getAllowFileAccess());
        assertFalse(w.getSettings().getAllowContentAccess());
        assertTrue(w.getSettings().getBlockNetworkLoads());
        assertFalse(w.getSettings().supportMultipleWindows());
        assertTrue(w.getSettings().getDomStorageEnabled());
        assertEquals("file:///android_asset/index.html", Shadows.shadowOf(w).getLastLoadedUrl());
        assertNotNull(Shadows.shadowOf(w).getJavascriptInterface("CabangHost"));
    }

    @Test public void codeScreenBlocksScreenshotsAndAutofillAndIsClearedOnCancel() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).create().get();
        WebView w = (WebView) ((ViewGroup) a.findViewById(android.R.id.content)).getChildAt(0);
        assertEquals(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS, w.getImportantForAutofill());
        int secure = android.view.WindowManager.LayoutParams.FLAG_SECURE;
        assertEquals(0, a.getWindow().getAttributes().flags & secure);
        a.setSecure(true); org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertNotEquals(0, a.getWindow().getAttributes().flags & secure);
        a.setSecure(false); org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertEquals(0, a.getWindow().getAttributes().flags & secure);
    }

    @Test public void pageCannotNavigateAway() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).create().get();
        WebView w = (WebView) ((ViewGroup) a.findViewById(android.R.id.content)).getChildAt(0);
        WebViewClient client = Shadows.shadowOf(w).getWebViewClient();
        WebResourceRequest r = new WebResourceRequest() {
            public android.net.Uri getUrl() { return android.net.Uri.parse("https://example.invalid/"); }
            public boolean isForMainFrame() { return true; }
            public boolean isRedirect() { return false; }
            public boolean hasGesture() { return true; }
            public String getMethod() { return "GET"; }
            public java.util.Map<String, String> getRequestHeaders() { return new java.util.HashMap<>(); }
        };
        assertTrue(client.shouldOverrideUrlLoading(w, r));
        assertNotNull(client.shouldInterceptRequest(w, r));       // replaced by an empty response, never fetched
    }

    @Test public void destroyClosesBridgeWithoutStartingPython() {
        org.robolectric.android.controller.ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create();
        c.destroy();   // no Chaquopy involved: onCreate does not start Python
        assertFalse(com.chaquo.python.Python.isStarted());
    }

    @Test public void onlyPlainBundledAssetsAreServed() {
        String[] ok = {"file:///android_asset/index.html", "file:///android_asset/licenses/a.txt"};
        String[] bad = {"file:///android_asset/../data/x", "file:///android_asset/%2e%2e/x", "file:///android_asset/a%2fb",
                "file:///data/data/x", "https://example.invalid/android_asset/index.html", "file:///android_asset//x", "content://x/android_asset/a"};
        for (String u : ok) assertTrue(u, MainActivity.isBundledAsset(android.net.Uri.parse(u)));
        for (String u : bad) assertFalse(u, MainActivity.isBundledAsset(android.net.Uri.parse(u)));
    }
}
