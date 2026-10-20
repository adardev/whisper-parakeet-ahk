package com.nemotron.voiceime.chat

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow

/** One visual contract for compact adarbot menus: model, attachments and actions. */
object AdarbotPopupSurface {
    fun menu(context: Context, horizontalPadding: Int = 12, verticalPadding: Int = 8): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, horizontalPadding), dp(context, verticalPadding), dp(context, horizontalPadding), dp(context, verticalPadding))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#172942"), Color.parseColor("#0D1727"))
            ).apply {
                cornerRadius = dp(context, 28).toFloat()
                setStroke(dp(context, 1), Color.parseColor("#365D93"))
            }
            clipToOutline = true
        }

    fun popup(content: View, width: Int, height: Int = ViewGroup.LayoutParams.WRAP_CONTENT): PopupWindow =
        PopupWindow(content, width, height, true).apply {
            elevation = dp(content.context, 18).toFloat()
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
        }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
