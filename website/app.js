const log=document.getElementById("log");const code=document.getElementById("code");const confirm=document.getElementById("confirm");
function write(s){log.textContent += "\n"+s}
document.getElementById("setup").onclick=()=>{
 const a=code.value,b=confirm.value;
 if(!/^\\d{4,12}$/.test(a)){log.textContent="שגיאה: קוד הגישה חייב להכיל 4–12 ספרות.";code.focus();return}
 if(a!==b){log.textContent="שגיאה: קודי הגישה אינם זהים.";confirm.focus();return}
 log.textContent="הקוד התקבל.\n\n1. ודא שאפשרויות למפתחים וניפוי USB מופעלים.\n2. חבר את הטלפון למחשב.\n3. הורד והפעל את כלי Approved Browser ADB.\n4. הכלי יבצע את פקודת Device Owner ויעביר את הקוד לאפליקציה.\n\nהקוד אינו מוצג ביומן.";
 localStorage.setItem("approvedbrowser_setup_ready","1");
};
document.getElementById("download").onclick=()=>{
 write("פתח את תיקיית כלי המחשב ב-GitHub והורד את גרסת Windows העדכנית.");
 window.open("https://github.com/ZYGY7678/A-database-of-approved-addresses-in-the-application/tree/main/desktop/ApprovedBrowser.AdbTool","_blank","noopener");
};
