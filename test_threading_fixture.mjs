// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 irvan-putra
// Synthetic chat fixtures only: no account, no network, no real messages.
// Simulates how Delta Chat core fills parentId (In-Reply-To) with the previous message
// when nothing is quoted, and checks that only a real quote makes a branch.
import { createRequire } from "module"; import assert from "assert";
const require = createRequire(import.meta.url); const C = require("./threads.js");

let ts = 1700000000;
// Build a chat the way core would: parentId = previous message id unless the sender quoted.
function chat(spec) {
  return spec.map(([id, quoteOf, extra], i) => {
    const m = { id, account: 1, timestamp: (ts += 60), text: "msg " + id,
      parentId: i === 0 ? null : spec[i - 1][0] };            // core default, not a user choice
    if (quoteOf != null) m.quote = { kind: "WithMessage", messageId: quoteOf };
    return Object.assign(m, extra || {});
  });
}
const link = (t, id) => t.byId["1:" + id];

// 1. A flat chat (nobody quoted) stays flat, even though every parentId is set.
let t = C.build(chat([[1], [2], [3], [4]]));
assert.equal(t.roots.length, 4);
[1, 2, 3, 4].forEach(i => assert.equal(link(t, i).link, "root"));

// 2. A real reply to an old message becomes a branch of that message, not of the previous one.
t = C.build(chat([[1], [2], [3], [4, 1]]));
assert.equal(link(t, 4).parent.id, "1:1"); assert.equal(link(t, 4).link, "quote");
assert.equal(link(t, 3).parent, null);

// 3. Reply to a reply gives depth 2, and later unquoted messages stay at the top.
t = C.build(chat([[1], [2, 1], [3, 2], [4]]));
assert.equal(link(t, 3).parent.id, "1:2"); assert.equal(link(t, 2).parent.id, "1:1");
assert.equal(link(t, 4).parent, null);
assert.equal(C.stats(t).maxDepth, 2);

// 4. Two replies to one message are siblings.
t = C.build(chat([[1], [2, 1], [3], [4, 1]]));
assert.deepEqual(link(t, 1).children.map(n => n.id), ["1:2", "1:4"]);

// 5. A quote of a message that is not in the loaded list is an orphan with a note, not invented.
t = C.build(chat([[10], [11, 5]]));
assert.equal(link(t, 11).parent, null); assert.equal(link(t, 11).missingQuote, true);

// 6. A text-only quote is not a branch.
t = C.build(chat([[1], [2, null, { quote: { kind: "JustText", text: "x" } }]]));
assert.equal(link(t, 2).parent, null); assert.equal(link(t, 2).missingQuote, true);

// 7. Self-quote and quote cycles do not loop or create parents.
t = C.build(chat([[1, 1]]));
assert.equal(link(t, 1).parent, null);
t = C.build(chat([[1, 2], [2, 1]]));
assert.equal(t.nodes.length, 2); assert.ok(t.roots.length >= 1);

// 8. Same local id in two accounts does not cross-link.
const a = chat([[1], [2, 1]]);
const b = chat([[1], [2]]).map(m => Object.assign(m, { account: 2 }));
t = C.build(a.concat(b));
assert.equal(t.byId["1:2"].parent.id, "1:1"); assert.equal(t.byId["2:2"].parent, null);

// 9. Guess mode never replaces a real quote and marks other links as guesses.
t = C.build(chat([[1], [2], [3, 1]]), { group: true, gapMinutes: 30 });
assert.equal(link(t, 3).link, "quote"); assert.equal(link(t, 3).parent.id, "1:1");
assert.notEqual(link(t, 2).link, "quote");

console.log("threading fixture ok");
