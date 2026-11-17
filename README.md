# cabang

Threaded (indented, collapsible) view for Delta Chat conversations. Prototype.

License: GPL-3.0-only (see `LICENSE`). Third-party components keep their own licenses: see `THIRD_PARTY.md`.
Status and open items: `STATUS.md`.

The static browser view reads a chat export and shows reply branches like a forum thread. It does not change the imported chat, though its topic links are saved locally. The Android app is separate: its source includes a new-identity setup flow, live Delta Chat core I/O after setup, and quoted-reply sending. Those Android flows have not been run on a device or against a live account, so this checkpoint does not establish that they work at runtime.

## What counts as a reply

- **Real reply**: the sender used Reply and the quoted message is in the loaded list
  (`quote.kind == "WithMessage"` in the Delta Chat JSON-RPC). Solid guide line. A quote that is only
  text (`JustText`) or points outside the list gives no parent.
- **Not a reply**: the `In-Reply-To` header / `parentId`. Delta Chat core fills it in with the last
  message of the chat when nothing is quoted (`src/chat.rs` in chatmail/core), so it records
  "what came before", not a choice. cabang ignores it by default.
- **Guess**: with "Guess branches" on, parentId and short time gaps group unquoted messages. These
  are drawn dashed and tagged "guess". Real quoted links are never replaced by guesses.
- A quote of a message missing from the export is shown as top-level with a note, not invented.

Message IDs in an export are local numbers of one account. cabang does not match anything across
devices.

## Reading on a phone

Indentation stops at 4 levels. Deeper branches show "Thread continues: N more" and open as their own
view (with a path back to the parent). Tap a guide line or `[-]` to collapse a branch.

## Use

Open `index.html` through any static web server (or GitHub Pages), then "Demo data" or
"Open export". Files are read in the browser only.
`export_chat.py` writes an export from a copy of your own profile (untested against a real
account so far). Demo data is synthetic. `node test.mjs` and `node test_threading_fixture.mjs` run the tree tests (the second uses synthetic chats where only a real quote makes a branch).

## Limits

The static export view has no sending or live connection. The Android source includes quoted-reply sending via `quotedMessageId`, but that path has not been tested on Android or with a real account.

## Linking and merging topics

Real replies form the tree. Conversations often overlap, so you can also link any two messages
("link" on a message, then "link here" on another). Linked topics show as a merged group in
"Topics & links", and each message shows chips for its links. This is a layer on top: the reply
edges are never changed. Suggestions (keyword overlap between topics) are tentative, need your
tap, and can be dismissed. Unlinking archives; Undo reverses the last change. Links are saved on
this device under the account's identity hash, keyed by each message's portable RFC Message-ID
(not local numbers), with no message text. They can only attach to the identical message of the
same identity. Messages without a portable id cannot be linked. Links for imported files or for
an account without a stable identity stay in memory only.

## Android app (v0.3.0 source checkpoint; not released or device-tested)

`android/` hosts this view in a WebView and embeds Delta Chat core 2.69.0 through Chaquopy
(`deltachat-rpc-client==2.69.0` plus the official arm64 `deltachat-rpc-server` from PyPI).

Build: `sh tools/fetch_core.sh` (downloads the core, checks its sha256 against PyPI), then
`cd android && gradle assembleDebug`. A debug build is signed with a throwaway key: test only.

cabang keeps its own accounts directory and does not read another app's data or import its profile.
When there is no configured account, the app offers a button to create a new cabang identity on
nine.testrun.org on this phone. After setup, the core connects to that relay on later starts. Existing
Delta Chat accounts are not changed. This is not a live account-creation test or Android runtime proof.

Latest host checks: `node test.mjs` passed; Python unittest ran 10 tests, 0 skipped; Gradle tests passed (Bridge 13/13, Activity 6/6) and `assembleDebug` exited 0. The APK produced by the local build was not inspected, included here, or released. The manifest backup restrictions were checked. These host results do not establish Android runtime behavior.

`patches/cabang_setup.py` is an earlier prototype helper; current phone-side identity setup is wired in the Android app. The setup path and restart networking are not verified on an Android device.

Not tested on a device or emulator, with a real relay account, or by sending a real message. Account creation, backup of a configured account, and restart/network behavior remain unverified.
