package com.smartfit.ble.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.smartfit.ble.data.model.UserProfile
import kotlinx.coroutines.tasks.await

class FirebaseUserDataSource {

    private val firestore = FirebaseFirestore.getInstance()

    fun createUserIfNotExists(profile: UserProfile) {
        firestore
            .collection("users")
            .document(profile.uid)
            .set(
                mapOf(
                    "profile" to mapOf(
                        "displayName" to profile.displayName,
                        "email" to profile.email,
                        "loginType" to profile.loginType,
                        "createdAt" to System.currentTimeMillis()
                    )
                ),
                SetOptions.merge()
            )
    }

    suspend fun getDisplayName(uid: String): String? {
        val doc = firestore.collection("users").document(uid).get().await()
        val profile = doc.get("profile") as? Map<*, *> ?: return null
        return profile["displayName"] as? String
    }

    suspend fun setDisplayName(uid: String, displayName: String) {
        firestore
            .collection("users")
            .document(uid)
            .set(
                mapOf(
                    "profile" to mapOf(
                        "displayName" to displayName
                    )
                ),
                SetOptions.merge()
            )
            .await()
    }
}


