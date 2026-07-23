package com.lunacattus.permission.compose

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

// ═══════════════════════════════════════════════════════════════
// 权限类型定义
// ═══════════════════════════════════════════════════════════════

/** 需要跳转系统设置页的特殊权限（不走标准 requestPermissions 流程） */
private val SPECIAL_PERMISSIONS = setOf(
    Manifest.permission.SYSTEM_ALERT_WINDOW,
    Manifest.permission.WRITE_SETTINGS,
    Manifest.permission.MANAGE_EXTERNAL_STORAGE
)

/**
 * 同伴权限依赖：修饰权限 → 必须同时请求的权限集合。
 * 组内任一权限授权即视为整组成功（如 Android 14+ 「选择部分照片」场景）。
 */
private val COMPANION_PERMISSIONS: Map<String, Set<String>> = mapOf(
    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED to setOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO
    )
)

// ═══════════════════════════════════════════════════════════════
// 权限分类判断
// ═══════════════════════════════════════════════════════════════

/** 判断是否为需要跳转系统设置页的特殊权限 */
internal fun isSpecialPermission(permission: String): Boolean =
    permission in SPECIAL_PERMISSIONS

/** 判断是否为同伴修饰权限（非独立权限，依赖主权限才有意义） */
internal fun isCompanionPermission(permission: String): Boolean =
    permission in COMPANION_PERMISSIONS

/** 判断修饰权限是否缺少必要的同伴权限（孤儿权限，请求无意义） */
internal fun isOrphanCompanion(permission: String, allPermissions: List<String>): Boolean {
    val required = COMPANION_PERMISSIONS[permission] ?: return false
    return required.none { it in allPermissions }
}

/** 获取权限所属的同伴权限组（含自身），不在任何组内返回 null */
internal fun getCompanionGroup(permission: String, allPermissions: List<String>): Set<String>? {
    for ((companion, required) in COMPANION_PERMISSIONS) {
        if ((permission == companion || permission in required) &&
            companion in allPermissions && required.any { it in allPermissions }
        ) {
            return setOf(companion) + required
        }
    }
    return null
}

// ═══════════════════════════════════════════════════════════════
// 权限状态检测
// ═══════════════════════════════════════════════════════════════

/** 检测权限是否在当前系统定义（低版本设备上高版本权限不存在时返回 false） */
internal fun isPermissionDefined(permission: String, context: Context): Boolean {
    return try {
        context.packageManager.getPermissionInfo(permission, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}

/** 检测特殊权限的授权状态 */
internal fun checkSpecialPermission(permission: String, context: Context): Boolean = when (permission) {
    Manifest.permission.SYSTEM_ALERT_WINDOW ->
        Settings.canDrawOverlays(context)
    Manifest.permission.WRITE_SETTINGS ->
        Settings.System.canWrite(context)
    Manifest.permission.MANAGE_EXTERNAL_STORAGE ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            false
        }
    else -> false
}

// ═══════════════════════════════════════════════════════════════
// 设置页跳转
// ═══════════════════════════════════════════════════════════════

/** 根据权限类型返回对应的系统设置页 Intent */
internal fun getSettingsIntent(permission: String, context: Context): Intent {
    val intent = when (permission) {
        Manifest.permission.SYSTEM_ALERT_WINDOW ->
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        Manifest.permission.WRITE_SETTINGS ->
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
        Manifest.permission.MANAGE_EXTERNAL_STORAGE ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            }
        else ->
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    }
    return intent.apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
