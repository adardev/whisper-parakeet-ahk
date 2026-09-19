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
    private val messages: MutableList<ChatMessage>,
    private val onCopy: (ChatMessage) -> Unit,
    private val onSpeak: (ChatMessage) -> Unit,
    private val onLongPress: (View, ChatMessage) -> Unit
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
        holder.bubble.animate().cancel()
        if (msg.role == "assistant" && msg.content == "adarbot está pensando…") {
            holder.bubble.text = "adarbot está pensando"
            holder.bubble.alpha = 0.55f
            holder.bubble.animate().alpha(1f).setDuration(620).withEndAction {
                holder.bubble.animate().alpha(0.55f).setDuration(620).start()
            }.start()
        } else {
            holder.bubble.alpha = 1f
            holder.bubble.text = MarkdownRenderer.render(msg.content)
        }
        holder.actions.visibility = View.GONE
        if (msg.role == "user") {
            holder.wrap.gravity = Gravity.END
            holder.bubble.setBackgroundResource(R.drawable.bg_bubble_user)
            holder.actions.gravity = Gravity.END
        } else {
            holder.wrap.gravity = Gravity.START
            holder.bubble.setBackgroundResource(R.drawable.bg_bubble_ai)
            holder.actions.gravity = Gravity.START
        }
        holder.copy.setOnClickListener { onCopy(msg) }
        holder.speak.setOnClickListener { onSpeak(msg) }
        holder.bubble.setOnLongClickListener {
            holder.bubble.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            onLongPress(holder.bubble, msg)
            true
        }
    }

    override fun getItemCount(): Int = messages.size

    class MessageVH(item: View) : RecyclerView.ViewHolder(item) {
        val wrap: LinearLayout = item.findViewById(R.id.msgWrap)
        val actions: LinearLayout = item.findViewById(R.id.msgActions)
        val bubble: TextView = item.findViewById(R.id.msgBubble)
        val copy: View = item.findViewById(R.id.msgCopy)
        val speak: View = item.findViewById(R.id.msgSpeak)
    }
}
