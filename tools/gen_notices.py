#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
# Copyright (C) 2026 irvan-putra
"""Builds assets/notices.js (shown by the in-app Licenses screen) from the license texts in
assets/licenses/. Texts are copied unchanged. Run from the repo root."""
import json, os

D = "android/app/src/main/assets"
ITEMS = [
    ("Cabang (this app)", "0.3.0", "GPL-3.0-only", "cabang-GPL-3.0.txt", "https://www.gnu.org/licenses/gpl-3.0.txt", "license chosen by the owner (GPLv3); \"only\" vs \"or later\" not yet confirmed"),
    ("Delta Chat core RPC server", "2.69.0", "MPL-2.0", "deltachat-core-MPL-2.0.txt",
     "https://github.com/chatmail/core/tree/v2.69.0", "native library lib/arm64-v8a/librpcsrv.so (source at the URL)"),
    ("deltachat-rpc-client", "2.69.0", "MPL-2.0", "deltachat-rpc-client-MPL-2.0.txt",
     "https://pypi.org/project/deltachat-rpc-client/2.69.0/", "Python package"),
    ("Chaquopy", "17.2.0", "MIT", "chaquopy-17.2.0-MIT.txt", "https://github.com/chaquo/chaquopy/tree/17.2.0", "Python runtime for Android"),
    ("CPython", "3.10.22", "PSF License", "cpython-3.10.22-PSF.txt", "https://github.com/python/cpython/tree/v3.10.22", "libpython3.10.so and standard library"),
    ("OpenSSL", "3.5.9", "Apache-2.0", "openssl-3.5.9-Apache-2.0.txt", "https://github.com/openssl/openssl/tree/openssl-3.5.9", "libcrypto/libssl shipped with Chaquopy"),
    ("setuptools", "68.2.2", "MIT", "setuptools-68.2.2-MIT.txt", "https://pypi.org/project/setuptools/68.2.2/", "Chaquopy bootstrap"),
    ("construct (vendored in pyelftools)", "0.26", "MIT", "construct-0.26-MIT.txt", "https://github.com/eliben/pyelftools/tree/v0.26/elftools/construct", "Chaquopy bootstrap"),
    ("SQLite", "3.53.4 (per the Chaquopy 17.2.0 build recipe)", "Public domain", "sqlite-public-domain.txt", "https://sqlite.org/copyright.html", "libsqlite3_chaquopy.so, libsqlite3_python.so"),
    ("CA certificate bundle cacert.pem = certifi", "2026.10.23", "MPL-2.0 (certifi's license file)", "certifi-2026.10.23-MPL-2.0.txt", "https://pypi.org/project/certifi/2026.10.23/", "assets/chaquopy/cacert.pem is byte-identical (sha256 2ef52ad5...44b8abd2b) to certifi 2026.10.23's certifi/cacert.pem"),
    ("bzip2 / libbzip2", "1.0.8", "bzip2 license (BSD-style)", "bzip2-1.0.8.txt", "https://sourceware.org/git/?p=bzip2.git;a=tag;h=refs/tags/bzip2-1.0.8", "statically in _bz2 (version string in the binary reads 1.0.8, 13-Jul-2019)"),
    ("XZ Utils / liblzma", "5.4.6", "public domain and 0BSD per the COPYING file", "xz-5.4.6-COPYING.txt", "https://github.com/tukaani-project/xz/tree/v5.4.6", "statically in _lzma (version string 5.4.6)"),
    ("libffi", "3.4.4", "MIT", "libffi-3.4.4-MIT.txt", "https://github.com/libffi/libffi/tree/v3.4.4", "in _ctypes. Version from the Chaquopy 17.2.0 build recipe (libffi-3.4.4-3), not from the binary: https://github.com/chaquo/chaquopy/blob/17.2.0/target/python/build.sh"),
    ("SQLCipher (vendored by the core's rusqlite)", "from libsqlite3-sys 0.35.0", "BSD-style (Zetetic)", "sqlcipher-in-libsqlite3-sys-0.35.0.txt", "https://crates.io/crates/libsqlite3-sys/0.35.0", "Core server default feature vendored = rusqlite/bundled-sqlcipher-vendored-openssl (Cargo.toml v2.69.0). Presence in the binary not checked."),
    ("OpenSSL (vendored by the core, openssl-src)", "3.4.1", "Apache-2.0", "openssl-3.4.1-in-openssl-src.txt", "https://crates.io/crates/openssl-src/300.4.2+3.4.1", "Core server vendored feature. Presence in the binary not checked."),
    ("pyelftools", "0.26", "Unlicense (public domain)", "pyelftools-0.26.txt", "https://pypi.org/project/pyelftools/0.26/", "Chaquopy bootstrap"),
]
out = [{"name": n, "version": v, "license": l, "source": u, "where": w,
        "text": open(f"{D}/licenses/{f}", encoding="utf-8").read()} for n, v, l, f, u, w in ITEMS]
note = ("This list is not complete. Not yet included: "
        "the Rust crates in the core server: their license files are collected in THIRD_PARTY-core-android-license-texts.txt in the source (cargo tree closure for aarch64-linux-android; not matched to the binary; not shown here yet).")
open(f"{D}/notices.js", "w", encoding="utf-8").write("window.CABANG_NOTICES=" + json.dumps({"items": out, "note": note}, ensure_ascii=False) + ";\n")
print(len(out), "components")
