package com.smartfit.ble.ui.information

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smartfit.ble.data.ai.ChatMessage
import com.smartfit.ble.data.ai.GroqAiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InformationViewModel(private val aiService: GroqAiService) : ViewModel() {

    companion object {
        private const val SYSTEM_PROMPT =
            "You are an AI Health Assistant for a medical clinic app. " +
            "Temperature is always in Fahrenheit (°F). Normal ranges: HR 60-100 bpm, SpO2 95-100%, Temp 97.0-99.0°F, BP <120/80 mmHg. " +
            "You can: (1) Answer medical questions clearly and accurately. " +
            "(2) Analyze symptoms and vitals when a doctor describes them (e.g. 'Patient has fever 101°F, HR 110 bpm, SpO2 94%'). " +
            "(3) Explain medical terms, conditions, medications, and procedures. " +
            "(4) Help doctors understand what certain vital readings may indicate. " +
            "Always use Fahrenheit. Keep responses concise (under 250 words). " +
            "Format with bullet points when listing multiple items. " +
            "For emergencies, always advise immediate medical intervention. " +
            "Never make final diagnoses — always recommend clinical judgment."
    }

    private val conversationHistory = mutableListOf<ChatMessage>()

    private val _chatItems = MutableStateFlow<List<ChatItem>>(emptyList())
    val chatItems: StateFlow<List<ChatItem>> = _chatItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun sendWelcome(welcomeText: String) {
        if (_chatItems.value.isEmpty()) {
            _chatItems.value = listOf(ChatItem.AiMessage(welcomeText))
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return

        conversationHistory.add(ChatMessage("user", trimmed))

        val items = _chatItems.value.toMutableList()
        items.add(ChatItem.UserMessage(trimmed))
        items.add(ChatItem.Loading)
        _chatItems.value = items
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val response = aiService.getChatCompletion(
                    systemPrompt = SYSTEM_PROMPT,
                    messages = conversationHistory
                )
                conversationHistory.add(ChatMessage("assistant", response))

                val updated = _chatItems.value.toMutableList()
                updated.removeAll { it is ChatItem.Loading }
                updated.add(ChatItem.AiMessage(response))
                _chatItems.value = updated
            } catch (e: Exception) {
                val errorMsg = "Sorry, I couldn't process that request. Please try again."
                val updated = _chatItems.value.toMutableList()
                updated.removeAll { it is ChatItem.Loading }
                updated.add(ChatItem.AiMessage(errorMsg))
                _chatItems.value = updated
            } finally {
                _isLoading.value = false
            }
        }
    }
}

class InformationViewModelFactory(
    private val aiService: GroqAiService
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(InformationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return InformationViewModel(aiService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
