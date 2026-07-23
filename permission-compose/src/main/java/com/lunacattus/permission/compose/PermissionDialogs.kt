package com.lunacattus.permission.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 自定义权限弹窗签名：state 当前状态, dismiss 关闭回调, confirm 确认回调 */
typealias CustomPermissionDialog = @Composable (PermissionState, () -> Unit, () -> Unit) -> Unit

private const val TAG = "PermissionDialog"

/**
 * 权限理由弹窗 —— 用户拒绝过一次权限后展示，引导再次授权。
 *
 * @param show 是否显示弹窗
 * @param onDismiss 取消回调
 * @param onConfirm 确认回调（重新发起权限请求）
 * @param title 弹窗标题
 * @param message 弹窗正文
 * @param confirmText 确认按钮文案
 * @param dismissText 取消按钮文案
 * @param cancelable 是否可取消（点击外部 / 返回键关闭），为 false 时不显示取消按钮
 * @param permissions 需要展示图标的权限列表
 * @param permissionIcons 权限 → 图标映射表，默认使用 [PermissionIconMap]
 * @param nameProvider 权限 → 可读名称提供者，默认使用 [permissionDisplayName]
 */
@Composable
fun PermissionRationaleDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    title: String = "Permission Required",
    message: String = "This permission is needed for full functionality.",
    confirmText: String = "Continue",
    dismissText: String = "Cancel",
    cancelable: Boolean = false,
    permissions: List<String> = emptyList(),
    permissionIcons: Map<String, ImageVector> = PermissionIconMap,
    nameProvider: PermissionNameProvider = { permissionDisplayName(it) }
) {
    if (show) {
        PermissionLog.d(TAG, "showRationaleDialog")
        AlertDialog(
            onDismissRequest = {
                if (cancelable) {
                    PermissionLog.d(TAG, "rationaleDialog: dismiss")
                    onDismiss()
                } else {
                    PermissionLog.d(TAG, "rationaleDialog: dismiss blocked (not cancelable)")
                }
            },
            title = { Text(title) },
            text = {
                DialogText(permissions, permissionIcons, message, nameProvider)
            },
            confirmButton = {
                TextButton(onClick = {
                    PermissionLog.d(TAG, "rationaleDialog: confirm -> re-request")
                    onConfirm()
                }) {
                    Text(confirmText)
                }
            },
            dismissButton = {
                if (cancelable) {
                    TextButton(onClick = {
                        PermissionLog.d(TAG, "rationaleDialog: cancel")
                        onDismiss()
                    }) {
                        Text(dismissText)
                    }
                }
            }
        )
    }
}

/**
 * 权限设置弹窗 —— 权限被永久拒绝后展示，引导前往系统设置页。
 *
 * @param show 是否显示弹窗
 * @param onDismiss 取消回调
 * @param onConfirm 确认回调（跳转系统设置页）
 * @param title 弹窗标题
 * @param message 弹窗正文
 * @param confirmText 确认按钮文案
 * @param dismissText 取消按钮文案
 * @param cancelable 是否可取消（点击外部 / 返回键关闭）
 * @param permissions 需要展示图标的权限列表
 * @param permissionIcons 权限 → 图标映射表，默认使用 [PermissionIconMap]
 * @param nameProvider 权限 → 可读名称提供者，默认使用 [permissionDisplayName]
 */
@Composable
fun PermissionSettingsDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    title: String = "Permission Required",
    message: String = "This permission has been permanently denied. Please enable it in system settings.",
    confirmText: String = "Go to Settings",
    dismissText: String = "Cancel",
    cancelable: Boolean = true,
    permissions: List<String> = emptyList(),
    permissionIcons: Map<String, ImageVector> = PermissionIconMap,
    nameProvider: PermissionNameProvider = { permissionDisplayName(it) }
) {
    if (show) {
        PermissionLog.d(TAG, "showSettingsDialog")
        AlertDialog(
            onDismissRequest = {
                if (cancelable) {
                    PermissionLog.d(TAG, "settingsDialog: dismiss")
                    onDismiss()
                } else {
                    PermissionLog.d(TAG, "settingsDialog: dismiss blocked (not cancelable)")
                }
            },
            title = { Text(title) },
            text = {
                DialogText(permissions, permissionIcons, message, nameProvider)
            },
            confirmButton = {
                TextButton(onClick = {
                    PermissionLog.d(TAG, "settingsDialog: confirm -> go to settings")
                    onConfirm()
                }) {
                    Text(confirmText)
                }
            },
            dismissButton = {
                if (cancelable) {
                    TextButton(onClick = {
                        PermissionLog.d(TAG, "settingsDialog: cancel")
                        onDismiss()
                    }) {
                        Text(dismissText)
                    }
                }
            }
        )
    }
}

/**
 * 权限弹窗宿主 —— 已集成到 [rememberPermissionState] 中，通常无需手动调用。
 *
 * 如需自定义弹窗样式，通过 [rememberPermissionState] 的 [rationaleDialog]
 * 或 [settingsDialog] 参数传入自定义实现。
 *
 * @param state 由 [rememberPermissionState] 返回的权限状态
 * @param rationaleDialog 自定义理由弹窗（null 则使用默认 [PermissionRationaleDialog]）
 * @param settingsDialog 自定义设置弹窗（null 则使用默认 [PermissionSettingsDialog]）
 * @param nameProvider 权限 → 可读名称提供者，默认使用 [permissionDisplayName]
 */
@Composable
fun PermissionDialogHost(
    state: PermissionState,
    rationaleDialog: CustomPermissionDialog? = null,
    settingsDialog: CustomPermissionDialog? = null,
    nameProvider: PermissionNameProvider = { permissionDisplayName(it) }
) {
    if (state.showRationaleDialog) {
        if (rationaleDialog != null) {
            rationaleDialog(state, state::onRationaleDismiss, state::onRationaleConfirm)
        } else {
            PermissionRationaleDialog(
                show = true,
                onDismiss = state::onRationaleDismiss,
                onConfirm = state::onRationaleConfirm,
                title = state.rationaleConfig.title,
                message = state.rationaleConfig.message,
                confirmText = state.rationaleConfig.confirmText,
                dismissText = state.rationaleConfig.dismissText,
                cancelable = state.rationaleConfig.cancelable,
                permissions = state.deniedPermissions,
                nameProvider = nameProvider
            )
        }
    }

    if (state.showSettingsDialog) {
        if (settingsDialog != null) {
            settingsDialog(state, state::onSettingsDismiss, state::onSettingsConfirm)
        } else {
            PermissionSettingsDialog(
                show = true,
                onDismiss = state::onSettingsDismiss,
                onConfirm = state::onSettingsConfirm,
                title = state.settingsConfig.title,
                message = state.settingsConfig.message,
                confirmText = state.settingsConfig.confirmText,
                dismissText = state.settingsConfig.dismissText,
                cancelable = state.settingsConfig.cancelable,
                permissions = state.deniedPermissions,
                nameProvider = nameProvider
            )
        }
    }
}

/**
 * 弹窗正文：展示权限图标 + 名称列表，然后展示说明文字。
 */
@Composable
private fun DialogText(
    permissions: List<String>,
    icons: Map<String, ImageVector>,
    message: String,
    nameProvider: PermissionNameProvider
) {
    Column {
        permissions.forEach { perm ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                icons[perm]?.let {
                    Icon(it, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                }
                Text(nameProvider(perm))
            }
            Spacer(Modifier.height(12.dp))
        }
        if (permissions.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
        }
        Text(message, fontSize = 15.sp)
    }
}
