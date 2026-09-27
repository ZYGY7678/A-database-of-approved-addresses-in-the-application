package com.zygy7678.approvedbrowser

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

object DeviceManagement {

    fun isDeviceOwner(context: Context): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        return dpm?.isDeviceOwnerApp(context.packageName) == true
    }

    fun adminComponent(context: Context): ComponentName =
        ComponentName(context, ApprovedBrowserDeviceAdminReceiver::class.java)

    fun enforcePolicies(context: Context) {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return
        if (!dpm.isDeviceOwnerApp(context.packageName)) return

        val admin = adminComponent(context)
        val activity = ComponentName(context, MainActivity::class.java)

        // A device owner cannot be uninstalled by the normal user.
        dpm.setUninstallBlocked(admin, context.packageName, true)

        // Only this package is allowed to enter Lock Task mode.
        dpm.setLockTaskPackages(admin, arrayOf(context.packageName))
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            dpm.setLockTaskFeatures(admin, 0)
        }

        // Make this app the persistent handler for web links.
        // The WebView still applies the whitelist before loading the URL.
        val webFilter = IntentFilter(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addCategory(Intent.CATEGORY_BROWSABLE)
            addDataScheme("http")
            addDataScheme("https")
        }
        runCatching {
            dpm.addPersistentPreferredActivity(admin, webFilter, activity)
        }
    }

    fun startKioskIfPossible(context: Context) {
        if (!isDeviceOwner(context)) return
        enforcePolicies(context)
        val activity = context as? android.app.Activity ?: return
        if (!activity.isInLockTaskModeCompat()) {
            activity.startLockTask()
        }
    }

    fun isKioskActive(context: Context): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val activityManager = context.getSystemService(android.app.ActivityManager::class.java)
            activityManager?.lockTaskModeState != android.app.ActivityManager.LOCK_TASK_MODE_NONE
        } else {
            false
        }

    private fun android.app.Activity.isInLockTaskModeCompat(): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val am = getSystemService(android.app.ActivityManager::class.java)
            am?.lockTaskModeState != android.app.ActivityManager.LOCK_TASK_MODE_NONE
        } else {
            false
        }
}
