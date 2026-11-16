// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 irvan-putra
import { createRequire } from "module"; import assert from "assert";
const require = createRequire(import.meta.url); const T = require("./threads.js"), O = require("./overlay.js");
const M = (m) => Object.assign({ mid: "mid" + m.id }, m);
const msgs = [
 {id:1,timestamp:1,text:"Release planning: signing key and map style"},
 {id:2,timestamp:2,text:"Lunch on Saturday?"},
 {id:3,timestamp:3,text:"Map style outdoors, signing question",quote:null},
 {id:4,timestamp:4,text:"reply in topic one",quote:{kind:"WithMessage",messageId:1}},
].map(M);
const t = T.build(msgs); const before = JSON.stringify(t.nodes.map(n => [n.id, n.parent && n.parent.id, n.link]));
const o = O.create();
// suggestions are tentative and need 2 shared words
let s = O.suggest(t, o); assert.equal(s.length, 1); assert.deepEqual([s[0].a, s[0].b], ["m:mid1", "m:mid3"]); assert.ok(s[0].shared.includes("signing"));
// link a reply in topic 1 to topic 3: groups follow the topics, tree edges untouched
const l = O.addLink(o, "m:mid4", "m:mid3"); assert.ok(l);
assert.equal(O.addLink(o, "m:mid3", "m:mid4"), null);      // duplicate either way round
assert.equal(O.addLink(o, "m:mid3", "m:mid3"), null);
let g = O.groups(t, o); assert.equal(g.length, 1); assert.deepEqual(g[0].map(n => n.id).sort(), [":1", ":3"]);
assert.equal(JSON.stringify(t.nodes.map(n => [n.id, n.parent && n.parent.id, n.link])), before);
assert.equal(O.suggest(t, o).length, 0);          // already linked
// archive keeps the record, removes it from groups, can restore; undo reverses
assert.ok(O.archive(o, l.id)); assert.equal(o.links.length, 1); assert.equal(O.groups(t, o).length, 0);
assert.equal(O.suggest(t, o).length, 0);          // archived link is not suggested again
assert.ok(O.undo(o)); assert.equal(o.links[0].status, "active"); assert.equal(O.groups(t, o).length, 1);
assert.ok(O.undo(o)); assert.equal(o.links.length, 0);
assert.equal(O.undo(o), false);
// dismissed suggestion stays dismissed, but the person can still link it manually later
const l2 = O.addLink(o, "m:mid1", "m:mid3", "suggested"); O.dismiss(o, l2.id); assert.equal(O.suggest(t, o).length, 0);
assert.ok(O.addLink(o, "m:mid1", "m:mid3")); assert.equal(O.find(o, l2.id).status, "active");
// persistence round trip keeps archived records and ignores junk
const r = O.load(O.serialize(o)); assert.equal(r.links.length, 1); assert.equal(O.load("not json").links.length, 0);
// links to messages not in the loaded chat are kept but do not form groups
const o2 = O.create(); O.addLink(o2, "m:mid1", "m:gone"); assert.equal(O.groups(t, o2).length, 0);
// same local ids after a restore, different messages (different mids): old links must not attach
const t2 = T.build([{id:1,timestamp:1,mid:"other1",text:"x y z w"},{id:3,timestamp:3,mid:"other3",text:"x y z w"}]);
const o3 = O.create(); O.addLink(o3, "m:mid1", "m:mid3"); assert.equal(O.groups(t2, o3).length, 0);
// messages without a portable id never get overlay keys
assert.equal(O.suggest(T.build([{id:1,timestamp:1,text:"signing map style"},{id:2,timestamp:2,text:"signing map style"}]), O.create()).length, 0);
console.log("overlay ok");
