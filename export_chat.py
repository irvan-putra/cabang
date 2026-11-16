#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
# Copyright (C) 2026 irvan-putra
"""Export one chat from YOUR OWN Delta Chat account into a file Cabang can open.

Runs locally, read-only, no network sends. Needs: pip install deltachat-rpc-client
and deltachat-rpc-server on PATH, plus an account directory you point at (use a copy
of a profile or a backup import, never write to your live profile).

  python3 export_chat.py --accounts ./accounts-copy --chat 13 --out chat.json
"""
import argparse, json
from deltachat_rpc_client import DeltaChat, Rpc

ap = argparse.ArgumentParser()
ap.add_argument("--accounts", required=True)
ap.add_argument("--chat", type=int, required=True, help="chat id (account-local number)")
ap.add_argument("--out", required=True)
a = ap.parse_args()

with Rpc(accounts_dir=a.accounts) as rpc:
    acc = DeltaChat(rpc).get_all_accounts()[0] if hasattr(DeltaChat(rpc), "get_all_accounts") else DeltaChat(rpc).get_accounts()[0]
    aid = acc.id
    ids = rpc.get_message_ids(aid, a.chat, False, False)
    out = []
    for mid, m in rpc.get_messages(aid, ids).items():
        if m.get("kind") == "message" or "text" in m:
            q = m.get("quote")
            out.append({
                "id": m["id"], "timestamp": m.get("timestamp"),
                "sender": (m.get("sender") or {}).get("displayName") or "?",
                "text": m.get("text") or "",
                "parentId": m.get("parentId"),
                "mid": rpc.get_message_info_object(aid, m["id"]).get("rfc724Mid"),
                "quote": ({"kind": q["kind"], "messageId": q.get("messageId"), "text": q.get("text")} if q else None),
            })
    json.dump({"source": "delta-chat jsonrpc", "messages": out}, open(a.out, "w"), ensure_ascii=False, indent=1)
    print(len(out), "messages written to", a.out)
