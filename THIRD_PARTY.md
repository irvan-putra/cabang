# Third-party components

Bundled in the Android app (exact versions, pins in `tools/core.lock` and `android/app/requirements.txt`):

| Component | Version | License | Where |
|---|---|---|---|
| Delta Chat core RPC server (`deltachat-rpc-server`, Android arm64 wheel from PyPI) | 2.69.0 | MPL-2.0 | `lib/arm64-v8a/librpcsrv.so`; text in `assets/licenses/deltachat-core-MPL-2.0.txt` |
| `deltachat-rpc-client` (Python, from PyPI) | 2.69.0 | MPL-2.0 | text in `assets/licenses/deltachat-rpc-client-MPL-2.0.txt` |
| Chaquopy (Python for Android) runtime | 17.2.0 | MIT | `assets/licenses/chaquopy-17.2.0-MIT.txt` |
| CPython (libpython3.10.so and stdlib) | 3.10.22 | PSF License | `assets/licenses/cpython-3.10.22-PSF.txt` |
| OpenSSL (libcrypto/libssl shipped by Chaquopy) | 3.5.9 | Apache-2.0 | `assets/licenses/openssl-3.5.9-Apache-2.0.txt` |
| setuptools (Chaquopy bootstrap) | 68.2.2 | MIT | `assets/licenses/setuptools-68.2.2-MIT.txt` |
| pyelftools (Chaquopy bootstrap) | 0.26 | Unlicense | `assets/licenses/pyelftools-0.26.txt` |
| construct, vendored in pyelftools | 0.26 | MIT | `assets/licenses/construct-0.26-MIT.txt` (identical to upstream v0.26 file) |

Inventory source: the built v0.2.5 debug APK (lib/arm64-v8a: libchaquopy_java, libpython3.10,
libcrypto/libssl (chaquopy and python variants), libsqlite3 (chaquopy and python variants), librpcsrv;
assets/chaquopy: bootstrap, stdlib, requirements, cacert.pem). Texts are copied unchanged. CPython,
Chaquopy and Delta Chat texts were fetched here and are byte-identical to the independently
prepared candidates from the Intodo notice research. The librpcsrv.so in the APK has the same
sha256 (101c7c59...257) as the one in the Intodo APK build.

Source for the MPL components: https://github.com/chatmail/core/tree/v2.69.0 (the server binary is
built by the Delta Chat project; Cabang does not modify it).

Not covered yet: `cacert.pem` (Mozilla CA bundle, MPL-2.0 data file), SQLite (public domain),
the other native libraries Chaquopy ships, and the Rust and C libraries statically linked into the
Delta Chat core server (closure not traced). This file does not claim a complete notice bundle or
legal compliance. The in-app Licenses screen shows the texts above.

Update (0.2.5): the Licenses screen also lists SQLite (public-domain pointer) and the cacert.pem CA bundle (MPL-2.0
attribution is a presumption: the file has no header). `THIRD_PARTY-core-crates.md` lists the 738 packages in the core's
Cargo.lock with declared licenses (audit list only; not matched to the binary, no texts collected).

Update (0.2.6): libffi 3.4.4, bzip2 1.0.8, xz 5.4.6, OpenSSL 3.5.9, SQLite 3.53.4 are the versions in the Chaquopy 17.2.0 recipe
(https://github.com/chaquo/chaquopy/blob/17.2.0/target/python/build.sh, `libs=` line); bzip2, xz and OpenSSL also match what the binaries report.
Core server: Cargo.toml default feature `vendored` bundles SQLCipher and OpenSSL 3.4.1 (openssl-src 300.4.2+3.4.1); their texts are in the Licenses screen.
`THIRD_PARTY-core-license-texts.txt` collects the license files shipped inside 696 reachable crates (435 distinct texts), 41 crates ship none.
This is reachability over all targets in Cargo.lock, not the binary's actual contents.

Update (v12): the all-target `THIRD_PARTY-core-license-texts.txt` is replaced by `THIRD_PARTY-core-android-license-texts.txt`: license files from the
483 crates in cargo's normal-dependency closure of deltachat-rpc-server for aarch64-linux-android (default features, tag v2.69.0 / commit 189c007b),
minus 49 proc-macro crates, plus openssl-src. Build recipe owner: chatmail/core nix/android-package.nix. Not checked against the shipped wheel binary.
`tools/offline_backup_probe.py` is the offline backup and logging probe (disposable accounts, no network).
The APK built as 0.2.6 predates the updated in-app note; rebuild before any delivery.

License compatibility note (not legal advice): Cabang is GPL-3.0-only. The bundled components are MPL-2.0 (the core runs as a separate process),
Apache-2.0 (OpenSSL 3.x), PSF, MIT, BSD-style, ISC, Zlib and public domain, which are generally usable in a GPL-3.0 work; MPL-2.0 files stay under MPL-2.0 and
their source is available at the tag above. This has not been reviewed by a lawyer.
