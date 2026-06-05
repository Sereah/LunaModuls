package com.lunacattus.common.utils

import android.content.Context
import android.content.pm.PackageManager

fun Context.isSystemSignature(): Boolean {
    return this.packageManager.checkSignatures(this.packageName, "android") ==
            PackageManager.SIGNATURE_MATCH
}