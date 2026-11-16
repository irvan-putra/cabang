# SPDX-License-Identifier: GPL-3.0-only
# Copyright (C) 2026 irvan-putra
import os, shutil, sys, tempfile, unittest
sys.path.insert(0, os.path.dirname(__file__))
import cabang_core as c


class FakeRpc:
    def __init__(self, configured=True, chat_of_quoted=7):
        self.configured, self.q_chat, self.sent = configured, chat_of_quoted, None
    def is_configured(self, a): return self.configured
    def get_message(self, a, m): return {"id": m, "chatId": self.q_chat}
    def send_msg(self, a, ch, data): self.sent = (a, ch, data); return 99


class SendTests(unittest.TestCase):
    def test_sets_quoted_message_id(self):
        r = FakeRpc(); self.assertEqual(c.send_reply(r, 1, 7, 55, " hi "), 99)
        self.assertEqual(r.sent, (1, 7, {"text": "hi", "quotedMessageId": 55}))
    def test_refuses_bad_ids_and_long_text(self):
        with self.assertRaises(ValueError): c.send_reply(FakeRpc(), 1, 7, 0, "x")
        with self.assertRaises(ValueError): c.send_reply(FakeRpc(), 1, 7, 55, "x" * 8001)
    def test_refuses_empty_unconfigured_wrong_chat(self):
        with self.assertRaises(ValueError): c.send_reply(FakeRpc(), 1, 7, 55, "  ")
        with self.assertRaises(RuntimeError): c.send_reply(FakeRpc(configured=False), 1, 7, 55, "x")
        with self.assertRaises(ValueError): c.send_reply(FakeRpc(chat_of_quoted=8), 1, 7, 55, "x")


class BackupHelperTests(unittest.TestCase):
    def test_backup_rules_and_path(self):
        import tempfile, os
        d = tempfile.mkdtemp()
        class R(FakeRpc):
            def export_backup(self, a, dest, code): self.args = (a, dest, code); open(os.path.join(dest, "delta-chat-backup-x.tar"), "w").write("t")
        r = R()
        p = c.export_backup(r, 1, d, "aaaa-bbbb-cccc-dddd-eeee")
        self.assertTrue(p.endswith("delta-chat-backup-x.tar")); self.assertEqual(r.args, (1, d, "aaaa-bbbb-cccc-dddd-eeee"))
        with self.assertRaises(ValueError): c.export_backup(FakeRpc(), 1, d, "short")
        with self.assertRaises(RuntimeError): c.export_backup(FakeRpc(configured=False), 1, d, "aaaa-bbbb-cccc-dddd-eeee")


class IdentityTests(unittest.TestCase):
    class R:
        def __init__(self, existing=(), fail=False, kind="account", configured_after=True):
            self.accts = {i: True for i in existing}; self.fail, self.kind, self.ca = fail, kind, configured_after
            self.removed, self.cfg, self.qr_used = [], {}, None
        def get_all_account_ids(self): return list(self.accts)
        def is_configured(self, a): return self.accts[a]
        def add_account(self): self.accts[9] = False; return 9
        def check_qr(self, a, q): self.qr_used = q; return {"kind": self.kind}
        def set_config(self, a, k, v): self.cfg[k] = v
        def add_transport_from_qr(self, a, q):
            if self.fail: raise RuntimeError("network down")
            self.accts[a] = self.ca
        def remove_account(self, a): self.removed.append(a); del self.accts[a]

    def test_creates_on_fixed_relay(self):
        r = self.R(); self.assertEqual(c.create_identity(r), 9)
        self.assertEqual(r.qr_used, "dcaccount:nine.testrun.org"); self.assertEqual(r.removed, [])
    def test_refuses_when_configured(self):
        r = self.R(existing=[1]); self.assertRaises(RuntimeError, c.create_identity, r); self.assertNotIn(9, r.accts)
    def test_failure_never_touches_a_pre_existing_unconfigured_account(self):
        r = self.R(); r.accts[3] = False; r.fail = True
        self.assertRaises(Exception, c.create_identity, r)
        self.assertEqual(r.removed, [9]); self.assertIn(3, r.accts)
    def test_failure_removes_half_made_account(self):
        for kw in ({"fail": True}, {"kind": "text"}, {"configured_after": False}):
            r = self.R(**kw)
            with self.assertRaises(RuntimeError) as cm: c.create_identity(r)
            self.assertEqual(r.removed, [9]); self.assertEqual(r.accts, {}); self.assertIn("cannot delete anything", str(cm.exception))


class EventGapTest(unittest.TestCase):
    def test_enum_gap_is_in_client_layer_only(self):
        from deltachat_rpc_client.const import EventType
        with self.assertRaises(ValueError):   # the known 2.69.0 gap: enum lacks the newer event
            EventType("PinnedMessagesChanged")
        import inspect, deltachat_rpc_client.rpc as r
        self.assertNotIn("EventType", inspect.getsource(r))   # Rpc layer passes raw dicts


@unittest.skipUnless(shutil.which("deltachat-rpc-server"), "needs deltachat-rpc-server on PATH")
class SyntheticProfile(unittest.TestCase):
    """Real 2.69.0 server, empty temp profile, no network, no account setup."""
    def test_snapshot_of_unconfigured_profile(self):
        from deltachat_rpc_client import DeltaChat
        d = tempfile.mkdtemp()
        rpc = c.open_rpc(d)
        try:
            a = DeltaChat(rpc).add_account()
            rpc.add_device_message(a.id, "t1", {"text": "synthetic one"}); rpc.add_device_message(a.id, "t2", {"text": "synthetic two"})
            s = c.snapshot(rpc)
            self.assertFalse(s["accounts"][0]["configured"])
            self.assertNotIn("messages", s["accounts"][0]["chats"][0])   # list only, no bulk load
            self.assertIsNone(s["accounts"][0]["fp"] if not s["accounts"][0]["configured"] else None)
            d = c.chat_data(rpc, a.id, s["accounts"][0]["chats"][0]["id"], limit=1)
            self.assertEqual(d["shown"], 1); self.assertEqual(d["total"], 2)   # bounded, newest first kept
            m = d["messages"][0]
            self.assertEqual(m["text"], "synthetic two"); self.assertTrue(m["mid"])   # portable id present
            self.assertEqual(m["account"], str(a.id)); self.assertIsNone(m["quote"])
        finally:
            c.close_rpc(rpc); c.close_rpc(rpc)   # double close is safe
            shutil.rmtree(d, ignore_errors=True)


if __name__ == "__main__":
    unittest.main()
