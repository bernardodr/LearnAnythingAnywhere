/*
 * Reusable quiz component.
 *
 * Usage:
 *   <div class="quiz" id="q1"></div>
 *   <script src="../assets/quiz.js"></script>
 *   <script>
 *     Quiz.mount("q1", [
 *       { q: "Question?", options: ["A", "B", "C", "D"], answer: 2, why: "Explanation." },
 *     ]);
 *   </script>
 *
 * - `answer` is the zero-based index of the correct option.
 * - Feedback appears immediately; the explanation shows whether right or wrong.
 * - The score counts first attempts only, which keeps retrieval practice honest.
 * - Add `sortable: true` and `items: [...]` (in correct order) for an ordering question.
 */
(function () {
  const css = `
  .quiz { font-family: ui-sans-serif, -apple-system, "Helvetica Neue", sans-serif; font-size: 0.9rem; margin: 1.5rem 0; }
  .quiz .qcard { border: 1px solid var(--rule, #e4ddd0); border-radius: 6px; padding: 1rem 1.1rem; margin: 0 0 1rem; background: #fff; }
  .quiz .qnum { font-size: 0.68rem; letter-spacing: 0.1em; text-transform: uppercase; color: var(--muted, #6b665e); margin-bottom: 0.3rem; }
  .quiz .qtext { font-weight: 600; margin: 0 0 0.75rem; line-height: 1.4; }
  .quiz .opts { display: grid; gap: 0.45rem; }
  .quiz button.opt { text-align: left; font: inherit; padding: 0.55rem 0.8rem; border: 1px solid var(--rule, #e4ddd0); border-radius: 4px; background: var(--paper, #fffdf8); color: inherit; cursor: pointer; }
  .quiz button.opt:hover:not([disabled]) { border-color: var(--ink, #1f1d1a); }
  .quiz button.opt.right { background: var(--good-soft, #e5f1e6); border-color: var(--good, #2f6b3a); }
  .quiz button.opt.wrong { background: var(--bad-soft, #f8e5e1); border-color: var(--bad, #9b2c1f); }
  .quiz button.opt[disabled] { cursor: default; }
  .quiz .why { margin-top: 0.7rem; font-size: 0.84rem; line-height: 1.5; color: var(--ink, #1f1d1a); display: none; }
  .quiz .why.show { display: block; }
  .quiz .why b.ok { color: var(--good, #2f6b3a); }
  .quiz .why b.no { color: var(--bad, #9b2c1f); }
  .quiz .score { font-weight: 600; padding: 0.6rem 0; }
  .quiz .sort-list { list-style: none; padding: 0; margin: 0; display: grid; gap: 0.4rem; }
  .quiz .sort-list li { display: flex; align-items: center; gap: 0.5rem; padding: 0.45rem 0.6rem; border: 1px solid var(--rule, #e4ddd0); border-radius: 4px; background: var(--paper, #fffdf8); }
  .quiz .sort-list li.right { background: var(--good-soft, #e5f1e6); border-color: var(--good, #2f6b3a); }
  .quiz .sort-list li.wrong { background: var(--bad-soft, #f8e5e1); border-color: var(--bad, #9b2c1f); }
  .quiz .sort-list li span { flex: 1; }
  .quiz .mv { font: inherit; border: 1px solid var(--rule, #e4ddd0); background: #fff; border-radius: 3px; cursor: pointer; padding: 0 0.45rem; }
  .quiz button.check { margin-top: 0.7rem; font: inherit; font-weight: 600; padding: 0.45rem 0.9rem; border-radius: 4px; border: 1px solid var(--ink, #1f1d1a); background: var(--ink, #1f1d1a); color: #fff; cursor: pointer; }
  .quiz button.reset { font: inherit; font-size: 0.8rem; background: none; border: none; color: var(--accent, #9b2c1f); cursor: pointer; text-decoration: underline; padding: 0; }
  `;
  if (!document.getElementById("quiz-css")) {
    const s = document.createElement("style");
    s.id = "quiz-css";
    s.textContent = css;
    document.head.appendChild(s);
  }

  function el(tag, cls, html) {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (html != null) e.innerHTML = html;
    return e;
  }

  function shuffle(a) {
    const b = a.slice();
    for (let i = b.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [b[i], b[j]] = [b[j], b[i]];
    }
    return b;
  }

  function mount(id, questions) {
    const root = document.getElementById(id);
    root.classList.add("quiz");
    root.innerHTML = "";
    let answered = 0, correct = 0;
    const scoreEl = el("div", "score");

    function updateScore() {
      if (answered === questions.length) {
        scoreEl.innerHTML = `First-try score: ${correct} / ${questions.length}. `;
        const r = el("button", "reset", "Try again");
        r.onclick = () => mount(id, questions);
        scoreEl.appendChild(r);
      }
    }

    questions.forEach((item, qi) => {
      const card = el("div", "qcard");
      card.appendChild(el("div", "qnum", `Question ${qi + 1} of ${questions.length}`));
      card.appendChild(el("p", "qtext", item.q));
      const why = el("div", "why");

      if (item.sortable) {
        let order = shuffle(item.items.map((t, i) => ({ t, i })));
        // avoid starting already-solved
        if (order.every((o, k) => o.i === k)) order.reverse();
        const list = el("ul", "sort-list");
        const render = () => {
          list.innerHTML = "";
          order.forEach((o, k) => {
            const li = el("li");
            li.appendChild(el("span", null, o.t));
            const up = el("button", "mv", "↑"), dn = el("button", "mv", "↓");
            up.onclick = () => { if (k > 0) { [order[k - 1], order[k]] = [order[k], order[k - 1]]; render(); } };
            dn.onclick = () => { if (k < order.length - 1) { [order[k + 1], order[k]] = [order[k], order[k + 1]]; render(); } };
            li.append(up, dn);
            list.appendChild(li);
          });
        };
        render();
        card.appendChild(list);
        const check = el("button", "check", "Check order");
        let first = true;
        check.onclick = () => {
          const lis = list.querySelectorAll("li");
          let ok = true;
          order.forEach((o, k) => {
            const good = o.i === k;
            ok = ok && good;
            lis[k].classList.add(good ? "right" : "wrong");
            lis[k].classList.remove(good ? "wrong" : "right");
          });
          if (first) { answered++; if (ok) correct++; first = false; updateScore(); }
          why.innerHTML = (ok ? '<b class="ok">Correct.</b> ' : '<b class="no">Not quite — reorder and check again.</b> ') + (item.why || "");
          why.classList.add("show");
        };
        card.appendChild(check);
      } else {
        const opts = el("div", "opts");
        let first = true;
        item.options.forEach((text, oi) => {
          const b = el("button", "opt", text);
          b.onclick = () => {
            const ok = oi === item.answer;
            b.classList.add(ok ? "right" : "wrong");
            if (first) { answered++; if (ok) correct++; first = false; updateScore(); }
            if (ok) opts.querySelectorAll("button").forEach(x => (x.disabled = true));
            else b.disabled = true;
            why.innerHTML = (ok ? '<b class="ok">Correct.</b> ' : '<b class="no">Not quite.</b> ') + (item.why || "");
            why.classList.add("show");
          };
          opts.appendChild(b);
        });
        card.appendChild(opts);
      }
      card.appendChild(why);
      root.appendChild(card);
    });
    root.appendChild(scoreEl);
  }

  window.Quiz = { mount };
})();
