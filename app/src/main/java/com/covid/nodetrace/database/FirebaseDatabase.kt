package com.covid.nodetrace.database

import android.content.Context
import android.util.Log
import com.covid.nodetrace.util.NetworkHelper
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class FirebaseDatabase () : FirebaseDao {
    private val TAG: String = FirebaseDatabase::class.java.getSimpleName()

    val db = FirebaseFirestore.getInstance()
    private val contactsCollection: CollectionReference
    private val broadcastCollection: CollectionReference

    init {
        contactsCollection = db.collection("contacts");
        broadcastCollection = db.collection("broadcast");
    }

    override suspend fun create(context: Context, contactID: String) : Boolean = suspendCancellableCoroutine { continuation ->
        if (NetworkHelper.isConnectedToNetwork(context)) {
            val newID: Map<String, String> = mapOf(Pair("ID", contactID))
            val IDs: DocumentReference = contactsCollection.document(contactID)
            IDs.set(newID)
                .addOnSuccessListener { continuation.resume(true) }
                .addOnFailureListener { failureReason ->
                    continuation.cancel()
                    Log.e(TAG, failureReason.message ?: failureReason.toString())
                }
                .addOnCanceledListener {
                    continuation.cancel()
                    Log.e(TAG, "Upload cancelled")
                }
        }
        else {
            continuation.cancel()
        }
    }

    override suspend fun read(context: Context) : List<String> = suspendCancellableCoroutine { continuation ->
        val IDs: CollectionReference = contactsCollection

        if (NetworkHelper.isConnectedToNetwork(context)) {
            IDs.get()
                .addOnSuccessListener { idList ->
                    val contactIDList : List<String> = idList.documents.map{document -> document.data?.getValue("ID")  as String }  as List<String>
                    continuation.resume(contactIDList)
                }
                .addOnFailureListener { failureReason ->
                    continuation.cancel()
                    Log.e(TAG, failureReason.message ?: failureReason.toString())
                }
                .addOnCanceledListener {
                    continuation.cancel()
                    Log.e(TAG, "Upload cancelled")
                }
        }
        else {
            // Since we're not connected to the database we get the data from the cache
            val source = Source.CACHE

            IDs.get(source)
                .addOnSuccessListener { idList ->
                    val contactIDList : List<String> = idList.documents.map{document -> document.data?.getValue("ID")  as String }  as List<String>
                    continuation.resume(contactIDList)
                }
                .addOnFailureListener { failureReason ->
                    continuation.cancel()
                    Log.e(TAG, failureReason.message ?: failureReason.toString())
                }
                .addOnCanceledListener {
                    continuation.cancel()
                    Log.e(TAG, "Upload cancelled")
                }
        }
    }

    override suspend fun update(context: Context, contactID: String) : Boolean{
        TODO("Not yet implemented")
    }

    override suspend fun delete(context: Context, contactID: String) : Boolean {
        TODO("Not yet implemented")
    }

    fun addBroadcast(Contact : String, Date : String, Latitude : String, Longitude : String,  Duration : String, Rssi : String, HealthStatus : String)
    {
        val info = hashMapOf(
            "contact" to Contact,
            "date" to Date,
            "latitude" to Latitude,
            "longitude" to Longitude,
            "duration" to Duration,
            "rssi" to Rssi,
            "health_status" to HealthStatus
        )

        broadcastCollection.document("broadcasting")
            .set(info)
            .addOnSuccessListener { Log.d(TAG, "DocumentSnapshot successfully written!") }
            .addOnFailureListener { e -> Log.w(TAG, "Error writing document", e) }
    }
}