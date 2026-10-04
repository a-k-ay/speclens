// SpecLens chat UI. Plain JavaScript, no framework or build step.
// Everything that comes from the server (answers, passages, file names) is HTML-escaped
// before it is put on the page, so document text can never inject markup or scripts.

// Grouped by the route SpecLens should choose, so a visitor can see each one in action.
const SAMPLE_QUESTIONS = [
  { group: "Documents", text: "When must the customer invoice be generated?", tag: "Documents disagree" },
  { group: "Documents", text: "What does BR-6.4 say?" },
  { group: "Project tracker", text: "Which tickets are blocked this sprint?" },
  { group: "Project tracker", text: "What's the status of LOG-142?" },
  { group: "Documents + tracker", text: "Is the 48-hour invoice rule from CR-3 built and tested?", tag: "Agreed vs built" },
  { group: "Documents + tracker", text: "Has the offline e-POD requirement passed UAT?" },
  { group: "Should be refused", text: "What is the penalty for late delivery of a shipment?", tag: "Not in the documents" },
  { group: "Should be refused", text: "What's the weather in Pune today?", tag: "Off-topic" },
];

const REFUSAL_EXPLANATIONS = {
  NO_RELEVANT_SOURCES: "Nothing in these documents is close to this question, so the model wasn't asked.",
  NOT_IN_SOURCES: "Related passages were found, but none of them contains the answer.",
  UNGROUNDED_ANSWER: "The model's answer didn't cite any document, so it was withheld.",
  OUT_OF_SCOPE: "This question isn't about the project's documents or its tracker.",
  UNVERIFIED_TRACKER_DATA: "The answer named a ticket, test or status the tracker didn't return, so it was withheld.",
  NOT_IN_TRACKER: "The project tracker has nothing that answers this question.",
};

const ROUTE_LABELS = {
  DOCUMENTS: "Documents",
  TRACKER: "Tracker API",
  DOCUMENTS_AND_TRACKER: "Documents + Tracker API",
  NONE: "Not routed",
};

const INTENT_LABELS = {
  DOC_QUESTION: "document question",
  LIVE_STATUS: "live status",
  TRACEABILITY: "traceability",
  OUT_OF_SCOPE: "out of scope",
};

const $ = (id) => document.getElementById(id);
const els = {
  project: $("project"), documents: $("documents"), docCount: $("doc-count"),
  uploadBox: $("upload-box"), file: $("file"), uploadHint: $("upload-hint"), uploadStatus: $("upload-status"),
  thread: $("thread"), intro: $("intro"), samples: $("samples"),
  form: $("ask-form"), question: $("question"), askBtn: $("ask-btn"), evalSummary: $("eval-summary"),
};

let messageCounter = 0;
let config = { editingEnabled: false, demoProjectName: null };
let projectList = [];
let chatHistory = []; // [{ question, response, at }] for the selected project

// ---------- chat history (this browser only) ----------
// There are no user accounts, so history is never stored on the server: on the public demo
// that would show one visitor's questions to everyone. localStorage keeps it private to this
// browser; Export gives a file to keep elsewhere.

const HISTORY_LIMIT = 50;
const historyKey = (projectId) => `speclens.chat.${projectId}`;

function loadHistory(projectId) {
  try {
    return JSON.parse(localStorage.getItem(historyKey(projectId)) || "[]");
  } catch {
    return []; // storage blocked (private window) or corrupted: start empty
  }
}

function saveHistory(projectId, entries) {
  try {
    localStorage.setItem(historyKey(projectId), JSON.stringify(entries.slice(-HISTORY_LIMIT)));
  } catch {
    // Storage full or blocked: the chat still works, it just won't be remembered.
  }
}

function clearHistory(projectId) {
  try { localStorage.removeItem(historyKey(projectId)); } catch { /* ignore */ }
}

// ---------- helpers ----------

function escapeHtml(text) {
  return String(text ?? "")
    .replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;").replaceAll("'", "&#39;");
}

async function api(path, options = {}) {
  const response = await fetch(path, options);
  const isJson = (response.headers.get("content-type") || "").includes("json");
  const body = isJson ? await response.json() : null;
  if (!response.ok) {
    const detail = body?.detail || body?.title || `Request failed (${response.status})`;
    const error = new Error(detail);
    error.status = response.status;
    throw error;
  }
  return body;
}

