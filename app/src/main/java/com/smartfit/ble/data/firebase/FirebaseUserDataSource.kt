package com.smartfit.ble.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.smartfit.ble.data.model.UserProfile

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
}


