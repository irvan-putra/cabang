// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 irvan-putra
import { createRequire } from "module"; import fs from "fs"; import assert from "assert";
const require = createRequire(import.meta.url); const C = require("./threads.js");
const d = JSON.parse(fs.readFileSync("sample.json"));
const t = C.build(d.messages), s = C.stats(t);
assert.equal(t.byId[":3"].parent.id, ":1"); assert.equal(t.byId[":3"].link, "quote");
assert.equal(t.byId[":2"].parent, null); assert.equal(t.byId[":2"].link, "root"); // parentId alone is not a reply
assert.equal(t.byId[":13"].parent, null);
assert.equal(t.byId[":15"].parent.id, ":4");      // quote beats parentId
assert.equal(t.byId[":17"].parent, null);      // header-only from another client: still not proof
const g = C.build(d.messages, { group: true }), gs = C.stats(g);
assert.equal(g.byId[":2"].link, "transport"); assert.equal(g.byId[":2"].parent.id, ":1");
assert.equal(g.byId[":3"].link, "quote");     // real link never replaced
const o = C.build([{id:5,timestamp:1,quote:{kind:"WithMessage",messageId:99}}]);
assert.equal(o.roots.length,1); assert.ok(o.byId[":5"].missingQuote);
// cycle safety
const c = C.build([{id:1,timestamp:1,quote:{kind:"WithMessage",messageId:2}},{id:2,timestamp:2,quote:{kind:"WithMessage",messageId:1}}]);
assert.equal(c.roots.length >= 1, true);
// dedupe + two accounts with the same local ids do not collide
const dd=C.build([{id:1,timestamp:1,account:"a"},{id:1,timestamp:2,account:"b"},{id:2,timestamp:3,account:"a",quote:{kind:"WithMessage",messageId:1}},{id:1,timestamp:9,account:"a"}]);
assert.equal(dd.dupes,1); assert.equal(dd.nodes.length,3);
assert.equal(dd.byId["a:2"].parent.id,"a:1"); assert.equal(dd.byId["b:1"].children.length,0);
// JustText never makes a parent
const jt=C.build([{id:1,timestamp:1},{id:2,timestamp:2,quote:{kind:"JustText",text:"x"},parentId:1}],{group:false});
assert.equal(jt.byId[":2"].parent,null);
// self-quote and long chain (no stack issue)
const ch=[];for(let i=1;i<=3000;i++)ch.push({id:i,timestamp:i,quote:i>1?{kind:"WithMessage",messageId:i-1}:null});
assert.equal(C.stats(C.build(ch)).maxDepth,2999);
console.log("strict", s); console.log("grouped", gs); console.log("ok");
