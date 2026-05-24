package com.example

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("Accessibility", "Jarvis Accessibility Connected")
        instance = this
        
        try {
            val intent = Intent("com.example.ACCESSIBILITY_CONNECTED")
            sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e("Accessibility", "Error sending connected broadcast: ${e.message}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Active on-demand execution. No continuous passive analysis is active.
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    companion object {
        @Volatile
        var instance: JarvisAccessibilityService? = null
            private set

        fun isServiceRunning(): Boolean {
            return instance != null
        }
        
        fun clickByText(text: String): Boolean {
            val service = instance ?: return false
            val rootNode = service.rootInActiveWindow ?: return false
            val result = findAndClickNodeByText(rootNode, text)
            return result
        }

        private fun findAndClickNodeByText(node: AccessibilityNodeInfo, text: String): Boolean {
            val contentDesc = node.contentDescription?.toString() ?: ""
            val nodeText = node.text?.toString() ?: ""
            if (nodeText.contains(text, ignoreCase = true) || contentDesc.contains(text, ignoreCase = true)) {
                if (node.isClickable) {
                    val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (clicked) return true
                }
                var parent = node.parent
                var depth = 0
                while (parent != null && depth < 4) {
                    if (parent.isClickable) {
                        val clicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if (clicked) {
                            return true
                        }
                    }
                    val nextParent = parent.parent
                    parent = nextParent
                    depth++
                }
            }
            val count = node.childCount
            for (i in 0 until count) {
                val child = node.getChild(i)
                if (child != null) {
                    val clicked = findAndClickNodeByText(child, text)
                    if (clicked) return true
                }
            }
            return false
        }

        fun clickAtCoordinates(x: Float, y: Float): Boolean {
            val service = instance ?: return false
            val builder = GestureDescription.Builder()
            val path = Path().apply {
                moveTo(x, y)
            }
            builder.addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            return service.dispatchGesture(builder.build(), null, null)
        }
    }
}