function scrollToBottom() {
  els.thread.scrollTop = els.thread.scrollHeight;
}

// Minimal, safe formatting for model answers: paragraphs, "* " / "- " bullet lists,
// **bold**, and markers in square brackets. Input is escaped first.
function formatAnswer(text, citedIds, trackerRefs, messageId) {
  // One marker: a cited document source becomes a button; a verified ticket becomes a link to
  // its JSON on the tracker API; a verified UAT test becomes a chip; anything else stays text.
  const marker = (p) => {
    if (citedIds.has(p)) {
      return `<button type="button" class="cite" data-msg="${messageId}" data-source="${p}" aria-label="Show source ${p}">${p}</button>`;
    }
    if (trackerRefs.has(p) && /^LOG-\d+$/.test(p)) {
      return `<a class="ticket" href="/mock-tracker/rest/api/3/issue/${p}" target="_blank" rel="noopener"
        title="Open ${p} in the tracker API (JSON)">${p}</a>`;
    }
    if (trackerRefs.has(p)) return `<span class="ticket test" title="UAT test">${p}</span>`;
    return null;
  };
  const inline = (line) => escapeHtml(line)
    .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
    // Markers come alone ([S1], [LOG-142]) or grouped ([S1, S2], [S3, TC-I-01]).
    .replace(/\[([^\[\]]{1,80})\]/g, (match, inner) => {
      const parts = inner.split(/[,;]/).map((p) => p.trim()).filter(Boolean);
      const rendered = parts.map((p) => marker(p));
      if (rendered.every((r) => r === null)) return match;
      const html = rendered.map((r, i) => r ?? parts[i]);
      return rendered.every((r) => r !== null) ? html.join("") : `[${html.join(", ")}]`;
    });

  const html = [];
  let list = null;
  let paragraph = [];
  const flushParagraph = () => {
    if (paragraph.length) html.push(`<p>${paragraph.map(inline).join("<br>")}</p>`);
    paragraph = [];
  };
  const flushList = () => {
    if (list) html.push(`<ul>${list.map((item) => `<li>${inline(item)}</li>`).join("")}</ul>`);
    list = null;
  };

  for (const raw of text.split("\n")) {
    const line = raw.trim();
    const bullet = line.match(/^[*-]\s+(.*)$/);
    if (bullet) {
      flushParagraph();
      (list ??= []).push(bullet[1]);
    } else if (line === "") {
      flushParagraph();
      flushList();
    } else {
      flushList();
      paragraph.push(line);
    }
  }
  flushParagraph();
  flushList();
  return html.join("");
}

// ---------- rendering ----------

function addQuestion(text) {
  els.intro.hidden = true;
  const node = document.getElementById("tpl-question").content.cloneNode(true);
  node.querySelector("p").textContent = text;
  els.thread.appendChild(node);
  scrollToBottom();
}

function addLoading() {
  const div = document.createElement("div");
  div.className = "msg msg-loading";
  div.innerHTML = '<span class="spinner" aria-hidden="true"></span><span>Working out where to look, then searching...</span>';
  els.thread.appendChild(div);
  scrollToBottom();
  return div;
}

// How the question was handled: route badge, intent, fallback, and any notice.
// Answers saved before routing existed have no route, so nothing is shown for them.
function renderRoute(response) {
  if (!response.route) return "";
  const intent = INTENT_LABELS[response.intent] ?? "";
  const confidence = typeof response.intentConfidence === "number" ? response.intentConfidence.toFixed(2) : "";
  return `
    <div class="route-bar">
      <span class="route-badge route-${escapeHtml(response.route)}">${escapeHtml(ROUTE_LABELS[response.route] ?? response.route)}</span>
      <span class="route-meta" title="What the intent classifier decided, and its confidence">
        ${escapeHtml(intent)}${confidence ? ` · ${confidence}` : ""}</span>
      ${response.routedByFallback ? '<span class="route-meta fallback" title="Below the confidence threshold, questions use the document path">low confidence → documents</span>' : ""}
    </div>
    ${response.notice ? `<p class="notice">${escapeHtml(response.notice)}</p>` : ""}`;
}

