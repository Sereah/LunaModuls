package com.lunacattus.permission.compose

import android.Manifest
import androidx.compose.runtime.Composable

/**
 * 权限名提供者：接收权限常量字符串，返回可读名称。
 *
 * 可在 Composable 上下文中调用 `stringResource` 等实现本地化。
 */
typealias PermissionNameProvider = @Composable (String) -> String

/**
 * 默认的权限名英文本地化映射。
 *
 * 覆盖所有常见 Android Runtime 权限，未知权限回退为权限常量最后一段。
 * 使用者可通过 [PermissionNameProvider] 注入自定义本地化实现。
 *
 * @param permission Android 权限常量字符串（如 [Manifest.permission.CAMERA]）
 * @return 该权限对应的可读英文名称
 */
fun permissionDisplayName(permission: String): String = when (permission) {
    Manifest.permission.RECORD_AUDIO -> "Microphone"
    Manifest.permission.CAMERA -> "Camera"
    Manifest.permission.ACCESS_FINE_LOCATION -> "Precise Location"
    Manifest.permission.ACCESS_COARSE_LOCATION -> "Approximate Location"
    Manifest.permission.ACCESS_BACKGROUND_LOCATION -> "Background Location"
    Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
    Manifest.permission.READ_MEDIA_AUDIO -> "Music & Audio"
    Manifest.permission.READ_MEDIA_IMAGES -> "Photos"
    Manifest.permission.READ_MEDIA_VIDEO -> "Videos"
    Manifest.permission.READ_EXTERNAL_STORAGE -> "Storage"
    Manifest.permission.WRITE_EXTERNAL_STORAGE -> "Storage"
    Manifest.permission.BLUETOOTH_CONNECT -> "Bluetooth"
    Manifest.permission.BLUETOOTH_SCAN -> "Bluetooth Scan"
    Manifest.permission.BLUETOOTH_ADVERTISE -> "Bluetooth Advertise"
    Manifest.permission.UWB_RANGING -> "Ultra-Wideband"
    Manifest.permission.READ_PHONE_STATE -> "Phone State"
    Manifest.permission.READ_PHONE_NUMBERS -> "Phone Numbers"
    Manifest.permission.CALL_PHONE -> "Phone Calls"
    Manifest.permission.ANSWER_PHONE_CALLS -> "Answer Calls"
    Manifest.permission.READ_CALL_LOG -> "Call Log"
    Manifest.permission.WRITE_CALL_LOG -> "Call Log"
    Manifest.permission.ADD_VOICEMAIL -> "Voicemail"
    Manifest.permission.USE_SIP -> "SIP Calling"
    Manifest.permission.ACCEPT_HANDOVER -> "Call Handover"
    Manifest.permission.READ_CONTACTS -> "Contacts"
    Manifest.permission.WRITE_CONTACTS -> "Contacts"
    Manifest.permission.GET_ACCOUNTS -> "Accounts"
    Manifest.permission.READ_CALENDAR -> "Calendar"
    Manifest.permission.WRITE_CALENDAR -> "Calendar"
    Manifest.permission.BODY_SENSORS -> "Body Sensors"
    Manifest.permission.BODY_SENSORS_BACKGROUND -> "Body Sensors"
    Manifest.permission.ACTIVITY_RECOGNITION -> "Physical Activity"
    Manifest.permission.READ_SMS -> "SMS"
    Manifest.permission.SEND_SMS -> "SMS"
    Manifest.permission.RECEIVE_SMS -> "SMS"
    Manifest.permission.RECEIVE_WAP_PUSH -> "WAP Push"
    Manifest.permission.RECEIVE_MMS -> "MMS"
    Manifest.permission.NEARBY_WIFI_DEVICES -> "Nearby Wi-Fi Devices"
    // 特殊权限
    Manifest.permission.SYSTEM_ALERT_WINDOW -> "Display Overlay"
    Manifest.permission.WRITE_SETTINGS -> "Modify System Settings"
    Manifest.permission.MANAGE_EXTERNAL_STORAGE -> "All Files Access"
    else -> permission.substringAfterLast(".")
}
