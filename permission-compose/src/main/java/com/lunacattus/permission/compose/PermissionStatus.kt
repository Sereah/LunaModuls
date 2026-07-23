package com.lunacattus.permission.compose

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private const val TAG = "PermissionState"

/**
 * 权限请求状态持有者。
 *
 * 由 [rememberPermissionState] 创建，协调标准权限请求、特殊权限跳转、
 * 理由弹窗和设置弹窗的完整生命周期。调用方通过 [request] 发起权限请求，
 * 通过 [allGranted] 观察整体授权状态。
 *
 * **不应直接构造** — 始终通过 [rememberPermissionState] 获取实例。
 *
 * @property permissions 请求的权限列表
 * @property allGranted 当前是否全部权限已授权（Compose 可观察状态）
 * @property rationaleConfig 理由弹窗配置
 * @property settingsConfig 设置弹窗配置
 */
class PermissionState internal constructor(
    val permissions: List<String>,
    private val context: Context,
    private val launcher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    val rationaleConfig: RationaleDialogConfig = RationaleDialogConfig(),
    val settingsConfig: SettingsDialogConfig = SettingsDialogConfig()
) {
    /** 当前是否全部权限已授权 */
    var allGranted by mutableStateOf(checkAllGranted())
        private set

    internal var showRationaleDialog by mutableStateOf(false)
    internal var showSettingsDialog by mutableStateOf(false)

    /** 最近一次请求中被拒绝的权限列表（用于弹窗展示） */
    internal var deniedPermissions by mutableStateOf(emptyList<String>())

    internal var waitingForSettingsResult by mutableStateOf(false)

    private var onResultCallback: ((Boolean, List<String>) -> Unit)? = null

    init {
        PermissionLog.d(TAG, "init: permissions=$permissions, allGranted=$allGranted")
    }

    /**
     * 发起权限请求。
     *
     * 如果全部已授权，立即通过回调返回成功。否则依次处理标准权限弹窗、
     * 特殊权限跳转、理由弹窗和设置弹窗。
     *
     * @param onResult 结果回调
     *   - [allGranted] 是否全部权限已授权
     *   - [deniedList] 被拒绝的权限列表（全部授权时为空）
     */
    fun request(onResult: (allGranted: Boolean, deniedList: List<String>) -> Unit) {
        if (allGranted) {
            PermissionLog.d(TAG, "request: already all granted")
            onResult(true, emptyList())
            return
        }
        PermissionLog.d(TAG, "request: launching")
        onResultCallback = onResult
        launchRequest()
    }

    /** 内部：根据权限类型分流请求（标准权限 → 系统弹窗，特殊权限 → 设置弹窗） */
    internal fun launchRequest() {
        val (standardPerms, specialPerms) = permissions.partition { !isSpecialPermission(it) }
        val ungrantedSpecial = specialPerms.filter { !checkSpecialPermission(it, context) }

        // 过滤当前设备不存在的权限
        val definedStandard = standardPerms.filter { isPermissionDefined(it, context) }
        val undefinedStandard = standardPerms.filter { !isPermissionDefined(it, context) }
        if (undefinedStandard.isNotEmpty()) {
            PermissionLog.d(
                TAG,
                "launchRequest: skipping undefined permissions: $undefinedStandard"
            )
        }

        // 过滤缺少同伴权限的孤儿权限
        val orphanCompanions = definedStandard.filter { isOrphanCompanion(it, permissions) }
        val validStandard = definedStandard - orphanCompanions.toSet()
        if (orphanCompanions.isNotEmpty()) {
            PermissionLog.e(
                TAG,
                "launchRequest: skipping orphan companion permissions: $orphanCompanions"
            )
        }

        if (ungrantedSpecial.isNotEmpty()) {
            PermissionLog.d(
                TAG,
                "launchRequest: special permissions need settings: $ungrantedSpecial"
            )
            deniedPermissions = ungrantedSpecial
            showSettingsDialog = true
            if (validStandard.isEmpty()) {
                PermissionLog.d(
                    TAG,
                    "launchRequest: no standard permissions to launch, showing settings dialog only"
                )
                return
            }
        }

        if (validStandard.isNotEmpty()) {
            PermissionLog.d(TAG, "launchRequest: launching standard: $validStandard")
            launcher.launch(validStandard.toTypedArray())
        }
    }

    /** 内部：跳转到系统设置页（用于特殊权限或永久拒绝场景） */
    internal fun goToSettings() {
        val firstSpecial = deniedPermissions.firstOrNull { isSpecialPermission(it) }
        val perm = firstSpecial ?: deniedPermissions.first()
        val intent = getSettingsIntent(perm, context)
        PermissionLog.d(TAG, "goToSettings: perm=$perm, action=${intent.action}")
        context.startActivity(intent)
    }

    /** 内部：从设置页返回后调用，检测权限是否已被手动开启 */
    internal fun checkAfterSettingsReturn() {
        if (!waitingForSettingsResult) return
        waitingForSettingsResult = false
        PermissionLog.d(TAG, "checkAfterSettingsReturn: checking permissions")
        if (checkAllGranted()) {
            allGranted = true
            PermissionLog.d(
                TAG,
                "checkAfterSettingsReturn: permissions granted after settings, invoking callback"
            )
            onResultCallback?.invoke(true, emptyList())
            onResultCallback = null
        } else {
            PermissionLog.d(
                TAG,
                "checkAfterSettingsReturn: permissions still not granted, re-requesting"
            )
            launchRequest()
        }
    }

    internal fun onRationaleConfirm() {
        PermissionLog.d(TAG, "rationaleConfirm: re-request")
        showRationaleDialog = false
        launchRequest()
    }

    internal fun onRationaleDismiss() {
        PermissionLog.d(TAG, "rationaleDismiss: invoking callback with denied=$deniedPermissions")
        showRationaleDialog = false
        onResultCallback?.invoke(false, deniedPermissions)
        onResultCallback = null
    }

    internal fun onSettingsConfirm() {
        PermissionLog.d(TAG, "settingsConfirm: go to settings")
        showSettingsDialog = false
        waitingForSettingsResult = true
        goToSettings()
    }

    internal fun onSettingsDismiss() {
        PermissionLog.d(TAG, "settingsDismiss: invoking callback with denied=$deniedPermissions")
        showSettingsDialog = false
        onResultCallback?.invoke(false, deniedPermissions)
        onResultCallback = null
    }

    internal fun handleResult(grantResults: Map<String, Boolean>) {
        // 过滤被同伴组覆盖的拒绝项（如 VISUAL_USER_SELECTED 授权覆盖 IMAGES 拒绝）
        val effectiveDenied = grantResults.filterValues { !it }.keys.filter { perm ->
            val group = getCompanionGroup(perm, permissions)
            if (group != null) {
                val groupGranted = grantResults.any { (p, g) -> p in group && g } ||
                        group.any { p -> checkRawPermission(p) }
                if (groupGranted) {
                    PermissionLog.d(
                        TAG,
                        "handleResult: $perm denied but covered by companion group grant, ignoring"
                    )
                }
                !groupGranted
            } else true
        }

        val specialPerms = permissions.filter { isSpecialPermission(it) }
        val ungrantedSpecial = specialPerms.filter { !checkSpecialPermission(it, context) }

        allGranted = effectiveDenied.isEmpty() && ungrantedSpecial.isEmpty()

        if (allGranted) {
            PermissionLog.d(TAG, "handleResult: all effective permissions granted")
            onResultCallback?.invoke(true, emptyList())
            onResultCallback = null
            return
        }

        // 展示列表不包含非孤儿同伴权限（主权限已代表相同的拒绝含义）
        val displayDenied = effectiveDenied.filter { perm ->
            !isCompanionPermission(perm) || isOrphanCompanion(perm, permissions)
        }
        val filteredCompanions = effectiveDenied - displayDenied.toSet()
        if (filteredCompanions.isNotEmpty()) {
            PermissionLog.d(
                TAG,
                "handleResult: filtered companion permissions from display: $filteredCompanions"
            )
        }
        deniedPermissions = displayDenied + ungrantedSpecial

        val anyCanShowRationale = effectiveDenied.any { perm ->
            (context as? Activity)?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, perm)
            } ?: false
        }

        PermissionLog.d(
            TAG,
            "handleResult: effectiveDenied=$effectiveDenied, specialUngranted=$ungrantedSpecial, anyRationale=$anyCanShowRationale"
        )

        if (ungrantedSpecial.isNotEmpty()) {
            deniedPermissions = ungrantedSpecial
            showSettingsDialog = true
            return
        }

        if (anyCanShowRationale) {
            showRationaleDialog = true
        } else {
            showSettingsDialog = true
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 私有方法
    // ═══════════════════════════════════════════════════════════

    private fun checkAllGranted(): Boolean {
        return permissions.all { checkSinglePermission(it) }
    }

    private fun checkSinglePermission(permission: String): Boolean {
        if (!isPermissionDefined(permission, context)) {
            PermissionLog.d(
                TAG,
                "checkSinglePermission: $permission not defined on this device, treating as granted"
            )
            return true
        }
        if (isOrphanCompanion(permission, permissions)) {
            PermissionLog.e(
                TAG,
                "checkSinglePermission: $permission is orphan companion, treating as granted"
            )
            return true
        }
        if (isSpecialPermission(permission)) {
            return checkSpecialPermission(permission, context)
        }

        // 同伴权限组：任一成员授权即视为本权限授权
        val group = getCompanionGroup(permission, permissions)
        if (group != null) {
            val anyGroupGranted = group.any { checkRawPermission(it) }
            if (anyGroupGranted) {
                PermissionLog.d(
                    TAG,
                    "checkSinglePermission: $permission covered by companion group grant"
                )
                return true
            }
        }

        return checkRawPermission(permission)
    }

    private fun checkRawPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
}

