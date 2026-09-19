package com.nemotron.voiceime.chat

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import com.nemotron.voiceime.R

class MessageAdapter(
    private val messages: MutableList<ChatMessage>
) : RecyclerView.Adapter<MessageAdapter.MessageVH>() {

    fun replaceMessages(next: List<ChatMessage>) {
        val old = messages.toList()
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = next.size
            override fun areItemsTheSame(oldPosition: Int, newPosition: Int) =
                old[oldPosition].role == next[newPosition].role && old[oldPosition].ts == next[newPosition].ts
            override fun areContentsTheSame(oldPosition: Int, newPosition: Int) = old[oldPosition] == next[newPosition]
        })
        messages.clear()
        messages.addAll(next)
        diff.dispatchUpdatesTo(this)
    }

    private val TYPE_AI = 0
    private val TYPE_USER = 1

    override fun getItemViewType(position: Int): Int =
        if (messages[position].role == "user") TYPE_USER else TYPE_AI

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return MessageVH(v)
    }

    override fun onBindViewHolder(holder: MessageVH, position: Int) {
        val msg = messages[position]
        holder.bubble.text = MarkdownRenderer.render(msg.content)
        if (msg.role == "user") {
            holder.wrap.gravity = Gravity.END
            holder.bubble.setBackgroundResource(R.drawable.bg_bubble_user)
        } else {
            holder.wrap.gravity = Gravity.START
            holder.bubble.setBackgroundResource(R.drawable.bg_bubble_ai)
        }
    }

    override fun getItemCount(): Int = messages.size

    class MessageVH(item: View) : RecyclerView.ViewHolder(item) {
        val wrap: LinearLayout = item.findViewById(R.id.msgWrap)
        val bubble: TextView = item.findViewById(R.id.msgBubble)
    }
}
