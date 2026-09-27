(() => {
  "use strict";

  const PACKAGE = "com.zygy7678.approvedbrowser";
  const ADMIN = PACKAGE + "/.ApprovedBrowserDeviceAdminReceiver";

  const logEl = document.getElementById("log");
  const statusEl = document.getElementById("deviceStatus");
  const badgeEl = document.getElementById("deviceBadge");
  const noticeEl = document.getElementById("browserNotice");
  const setupNoticeEl = document.getElementById("setupNotice");
  const removeOwnerNoticeEl = document.getElementById("removeOwnerNotice");
  const connectBtn = document.getElementById("connect");
  const setupBtn = document.getElementById("setup");
  const removeOwnerBtn = document.getElementById("removeOwner");
  const codeEl = document.getElementById("code");
  const confirmEl = document.getElementById("confirm");
  const selectedRouteNoticeEl = document.getElementById("selectedRouteNotice");
  const routeButtons = [...document.querySelectorAll(".route-option")];
  const APK_URL = "https://github.com/ZYGY7678/A-database-of-approved-addresses-in-the-application/releases/download/latest/app-debug.apk";
  const LATEST_VERSION_CODE = 2;
  const LATEST_VERSION_NAME = "1.0.1";

  const ROUTES = {
    ETROG: { title: "אתרוג", summary: "אתרים חיוניים ומאושרים בלבד" },
    HADASS: { title: "הדס", summary: "אתרים מאושרים + AI, בלי חדשות ופורומים" },
    LULAV: { title: "לולב", summary: "כל האתרים המאושרים, כולל תמונות" }
  };

  let selectedRoute = "ETROG";
  let transport = null;
  let adb = null;
  let connected = false;
  let busy = false;

  function selectRoute(route) {
    if (!ROUTES[route]) return;
    selectedRoute = route;
    routeButtons.forEach(button => {
      const selected = button.dataset.route === route;
      button.classList.toggle("selected", selected);
      button.setAttribute("aria-pressed", String(selected));
    });
    if (selectedRouteNoticeEl) {
      selectedRouteNoticeEl.innerHTML =
        "המסלול שנבחר: <b>" + ROUTES[route].title + "</b> — " + ROUTES[route].summary;
    }
  }

  function setLog(message, append = false) {
    logEl.textContent = append && logEl.textContent ? logEl.textContent + "\n" + message : message;
    logEl.scrollTop = logEl.scrollHeight;
  }

  function log(message) { setLog(message, true); }

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
    removeOwnerBtn.disabled = value || !connected;
    connectBtn.textContent = value ? "מתחבר..." : (connected ? "חבר מחדש" : "חבר את הטלפון ב־USB");
  }

  function ensureWebUsb() {
    if (!window.isSecureContext) throw new Error("האתר חייב להיפתח ב־HTTPS");
    if (!(("usb") in navigator)) throw new Error("הדפדפן הזה לא תומך ב־WebUSB. השתמש ב־Chrome או Edge במחשב.");
    if (typeof window.Adb === "undefined") throw new Error("רכיב חיבור ה־ADB לא נטען. רענן את הדף ונסה שוב.");
  }

  async function closeConnection() {
    connected = false;
    setupBtn.disabled = true;
    removeOwnerBtn.disabled = true;
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
        if (response.cmd === "OKAY") continue;
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
    if (!/^\d{4,12}$/.test(value)) throw new Error("קוד הגישה חייב להכיל 4–12 ספרות.");
    if (value !== confirmEl.value) throw new Error("קודי הגישה אינם זהים.");
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
      log("שלב 1/4: בחירת התקן USB...");
      log("שלב 2/4: פתיחת ממשק ה־ADB. עדיין לא מתבצעת התקנת APK.");

      let openError = null;
      for (let attempt = 1; attempt <= 4; attempt++) {
        try {
          if (attempt > 1) {
            setStatus("מאתחל את חיבור ה־USB מחדש (" + attempt + "/4)...", "מנסה שוב");
            log("ה־USB עדיין במעבר מצב. ממתין לפני ניסיון " + attempt + "...");
            await new Promise(resolve => setTimeout(resolve, 1200));
          }
          transport = await window.Adb.open("WebUSB");
          openError = null;
          break;
        } catch (error) {
          openError = error;
          const raw = String(error?.message || error || "");
          const transient = /operation that changes the device state is in progress|invalidstateerror|interface state change is in progress/i.test(raw);
          log("פתיחת ADB נכשלה בניסיון " + attempt + ": " + raw);
          if (!transient || attempt === 4) throw error;
          try { await transport?.close?.(); } catch (_) {}
          transport = null;
        }
      }
      if (openError) throw openError;
      if (!transport || !transport.isAdb()) {
        throw new Error("המכשיר נבחר אך ממשק ה־ADB לא נפתח. ודא שניפוי USB מופעל ונסה שוב.");
      }

      setStatus("ממשק ADB נפתח. ממתין לאישור RSA בטלפון...", "ממתין לאישור");
      log("שלב 3/4: ממשק ADB נפתח בהצלחה.");
      log("כעת Android אמור להציג בקשת 'אפשר ניפוי USB' אם המחשב עדיין לא אושר.");

      transport.device.addEventListener?.("disconnect", () => {
        connected = false;
        adb = null;
        setupBtn.disabled = true;
        removeOwnerBtn.disabled = true;
        setStatus("הטלפון נותק", "נותק");
        log("החיבור ל־USB נותק.");
      });

      adb = await transport.connectAdb("host::", () => {
        setStatus("אשר את מפתח ה־RSA בטלפון...", "נדרש אישור");
        log("שלב 4/4: Android ביקש אישור RSA. אשר את המחשב בטלפון ואז ההתקנה תוכל להמשיך.");
      });

      log("חיבור ADB נפתח. בודק עכשיו את Android...");
      const model = await runShell("getprop ro.product.model");
      const android = await runShell("getprop ro.build.version.release");
      let packagePath = await runShell("pm path " + PACKAGE);

      if (!packagePath || !/^package:/.test(packagePath.trim())) {
        log("האפליקציה לא מותקנת. מתקין את הגרסה העדכנית...");
        await installBundledApk("התקנה ראשונית");
        packagePath = await runShell("pm path " + PACKAGE);
        if (!packagePath || !/^package:/.test(packagePath.trim())) {
          throw new Error("התקנת דפדפן מאושר הסתיימה ללא זיהוי החבילה במכשיר.");
        }
      } else {
        const installed = await getInstalledVersion();
        if (installed.versionCode < LATEST_VERSION_CODE) {
          log("נמצאה גרסה " + installed.versionCode + " במכשיר. הגרסה העדכנית היא " + LATEST_VERSION_CODE + ".");
          await installBundledApk("עדכון לגרסה העדכנית");
          const afterUpdate = await getInstalledVersion();
          if (afterUpdate.versionCode < LATEST_VERSION_CODE) {
            throw new Error("העדכון הסתיים אבל הגרסה העדכנית עדיין לא מזוהה במכשיר.");
          }
          log("✓ האפליקציה עודכנה לגרסה " + afterUpdate.versionName + " (" + afterUpdate.versionCode + ").");
        } else {
          log("✓ האפליקציה כבר מעודכנת לגרסה " + installed.versionName + " (" + installed.versionCode + ").");
        }
      }

      connected = true;
      setStatus("מחובר: " + (model || "Android") + " • Android " + (android || "?"), "מחובר", true);
      setupBtn.disabled = false;
      removeOwnerBtn.disabled = false;
      setupNoticeEl.textContent = "המכשיר מחובר והאפליקציה נבדקה/עודכנה לגרסה העדכנית. הזן קוד גישה ולחץ על הכפתור כדי לבצע את ההגדרה.";
      removeOwnerNoticeEl.textContent = "המכשיר מחובר. הכפתור ינסה להסיר רק את מנהל המכשיר של האפליקציה שלנו, ללא איפוס.";
      log("החיבור הצליח: " + (model || "Android") + " • Android " + (android || "?"));
      log("האפליקציה נמצאה במכשיר ונבדקה מול הגרסה העדכנית.");

      setStatus("בודק מנהל מכשיר וחשבונות...", "בודק");
      const ownerInfo = await inspectDevicePolicy();
      const accountInfo = await inspectAccounts();
      if (!ownerInfo.hasAnyOwner && !accountInfo.hasAccounts) {
        setStatus("מחובר • מנהל מכשיר: לא נמצא", "מחובר", true);
      } else if (ownerInfo.deviceOwner) {
        setStatus("מחובר • Device Owner: " + ownerInfo.deviceOwner, "מנהל נמצא", true);
      } else if (ownerInfo.profiles.length) {
        setStatus("מחובר • Profile Owner: " + ownerInfo.profiles.join(", "), "מנהל נמצא", true);
      }
    } catch (error) {
      await closeConnection();
      setStatus("לא ניתן להתחבר", "שגיאה");
      const message = normalizeError(error);
      showNotice(message);
      setLog("שגיאה: " + message);
    } finally {
      setBusy(false);
      setupBtn.disabled = !connected;
      removeOwnerBtn.disabled = !connected;
    }
  }

  async function getInstalledVersion() {
    const output = await runShell("dumpsys package " + PACKAGE);
    const codeMatch = output.match(/versionCode=(\d+)/);
    const nameMatch = output.match(/versionName=([^\s]+)/);
    const versionCode = codeMatch ? Number(codeMatch[1]) : NaN;
    const versionName = nameMatch ? nameMatch[1] : "לא ידוע";
    if (!Number.isFinite(versionCode)) throw new Error("לא הצלחתי לקרוא את גרסת האפליקציה המותקנת.");
    return { versionCode, versionName };
  }

  async function installBundledApk(reason = "התקנה") {
    setStatus(reason + "...", "מתקין");
    log(reason + ": מוריד את ה־APK העדכני מהאתר...");
    const response = await fetch(APK_URL, { cache: "no-store" });
    if (!response.ok) throw new Error("לא ניתן להוריד את קובץ האפליקציה מהאתר.");
    const apkBlob = await response.blob();
    if (!apkBlob.size) throw new Error("קובץ ה־APK שהתקבל ריק.");
    log("מעביר את האפליקציה לטלפון דרך USB...");

    const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms));
    const remotePath = "/data/local/tmp/ApprovedBrowser.apk";
    let lastError = null;

    for (let attempt = 1; attempt <= 4; attempt++) {
      let sync = null;
      try {
        await sleep(attempt === 1 ? 350 : 750);
        sync = await adb.sync();
        await sync.push(apkBlob, remotePath, 0o644, (done, total) => {
          const percent = total ? Math.round(done * 100 / total) : 0;
          setStatus("מעביר את האפליקציה... " + percent + "%", "מתקין");
        });
        lastError = null;
        break;
      } catch (error) {
        lastError = error;
        const message = String(error?.message || error || "");
        const transientUsbState = /operation that changes the device state is in progress|invalidstateerror/i.test(message);
        if (!transientUsbState || attempt === 4) throw error;
        log("ה־USB עדיין עסוק. ממתין ומנסה שוב (" + (attempt + 1) + "/4)...");
        await sleep(1000);
      } finally {
        if (sync) {
          try { await sync.quit(); } catch (_) {}
        }
      }
    }
    if (lastError) throw lastError;

    log("מתקין את דפדפן מאושר במכשיר...");
    const result = await runShell("pm install -r -t " + remotePath);
    if (!/success/i.test(result)) throw new Error(result || "Android דחה את התקנת האפליקציה.");
    await runShell("rm -f " + remotePath);
    log("✓ דפדפן מאושר הותקן/עודכן בהצלחה.");
  }

  async function inspectAccounts() {
    const output = await runShell("dumpsys account");
    const accounts = [];
    const seen = new Set();

    const addAccount = (name, type) => {
      const cleanName = String(name || "").trim();
      const cleanType = String(type || "").trim();
      if (!cleanName && !cleanType) return;
      const key = cleanName + "\0" + cleanType;
      if (seen.has(key)) return;
      seen.add(key);
      accounts.push({ name: cleanName || "(ללא שם)", type: cleanType || "(סוג לא ידוע)" });
    };

    for (const match of output.matchAll(/Account\s*\{\s*name=([^,}]+),\s*type=([^}]+)\}/g)) {
      addAccount(match[1], match[2]);
    }
    for (const match of output.matchAll(/name=([^,\n}]+),\s*type=([^\n}]+)/g)) {
      addAccount(match[1], match[2]);
    }

    if (!accounts.length) {
      log("חשבונות Android: לא נמצאו חשבונות.");
      return { accounts: [], hasAccounts: false };
    }

    log("חשבונות Android שנמצאו (" + accounts.length + "):");
    accounts.forEach((account, index) => log("  " + (index + 1) + ". " + account.name + "  [" + account.type + "]"));
    log("⚠ קיימים חשבונות במכשיר. Android עלול לחסום הגדרת Device Owner בגלל החשבונות האלה.");
    log("ℹ Android לא מציין איזה חשבון יחיד גרם לחסימה — התנאי הוא שקיים חשבון במכשיר.");
    return { accounts, hasAccounts: true };
  }

  async function inspectDevicePolicy() {
    const owners = await runShell("dpm list-owners");
    const policy = await runShell("dumpsys device_policy");

    const components = [...new Set(
      [...owners.matchAll(/ComponentInfo\{([^/}\s]+)\/[^}]*\}/g)]
        .map(match => match[1]).filter(Boolean)
    )];

    const deviceOwnerMatch = policy.match(
      /Device Owner[^\n]*[\s\S]{0,500}?admin=ComponentInfo\{([^/}\s]+)\/[^}]*\}/i
    );
    const profileOwners = [];
    const profileRegex = /Profile Owner[^\n]*?[\s\S]{0,500}?admin=ComponentInfo\{([^/}\s]+)\/[^}]*\}/gi;
    let match;
    while ((match = profileRegex.exec(policy)) !== null) {
      if (match[1]) profileOwners.push(match[1]);
    }

    const deviceOwner = deviceOwnerMatch?.[1] || null;
    const profiles = [...new Set(profileOwners)];
    const hasAnyOwner = Boolean(deviceOwner || profiles.length || components.length);

    if (!hasAnyOwner) {
      log("מנהל מכשיר: לא נמצא Device Owner או Profile Owner.");
    } else {
      if (deviceOwner) log("Device Owner: " + deviceOwner);
      if (profiles.length) log("Profile Owner: " + profiles.join(", "));
      if (!deviceOwner && !profiles.length && components.length) log("Android דיווח על מנהל/בעלים: " + components.join(", "));
      if (deviceOwner === PACKAGE) log("✓ האפליקציה שלנו היא Device Owner.");
      else if (profiles.includes(PACKAGE)) log("⚠ האפליקציה שלנו היא Profile Owner, לא Device Owner.");
      else log("⚠ קיים מנהל אחר במכשיר.");
    }

    return {
      deviceOwner,
      profiles,
      components,
      hasAnyOwner,
      hasOurDeviceOwner: deviceOwner === PACKAGE,
      hasOurProfileOwner: profiles.includes(PACKAGE)
    };
  }

  async function removeOurDeviceOwner() {
    if (busy || !connected) return;

    const confirmed = window.confirm(
      "פעולה זו תנסה להסיר את מנהל המכשיר של האפליקציה שלנו בלבד.\n\n" +
      "היא לא מבצעת איפוס מפעל ולא מוחקת את חשבון Google.\n\nלהמשיך?"
    );
    if (!confirmed) return;

    busy = true;
    connectBtn.disabled = true;
    setupBtn.disabled = true;
    removeOwnerBtn.disabled = true;

    try {
      setStatus("בודק את מנהל המכשיר...", "בודק");
      log("בודק לפני ההסרה איזה מנהל מוגדר במכשיר...");
      const ownerInfo = await inspectDevicePolicy();

      if (ownerInfo.deviceOwner !== PACKAGE && !ownerInfo.hasOurProfileOwner) {
        throw new Error(
          "האפליקציה שלנו לא מזוהה כרגע כ־Device Owner או Profile Owner. לא בוצע שינוי."
        );
      }

      log("נמצא מנהל של האפליקציה שלנו. מנסה להסיר אותו דרך ADB...");
      setStatus("מסיר את מנהל המכשיר...", "מסיר");

      let result = await runShell("dpm remove-active-admin --user 0 " + ADMIN);

      if (/(unknown|error|exception|failed|failure|not allowed)/i.test(result)) {
        log("ניסיון ראשון לא הצליח: " + (result || "(ללא פלט)"));
        log("מנסה שוב ללא ציון משתמש...");
        result = await runShell("dpm remove-active-admin " + ADMIN);
      }

      if (/(unknown|error|exception|failed|failure|not allowed)/i.test(result)) {
        throw new Error(result || "Android לא אפשר להסיר את מנהל המכשיר דרך הפקודה הזו.");
      }

      const after = await inspectDevicePolicy();
      if (after.deviceOwner === PACKAGE || after.hasOurProfileOwner) {
        throw new Error(
          "הפקודה הסתיימה אבל Android עדיין מדווח שהאפליקציה היא מנהל. לא ניתן לשחרר אותה בדרך הזו."
        );
      }

      setStatus("המכשיר שוחרר", "הושלם", true);
      removeOwnerNoticeEl.textContent = "מנהל המכשיר של האפליקציה הוסר. עכשיו אפשר לנסות לחזור למסך הבית או להסיר את האפליקציה.";
      log("✓ מנהל המכשיר של האפליקציה הוסר.");
      log("לא בוצע איפוס מפעל.");
    } catch (error) {
      const message = normalizeError(error);
      setStatus("לא ניתן לשחרר את המכשיר", "שגיאה");
      removeOwnerNoticeEl.textContent = message;
      log("שגיאה בשחרור המכשיר: " + message);
    } finally {
      busy = false;
      connectBtn.disabled = false;
      setupBtn.disabled = !connected;
      removeOwnerBtn.disabled = !connected;
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
      const ownerInfo = await inspectDevicePolicy();
      const accountInfo = await inspectAccounts();
      const hasOurOwner = ownerInfo.hasOurDeviceOwner || ownerInfo.hasOurProfileOwner;
      const hasProfileOwner = ownerInfo.profiles.length > 0;

      if (!hasOurOwner && hasProfileOwner) {
        throw new Error(
          "Android מדווח שכבר מוגדר Profile Owner במכשיר. אי אפשר להגדיר Device Owner במצב הזה. " +
          "יש להסיר קודם את פרופיל העבודה/הניהול הקיים, או להשתמש במכשיר לאחר איפוס מלא."
        );
      }

      if (!hasOurOwner && accountInfo.hasAccounts) {
        const accountList = accountInfo.accounts.map(account => account.name + " [" + account.type + "]").join(", ");
        throw new Error(
          "לא ניתן להגדיר Device Owner כי קיימים חשבונות במכשיר. " +
          "החשבונות שנמצאו: " + accountList + ". " +
          "Android לא מציין חשבון יחיד כאשם — עצם קיום החשבונות חוסם את ההגדרה."
        );
      }

      if (!hasOurOwner) {
        setStatus("מגדיר את האפליקציה כבעלת המכשיר...", "מתקין");
        log("שולח את פקודת Device Owner ישירות לטלפון...");
        const result = await runShell("dpm set-device-owner " + ADMIN);
        if (/already has a profile owner/i.test(result)) {
          throw new Error("Android מדווח שכבר מוגדר Profile Owner במכשיר. אי אפשר להגדיר Device Owner במצב הזה. יש להסיר קודם את פרופיל העבודה/הניהול הקיים, או להשתמש במכשיר לאחר איפוס מלא.");
        }
        if (/(error|exception|failed|failure|not allowed|unknown)/i.test(result)) throw new Error(result || "Android דחה את הגדרת Device Owner.");
        log("פקודת Device Owner הסתיימה.");
      } else {
        log("האפליקציה כבר מוגדרת כבעלת המכשיר.");
      }

      setStatus("מעביר את קוד הגישה לאפליקציה...", "מגדיר קוד");
      const encoded = base64Utf8(accessCode);
      const startResult = await runShell(
        "am start -n " + PACKAGE + "/.MainActivity --es setup_access_code_b64 " + encoded + " --es setup_route " + selectedRoute
      );
      if (/error|exception|unable to/i.test(startResult)) throw new Error(startResult || "לא הצלחתי להפעיל את האפליקציה.");

      setStatus("ההגדרה הושלמה", "הושלם", true);
      setupNoticeEl.textContent = "ההגדרה הושלמה. דפדפן מאושר הופעל וקוד הגישה הוגדר.";
      log("קוד הגישה הועבר ישירות למכשיר.");
      log("המסלול שנבחר: " + ROUTES[selectedRoute].title + ".");
      log("דפדפן מאושר הופעל.");
    } catch (error) {
      const message = normalizeError(error);
      setStatus("ההגדרה נכשלה", "שגיאה");
      setLog("שגיאה: " + message);
      setupNoticeEl.textContent = "ההגדרה לא הושלמה. בדוק את ההודעה ביומן ונסה שוב.";
    } finally {
      setBusy(false);
      setupBtn.disabled = !connected;
      removeOwnerBtn.disabled = !connected;
    }
  }

  function normalizeError(error) {
    const raw = String(error?.message || error || "שגיאה לא ידועה");
    if (/user rejected|notfound|cancel/i.test(raw)) return "בחירת המכשיר בוטלה. לחץ שוב על 'חבר את הטלפון ב־USB'.";
    if (/operation that changes the device state is in progress|invalidstateerror/i.test(raw)) return "חיבור ה־USB עדיין מבצע פעולה קודמת. האתר ינסה שוב אוטומטית; אם השגיאה חוזרת, נתק וחבר מחדש את הטלפון.";
    if (/claim|interface/i.test(raw)) return "הממשק תפוס על ידי תוכנת ADB אחרת. סגור Android Studio, scrcpy, WebADB או תוכנת ניהול אחרת ונסה שוב.";
    if (/failed to fetch|networkerror|cors/i.test(raw)) return "לא ניתן להוריד את האפליקציה מהאתר. רענן את הדף ונסה שוב.";
    if (/already has a profile owner|trying to set the device owner.*profile owner/i.test(raw)) return "כבר מוגדר במכשיר Profile Owner. Android לא מאפשר להגדיר Device Owner במצב הזה. יש להסיר קודם את פרופיל העבודה/הניהול הקיים, או להשתמש במכשיר לאחר איפוס מלא.";
    if (/INSTALL_FAILED|INSTALL_PARSE_FAILED|INSTALL_FAILED_VERSION_DOWNGRADE/i.test(raw)) return "Android דחה את התקנת האפליקציה. אם קיימת גרסה חתומה אחרת, הסר אותה או התקן מחדש את הגרסה הנוכחית.";
    if (/access|permission|security/i.test(raw)) return "הגישה ל־USB נחסמה. אשר את חלון ה־USB בדפדפן ואת הרשאת ניפוי ה־USB בטלפון.";
    if (/already.*accounts|there are already some accounts on the device|set the device owner because there are already some accounts/i.test(raw)) return "Android חוסם את הגדרת Device Owner כי קיימים חשבונות במכשיר. בדוק ביומן את רשימת החשבונות; Android לא מציין חשבון יחיד כאשם, אלא חוסם כאשר קיימים חשבונות.";
    if (/auth|unauthorized|RSA/i.test(raw)) return "הטלפון לא אישר את מפתח ה־RSA. אשר את חלון 'אפשר ניפוי USB' בטלפון ונסה שוב.";
    return raw;
  }

  document.getElementById("connect").addEventListener("click", connectDevice);
  document.getElementById("setup").addEventListener("click", setupDeviceOwner);
  document.getElementById("removeOwner").addEventListener("click", removeOurDeviceOwner);
  routeButtons.forEach(button => button.addEventListener("click", () => selectRoute(button.dataset.route)));
  selectRoute(selectedRoute);

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