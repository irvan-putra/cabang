# SPDX-License-Identifier: GPL-3.0-only
# Copyright (C) 2026 irvan-putra
"""Cabang core: reads Delta Chat through its JSON-RPC server and returns plain data for the
threaded view. It creates ONE new identity of its own, only when the person taps the button
(create_identity); it never imports keys or accounts from anywhere. Only accounts in Cabang's own
accounts directory are used.

Message links are passed through as Delta Chat reports them (`quote`, `parentId`); deciding what
is a real reply happens in threads.js.
"""
import json

MSG_FIELDS = ("id", "timestamp", "text", "parent_id", "quote", "is_info", "from_id", "sender")


def _drain_events(rpc, every=5.0):
    """Rpc.start() runs an event thread that queues every core event per account forever.
    Cabang does not use events, so empty the queues or memory grows for as long as the app runs.
    (Events are raw dicts at this level; the PinnedMessagesChanged enum gap only breaks
    deltachat_rpc_client.client.Client/Bot, which Cabang does not use.)"""
    import time
    while not getattr(rpc, "closing", False):
        for aid in list(getattr(rpc, "event_queues", {})):
            rpc.clear_all_events(aid)
        time.sleep(every)


def open_rpc(accounts_dir, server_path=None):
    import threading
    from deltachat_rpc_client import Rpc
    kw = {"accounts_dir": accounts_dir}
    if server_path:
        kw["rpc_server_path"] = server_path
    rpc = Rpc(**kw)
    rpc.start()
    threading.Thread(target=_drain_events, args=(rpc,), daemon=True).start()
    return rpc


def close_rpc(rpc):
    """Stop the server process and its threads. Safe to call twice."""
    try:
        rpc.close()
    except Exception:
        pass


def _fingerprint(rpc, aid):
    """Stable id of the identity behind an account: hash of self address and self key fingerprint.
    None when the account has no usable identity yet (then nothing is persisted for it)."""
    import hashlib
    try:
        me = rpc.get_contact(aid, 1)   # DC_CONTACT_ID_SELF
        addr, key = me.get("address"), me.get("fingerprint")
    except Exception:
        return None
    if not addr or not key:
        return None
    return hashlib.sha256((addr + "|" + key).encode()).hexdigest()[:16]


def _msg(account_id, m, mid, quote_mid):
    sender = m.get("sender") or {}
    q = m.get("quote")
    if q:
        q = {"kind": q.get("kind"), "messageId": q.get("messageId"), "text": q.get("text"),
             "chatId": q.get("chatId"), "mid": quote_mid}
    return {
        "account": str(account_id), "id": m["id"], "mid": mid, "timestamp": m.get("timestamp"),
        "sender": sender.get("displayName") or "?", "text": m.get("text") or "",
        "parentId": m.get("parentId"), "quote": q, "isInfo": bool(m.get("isInfo")),
    }


def snapshot(rpc):
    """Accounts and chat list only (no messages): messages are loaded per chat with chat_data."""
    out = {"accounts": []}
    for aid in rpc.get_all_account_ids():
        acc = {"id": aid, "configured": bool(rpc.is_configured(aid)), "fp": _fingerprint(rpc, aid), "chats": []}
        for entry in rpc.get_chatlist_entries(aid, 0, None, None):
            info = rpc.get_basic_chat_info(aid, entry)
            acc["chats"].append({"id": entry, "name": info.get("name") or "?"})
        out["accounts"].append(acc)
    return out


def chat_data(rpc, account_id, chat_id, limit=500):
    """Newest `limit` messages of ONE chat. `mid` is the portable RFC 724 Message-ID (same on every
    device and after a restore); local ids are not."""
    limit = max(1, min(int(limit), 2000))
    ids = rpc.get_message_ids(account_id, chat_id, False, False)
    total = len(ids)
    ids = ids[-limit:]
    msgs = rpc.get_messages(account_id, ids) if ids else {}
    mids = {}
    for i in ids:
        try:
            mids[i] = rpc.get_message_info_object(account_id, i).get("rfc724Mid")
        except Exception:
            mids[i] = None
    rows = []
    for m in msgs.values():
        if m.get("kind", "message") != "message":
            continue
        q = m.get("quote") or {}
        qm = None
        if q.get("kind") == "WithMessage":
            qid = q.get("messageId")
            if qid in mids:
                qm = mids[qid]
            else:
                try:
                    qm = rpc.get_message_info_object(account_id, qid).get("rfc724Mid")
                except Exception:
                    qm = None
        rows.append(_msg(account_id, m, mids.get(m["id"]), qm))
    return {"account": account_id, "chat": chat_id, "total": total, "shown": len(rows),
            "fp": _fingerprint(rpc, account_id), "messages": rows}


