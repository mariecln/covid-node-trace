package com.covid.nodetrace.workmanager

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.work.CoroutineWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.covid.nodetrace.Contact
import com.covid.nodetrace.database.AppDatabase
import com.covid.nodetrace.database.DatabaseFactory
import com.covid.nodetrace.util.DataFormatter


class RefreshDataWorker(context: Context, workerParams : WorkerParameters) :
    CoroutineWorker(context, workerParams){
    private lateinit var appDatabase : AppDatabase

    override suspend fun doWork(): Result {
        var contact_ID = ""
        var contact_name = ""
        var date = ""
        var location = ""
        var duration = ""
        var rssi = ""
        var device_name = ""

        appDatabase = AppDatabase.getInstance(applicationContext)
        val allContacts : List<Contact> = appDatabase.contactDao().getAll()

        allContacts.forEach { contact ->
            contact_ID = contact.ID
            contact_name = contact.name
            date = DataFormatter.createDateFormat(contact.date).toString()
            location = DataFormatter.createLocationFormat(contact.latitude, contact.longitude)
            duration = DataFormatter.createDurationFormat(contact.duration)
            rssi = contact.rssi.toString()
            device_name = Build.MODEL
            val fileUploaded = DatabaseFactory.getFirebaseDatabase().addBroadcast(contact_ID, contact_name, date, location,  duration, rssi, device_name)
            if (fileUploaded)
                Log.d("Database", "Data add");
        }
        if (allContacts.isEmpty()) {
            Log.d("List", "empty");
            return Result.failure()
        }
        else{
            Log.d("MyWorker", "worker start");
            return Result.success();
        }
    }
}