// Every tracker call made for this answer, collapsed by default.
function renderToolCalls(response) {
  const calls = response.toolCalls ?? [];
  if (!calls.length) return "";
  return `
    <details class="tool-calls">
      <summary>Tool calls (${calls.length})</summary>
      <ol>
        ${calls.map((c) => `
          <li>
            <code>${escapeHtml(c.tool)}(${escapeHtml(c.arguments)})</code>
            <span class="outcome outcome-${escapeHtml(c.outcome)}">${escapeHtml(c.outcome)}</span>
            <span class="tool-detail">${escapeHtml(c.detail)}</span>
            <span class="muted small">${c.durationMs} ms</span>
          </li>`).join("")}
      </ol>
    </details>`;
}

function renderAnswer(response) {
  const messageId = `m${++messageCounter}`;
  const div = document.createElement("div");

  if (!response.answered) {
    div.className = "msg msg-answer msg-refused";
    div.innerHTML = `
      ${renderRoute(response)}
      <p class="refusal-title">${escapeHtml(response.answer)}</p>
      <p class="refusal-reason">${escapeHtml(REFUSAL_EXPLANATIONS[response.refusalReason] || "")}</p>
      ${renderToolCalls(response)}`;
    return div;
  }

  const citedIds = new Set(response.citations.map((c) => c.sourceId));
  const trackerRefs = new Set(response.trackerRefs ?? []);
  div.className = "msg msg-answer";
  div.innerHTML = `
    ${renderRoute(response)}
    <div class="answer-text">${formatAnswer(response.answer, citedIds, trackerRefs, messageId)}</div>
    ${renderToolCalls(response)}
    <div class="sources" ${response.citations.length ? "" : "hidden"}>
      <p class="sources-title">Document sources</p>
      ${response.citations.map((c) => `
        <details class="source" id="${messageId}-${c.sourceId}">
          <summary>
            <span class="cite">${escapeHtml(c.sourceId)}</span>
            <span class="source-meta"><strong>${escapeHtml(c.documentName)}</strong>, page ${c.page}</span>
            <span class="source-sim" title="Cosine similarity between your question and this passage">match ${c.similarity.toFixed(2)}</span>
          </summary>
          ${/\.pdf$/i.test(c.documentName) ? `
            <div class="page-view">
              <button type="button" class="btn btn-secondary btn-small view-page"
                data-src="/api/documents/${c.documentId}/pages/${c.page}/image">View page ${c.page} as in the PDF</button>
            </div>` : ""}
          <p class="passage"><span class="passage-label">Passage given to the model:</span>${escapeHtml(c.passage)}</p>
        </details>`).join("")}
    </div>`;
  return div;
}

function renderError(message) {
  const div = document.createElement("div");
  div.className = "msg msg-error";
  div.textContent = message;
  return div;
}

// "View page" swaps the button for the rendered PDF page. The image is only requested on
// click, and a click on the image opens it full size in a new tab.
els.thread.addEventListener("click", (event) => {
  const button = event.target.closest("button.view-page");
  if (!button) return;
  const src = button.dataset.src;
  const link = document.createElement("a");
  link.href = src;
  link.target = "_blank";
  link.rel = "noopener";
  link.title = "Open full size in a new tab";
  const img = document.createElement("img");
  img.className = "page-image";
  img.alt = button.textContent.replace("View ", "Rendered ");
  img.src = src;
  img.addEventListener("error", () => {
    const note = document.createElement("p");
    note.className = "muted small";
    note.textContent = "The page image isn't available for this document (it may have been uploaded before page previews existed).";
    link.replaceWith(note);
  });
  link.appendChild(img);
  button.replaceWith(link);
});

// Clicking an inline [S1] marker opens and highlights that source below the answer.
els.thread.addEventListener("click", (event) => {
  const button = event.target.closest("button.cite");
  if (!button) return;
  const details = document.getElementById(`${button.dataset.msg}-${button.dataset.source}`);
  if (!details) return;
  details.open = true;
  details.scrollIntoView({ behavior: "smooth", block: "nearest" });
  details.classList.remove("flash");
  void details.offsetWidth; // restart the animation
  details.classList.add("flash");
});

