
package com.example.docdoom.data.storage

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object StoragePermissionHelper {

    fun requiredPermissions(): Array<String> {

        return when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )

            Build.VERSION.SDK_INT >= 33 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )

            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    fun hasImageAccess(context: Context): Boolean {

        return when {
            Build.VERSION.SDK_INT >= 34 -> {
                hasPermission(
                    context,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) || hasPermission(
                    context,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                )
            }

            Build.VERSION.SDK_INT >= 33 -> {
                hasPermission(
                    context,
                    Manifest.permission.READ_MEDIA_IMAGES
                )
            }

            else -> {
                hasPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            }
        }
    }

    fun hasVideoAccess(context: Context): Boolean {

        return when {
            Build.VERSION.SDK_INT >= 34 -> {
                hasPermission(
                    context,
                    Manifest.permission.READ_MEDIA_VIDEO
                ) || hasPermission(
                    context,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                )
            }

            Build.VERSION.SDK_INT >= 33 -> {
                hasPermission(
                    context,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
            }

            else -> {
                hasPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            }
        }
    }

    fun hasAudioAccess(context: Context): Boolean {

        return if (Build.VERSION.SDK_INT >= 33) {
            hasPermission(
                context,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            hasPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    private fun hasPermission(
        context: Context,
        permission: String
    ): Boolean {

        return context.checkSelfPermission(permission) ==
                PackageManager.PERMISSION_GRANTED
    }
}
