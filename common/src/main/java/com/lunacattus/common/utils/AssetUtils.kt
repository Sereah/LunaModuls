package com.lunacattus.common.utils

import android.content.Context
import com.lunacattus.common.CommonLog
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object AssetUtils {
    private const val TAG = "AssetUtils"

    /**
     * 将 assets 目录下的指定文件夹及其所有子文件/文件夹，复制到应用的 files 目录下
     *
     * @param context Context
     * @param assetDirName assets 中的目标文件夹名
     * @return 复制后的真实完整绝对路径，如果发生异常可能返回 null
     */
    fun copyToFiles(context: Context, assetDirName: String): String? {
        val targetDir = File(context.filesDir, assetDirName)

        return try {
            CommonLog.d(TAG, "Copying assets/$assetDirName to ${targetDir.absolutePath}")
            copyAssetDir(context, assetDirName, targetDir)
            CommonLog.d(TAG, "Copy completed")
            targetDir.absolutePath
        } catch (e: Exception) {
            CommonLog.e(TAG, "copyToFiles failed: ${e.message}", e)
            null
        }
    }

    private fun copyAssetDir(context: Context, assetPath: String, targetDir: File) {
        val assetManager = context.assets
        try {
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            val files = assetManager.list(assetPath) ?: return

            for (fileName in files) {
                val currentAssetPath = if (assetPath.isEmpty()) fileName else "$assetPath/$fileName"
                val currentTargetFile = File(targetDir, fileName)
                val subFiles = assetManager.list(currentAssetPath)

                if (!subFiles.isNullOrEmpty()) {
                    copyAssetDir(context, currentAssetPath, currentTargetFile)
                } else {
                    copySingleFile(context, currentAssetPath, currentTargetFile)
                }
            }
        } catch (e: IOException) {
            CommonLog.e(TAG, "Failed to copy asset directory: $assetPath", e)
        }
    }

    private fun copySingleFile(context: Context, assetFilePath: String, targetFile: File) {
        try {
            context.assets.open(assetFilePath).use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        } catch (e: IOException) {
            CommonLog.e(TAG, "Skipping unopenable file (likely empty dir): $assetFilePath", e)
        }
    }
}