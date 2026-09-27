(() => {
  "use strict";

  const OWNER = "ZYGY7678";
  const REPO = "A-database-of-approved-addresses-in-the-application";
  const API = "https://api.github.com/repos/" + OWNER + "/" + REPO;
  const TOKEN_KEY = "approved-browser-developer-token";
  const REQUEST_PREFIXES = [
    "[בקשת אישור אתר חדש]",
    "[תלונה על אתר מאושר]",
    "[בקשה להסרת אתר מאושר]"
  ];
  const STATUS_LABELS = ["מאושר", "נדחה"];

  const tokenEl = document.getElementById("token");
  const statusEl = document.getElementById("status");
  const requestsEl = document.getElementById("requests");
  const countEl = document.getElementById("count");

  tokenEl.value = sessionStorage.getItem(TOKEN_KEY) || "";

  function setStatus(message, ok = false) {
    statusEl.textContent = message;
    statusEl.classList.toggle("ok", ok);
  }

  function getToken() {
    const token = tokenEl.value.trim();
    if (!token) throw new Error("הזן GitHub Token של המפתח.");
    return token;
  }

  function headers() {
    return {
      "Accept": "application/vnd.github+json",
      "Authorization": "Bearer " + getToken(),
      "X-GitHub-Api-Version": "2022-11-28"
    };
  }

  async function api(path, options = {}) {
    const response = await fetch(API + path, {
      ...options,
      headers: { ...headers(), ...(options.headers || {}) }
    });

    let data = null;
    try { data = await response.json(); } catch (_) {}

    if (!response.ok) {
      throw new Error(data?.message || ("GitHub API error " + response.status));
    }
    return data;
  }

  async function ensureLabels() {
    const labels = [
      ["בקשת אתר", "1d76db", "פנייה ממשתמש לגבי אתר"],
      ["דיווח אתר", "b60205", "דיווח או בקשת הסרה"],
      ["מאושר", "0e8a16", "הפנייה אושרה"],
      ["נדחה", "5319e7", "הפנייה נדחתה"]
    ];

    for (const [name, color, description] of labels) {
      try {
        await api("/labels/" + encodeURIComponent(name));
      } catch (error) {
        if (!/not found|404/i.test(error.message)) continue;
        await api("/labels", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ name, color, description })
        });
      }
    }
  }

  function isRequest(issue) {
    if (issue.pull_request) return false;
    const title = String(issue.title || "");
    return REQUEST_PREFIXES.some(prefix => title.startsWith(prefix)) ||
      String(issue.body || "").includes("בקשה שנשלחה מתוך דפדפן מאושר");
  }

  function statusOf(issue) {
    const names = (issue.labels || []).map(label => String(label.name));
    if (names.includes("מאושר")) return "approved";
    if (names.includes("נדחה")) return "denied";
    return "pending";
  }

  function statusText(status) {
    return status === "approved" ? "אושר" : status === "denied" ? "נדחה" : "ממתין לטיפול";
  }

  function escapeHtml(value) {
    return String(value)
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  function formatDate(value) {
    try {
      return new Intl.DateTimeFormat("he-IL", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
    } catch (_) {
      return value;
    }
  }

  function render(issues) {
    countEl.textContent = String(issues.length);
    if (!issues.length) {
      requestsEl.innerHTML = '<div class="notice">לא נמצאו פניות.</div>';
      return;
    }

    requestsEl.innerHTML = issues.map(issue => {
      const status = statusOf(issue);
      const body = String(issue.body || "");
      const labels = (issue.labels || [])
        .map(label => '<span class="status">' + escapeHtml(label.name) + '</span>')
        .join(" ");

      const actionButtons = status === "pending"
        ? '<div class="request-actions">' +
          '<button class="approve" data-action="approve" data-number="' + issue.number + '">✅ אשר</button>' +
          '<button class="deny" data-action="deny" data-number="' + issue.number + '">❌ דחה</button>' +
          '<a class="openIssue" href="' + escapeHtml(issue.html_url) + '" target="_blank" rel="noopener">פתח ב־GitHub</a>' +
          '</div>'
        : '<div class="request-actions"><a class="openIssue" href="' + escapeHtml(issue.html_url) + '" target="_blank" rel="noopener">פתח ב־GitHub</a></div>';

      return '<article class="request-card">' +
        '<div class="request-head">' +
          '<div><h3>#' + issue.number + ' — ' + escapeHtml(issue.title) + '</h3>' +
          '<p class="request-meta">נפתח ' + escapeHtml(formatDate(issue.created_at)) + ' • מאת ' + escapeHtml(issue.user?.login || "לא ידוע") + '</p></div>' +
          '<span class="status ' + status + '">' + statusText(status) + '</span>' +
        '</div>' +
        '<div>' + labels + '</div>' +
        '<div class="request-body">' + escapeHtml(body) + '</div>' +
        actionButtons +
      '</article>';
    }).join("");

    requestsEl.querySelectorAll("button[data-action]").forEach(button => {
      button.addEventListener("click", () => decide(Number(button.dataset.number), button.dataset.action));
    });
  }

  async function loadRequests() {
    setStatus("טוען פניות...");
    try {
      await ensureLabels();
      const issues = await api("/issues?state=all&per_page=100&sort=created&direction=desc");
      const requests = issues.filter(isRequest);
      render(requests);
      setStatus("הבקשות נטענו בהצלחה. " + requests.length + " פניות נמצאו.", true);
    } catch (error) {
      setStatus("שגיאה: " + error.message);
      requestsEl.innerHTML = "";
    }
  }

  async function decide(number, action) {
    const approved = action === "approve";
    const defaultMessage = approved
      ? "✅ הבקשה שלכם נבדקה ואושרה. תודה על הפנייה."
      : "❌ הבקשה שלכם נבדקה, ובשלב זה היא לא אושרה. תודה על הפנייה.";

    const message = window.prompt(
      approved ? "הודעת אישור למשתמש" : "הודעת דחייה למשתמש",
      defaultMessage
    );
    if (message === null) return;

    const cleanMessage = message.trim();
    if (!cleanMessage) {
      setStatus("לא נשלחה החלטה כי ההודעה ריקה.");
      return;
    }

    setStatus("שולח החלטה לפנייה #" + number + "...");
    try {
      const issue = await api("/issues/" + number);
      const preservedLabels = (issue.labels || [])
        .map(label => String(label.name))
        .filter(name => !STATUS_LABELS.includes(name));
      const nextLabels = [...new Set([...preservedLabels, approved ? "מאושר" : "נדחה"])];

      await api("/issues/" + number + "/comments", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ body: cleanMessage })
      });

      await api("/issues/" + number, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          labels: nextLabels,
          state: "closed",
          state_reason: approved ? "completed" : "not_planned"
        })
      });

      setStatus(
        approved
          ? "הפנייה אושרה וההודעה נשלחה למגיש."
          : "הפנייה נדחתה וההודעה נשלחה למגיש.",
        true
      );
      await loadRequests();
    } catch (error) {
      setStatus("שגיאה בטיפול בפנייה: " + error.message);
    }
  }

  document.getElementById("saveToken").addEventListener("click", async () => {
    try {
      const token = getToken();
      sessionStorage.setItem(TOKEN_KEY, token);
      await loadRequests();
    } catch (error) {
      setStatus("שגיאה: " + error.message);
    }
  });

  document.getElementById("clearToken").addEventListener("click", () => {
    sessionStorage.removeItem(TOKEN_KEY);
    tokenEl.value = "";
    setStatus("הטוקן נמחק מהדפדפן.");
    requestsEl.innerHTML = "";
    countEl.textContent = "0";
  });

  document.getElementById("refresh").addEventListener("click", loadRequests);

  if (tokenEl.value) loadRequests();
})();