// ---------- asking ----------

async function ask(question) {
  const projectId = els.project.value;
  if (!projectId || !question.trim()) return;

  addQuestion(question);
  els.question.value = "";
  autosize();
  els.askBtn.disabled = true;
  const loading = addLoading();

  try {
    const response = await api(`/api/projects/${projectId}/ask`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ question }),
    });
    loading.replaceWith(renderAnswer(response));
    const entries = loadHistory(projectId);
    entries.push({ question, response, at: new Date().toISOString() });
    saveHistory(projectId, entries);
    if (els.project.value === projectId) chatHistory = entries;
    updateChatButtons();
  } catch (error) {
    loading.replaceWith(renderError(error.message));
  } finally {
    els.askBtn.disabled = false;
    scrollToBottom();
    els.question.focus();
  }
}

els.form.addEventListener("submit", (event) => {
  event.preventDefault();
  ask(els.question.value.trim());
});

// Enter sends, Shift+Enter adds a new line.
els.question.addEventListener("keydown", (event) => {
  if (event.key === "Enter" && !event.shiftKey) {
    event.preventDefault();
    els.form.requestSubmit();
  }
});

function autosize() {
  els.question.style.height = "auto";
  els.question.style.height = `${Math.min(els.question.scrollHeight, 160)}px`;
}
els.question.addEventListener("input", autosize);

function renderSamples() {
  els.samples.innerHTML = "";
  const groups = [...new Set(SAMPLE_QUESTIONS.map((s) => s.group))];
  for (const group of groups) {
    const row = document.createElement("div");
    row.className = "sample-group";
    const label = document.createElement("span");
    label.className = "sample-group-label";
    label.textContent = group;
    row.appendChild(label);
    for (const sample of SAMPLE_QUESTIONS.filter((s) => s.group === group)) {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "sample";
      button.textContent = sample.text;
      if (sample.tag) {
        const tag = document.createElement("span");
        tag.className = "sample-tag";
        tag.textContent = sample.tag;
        button.appendChild(tag);
      }
      button.addEventListener("click", () => ask(sample.text));
      row.appendChild(button);
    }
    els.samples.appendChild(row);
  }
}

// ---------- projects and documents ----------

async function loadProjects(selectId) {
  config = await api("/api/config");
  projectList = await api("/api/projects");
  els.project.innerHTML = projectList
    .map((p) => `<option value="${p.id}">${escapeHtml(p.name)}</option>`)
    .join("");
  const hasProjects = projectList.length > 0;
  els.askBtn.disabled = !hasProjects;
  $("delete-project").disabled = !hasProjects;
  if (!hasProjects) {
    els.project.innerHTML = '<option value="">No projects yet</option>';
    els.documents.innerHTML = "";
    els.docCount.textContent = "";
    showProjectHistory();
    return;
  }
  // Open the requested project, else the one used last in this browser, else the demo, else the first.
  const byId = (id) => projectList.find((p) => String(p.id) === String(id));
  const demo = projectList.find((p) => p.name === config.demoProjectName);
  const chosen = byId(selectId) ?? byId(rememberedProject()) ?? demo ?? projectList[0];
  els.project.value = String(chosen.id);
  rememberProject(chosen.id);
  showProjectHistory();
  await loadDocuments();
}

// Re-draws the saved conversation of the selected project (or the intro if there is none).
function showProjectHistory() {
  els.thread.querySelectorAll(".msg").forEach((m) => m.remove());
  chatHistory = els.project.value ? loadHistory(els.project.value) : [];
  els.intro.hidden = chatHistory.length > 0;
  for (const entry of chatHistory) {
    addQuestion(entry.question);
    els.thread.appendChild(renderAnswer(entry.response));
  }
  updateChatButtons();
  scrollToBottom();
}

function rememberedProject() {
  try { return localStorage.getItem("speclens.project"); } catch { return null; }
}

function rememberProject(id) {
  try { localStorage.setItem("speclens.project", String(id)); } catch { /* ignore */ }
}

