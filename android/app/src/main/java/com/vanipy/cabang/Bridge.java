// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 irvan-putra
package com.vanipy.cabang;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Work queue between the page and the Delta Chat core. Everything runs on one background thread
 * (the UI thread is never blocked, core calls never overlap). Inputs from the page are bounded.
 */
final class Bridge {
    interface Core { String snapshotJson() throws Exception; String chatJson(int account, int chat, int limit) throws Exception; void sendReply(int account, int chat, int quoted, String text) throws Exception; String exportBackup(int account, String dir, String code) throws Exception; void createIdentity() throws Exception; void close(); }
    interface Opener { java.io.OutputStream open() throws java.io.IOException; }
    interface Sink { void js(String script); }

    static final int MAX_TEXT = 8000;
    static final int MAX_MESSAGES = 2000;

    private final Core core;
    private final Sink sink;
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "cabang-core"); t.setDaemon(true); return t; });
    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    private volatile boolean closed = false;

    Bridge(Core core, Sink sink) { this.core = core; this.sink = sink; }

    /** A second refresh while one is running is ignored (duplicate open / double tap). */
    boolean refresh() {
        if (closed || !refreshing.compareAndSet(false, true)) return false;
        exec.execute(() -> {
            try {
                String json;
                try { json = core.snapshotJson(); }
                catch (Throwable t) { json = "{\"error\":" + jsString(String.valueOf(t.getMessage())) + "}"; }
                sink.js("window.cabangLoad(JSON.parse(" + jsString(json) + "))");
            } finally { refreshing.set(false); }
        });
        return true;
    }

    /** Loads one chat (newest `limit` messages). Chats load one at a time so a big account is never read in bulk. */
    boolean loadChat(int account, int chat, int limit) {
        if (closed || account < 0 || chat <= 0) return false;
        final int n = Math.max(1, Math.min(limit, MAX_MESSAGES));
        exec.execute(() -> {
            String json;
            try { json = core.chatJson(account, chat, n); }
            catch (Throwable t) { json = "{\"error\":" + jsString(String.valueOf(t.getMessage())) + "}"; }
            sink.js("window.cabangChat(JSON.parse(" + jsString(json) + "))");
        });
        return true;
    }

    boolean sendReply(int account, int chat, int quoted, String text) {
        if (closed || account < 0 || chat <= 0 || quoted <= 0 || text == null) return false;
        final String t = text.trim();
        if (t.isEmpty() || t.length() > MAX_TEXT) return false;
        exec.execute(() -> {
            String res;
            try { core.sendReply(account, chat, quoted, t); res = "{\"ok\":true}"; }
            catch (Throwable e) { res = "{\"error\":" + jsString(String.valueOf(e.getMessage())) + "}"; }
            sink.js("window.cabangSent(JSON.parse(" + jsString(res) + "))");
        });
        return true;
    }

    /** Random code (20 hex chars in groups of 4) that encrypts one backup. Made here, shown on screen, never stored or sent. */
    static String newCode() {
        byte[] b = new byte[10]; new SecureRandom().nextBytes(b);
        StringBuilder h = new StringBuilder();
        for (int i = 0; i < b.length; i++) { if (i > 0 && i % 2 == 0) h.append('-'); h.append(String.format("%02x", b[i] & 0xff)); }
        return h.toString();
    }

    interface Source { InputStream open() throws java.io.IOException; }

    /**
     * Encrypted backup. The page has already shown `code` and the person confirmed writing it down, so a finished file
     * is never left without a known code. The core writes one file into the app-private `tmp` dir, it is copied to the
     * place the person chose (`dest`), then the destination is READ BACK (`readBack`) and its size and SHA-256 must equal
     * the original before success is reported. The temp copy is always deleted. The code is never sent to the page again.
     */
    boolean backup(int account, String code, File tmp, Opener dest, Source readBack) {
        if (closed || account < 0 || dest == null || readBack == null || code == null || code.length() < 12) return false;
        exec.execute(() -> {
            String res;
            try {
                wipe(tmp); tmp.mkdirs();
                String path = core.exportBackup(account, tmp.getPath(), code);
                long[] want, got;
                String wantHash, gotHash;
                try (InputStream in = Files.newInputStream(new File(path).toPath()); OutputStream out = dest.open()) {
                    if (out == null) throw new java.io.IOException("no destination");
                    byte[] buf = new byte[65536]; int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    out.flush();
                }
                try (InputStream in = Files.newInputStream(new File(path).toPath())) { Object[] h = digest(in); want = new long[]{(Long) h[0]}; wantHash = (String) h[1]; }
                try (InputStream in = readBack.open()) {
                    if (in == null) throw new java.io.IOException("cannot read the saved file back");
                    Object[] h = digest(in); got = new long[]{(Long) h[0]}; gotHash = (String) h[1];
                }
                if (want[0] <= 0 || want[0] != got[0] || !wantHash.equals(gotHash))
                    throw new java.io.IOException("the saved file does not match (" + got[0] + " of " + want[0] + " bytes); do not rely on it");
                res = "{\"ok\":true,\"bytes\":" + want[0] + "}";
            } catch (Throwable e) { res = "{\"error\":" + jsString(String.valueOf(e.getMessage()).replace(code, "[code]")) + "}"; }   // a core error that echoed the code can never reach the page
            finally { wipe(tmp); }
            sink.js("window.cabangBackup(JSON.parse(" + jsString(res) + "))");
        });
        return true;
    }

    private static Object[] digest(InputStream in) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] buf = new byte[65536]; int n; long total = 0;
        while ((n = in.read(buf)) > 0) { md.update(buf, 0, n); total += n; }
        StringBuilder h = new StringBuilder();
        for (byte x : md.digest()) h.append(String.format("%02x", x & 0xff));
        return new Object[]{total, h.toString()};
    }

    private static void wipe(File d) {
        File[] fs = d.listFiles();
        if (fs != null) for (File f : fs) f.delete();
    }

    private final AtomicBoolean creating = new AtomicBoolean(false);

    /** Creates this app's own new identity on the fixed relay (one at a time; can take a while on a slow network). */
    boolean createIdentity() {
        if (closed || !creating.compareAndSet(false, true)) return false;
        exec.execute(() -> {
            String res;
            try { core.createIdentity(); res = "{\"ok\":true}"; }
            catch (Throwable e) { res = "{\"error\":" + jsString(String.valueOf(e.getMessage())) + "}"; }
            finally { creating.set(false); }
            sink.js("window.cabangIdentity(JSON.parse(" + jsString(res) + "))");
        });
        return true;
    }

    /** Stops accepting work, lets queued work finish, then closes the core off the UI thread. */
    void close() {
        if (closed) return;
        closed = true;
        exec.execute(core::close);
        exec.shutdown();
    }

    /** Quoted JS string literal. Escapes everything that could end the string or the script. */
    static String jsString(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': b.append("\\\\"); break;
                case '"': b.append("\\\""); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\u2028': b.append("\\u2028"); break;
                case '\u2029': b.append("\\u2029"); break;
                case '<': b.append("\\u003c"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c);
            }
        }
        return b.append('"').toString();
    }
}
