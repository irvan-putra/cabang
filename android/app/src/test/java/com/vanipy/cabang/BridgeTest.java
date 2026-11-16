// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 irvan-putra
package com.vanipy.cabang;

import static org.junit.Assert.*;

import java.util.List;
import java.util.concurrent.*;
import org.junit.Test;

public class BridgeTest {
    static class FakeCore implements Bridge.Core {
        final CountDownLatch gate = new CountDownLatch(1);
        volatile int snapshots, sends, closes; volatile String thread; volatile boolean block, fail;
        public String snapshotJson() throws Exception {
            snapshots++; thread = Thread.currentThread().getName();
            if (block) gate.await(5, TimeUnit.SECONDS);
            if (fail) throw new RuntimeException("boom \"x\"\n</script>");
            return "{\"accounts\":[]}";
        }
        volatile int lastLimit;
        public String chatJson(int a, int c, int n) { lastLimit = n; return "{\"messages\":[]}"; }
        public void sendReply(int a, int c, int q, String t) { sends++; }
        volatile String lastCode; volatile boolean backupFail;
        public String exportBackup(int a, String dir, String code) throws Exception {
            lastCode = code; if (backupFail) throw new RuntimeException("nope " + code + " echoed");
            java.io.File f = new java.io.File(dir, "b.tar"); java.nio.file.Files.write(f.toPath(), "DATA".getBytes()); return f.getPath();
        }
        volatile int creates; volatile boolean createFail;
        public void createIdentity() throws Exception { creates++; if (createFail) throw new RuntimeException("net down"); }
        public void close() { closes++; }
    }

    private static List<String> sinkList() { return new CopyOnWriteArrayList<>(); }

    @Test public void refreshRunsOffCallerThreadAndDeliversJson() throws Exception {
        FakeCore core = new FakeCore(); List<String> out = sinkList(); CountDownLatch l = new CountDownLatch(1);
        Bridge b = new Bridge(core, js -> { out.add(js); l.countDown(); });
        assertTrue(b.refresh()); assertTrue(l.await(3, TimeUnit.SECONDS));
        assertEquals("cabang-core", core.thread);
        assertTrue(out.get(0).startsWith("window.cabangLoad(JSON.parse(\""));
    }

    @Test public void duplicateRefreshWhileRunningIsIgnored() throws Exception {
        FakeCore core = new FakeCore(); core.block = true; CountDownLatch l = new CountDownLatch(1);
        Bridge b = new Bridge(core, js -> l.countDown());
        assertTrue(b.refresh()); assertFalse(b.refresh()); assertFalse(b.refresh());
        core.gate.countDown(); assertTrue(l.await(3, TimeUnit.SECONDS));
        Thread.sleep(50);
        assertEquals(1, core.snapshots);
        assertTrue(b.refresh());   // allowed again after completion
    }

    @Test public void errorTextCannotBreakOutOfTheScriptString() throws Exception {
        FakeCore core = new FakeCore(); core.fail = true; List<String> out = sinkList(); CountDownLatch l = new CountDownLatch(1);
        new Bridge(core, js -> { out.add(js); l.countDown(); }).refresh();
        assertTrue(l.await(3, TimeUnit.SECONDS));
        String js = out.get(0);
        assertFalse(js.contains("</script>")); assertFalse(js.contains("\n"));
        assertTrue(js.contains("\\u003c/script>") || js.contains("\\u003c"));
    }

    @Test public void jsStringEscapes() {
        assertEquals("\"a\\\\b\\\"c\\nd\\u2028\\u003c\\u0001\"", Bridge.jsString("a\\b\"c\nd\u2028<\u0001"));
    }

    @Test public void replyInputsAreBounded() throws Exception {
        FakeCore core = new FakeCore(); Bridge b = new Bridge(core, js -> {});
        assertFalse(b.sendReply(1, 0, 5, "x")); assertFalse(b.sendReply(1, 3, -1, "x"));
        assertFalse(b.sendReply(1, 3, 5, null)); assertFalse(b.sendReply(1, 3, 5, "   "));
        assertFalse(b.sendReply(1, 3, 5, new String(new char[Bridge.MAX_TEXT + 1]).replace('\0', 'a')));
        assertTrue(b.sendReply(1, 3, 5, " ok "));
        b.close(); Thread.sleep(200);
        assertEquals(1, core.sends); assertEquals(1, core.closes);
    }

    @Test public void closeStopsNewWorkAndClosesCoreOnce() throws Exception {
        FakeCore core = new FakeCore(); Bridge b = new Bridge(core, js -> {});
        b.close(); b.close(); Thread.sleep(200);
        assertEquals(1, core.closes); assertFalse(b.refresh()); assertFalse(b.sendReply(1, 3, 5, "x"));
    }

    @Test public void loadChatIsBounded() throws Exception {
        FakeCore core = new FakeCore(); List<String> out = sinkList(); CountDownLatch l = new CountDownLatch(1);
        Bridge b = new Bridge(core, js -> { out.add(js); l.countDown(); });
        assertFalse(b.loadChat(-1, 3, 10)); assertFalse(b.loadChat(1, 0, 10));
        assertTrue(b.loadChat(1, 3, 1_000_000)); assertTrue(l.await(3, TimeUnit.SECONDS));
        assertEquals(Bridge.MAX_MESSAGES, core.lastLimit);
        assertTrue(out.get(0).startsWith("window.cabangChat(JSON.parse("));
    }

