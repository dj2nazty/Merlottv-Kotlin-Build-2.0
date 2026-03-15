package com.merlottv.kotlin.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.merlottv.kotlin.domain.repository.DeviceCodeRepository
import com.merlottv.kotlin.domain.repository.DeviceCodeStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceCodeRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : DeviceCodeRepository {

    companion object {
        private const val COLLECTION = "device_codes"
        private const val CODE_LENGTH = 6
        private const val EXPIRY_MS = 10 * 60 * 1000L // 10 minutes
    }

    override suspend fun generateCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no I/O/0/1 to avoid confusion
        val code = (1..CODE_LENGTH).map { chars.random() }.joinToString("")

        firestore.collection(COLLECTION).document(code).set(
            mapOf(
                "status" to "pending",
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
        ).await()

        return code
    }

    override fun observeCodeStatus(code: String): Flow<DeviceCodeStatus> = callbackFlow {
        val startTime = System.currentTimeMillis()
        var registration: ListenerRegistration? = null

        registration = firestore.collection(COLLECTION).document(code)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(DeviceCodeStatus.Error(error.message ?: "Firestore error"))
                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) {
                    trySend(DeviceCodeStatus.Expired)
                    return@addSnapshotListener
                }

                // Check expiry
                if (System.currentTimeMillis() - startTime > EXPIRY_MS) {
                    trySend(DeviceCodeStatus.Expired)
                    return@addSnapshotListener
                }

                val status = snapshot.getString("status") ?: "pending"
                when (status) {
                    "linked" -> {
                        val email = snapshot.getString("email") ?: ""
                        val password = snapshot.getString("password") ?: ""
                        trySend(DeviceCodeStatus.Linked(email, password))
                    }
                    else -> trySend(DeviceCodeStatus.Pending)
                }
            }

        awaitClose { registration.remove() }
    }

    override suspend fun deleteCode(code: String) {
        try {
            firestore.collection(COLLECTION).document(code).delete().await()
        } catch (_: Exception) { }
    }
}
