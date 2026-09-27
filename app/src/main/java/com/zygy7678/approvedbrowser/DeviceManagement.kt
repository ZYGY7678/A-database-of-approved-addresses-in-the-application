package com.zygy7678.approvedbrowser

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager

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
        suspendKnownBrowsers(context, dpm, admin)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            dpm.setLockTaskFeatures(admin, 0)
        }

        applyDefaultBrowserPolicy(context, true)
    }

    fun applyDefaultBrowserPolicy(context: Context, enabled: Boolean) {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return
        if (!dpm.isDeviceOwnerApp(context.packageName)) return

        val admin = adminComponent(context)
        val activity = ComponentName(context, MainActivity::class.java)
        val webFilter = IntentFilter(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addCategory(Intent.CATEGORY_BROWSABLE)
            addDataScheme("http")
            addDataScheme("https")
        }

        if (enabled) {
            runCatching {
                dpm.addPersistentPreferredActivity(admin, webFilter, activity)
            }
            suspendKnownBrowsers(context, dpm, admin)
        } else {
            runCatching {
                dpm.clearPackagePersistentPreferredActivities(admin, context.packageName)
            }
            unsuspendKnownBrowsers(context, dpm, admin)
        }
    }

    private fun suspendKnownBrowsers(
        context: Context,
        dpm: DevicePolicyManager,
        admin: ComponentName
    ) {
        val browserPackages = linkedSetOf<String>()
        val pm = context.packageManager
        val probes = listOf(
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("http://example.com")),
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com"))
        )

        for (intent in probes) {
            val handlers = pm.queryIntentActivities(
                intent,
                PackageManager.MATCH_ALL
            )
            for (info in handlers) {
                val packageName = info.activityInfo.packageName
                if (packageName != context.packageName) {
                    browserPackages += packageName
                }
            }
        }

        if (browserPackages.isNotEmpty()) {
            runCatching {
                dpm.setPackagesSuspended(
                    admin,
                    browserPackages.toTypedArray(),
                    true
                )
            }
        }
    }

    private fun unsuspendKnownBrowsers(
        context: Context,
        dpm: DevicePolicyManager,
        admin: ComponentName
    ) {
        val pm = context.packageManager
        val probes = listOf(
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("http://example.com")),
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com"))
        )
        val browserPackages = probes.flatMap { intent ->
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                .map { it.activityInfo.packageName }
        }.filter { it != context.packageName }.distinct()

        if (browserPackages.isNotEmpty()) {
            runCatching {
                dpm.setPackagesSuspended(
                    admin,
                    browserPackages.toTypedArray(),
                    false
                )
            }
        }
    }

    fun applyManagedAppPolicies(
        context: Context,
        lockedPackages: Set<String>,
        protectedUninstallPackages: Set<String>
    ) {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return
        if (!dpm.isDeviceOwnerApp(context.packageName)) return
        val admin = adminComponent(context)
        val pm = context.packageManager
        val installedLaunchers = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            PackageManager.MATCH_ALL
        ).map { it.activityInfo.packageName }.toSet()
        val safeLocked = lockedPackages.filter { it != context.packageName && it in installedLaunchers }.toTypedArray()
        val safeProtected = protectedUninstallPackages.filter { it != context.packageName && it in installedLaunchers }.toSet()
        runCatching { dpm.setPackagesSuspended(admin, safeLocked, true) }
        for (packageName in installedLaunchers) {
            if (packageName == context.packageName) continue
            if (packageName !in lockedPackages) {
                runCatching { dpm.setPackagesSuspended(admin, arrayOf(packageName), false) }
            }
            runCatching { dpm.setUninstallBlocked(admin, packageName, packageName in safeProtected) }
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
