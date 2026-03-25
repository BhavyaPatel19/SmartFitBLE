package com.smartfit.shared.auth

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
    val expiresInMins: Int = 30
)

@Serializable
data class LoginResponse(
    val id: Int,
    val username: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val gender: String,
    val image: String,
    val accessToken: String,
    val refreshToken: String
)

@Serializable
data class UserDetails(
    val id: Int,
    val email: String,
    val firstName: String,
    val lastName: String
)
