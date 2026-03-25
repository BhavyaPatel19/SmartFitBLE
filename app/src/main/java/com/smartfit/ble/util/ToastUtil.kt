package com.smartfit.ble.util

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.card.MaterialCardView
import com.smartfit.ble.R

object ToastUtil {
    
    fun showError(context: Context, message: String) {
        showCustomToast(context, message, isError = true)
    }
    
    fun showSuccess(context: Context, message: String) {
        showCustomToast(context, message, isError = false)
    }
    
    private fun showCustomToast(context: Context, message: String, isError: Boolean) {
        val inflater = LayoutInflater.from(context)
        val layout = inflater.inflate(R.layout.custom_toast, null)
        
        val cardView = layout.findViewById<MaterialCardView>(R.id.toastCard)
        val textView = layout.findViewById<TextView>(R.id.toastText)
        
        textView.text = message
        
        // Set background color based on error/success
        val backgroundColor = if (isError) {
            context.getColor(R.color.error_background)
        } else {
            context.getColor(R.color.success_background)
        }
        cardView.setCardBackgroundColor(backgroundColor)
        
        val toast = Toast(context)
        toast.duration = Toast.LENGTH_LONG
        toast.view = layout
        toast.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 100)
        toast.show()
    }
}

