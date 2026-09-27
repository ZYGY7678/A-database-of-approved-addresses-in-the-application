(() => {
  "use strict";

  const PACKAGE = "com.zygy7678.approvedbrowser";
  const ADMIN = PACKAGE + "/.ApprovedBrowserDeviceAdminReceiver";

  const logEl = document.getElementById("log");
  const statusEl = document.getElementById("deviceStatus");
  const badgeEl = document.getElementById("deviceBadge");
  const noticeEl = document.getElementById("browserNotice");
  const setupNoticeEl = document.getElementById("setupNotice");
  const connectBtn = document.getElementById("connect");
  const setupBtn = document.getElementById("setup");
  const codeEl = document.getElementById("code");
  const confirmEl = document.getElementById("confirm");

  let transport = null;
  let adb = null;
  let connected = false;
  let busy = false;

  function setLog(message, append = false) {
    logEl.textContent = append && logEl.textContent ? logEl.textContent + "\n" + message : message;
    logEl.scrollTop = logEl.scrollHeight;
  }

  function log(message) {
    setLog(message, true);
  }

  function setStatus(text, badge = "ממתין לחיבור", ok = false) {
    statusEl.textContent = text;
    badgeEl.textContent = badge;
    badgeEl.classList.toggle("ok", ok);
  }

  function showNotice(text) {
    noticeEl.textContent = text;
    noticeEl.classList.remove("hidden");
  }

  function clearNotice() {
    noticeEl.textContent = "";
    noticeEl.classList.add("hidden");
  }

  function setBusy(value) {
    busy = value;
    connectBtn.disabled = value;
    setupBtn.disabled = value || !connected;
    connectBtn.textContent = value ? "מתחבר..." : (connected ? "חבר מחדש" : "חבר את הטלפון ב־USB");
  }

  function ensureWebUsb() {
    if (!window.isSecureContext) {
      throw new Error("האתר חייב להיפתח ב־HTTPS");
    }
    if (!("usb" in navigator)) {
      throw new Error("הדפדפן הזה לא תומך ב־WebUSB. השתמש ב־Chrome או Edge במחשב.");
    }
    if (typeof window.Adb === "undefined") {
      throw new Error("רכיב חיבור ה־ADB לא נטען. רענן את הדף ונסה שוב.");
    }
  }

  async function closeConnection() {
    connected = false;
    setupBtn.disabled = true;
    const oldTransport = transport;
    transport = null;
    adb = null;
    if (oldTransport) {
      try { await oldTransport.close(); } catch (_) {}
    }
  }

  async function runShell(command) {
    if (!adb) throw new Error("אין חיבור למכשיר");
    const stream = await adb.shell(command);
    const decoder = new TextDecoder();
    let output = "";
    let closed = false;

    try {
      while (!closed) {
        const response = await stream.receive();

        if (response.cmd === "WRTE") {
          if (response.data) {
            output += decoder.decode(
              new Uint8Array(response.data.buffer, response.data.byteOffset, response.data.byteLength),
              { stream: true }
            );
          }
          await stream.send("OKAY");
          continue;
        }

        if (response.cmd === "CLSE") {
          await stream.send("OKAY").catch(() => {});
          closed = true;
          continue;
        }

        if (response.cmd === "OKAY") {
          continue;
        }

        throw new Error("תגובה לא צפויה מ־ADB: " + response.cmd);
      }
    } finally {
      try { await stream.close(); } catch (_) {}
    }

    return output.trim();
  }

  function base64Utf8(value) {
    const bytes = new TextEncoder().encode(value);
    let binary = "";
    for (const byte of bytes) binary += String.fromCharCode(byte);
    return btoa(binary);
  }

  function validateCode() {
    const value = codeEl.value;
    if (!/^\d{4,12}$/.test(value)) {
      throw new Error("קוד הגישה חייב להכיל 4–12 ספרות.");
    }
    if (value !== confirmEl.value) {
      throw new Error("קודי הגישה אינם זהים.");
    }
    return value;
  }

  async function connectDevice() {
    if (busy) return;

    clearNotice();
    setBusy(true);
    await closeConnection();
    setStatus("פותח את חלון בחירת המכשיר...", "בחירת מכשיר");

    try {
      ensureWebUsb();
      log("מבקש הרשאת USB למכשיר Android...");
      transport = await window.Adb.open("WebUSB");

      if (!transport || !transport.isAdb()) {
        throw new Error("המכשיר שנבחר אינו מופיע כממשק ADB. ודא שניפוי USB מופעל.");
      }

      setStatus("מתחבר ל־ADB וממתין לאישור בטלפון...", "ממתין לאישור");
      log("נמצא ממשק ADB. אם מופיע בטלפון חלון 'אפשר ניפוי USB', אשר אותו.");

      transport.device.addEventListener?.("disconnect", () => {
        connected = false;
        adb = null;
        setupBtn.disabled = true;
        setStatus("הטלפון נותק", "נותק");
        log("החיבור ל־USB נותק.");
      });

      adb = await transport.connectAdb("host::", () => {
        setStatus("אשר את מפתח ה־RSA בטלפון...", "נדרש אישור");
        log("Android ביקש אישור RSA. אשר את המחשב בטלפון ואז ההתקנה תוכל להמשיך.");
      });

      const model = await runShell("getprop ro.product.model");
      const android = await runShell("getprop ro.build.version.release");
      const packagePath = await runShell("pm path " + PACKAGE);

      if (!packagePath || !/^package:/.test(packagePath.trim())) {
        throw new Error("האפליקציה לא מותקנת בטלפון. התקן קודם את דפדפן מאושר ואז חזור לכאן.");
      }

      connected = true;
      setStatus(
        "מחובר: " + (model || "Android") + " • Android " + (android || "?"),
        "מחובר",
        true
      );
      setupBtn.disabled = false;
      setupNoticeEl.textContent = "המכשיר מחובר. הזן קוד גישה ולחץ על הכפתור כדי לבצע את ההגדרה ישירות דרך הדפדפן.";
      log("החיבור הצליח: " + (model || "Android") + " • Android " + (android || "?"));
      log("האפליקציה נמצאה במכשיר.");
    } catch (error) {
      await closeConnection();
      setStatus("לא ניתן להתחבר", "שגיאה");
      const message = normalizeError(error);
      showNotice(message);
      setLog("שגיאה: " + message);
    } finally {
      setBusy(false);
      setupBtn.disabled = !connected;
    }
  }

  async function setupDeviceOwner() {
    if (busy || !connected) return;

    let accessCode;
    try {
      accessCode = validateCode();
    } catch (error) {
      setLog("שגיאה: " + error.message);
      return;
    }

    setBusy(true);
    try {
      setStatus("בודק מצב בעל המכשיר...", "בודק");
      log("בודק אם כבר מוגדר Device Owner...");

      const owners = await runShell("dpm list-owners");
      const hasOurOwner = owners.includes(PACKAGE);

      if (!hasOurOwner && owners.trim()) {
        throw new Error("כבר מוגדר במכשיר בעל מכשיר אחר. לא אשנה את הבעלות הקיימת.");
      }

      if (!hasOurOwner) {
        setStatus("מגדיר את האפליקציה כבעלת המכשיר...", "מתקין");
        log("שולח את פקודת Device Owner ישירות לטלפון...");
        const result = await runShell("dpm set-device-owner " + ADMIN);

        if (/(error|exception|failed|failure|not allowed|unknown)/i.test(result)) {
          throw new Error(result || "Android דחה את הגדרת Device Owner.");
        }
        log("פקודת Device Owner הסתיימה.");
      } else {
        log("האפליקציה כבר מוגדרת כבעלת המכשיר.");
      }

      setStatus("מעביר את קוד הגישה לאפליקציה...", "מגדיר קוד");
      const encoded = base64Utf8(accessCode);
      const startResult = await runShell(
        "am start -n " + PACKAGE + "/.MainActivity --es setup_access_code_b64 " + encoded
      );

      if (/error|exception|unable to/i.test(startResult)) {
        throw new Error(startResult || "לא הצלחתי להפעיל את האפליקציה.");
      }

      setStatus("ההגדרה הושלמה", "הושלם", true);
      setupNoticeEl.textContent = "ההגדרה הושלמה. דפדפן מאושר הופעל וקוד הגישה הוגדר.";
      log("קוד הגישה הועבר ישירות למכשיר.");
      log("דפדפן מאושר הופעל.");
    } catch (error) {
      const message = normalizeError(error);
      setStatus("ההגדרה נכשלה", "שגיאה");
      setLog("שגיאה: " + message);
      setupNoticeEl.textContent = "ההגדרה לא הושלמה. בדוק את ההודעה ביומן ונסה שוב.";
    } finally {
      setBusy(false);
      setupBtn.disabled = !connected;
    }
  }

  function normalizeError(error) {
    const raw = String(error?.message || error || "שגיאה לא ידועה");
    if (/user rejected|notfound|cancel/i.test(raw)) return "בחירת המכשיר בוטלה. לחץ שוב על 'חבר את הטלפון ב־USB'.";
    if (/claim|interface/i.test(raw)) return "הממשק תפוס על ידי תוכנת ADB אחרת. סגור Android Studio, scrcpy, WebADB או תוכנת ניהול אחרת ונסה שוב.";
    if (/access|permission|security/i.test(raw)) return "הגישה ל־USB נחסמה. אשר את חלון ה־USB בדפדפן ואת הרשאת ניפוי ה־USB בטלפון.";
    if (/auth|unauthorized|RSA/i.test(raw)) return "הטלפון לא אישר את מפתח ה־RSA. אשר את חלון 'אפשר ניפוי USB' בטלפון ונסה שוב.";
    return raw;
  }

  document.getElementById("connect").addEventListener("click", connectDevice);
  document.getElementById("setup").addEventListener("click", setupDeviceOwner);

  document.getElementById("download").addEventListener("click", () => {
    window.open(
      "https://github.com/ZYGY7678/A-database-of-approved-addresses-in-the-application/tree/main/desktop/ApprovedBrowser.AdbTool",
      "_blank",
      "noopener"
    );
  });

  try {
    ensureWebUsb();
    setStatus("מוכן לחיבור USB", "מוכן");
    log("WebUSB זמין. השתמש ב־Chrome או Edge וחבר את הטלפון בכבל USB.");
  } catch (error) {
    const message = normalizeError(error);
    setStatus("נדרש דפדפן תומך", "לא זמין");
    showNotice(message);
    log("הערה: " + message);
  }
})();