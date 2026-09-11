package com.smartfit.ble.data.ai

data class ChatMessage(
    val role: String,
    val content: String
)

data class AiInsight(
    val riskLevel: RiskLevel,
    val explanation: String,
    val nextSteps: String
)

enum class RiskLevel(val label: String) {
    NORMAL("Normal"),
    CAUTION("Caution"),
    URGENT("Urgent"),
    UNKNOWN("Unknown")
}
