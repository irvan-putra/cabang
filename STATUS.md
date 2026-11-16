# Cabang status (public-safe checkpoint, source 0.3.0 + docs; not released)

Decisions (from the owner): license GPL-3.0-only (the owner said "GPLv3"; "or later" not chosen); a NEW Cabang identity on nine.testrun.org, created
on the phone by a button; no import of any other profile.

Implemented in source and host-tested: threaded view (real quote links vs transport parent vs optional guess), link overlay, chat picker, Android host
(Chaquopy + the official pinned core wheel as librpcsrv.so), identity-creation button, encrypted-backup flow code, Licenses screen, CI workflow.
Python: 10 tests passed, 0 skipped. Gradle: BridgeTest 13/13, MainActivityTest 6/6, and assembleDebug passed. Node tree tests passed.
The backup flow was exercised only with a disposable pseudo-configured host fixture, not on Android or a real account. The local build APK was not inspected or delivered.

What was run, precisely: on a Linux host, against the official Linux x86_64 core 2.69.0, offline, with a disposable pseudo-configured fixture:
export, wrong-code rejection, right-code import, passphrase absent from events/stderr/files; and no sockets opened by core startup with no account.
NOT tested: Android runtime (Chaquopy start, native server exec, WebView, SAF dialog), real account creation/traffic on a relay, backup of a configured profile, or a real sent reply.

Open:
- Never run on Android; no emulator available.
- No account created anywhere; creation failure can leave a relay-side orphan the app cannot delete; a killed process can leave a local "[not set up]" account (not removed automatically).
- Relay retention: its info page says 20 days, its privacy page says usually 4-8 weeks; no guaranteed deletion.
- Notice texts: Rust crate closure computed from cargo tree on the tag, not matched to the shipped binary; NDK notices not fetched; crate texts not shown in-app.
- Wheel not rebuilt from source. Not published; no release.
