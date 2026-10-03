// SpecLens chat UI. Plain JavaScript, no framework or build step.
// Everything that comes from the server (answers, passages, file names) is HTML-escaped
// before it is put on the page, so document text can never inject markup or scripts.

const SAMPLE_QUESTIONS = [
  { text: "When must the customer invoice be generated?", tag: "Documents disagree" },
  { text: "What is the fixed fee for the implementation and how is it paid?" },
  { text: "What does BR-6.4 say?" },
  { text: "Which test case covers BR-6.3?" },
  { text: "What is the penalty for late delivery of a shipment?", tag: "Not in the documents" },
];

const REFUSAL_EXPLANATIONS = {
  NO_RELEVANT_SOURCES: "Nothing in these documents is close to this question, so the model wasn't asked.",
  NOT_IN_SOURCES: "Related passages were found, but none of them contains the answer.",
  UNGROUNDED_ANSWER: "The model's answer didn't cite any document, so it was withheld.",
};

const $ = (id) => document.getElementById(id);
const els = {
  project: $("project"), documents: $("documents"), docCount: $("doc-count"),
  uploadBox: $("upload-box"), file: $("file"), uploadHint: $("upload-hint"), uploadStatus: $("upload-status"),
  thread: $("thread"), intro: $("intro"), samples: $("samples"),
  form: $("ask-form"), question: $("question"), askBtn: $("ask-btn"), evalSummary: $("eval-summary"),
};

let messageCounter = 0;
let config = { uploadsEnabled: false, demoProjectName: null };

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
// **bold**, and [S1] citation markers turned into buttons. Input is escaped first.
function formatAnswer(text, citedIds, messageId) {
  const inline = (line) => escapeHtml(line)
    .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
    .replace(/\[(S\d+)\]/g, (match, id) => citedIds.has(id)
      ? `<button type="button" class="cite" data-msg="${messageId}" data-source="${id}" aria-label="Show source ${id}">${id}</button>`
      : match);

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
  div.innerHTML = '<span class="spinner" aria-hidden="true"></span><span>Searching the documents...</span>';
  els.thread.appendChild(div);
  scrollToBottom();
  return div;
}

function renderAnswer(response) {
  const messageId = `m${++messageCounter}`;
  const div = document.createElement("div");

  if (!response.answered) {
    div.className = "msg msg-answer msg-refused";
    div.innerHTML = `
      <p class="refusal-title">${escapeHtml(response.answer)}</p>
      <p class="refusal-reason">${escapeHtml(REFUSAL_EXPLANATIONS[response.refusalReason] || "")}</p>`;
    return div;
  }

  const citedIds = new Set(response.citations.map((c) => c.sourceId));
  div.className = "msg msg-answer";
  div.innerHTML = `
    <div class="answer-text">${formatAnswer(response.answer, citedIds, messageId)}</div>
    <div class="sources">
      <p class="sources-title">Sources</p>
      ${response.citations.map((c) => `
        <details class="source" id="${messageId}-${c.sourceId}">
          <summary>
            <span class="cite">${escapeHtml(c.sourceId)}</span>
            <span class="source-meta"><strong>${escapeHtml(c.documentName)}</strong>, page ${c.page}</span>
            <span class="source-sim" title="Cosine similarity between your question and this passage">match ${c.similarity.toFixed(2)}</span>
          </summary>
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
  for (const sample of SAMPLE_QUESTIONS) {
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
    els.samples.appendChild(button);
  }
}

// ---------- projects and documents ----------

async function loadProjects() {
  config = await api("/api/config");
  const projects = await api("/api/projects");
  els.project.innerHTML = projects
    .map((p) => `<option value="${p.id}">${escapeHtml(p.name)}</option>`)
    .join("");
  if (!projects.length) {
    els.project.innerHTML = '<option value="">No projects yet</option>';
    els.askBtn.disabled = true;
    return;
  }
  // Open the demo project by default when there is one.
  const demo = projects.find((p) => p.name === config.demoProjectName);
  els.project.value = String((demo ?? projects[0]).id);
  await loadDocuments();
}

async function loadDocuments() {
  const projectId = els.project.value;
  if (!projectId) return;
  const docs = await api(`/api/projects/${projectId}/documents`);
  els.docCount.textContent = docs.length ? `${docs.length}` : "";
  els.documents.innerHTML = docs.length
    ? docs.map((d) => `
        <li><span class="doc-name">${escapeHtml(d.filename)}</span>
            <span class="doc-pages">${d.pageCount} ${d.pageCount === 1 ? "page" : "pages"}</span></li>`).join("")
    : '<li class="muted">No documents in this project yet.</li>';
}

els.project.addEventListener("change", () => {
  els.thread.querySelectorAll(".msg").forEach((m) => m.remove());
  els.intro.hidden = false;
  loadDocuments().catch((e) => console.error(e));
});

// ---------- upload (only when the server allows it) ----------

function setUpUpload() {
  els.uploadBox.hidden = !config.uploadsEnabled;
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
  try {
    const s = await api("/eval-summary.json");
    els.evalSummary.innerHTML = `Evaluation on ${s.answerable + s.unanswerable} golden questions:
      retrieval hit@${s.k} <strong>${s.hitAtK}/${s.answerable}</strong> ·
      answered with correct citation <strong>${s.answeredWithExpectedCitation}/${s.answerable}</strong> ·
      unanswerable refused <strong>${s.unanswerableRefused}/${s.unanswerable}</strong> ·
      <a href="https://github.com/a-k-ay/speclens/blob/main/docs/EVAL.md" target="_blank" rel="noopener">method and details</a>`;
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
