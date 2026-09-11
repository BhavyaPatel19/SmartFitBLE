package com.smartfit.ble.data.ai

import com.smartfit.ble.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GroqAiService {

    companion object {
        private const val API_URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val MODEL = "llama-3.3-70b-versatile"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private suspend fun postRequest(requestBodyJson: JSONObject): String = withContext(Dispatchers.IO) {
        val body = requestBodyJson.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(API_URL)
            .post(body)
            .addHeader("Authorization", "Bearer ${BuildConfig.GROQ_API_KEY}")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "SmartFitBLE/1.0 (Android)")
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
            ?: throw AiException("Empty response from Groq API")

        if (!response.isSuccessful) {
            throw AiException("Groq API error ${response.code}: $responseBody")
        }

        JSONObject(responseBody)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
    }

    suspend fun getCompletion(
        systemPrompt: String,
        userMessage: String,
        temperature: Double = 0.3,
        maxTokens: Int = 512
    ): String {
        val messagesArray = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            put(JSONObject().put("role", "user").put("content", userMessage))
        }
        val requestBody = JSONObject().apply {
            put("model", MODEL)
            put("messages", messagesArray)
            put("temperature", temperature)
            put("max_tokens", maxTokens)
        }
        return postRequest(requestBody)
    }

    suspend fun getChatCompletion(
        systemPrompt: String,
        messages: List<ChatMessage>,
        temperature: Double = 0.4,
        maxTokens: Int = 1024
    ): String {
        val messagesArray = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            messages.forEach { msg ->
                put(JSONObject().put("role", msg.role).put("content", msg.content))
            }
        }
        val requestBody = JSONObject().apply {
            put("model", MODEL)
            put("messages", messagesArray)
            put("temperature", temperature)
            put("max_tokens", maxTokens)
        }
        return postRequest(requestBody)
    }

    suspend fun getVitalsAssessment(
        patientAge: Int?,
        heartRate: Int,
        spo2: Int?,
        temperature: Float?  // stored in °C internally, converted to °F for AI prompt
    ): AiInsight {
        val systemPrompt = """You are a clinical triage assistant. Analyze the patient vitals and respond in EXACTLY this format with no extra text:
RISK: <Normal|Caution|Urgent>
EXPLANATION: <2-sentence clinical explanation of what the vitals indicate>
NEXT_STEPS: <Specific suggested next steps for the doctor>

Use Fahrenheit for temperature. Normal ranges: HR 60-100 bpm, SpO2 95-100%, Temp 97.0-99.0°F."""

        val userMessage = buildString {
            append("Analyze these patient vitals: ")
            patientAge?.let { append("Age $it years, ") }
            append("Heart Rate $heartRate bpm")
            spo2?.let { append(", SpO2 $it%") }
            temperature?.let {
                val tempF = it * 9f / 5f + 32f
                append(", Temperature ${"%.1f".format(tempF)}°F")
            }
        }

        return try {
            val response = getCompletion(systemPrompt, userMessage)
            parseVitalsResponse(response)
        } catch (e: Exception) {
            AiInsight(
                riskLevel = RiskLevel.UNKNOWN,
                explanation = "Unable to get AI assessment: ${e.message}",
                nextSteps = "Please review vitals manually."
            )
        }
    }

    private fun parseVitalsResponse(response: String): AiInsight {
        val lines = response.lines().map { it.trim() }.filter { it.isNotBlank() }

        var riskLevel = RiskLevel.UNKNOWN
        var explanation = ""
        var nextSteps = ""

        for (line in lines) {
            when {
                line.startsWith("RISK:", ignoreCase = true) -> {
                    val value = line.substringAfter(":").trim()
                    riskLevel = when {
                        value.contains("Urgent", ignoreCase = true) -> RiskLevel.URGENT
                        value.contains("Caution", ignoreCase = true) -> RiskLevel.CAUTION
                        value.contains("Normal", ignoreCase = true) -> RiskLevel.NORMAL
                        else -> RiskLevel.UNKNOWN
                    }
                }
                line.startsWith("EXPLANATION:", ignoreCase = true) -> {
                    explanation = line.substringAfter(":").trim()
                }
                line.startsWith("NEXT_STEPS:", ignoreCase = true) ||
                line.startsWith("NEXT STEPS:", ignoreCase = true) -> {
                    nextSteps = line.substringAfter(":").trim()
                }
            }
        }

        if (explanation.isBlank()) explanation = response
        if (nextSteps.isBlank()) nextSteps = "Review vitals and monitor patient."

        return AiInsight(riskLevel, explanation, nextSteps)
    }

    suspend fun getPatientSummary(
        patientName: String,
        visits: List<String>
    ): String {
        val systemPrompt = "You are a medical records summarizer for a clinic. Temperature values are in Fahrenheit. Summarize the patient's health trends concisely in 3-4 sentences. Highlight any concerning trends, improvements, or patterns. Be factual and clinical. Mention total number of visits."

        val userMessage = buildString {
            append("Summarize the health record of patient \"$patientName\" from these visits:\n")
            visits.forEach { append("- $it\n") }
        }

        return try {
            getCompletion(systemPrompt, userMessage, maxTokens = 300)
        } catch (e: Exception) {
            "Unable to generate summary: ${e.message}"
        }
    }
}

class AiException(message: String) : Exception(message)