/**
 * 创建并记住权限请求状态，管理完整的权限请求生命周期。
 *
 * 自动处理：
 * - 标准权限的系统弹窗请求
 * - 特殊权限（悬浮窗、修改系统设置等）的跳转引导
 * - 权限拒绝后的理由弹窗（"不再询问"前）
 * - 权限永久拒绝后的设置弹窗（"不再询问"后）
 * - 同伴权限组（如 READ_MEDIA_VISUAL_USER_SELECTED 与 IMAGES/VIDEO）
 * - 从系统设置页返回后的自动检测
 *
 * 使用示例：
 * ```kotlin
 * val recordPermission = rememberPermissionState(
 *     Manifest.permission.RECORD_AUDIO,
 *     rationaleConfig = RationaleDialogConfig(title = "需要麦克风权限"),
 *     settingsConfig = SettingsDialogConfig(message = "请在设置中开启麦克风权限"),
 *     nameProvider = { permissionDisplayName(it) }
 * )
 * recordPermission.request { allGranted, denied ->
 *     if (allGranted) { /* 执行需要权限的操作 */ }
 * }
 * ```
 *
 * @param permissions 需要请求的权限（vararg，支持多个）
 * @param rationaleConfig 理由弹窗配置，可选
 * @param settingsConfig 设置弹窗配置，可选
 * @param rationaleDialog 自定义理由弹窗（null 使用默认弹窗）
 * @param settingsDialog 自定义设置弹窗（null 使用默认弹窗）
 * @param nameProvider 权限 → 可读名称提供者，默认使用 [permissionDisplayName]
 * @return 权限状态持有者，调用 [PermissionState.request] 发起请求
 */
@Composable
fun rememberPermissionState(
    vararg permissions: String,
    rationaleConfig: RationaleDialogConfig = RationaleDialogConfig(),
    settingsConfig: SettingsDialogConfig = SettingsDialogConfig(),
    rationaleDialog: CustomPermissionDialog? = null,
    settingsDialog: CustomPermissionDialog? = null,
    nameProvider: PermissionNameProvider = { permissionDisplayName(it) }
): PermissionState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionState: PermissionState? = null
    val permList = permissions.toList()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grantResults ->
        PermissionLog.d(TAG, "requestResult: results=$grantResults")
        permissionState?.handleResult(grantResults)
    }

    permissionState = remember(permList) {
        PermissionState(permList, context, launcher, rationaleConfig, settingsConfig)
    }

    // 从设置页返回后自动检测权限是否已开启
    DisposableEffect(lifecycleOwner, permissionState) {
        PermissionLog.d(TAG, "rememberPermissionState: lifecycle observer registered")
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                PermissionLog.d(TAG, "lifecycle: ON_RESUME, checking permissions")
                permissionState.checkAfterSettingsReturn()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    PermissionDialogHost(
        state = permissionState,
        rationaleDialog = rationaleDialog,
        settingsDialog = settingsDialog,
        nameProvider = nameProvider
    )
    return permissionState
}
