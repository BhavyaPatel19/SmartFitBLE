package com.smartfit.shared.auth

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class AuthClient(
    private val baseUrl: String = "https://dummyjson.com"
) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    suspend fun login(username: String, password: String): LoginResponse {
        return client.post("$baseUrl/auth/login") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)      // 🔥 REQUIRED NOW

            setBody(
                LoginRequest(
                    username = username,
                    password = password,
                    expiresInMins = 30
                )
            )
        }.body()
    }

    suspend fun me(accessToken: String): UserDetails {
        return client.get("$baseUrl/auth/me") {
            accept(ContentType.Application.Json)      // 🔥 REQUIRED
            header("Authorization", "Bearer $accessToken")
        }.body()
    }
}
