package com.trace.api.data

import android.annotation.TargetApi
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.os.Build
import android.util.Log

class BleAdvertiser @TargetApi(Build.VERSION_CODES.M) constructor() {
    private val TAG = "BleAdvertiser"
    private val advertiser: BluetoothLeAdvertiser?
    private var advertiseCallback : AdvertiseCallback? = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            super.onStartSuccess(settingsInEffect)
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
        }
    }

    init {
        advertiser = BluetoothAdapter.getDefaultAdapter().bluetoothLeAdvertiser
    }

    @TargetApi(Build.VERSION_CODES.M)
    fun advertiseData(data: AdvertiseData?, advScanResponse: AdvertiseData?, settings : AdvertiseSettings) {

        if (advertiser == null) {
            Log.w(TAG, "Device cannot advertise data");
            return;
        }

        advertiser.startAdvertising(settings, advScanResponse, data, advertiseCallback)
    }

    fun stopAdvertising () {
        if (advertiseCallback == null)
            return

        advertiser?.stopAdvertising(advertiseCallback!!)
    }
}