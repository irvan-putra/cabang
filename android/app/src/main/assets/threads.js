// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 irvan-putra
// Builds a reply tree from Delta Chat JSON-RPC message objects.
// Link kinds (strongest first):
//   "quote"    - user used Reply: quote.kind == "WithMessage" and the target is in the list. Real.
//   "header"   - In-Reply-To (parentId) points at a message that is NOT the one just before it. Real
//                (sent by another client, or by a client that set it on purpose).
//   "adjacent" - parentId points at the message right before it. Delta Chat core sets In-Reply-To
//                to the previous message by default when nothing is quoted, so this is NOT a reply
//                choice. Treated as a guess.
//   "guess"    - optional time-gap grouping, only when options.group is true.
(function (root) {
  // Link kinds:
  //   "quote"     - the sender used Reply: quote.kind == "WithMessage" and the target is present.
  //                 The only edge that records a user choice. Real branch.
  //   "transport" - only In-Reply-To (parentId) is set. Delta Chat core fills this in with the last
  //                 message of the chat when nothing is quoted (src/chat.rs), so it is NOT a user
  //                 choice. Used only when options.group is true, drawn dashed.
  //   "guess"     - optional time-gap grouping (options.group), drawn dashed.
  //   "root"      - no explicit link: starts a top-level topic.
  function build(messages, options) {
    options = Object.assign({ group: false, gapMinutes: 10 }, options || {});
    // Message ids are local numbers of one account. Key = account + id, so two accounts never
    // collide; duplicates of the same key are dropped (first wins) and counted.
    var seen = {}, dupes = 0;
    messages = messages.filter(function (m) {
      if (!m || m.id == null) return false;
      var k = (m.account || "") + ":" + m.id;
      if (seen[k]) { dupes++; return false; }
      seen[k] = 1; m._k = k; return true;
    });
    var msgs = messages.slice().sort(function (a, b) {
      return (a.timestamp - b.timestamp) || (a.id - b.id);
    });
    var byId = {}, byPid = {};
    var nodes = msgs.map(function (m, i) {
      var n = { msg: m, id: m._k, children: [], parent: null, link: "root", index: i, missingQuote: false };
      byId[m._k] = n;
      if (m.mid && !byPid["m:" + m.mid]) byPid["m:" + m.mid] = n;   // portable identity (RFC Message-ID)
      return n;
    });
    nodes.forEach(function (n, i) {
      var m = n.msg, prev = i > 0 ? nodes[i - 1] : null, p = null, link = "root";
      var q = m.quote;
      if (q && q.kind === "WithMessage") {
        var qk = (m.account || "") + ":" + q.messageId;
        if (byId[qk] && qk !== m._k) { p = byId[qk]; link = "quote"; }
        else n.missingQuote = true; // quoted a message not in this list: honest orphan
      } else if (q && q.kind === "JustText") {
        n.missingQuote = true;
      }
      if (!p && options.group) {
        var pk = (m.account || "") + ":" + m.parentId;
        if (m.parentId != null && byId[pk] && pk !== m._k) { p = byId[pk]; link = "transport"; }
        else if (prev && (m.timestamp - prev.msg.timestamp) <= options.gapMinutes * 60) { p = prev; link = "guess"; }
      }
      for (var a = p; a; a = a.parent) if (a === n) { p = null; link = "root"; break; }
      n.parent = p; n.link = p ? link : "root";
      if (p) p.children.push(n);
    });
    var roots = nodes.filter(function (n) { return !n.parent; });
    // sizes bottom-up without recursion (a 10k-message chain must not overflow the stack)
    nodes.forEach(function (n) { n.size = 1; });
    var order = [], stack = roots.slice();
    while (stack.length) { var x = stack.pop(); order.push(x); x.children.forEach(function (c) { stack.push(c); }); }
    for (var j = order.length - 1; j >= 0; j--) if (order[j].parent) order[j].parent.size += order[j].size;
    return { roots: roots, byId: byId, nodes: nodes, byPid: byPid, dupes: dupes };
  }
  function stats(tree) {
    var s = { total: tree.nodes.length, quote: 0, transport: 0, guess: 0, root: 0, missingQuote: 0, maxDepth: 0 };
    tree.nodes.forEach(function (n) {
      s[n.link] = (s[n.link] || 0) + 1;
      if (n.missingQuote) s.missingQuote++;
    });
    var st = tree.roots.map(function (n) { return [n, 0]; });
    while (st.length) { var e = st.pop(); if (e[1] > s.maxDepth) s.maxDepth = e[1]; e[0].children.forEach(function (c) { st.push([c, e[1] + 1]); }); }
    return s;
  }
  var api = { build: build, stats: stats };
  if (typeof module !== "undefined") module.exports = api; else root.Cabang = api;
})(this);
