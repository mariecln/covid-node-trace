package com.covid.nodetrace.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/**
 * A static class to help requesting permissions for Android
 */
object Permissions  {

    /**
     * We request
     * @see ACCESS_FINE_LOCATION for BLE communication
     * @see WRITE_EXTERNAL_STORAGE to write data to the local Room database
     * @see READ_EXTERNAL_STORAGE to read data from the local Room database
     */
    public val requiredPermissions: Array<String> = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    private val PERMISSION_REQUEST_CODE =  0x78

    /**
     * Check if the app has the needed permissions to be able to function
     */
    fun hasPermissions(context: Context, permissions: Array<String>): Boolean {
        for (permission : String in permissions) {
            val result: Int = ContextCompat.checkSelfPermission(context, permission)
            if (result != PackageManager.PERMISSION_GRANTED)
                return false
        }
        return true
    }

    /**
     * Request a certain permission and show a pop-up rationale why to grant the permission
     * if the user rejects the permission.
     *
     * If the user keeps rejecting the permissions then eventually no more requests will be sent/prompted
     * and the only way to have a working app is if the user gives the permissions from the phone's settings.
     */
    fun requestPermission(activity: Activity, permissions: Array<String>,receivedPermissions: ((Boolean) -> Unit)? = null) {

        val permissionHelper = PermissionHelper(permissions)
        permissionHelper.requestPermission(activity, object : PermissionResult {
            override fun OnPermissionGranted(permission: String?, requestCode: Int) {
                receivedPermissions?.invoke(true)
            }

            @RequiresApi(Build.VERSION_CODES.M)
            override fun OnPermissionDenied(permission: String?, requestCode: Int) {
                activity.requestPermissions(
                    permissions,
                    PERMISSION_REQUEST_CODE
                )
            }

            override fun OnShowRationale(permission: String?, requestCode: Int) {
                val permissionRationale = PermissionRationale()
                permissionRationale.showRationale(activity, requestCode)
            }

            override fun OnInvalidPermissions() {
                receivedPermissions?.invoke(false)
            }
        })
    }
}