function updateChatButtons() {
  $("export-chat").disabled = chatHistory.length === 0;
}

$("new-project-btn").addEventListener("click", () => {
  $("new-project-form").hidden = false;
  $("new-project-btn").hidden = true;
  $("new-project-error").textContent = "";
  $("new-project-name").focus();
});

$("new-project-cancel").addEventListener("click", () => {
  $("new-project-form").hidden = true;
  $("new-project-btn").hidden = false;
});

$("new-project-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const name = $("new-project-name").value.trim();
  if (!name) return;
  try {
    const project = await api("/api/projects", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name }),
    });
    $("new-project-name").value = "";
    $("new-project-form").hidden = true;
    $("new-project-btn").hidden = false;
    await loadProjects(project.id);
  } catch (error) {
    $("new-project-error").textContent = error.message;
  }
});

$("delete-project").addEventListener("click", async () => {
  const project = projectList.find((p) => String(p.id) === els.project.value);
  if (!project) return;
  const docCount = els.documents.querySelectorAll("li[data-id]").length;
  const ok = window.confirm(`Delete the project "${project.name}" and its ${docCount} document(s)?\n\n`
    + "This removes the documents and their search index for good. It can't be undone.");
  if (!ok) return;
  try {
    await api(`/api/projects/${project.id}`, { method: "DELETE" });
    clearHistory(project.id);
    await loadProjects();
  } catch (error) {
    window.alert(error.message);
  }
});

async function loadDocuments() {
  const projectId = els.project.value;
  if (!projectId) return;
  const docs = await api(`/api/projects/${projectId}/documents`);
  els.docCount.textContent = docs.length ? `${docs.length}` : "";
  els.documents.innerHTML = docs.length
    ? docs.map((d) => `
        <li data-id="${d.id}" data-name="${escapeHtml(d.filename)}">
          <span class="doc-name">${escapeHtml(d.filename)}</span>
          <span class="doc-pages">${d.pageCount} ${d.pageCount === 1 ? "page" : "pages"}</span>
          ${config.editingEnabled ? `<button type="button" class="icon-btn small danger delete-doc"
              title="Delete this document" aria-label="Delete ${escapeHtml(d.filename)}">&times;</button>` : ""}
        </li>`).join("")
    : '<li class="muted">No documents in this project yet.</li>';
}

els.documents.addEventListener("click", async (event) => {
  const button = event.target.closest("button.delete-doc");
  if (!button) return;
  const item = button.closest("li");
  const ok = window.confirm(`Delete "${item.dataset.name}" from this project?\n\n`
    + "Its passages will no longer be searched. Earlier answers in this chat keep their quoted passages.");
  if (!ok) return;
  try {
    await api(`/api/projects/${els.project.value}/documents/${item.dataset.id}`, { method: "DELETE" });
    await loadDocuments();
  } catch (error) {
    window.alert(error.message);
  }
});

$("refresh-docs").addEventListener("click", () => {
  loadDocuments().catch((error) => window.alert(error.message));
});

els.project.addEventListener("change", () => {
  rememberProject(els.project.value);
  showProjectHistory();
  loadDocuments().catch((e) => console.error(e));
});

// ---------- new chat and export ----------

$("new-chat").addEventListener("click", () => {
  if (chatHistory.length && !window.confirm("Start a new chat? This conversation will be removed from this browser.\n\n"
      + "Use Export first if you want to keep it.")) {
    return;
  }
  clearHistory(els.project.value);
  showProjectHistory();
  els.question.focus();
});

$("export-chat").addEventListener("click", () => {
  if (!chatHistory.length) return;
  const project = projectList.find((p) => String(p.id) === els.project.value);
  const blob = new Blob([toMarkdown(project?.name ?? "Project", chatHistory)], { type: "text/markdown" });
  const link = document.createElement("a");
  link.href = URL.createObjectURL(blob);
  const slug = (project?.name ?? "project").toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
  link.download = `speclens-${slug}-${new Date().toISOString().slice(0, 10)}.md`;
  link.click();
  URL.revokeObjectURL(link.href);
});

