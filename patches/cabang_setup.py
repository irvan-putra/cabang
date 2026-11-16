# SPDX-License-Identifier: GPL-3.0-only
# Copyright (C) 2026 irvan-putra
"""PROPOSED, not applied: create Cabang's own new chatmail identity (never copies or imports another).

Only called after an explicit tap and relay choice in the app. Needs INTERNET (not enabled in the
reviewed candidate). Credentials stay inside the core's profile in the app's private storage;
nothing is printed or sent to the page.
"""
import re

_HOST = re.compile(r"^(?=.{4,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,}$")


def relay_qr(host):
    host = (host or "").strip().lower()
    if not _HOST.match(host):
        raise ValueError("relay must be a plain domain name like example.org")
    return "dcaccount:" + host


def create_identity(rpc, host, display_name="Cabang"):
    """Adds a NEW empty account to Cabang's own profile directory and configures it on the relay.
    Refuses if Cabang already has a configured account (one identity per install)."""
    qr = relay_qr(host)
    for aid in rpc.get_all_account_ids():
        if rpc.is_configured(aid):
            raise RuntimeError("Cabang already has an account")
    aid = rpc.add_account()
    kind = rpc.check_qr(aid, qr).get("kind")
    if kind not in ("account", "login"):
        rpc.remove_account(aid)
        raise ValueError("not an account-creation link")
    rpc.set_config(aid, "displayname", display_name)
    rpc.add_transport_from_qr(aid, qr)   # network: registers the address on the relay
    rpc.start_io(aid)
    return aid
