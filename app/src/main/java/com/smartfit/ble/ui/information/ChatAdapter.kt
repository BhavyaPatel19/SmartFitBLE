package com.smartfit.ble.ui.information

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smartfit.ble.R

sealed class ChatItem {
    data class UserMessage(val text: String) : ChatItem()
    data class AiMessage(val text: String) : ChatItem()
    object Loading : ChatItem()
}

class ChatAdapter : ListAdapter<ChatItem, RecyclerView.ViewHolder>(ChatDiffCallback()) {

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_AI = 1
        private const val TYPE_LOADING = 2
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ChatItem.UserMessage -> TYPE_USER
        is ChatItem.AiMessage -> TYPE_AI
        ChatItem.Loading -> TYPE_LOADING
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_USER -> UserViewHolder(inflater.inflate(R.layout.item_chat_user, parent, false))
            TYPE_AI -> AiViewHolder(inflater.inflate(R.layout.item_chat_ai, parent, false))
            else -> LoadingViewHolder(inflater.inflate(R.layout.item_chat_loading, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ChatItem.UserMessage -> (holder as UserViewHolder).bind(item)
            is ChatItem.AiMessage -> (holder as AiViewHolder).bind(item)
            ChatItem.Loading -> { /* static layout */ }
        }
    }

    class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvMessage: TextView = view.findViewById(R.id.tvMessage)
        fun bind(item: ChatItem.UserMessage) {
            tvMessage.text = item.text
        }
    }

    class AiViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvMessage: TextView = view.findViewById(R.id.tvMessage)
        fun bind(item: ChatItem.AiMessage) {
            tvMessage.text = item.text
        }
    }

    class LoadingViewHolder(view: View) : RecyclerView.ViewHolder(view)
}

private class ChatDiffCallback : DiffUtil.ItemCallback<ChatItem>() {
    override fun areItemsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
        return oldItem == newItem
    }
}