    private static final String CODE = "0123-4567-89ab-cdef-0123";

    @Test public void backupCopiesReadsBackVerifiesAndNeverSendsCode() throws Exception {
        FakeCore core = new FakeCore(); List<String> out = sinkList(); CountDownLatch l = new CountDownLatch(1);
        Bridge b = new Bridge(core, js -> { out.add(js); l.countDown(); });
        java.io.File tmp = java.nio.file.Files.createTempDirectory("bk").toFile();
        java.io.ByteArrayOutputStream dest = new java.io.ByteArrayOutputStream();
        assertTrue(b.backup(1, CODE, tmp, () -> dest, () -> new java.io.ByteArrayInputStream(dest.toByteArray()))); assertTrue(l.await(3, TimeUnit.SECONDS));
        assertEquals("DATA", dest.toString()); assertEquals(CODE, core.lastCode);
        assertEquals(0, tmp.listFiles().length);
        assertTrue(out.get(0).contains("ok")); assertFalse(out.get(0).contains(CODE));
    }

    @Test public void backupTruncatedOrMissingDestinationIsNotSuccess() throws Exception {
        for (int mode = 0; mode < 2; mode++) {
            FakeCore core = new FakeCore(); List<String> out = sinkList(); CountDownLatch l = new CountDownLatch(1);
            Bridge b = new Bridge(core, js -> { out.add(js); l.countDown(); });
            java.io.File tmp = java.nio.file.Files.createTempDirectory("bk").toFile();
            final int m = mode;
            assertTrue(b.backup(1, CODE, tmp, () -> new java.io.ByteArrayOutputStream(),
                () -> m == 0 ? new java.io.ByteArrayInputStream("DA".getBytes()) : null)); assertTrue(l.await(3, TimeUnit.SECONDS));
            assertTrue(out.get(0).contains("error")); assertFalse(out.get(0).contains("\"ok\""));
            assertEquals(0, tmp.listFiles().length);
        }
    }

    @Test public void backupSameLengthDifferentContentIsRejected() throws Exception {
        FakeCore core = new FakeCore(); List<String> out = sinkList(); CountDownLatch l = new CountDownLatch(1);
        Bridge b = new Bridge(core, js -> { out.add(js); l.countDown(); });
        java.io.File tmp = java.nio.file.Files.createTempDirectory("bk").toFile();
        assertTrue(b.backup(1, CODE, tmp, () -> new java.io.ByteArrayOutputStream(), () -> new java.io.ByteArrayInputStream("DATX".getBytes()))); assertTrue(l.await(3, TimeUnit.SECONDS));
        assertTrue(out.get(0).contains("error"));
    }

    @Test public void backupCoreFailureLeavesNoTempAndBadInputsRefused() throws Exception {
        FakeCore core = new FakeCore(); core.backupFail = true; List<String> out = sinkList(); CountDownLatch l = new CountDownLatch(1);
        Bridge b = new Bridge(core, js -> { out.add(js); l.countDown(); });
        java.io.File tmp = java.nio.file.Files.createTempDirectory("bk").toFile();
        assertTrue(b.backup(1, CODE, tmp, () -> new java.io.ByteArrayOutputStream(), () -> null)); assertTrue(l.await(3, TimeUnit.SECONDS));
        assertTrue(out.get(0).contains("error")); assertFalse(out.get(0).contains(CODE)); assertTrue(out.get(0).contains("[code]")); assertEquals(0, tmp.listFiles().length);
        assertFalse(b.backup(-1, CODE, tmp, () -> null, () -> null)); assertFalse(b.backup(1, "short", tmp, () -> null, () -> null));
    }

    @Test public void codeFormatAndUniqueness() {
        String a = Bridge.newCode(), c = Bridge.newCode();
        assertTrue(a.matches("[0-9a-f]{4}(-[0-9a-f]{4}){4}")); assertNotEquals(a, c);
    }

    @Test public void createIdentityRunsOnceAtATimeAndReportsErrors() throws Exception {
        FakeCore core = new FakeCore(); List<String> out = sinkList(); CountDownLatch l = new CountDownLatch(1);
        Bridge b = new Bridge(core, js -> { out.add(js); l.countDown(); });
        assertTrue(b.createIdentity()); assertTrue(l.await(3, TimeUnit.SECONDS));
        assertTrue(out.get(0).startsWith("window.cabangIdentity(")); assertTrue(out.get(0).contains("ok")); assertEquals(1, core.creates);
        FakeCore bad = new FakeCore(); bad.createFail = true; List<String> o2 = sinkList(); CountDownLatch l2 = new CountDownLatch(1);
        Bridge b2 = new Bridge(bad, js -> { o2.add(js); l2.countDown(); });
        assertTrue(b2.createIdentity()); assertTrue(l2.await(3, TimeUnit.SECONDS)); assertTrue(o2.get(0).contains("net down"));
    }
}
