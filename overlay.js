// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 irvan-putra
// Cross-links between messages and topic groups, kept apart from the reply tree.
// The tree (from threads.js) is never changed. A link says "these two messages belong together";
// it is a person's choice (manual) or a tentative suggestion the person accepted.
// Nothing is deleted: unlinking archives, and every action can be undone.
(function (root) {
  var STOP = "the a an and or of to in on at for is are was were be it this that with as by from we you i me my our not no yes ok okay so but if then than too also just can will would should could have has had do does did about into out up down over again more most some any all".split(" ");
  function create() { return { links: [], log: [], seq: 0 }; }
  function find(o, id) { for (var i = 0; i < o.links.length; i++) if (o.links[i].id === id) return o.links[i]; return null; }
  function pairKey(a, b) { return a < b ? a + "|" + b : b + "|" + a; }
  function existing(o, a, b) {
    var k = pairKey(a, b);
    for (var i = 0; i < o.links.length; i++) if (pairKey(o.links[i].a, o.links[i].b) === k) return o.links[i];
    return null;
  }
  // status: "active" (counts), "archived" (hidden, restorable), "dismissed" (a suggestion the person rejected)
  function addLink(o, a, b, kind) {
    if (!a || !b || a === b) return null;
    var e = existing(o, a, b);
    if (e) {
      if (e.status === "active") return null;
      o.log.push({ op: "status", id: e.id, prev: e.status }); e.status = "active"; e.kind = kind || e.kind; return e;
    }
    var l = { id: "l" + (++o.seq), a: a, b: b, kind: kind || "manual", status: "active" };
    o.links.push(l); o.log.push({ op: "add", id: l.id }); return l;
  }
  function setStatus(o, id, status) {
    var l = find(o, id); if (!l || l.status === status) return false;
    o.log.push({ op: "status", id: id, prev: l.status }); l.status = status; return true;
  }
  function archive(o, id) { return setStatus(o, id, "archived"); }
  function restore(o, id) { return setStatus(o, id, "active"); }
  function dismiss(o, id) { return setStatus(o, id, "dismissed"); }
  function undo(o) {
    var e = o.log.pop(); if (!e) return false;
    if (e.op === "add") { o.links = o.links.filter(function (l) { return l.id !== e.id; }); }
    else { var l = find(o, e.id); if (l) l.status = e.prev; }
    return true;
  }
  function pid(n) { return n.msg.mid ? "m:" + n.msg.mid : null; }
  // Link endpoints are portable ids ("m:<RFC Message-ID>"), never local numbers, so links survive a
  // restore or a recreated account and can only attach to the identical message.
  function rootOf(tree, key) { var n = tree.byPid[key]; while (n && n.parent) n = n.parent; return n; }
  // Groups of top-level topics tied together by active links. Singletons are not returned.
  function groups(tree, o) {
    var parent = {};
    function f(x) { while (parent[x] !== x) { parent[x] = parent[parent[x]]; x = parent[x]; } return x; }
    o.links.forEach(function (l) {
      if (l.status !== "active") return;
      var ra = rootOf(tree, l.a), rb = rootOf(tree, l.b); if (!ra || !rb) return; // endpoint not loaded
      if (!(ra.id in parent)) parent[ra.id] = ra.id; if (!(rb.id in parent)) parent[rb.id] = rb.id;
      parent[f(ra.id)] = f(rb.id);
    });
    var by = {};
    Object.keys(parent).forEach(function (k) { (by[f(k)] = by[f(k)] || []).push(tree.byId[k]); });
    return Object.keys(by).map(function (k) { return by[k]; }).filter(function (g) { return g.length > 1; });
  }
  function words(text) {
    var out = {}; String(text || "").toLowerCase().split(/[^a-z0-9\u00c0-\uffff]+/).forEach(function (w) {
      if (w.length > 3 && STOP.indexOf(w) < 0) out[w] = 1;
    }); return out;
  }
  function topicWords(n) {
    var all = {}, st = [n];
    while (st.length) { var x = st.pop(); Object.assign(all, words(x.msg.text)); x.children.forEach(function (c) { st.push(c); }); }
    return all;
  }
  // Tentative suggestions only: two different top-level topics that share at least `min` words.
  // This is keyword overlap, not knowledge that they are about the same thing.
  function suggest(tree, o, min) {
    min = min || 2;
    var tw = tree.roots.filter(function (r) { return pid(r); }).map(function (r) { return { r: r, w: topicWords(r) }; }), out = [];
    for (var i = 0; i < tw.length; i++) for (var j = i + 1; j < tw.length; j++) {
      var shared = Object.keys(tw[i].w).filter(function (w) { return tw[j].w[w]; });
      if (shared.length < min) continue;
      var ri = tw[i].r.id, rj = tw[j].r.id, seen = false;   // any link between these two topics, in any state: do not suggest again
      o.links.forEach(function (l) {
        var x = rootOf(tree, l.a), y = rootOf(tree, l.b);
        if (x && y && pairKey(x.id, y.id) === pairKey(ri, rj)) seen = true;
      });
      if (seen) continue;
      out.push({ a: pid(tw[i].r), b: pid(tw[j].r), shared: shared.sort().slice(0, 6) });
    }
    return out.sort(function (x, y) { return y.shared.length - x.shared.length; });
  }
  function serialize(o) { return JSON.stringify({ links: o.links, seq: o.seq }); }
  function load(s) {
    var o = create();
    try { var d = JSON.parse(s); if (Array.isArray(d.links)) { o.links = d.links.filter(function (l) { return l && l.id && l.a && l.b; }); o.seq = d.seq | 0; } } catch (e) {}
    return o;
  }
  var api = { create: create, pid: pid, addLink: addLink, archive: archive, restore: restore, dismiss: dismiss, undo: undo, groups: groups, suggest: suggest, serialize: serialize, load: load, find: find };
  if (typeof module !== "undefined") module.exports = api; else root.CabangOverlay = api;
})(this);
