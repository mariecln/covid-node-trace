package com.covid.nodetrace.bluetooth

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.os.Build
import android.os.Handler
import android.util.Log
import com.covid.nodetrace.ContactService
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanResult
import no.nordicsemi.android.support.v18.scanner.ScanSettings
import java.util.*

class BleScanner {

    companion object {
        private val TAG = BleScanner::class.java.simpleName
        private const val NODE_STRING = "NODE"
    }

    private var advertisementFoundCallback: OnAdvertisementFound? = null
    private val mHandler: Handler
    private var applicationContext: Context?
    private var mScanning = false
    private var scanActive: ScanActive? = null

    constructor(context: Context?) {
        applicationContext = context
        mHandler = Handler()
    }

    constructor(context: Context, scanActive: ScanActive) {
        applicationContext = context
        this.scanActive = scanActive
        mHandler = Handler()
    }

    /**
     * Sets the scan callback listener.
     *
     * @param callback the callback listener.
     */
    fun scanLeDevice(callback: OnAdvertisementFound?) {
        advertisementFoundCallback = callback
        scanLeDevice()
    }

    private fun scanLeDevice() {
        if (mScanning) return

        // Check if the bluetooth adapter is available and turned on
        val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
            return
        } else if (!mBluetoothAdapter.isEnabled) {
            // Bluetooth is not enabled
            mBluetoothAdapter.enable();
            return
        }
        val builder = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setCallbackType(android.bluetooth.le.ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.setMatchMode(android.bluetooth.le.ScanSettings.MATCH_MODE_AGGRESSIVE)
            builder.setNumOfMatches(android.bluetooth.le.ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
        }
        val settings = builder.build()
        BluetoothLeScannerCompat.getScanner().startScan(null, settings, mLeScanCallback!!)

        mScanning = true

        if (scanActive != null)
            scanActive!!.isBleScannerActive(true)
    }

    private var mLeScanCallback: ScanCallback? = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            val manufacturerSpecificData = result.scanRecord!!.getManufacturerSpecificData(ContactService.NODE_IDENTIFIER)
            if (manufacturerSpecificData != null) {
                advertisementFoundCallback!!.onAdvertisementFound(result)
            }
        }
    }


    /**
     * Stop scanning for BLE devices.
     */
    fun stopScan() {
        if (mScanning) {
            Log.i(TAG, "Scan stopped")
            BluetoothLeScannerCompat.getScanner().stopScan(mLeScanCallback!!)
            mHandler.removeCallbacksAndMessages(null)
            mScanning = false
            if (scanActive != null) scanActive!!.isBleScannerActive(false)
        }
    }

    fun destroyScanner() {
        stopScan()
        mLeScanCallback = null
        advertisementFoundCallback = null
        applicationContext = null
    }
}