package com.covid.nodetrace.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.content.Context
import android.os.Build
import android.os.Handler
import android.util.Log
import com.covid.nodetrace.ContactService
import no.nordicsemi.android.support.v18.scanner.*
import java.nio.ByteBuffer
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
    var bleScan : BluetoothLeScanner? = null
    var resetScan : Timer? = null


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
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q)
        {
            resetScan = Timer()
            resetScan?.schedule(object : TimerTask() {
                override fun run() {
                    Log.d("Scan", "resetScan")
                    stopScan()
                    scanLeDevice()
                }
            }, 1, 1200000)
        }

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

        val settingsBuilder = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            /*.setUseHardwareBatchingIfSupported(true)
            .setUseHardwareFilteringIfSupported(true)*/
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settingsBuilder.setCallbackType(android.bluetooth.le.ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            settingsBuilder.setMatchMode(android.bluetooth.le.ScanSettings.MATCH_MODE_AGGRESSIVE)
            settingsBuilder.setNumOfMatches(android.bluetooth.le.ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
        }
        val settings = settingsBuilder.build()

        val manData = ByteBuffer.allocate(23)
        val manMask = ByteBuffer.allocate(23)
        manData.put(0, 0x02.toByte())
        manData.put(1, 0x15.toByte())
        manData.put(18, 0x00.toByte()) // first byte of Major
        manData.put(19, 0x09.toByte()) // second byte of Major
        manData.put(20, 0x00.toByte()) // first minor
        manData.put(21, 0x06.toByte()) // second minor
        manData.put(22, 0xB5.toByte())

        manMask.put(0,0x01.toByte())
        manMask.put(1,0x01.toByte())
        manMask.put(18,0x01.toByte())
        manMask.put(19,0x01.toByte())
        manMask.put(20,0x01.toByte())
        manMask.put(21,0x01.toByte())
        manMask.put(22,0x01.toByte())

        val filter = ScanFilter.Builder()
            .setManufacturerData(ContactService.NODE_IDENTIFIER, manData.array(), manMask.array())
            .build()

        val filters : MutableList<ScanFilter> = mutableListOf()
        filters.add(filter)

        BluetoothLeScannerCompat.getScanner().startScan(filters, settings, mLeScanCallback!!)

        mScanning = true

        if (scanActive != null)
            scanActive!!.isBleScannerActive(true)
    }

    private var mLeScanCallback: ScanCallback? = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            Log.d("Scan","result"+result.device.name)

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