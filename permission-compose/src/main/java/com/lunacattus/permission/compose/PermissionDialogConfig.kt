package com.lunacattus.permission.compose

/**
 * 理由弹窗配置。
 *
 * 当用户拒绝过一次权限后，展示此弹窗解释权限用途，引导再次授权。
 *
 * @param title 弹窗标题
 * @param message 弹窗正文，解释权限的必要性
 * @param confirmText 确认按钮文案（继续请求权限）
 * @param dismissText 取消按钮文案
 * @param cancelable 是否可取消，false 时无取消按钮且点击外部不关闭
 */
data class RationaleDialogConfig(
    val title: String = "Permission Required",
    val message: String = "This permission is needed for full functionality.",
    val confirmText: String = "Continue",
    val dismissText: String = "Cancel",
    val cancelable: Boolean = false
)

/**
 * 设置弹窗配置。
 *
 * 当权限被永久拒绝后，展示此弹窗引导用户前往系统设置页手动开启。
 *
 * @param title 弹窗标题
 * @param message 弹窗正文，引导用户前往设置
 * @param confirmText 确认按钮文案（跳转设置）
 * @param dismissText 取消按钮文案
 * @param cancelable 是否可取消，false 时无取消按钮且点击外部不关闭
 */
data class SettingsDialogConfig(
    val title: String = "Permission Required",
    val message: String = "This permission has been permanently denied. Please enable it in system settings.",
    val confirmText: String = "Go to Settings",
    val dismissText: String = "Cancel",
    val cancelable: Boolean = true
)