// The conversation as a Markdown file: each question, the answer, and its cited passages.
function toMarkdown(projectName, entries) {
  const quote = (text) => String(text).split("\n").map((line) => `> ${line}`).join("\n");
  const parts = [`# SpecLens conversation: ${projectName}`, `Exported ${new Date().toLocaleString()}`, ""];
  for (const { question, response, at } of entries) {
    parts.push("---", "", `## ${question}`, `_Asked ${new Date(at).toLocaleString()}_`, "");
    if (response.route) {
      parts.push(`_Route: ${ROUTE_LABELS[response.route] ?? response.route} (intent: ${INTENT_LABELS[response.intent] ?? response.intent})_`, "");
    }
    if (response.notice) parts.push(`> Note: ${response.notice}`, "");
    if (!response.answered) {
      parts.push(`**${response.answer}** ${REFUSAL_EXPLANATIONS[response.refusalReason] || ""}`, "");
    } else {
      parts.push(response.answer, "");
      if (response.citations.length) {
        parts.push("**Document sources**", "");
        for (const c of response.citations) {
          parts.push(`- **[${c.sourceId}] ${c.documentName}, page ${c.page}**`, "", quote(c.passage), "");
        }
      }
    }
    if (response.toolCalls?.length) {
      parts.push("**Tracker API calls**", "");
      for (const c of response.toolCalls) {
        parts.push(`- \`${c.tool}(${c.arguments})\` → ${c.outcome}: ${c.detail}`);
      }
      parts.push("");
    }
  }
  return parts.join("\n");
}

// ---------- upload (only when the server allows it) ----------

function setUpUpload() {
  // Uploads, new projects and deletions are all off on the read-only public demo.
  document.querySelectorAll(".editing-only").forEach((el) => { el.hidden = !config.editingEnabled; });
  els.uploadBox.hidden = !config.editingEnabled;
  els.uploadHint.textContent = `PDF or DOCX, up to ${config.maxFileMb} MB and ${config.maxPages} pages.`;
}

$("upload-btn").addEventListener("click", () => els.file.click());

els.file.addEventListener("change", async () => {
  const file = els.file.files[0];
  if (!file || !els.project.value) return;
  const form = new FormData();
  form.append("file", file);
  els.uploadStatus.className = "small muted";
  els.uploadStatus.textContent = `Reading and indexing ${file.name}...`;
  try {
    const doc = await api(`/api/projects/${els.project.value}/documents`, { method: "POST", body: form });
    els.uploadStatus.textContent = `Added ${doc.filename}: ${doc.pageCount} pages, ${doc.chunkCount} passages.`;
    await loadDocuments();
  } catch (error) {
    els.uploadStatus.className = "small";
    els.uploadStatus.style.color = "var(--error)";
    els.uploadStatus.textContent = error.message;
  } finally {
    els.file.value = "";
  }
});

// ---------- footer: real evaluation result ----------

async function loadEvalSummary() {
  const docs = "https://github.com/a-k-ay/speclens/blob/main/docs/";
  try {
    const s = await api("/eval-summary.json");
    let html = `Evaluation: retrieval hit@${s.k} <strong>${s.hitAtK}/${s.answerable}</strong> ·
      correct citation <strong>${s.answeredWithExpectedCitation}/${s.answerable}</strong> ·
      unanswerable refused <strong>${s.unanswerableRefused}/${s.unanswerable}</strong>`;
    try {
      const r = await api("/eval-routing-summary.json");
      html += ` · routing <strong>${r.intentCorrect}/${r.questions}</strong> ·
        invented tracker statuses shown <strong>${r.inventedShown}</strong>`;
    } catch {
      // No routing results published yet.
    }
    html += ` · <a href="${docs}EVAL.md" target="_blank" rel="noopener">method</a>,
      <a href="${docs}EVAL-ROUTING.md" target="_blank" rel="noopener">routing</a>`;
    els.evalSummary.innerHTML = html;
  } catch {
    els.evalSummary.textContent = "";
  }
}

// ---------- start ----------

renderSamples();
autosize();
Promise.all([loadProjects().then(setUpUpload), loadEvalSummary()]).catch((error) => {
  els.thread.appendChild(renderError(`Could not load SpecLens: ${error.message}`));
});