def snapshot_json(rpc):
    return json.dumps(snapshot(rpc), ensure_ascii=False)


def chat_json(rpc, account_id, chat_id, limit=500):
    return json.dumps(chat_data(rpc, account_id, chat_id, limit), ensure_ascii=False)


def send_reply(rpc, account_id, chat_id, quoted_msg_id, text):
    """Send `text` as a real reply: sets quotedMessageId, the field that makes a branch real.
    Refuses empty text and a quoted message that is not in this chat. Needs a configured account."""
    text = (text or "").strip()
    if not text:
        raise ValueError("empty reply")
    if not isinstance(chat_id, int) or not isinstance(quoted_msg_id, int) or chat_id <= 0 or quoted_msg_id <= 0:
        raise ValueError("bad id")
    if len(text) > 8000:
        raise ValueError("reply too long")
    if not rpc.is_configured(account_id):
        raise RuntimeError("account is not set up; use the identity setup button first")
    target = rpc.get_message(account_id, quoted_msg_id)
    if target.get("chatId") != chat_id:
        raise ValueError("the quoted message is not in this chat")
    return rpc.send_msg(account_id, chat_id, {"text": text, "quotedMessageId": quoted_msg_id})


def export_backup(rpc, account_id, directory, code):
    """Writes an encrypted full backup (keys and messages) of one account into `directory` and returns the
    file path. The call blocks until the core has finished (it writes a .part file and renames it).
    The code is made on the device by the caller. Untested against a real account so far."""
    import os
    if not code or len(code) < 12:
        raise ValueError("transfer code too short")
    if not rpc.is_configured(account_id):
        raise RuntimeError("This account is not set up yet, so there is nothing to back up")
    rpc.export_backup(account_id, directory, code)
    tars = sorted(f for f in os.listdir(directory) if f.endswith(".tar"))
    if not tars:
        raise RuntimeError("backup file was not written")
    return os.path.join(directory, tars[-1])


RELAY = "nine.testrun.org"   # chosen by the user; fixed here, the page cannot pick another host


def start_configured_io(rpc):
    """After a restart, receiving needs I/O running for each already configured account."""
    for aid in rpc.get_all_account_ids():
        if rpc.is_configured(aid):
            rpc.start_io(aid)


def create_identity(rpc, display_name="Cabang"):
    """Creates Cabang's OWN new chatmail identity on RELAY. Keys are made by the core inside this app's
    private storage; nothing is imported or copied from another app. One identity per install: refuses
    if a configured account exists. On any failure the half-made account is removed. Network call."""
    for aid in rpc.get_all_account_ids():
        if rpc.is_configured(aid):
            raise RuntimeError("Cabang already has an account")
    qr = "dcaccount:" + RELAY
    aid = rpc.add_account()
    try:
        if rpc.check_qr(aid, qr).get("kind") not in ("account", "login"):
            raise ValueError("not an account-creation link")
        rpc.set_config(aid, "displayname", display_name)
        rpc.add_transport_from_qr(aid, qr)   # registers the address on the relay and starts I/O
        if not rpc.is_configured(aid):
            raise RuntimeError("the relay did not finish setting up the account")
    except Exception as e:   # not BaseException: a killed process or interrupt skips this, see below
        # If the process is killed mid-way (not an Exception), the half-made, unconfigured account stays in the
        # app's accounts dir; nothing removes it automatically. It is not destructive: it shows as "[not set up]".
        # Removing the local account does NOT delete anything on the relay. If the relay had already accepted
        # the new login before this failed, an unused account may remain there and a retry makes another one.
        try:
            rpc.remove_account(aid)
        except Exception:
            pass
        raise RuntimeError("%s\n\nThe local half-made profile was removed. This app cannot delete anything on %s: if "
                           "the failure came after the relay accepted the sign-in, an unused account may exist there, "
                           "and trying again creates a new one." % (e, RELAY)) from e
    return aid
