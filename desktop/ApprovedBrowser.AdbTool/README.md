# כלי ADB — דפדפן מאושר

כלי Windows להגדרת אפליקציית דפדפן מאושר כבעלת המכשיר באמצעות ADB.

## דרישות
- Windows 10/11
- Android SDK Platform Tools עם `adb` זמין ב־PATH
- USB debugging מאופשר במכשיר
- המכשיר מחובר ב־USB ואושר מול המחשב

## מה הכלי עושה
1. בודק שהמכשיר מזוהה דרך ADB
2. מציג את סטטוס החיבור
3. שולח את הפקודה:

```text
adb shell dpm set-device-owner com.zygy7678.approvedbrowser/.ApprovedBrowserDeviceAdminReceiver
```
