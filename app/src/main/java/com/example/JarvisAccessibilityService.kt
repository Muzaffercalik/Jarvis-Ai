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
            return findAndClickNodeByText(rootNode, text)
        }

        private fun findAndClickNodeByText(node: AccessibilityNodeInfo, text: String): Boolean {
            val contentDesc = node.contentDescription?.toString() ?: ""
            val nodeText = node.text?.toString() ?: ""
            if (nodeText.contains(text, ignoreCase = true) || contentDesc.contains(text, ignoreCase = true)) {
                // Get visual bounds on the physical screen
                val rect = android.graphics.Rect()
                node.getBoundsInScreen(rect)
                if (rect.centerX() > 0 && rect.centerY() > 0) {
                    // Try coordinates-based click which is 100% reliable for custom apps (YouTube, browser, etc.)
                    val coordinateClicked = clickAtCoordinates(rect.centerX().toFloat(), rect.centerY().toFloat())
                    if (coordinateClicked) return true
                }

                // Fallback 1: Click directly on the node
                if (node.isClickable) {
                    val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (clicked) return true
                }

                // Fallback 2: Traverse up parents to click clickable wrappers
                var parent = node.parent
                var depth = 0
                while (parent != null && depth < 5) {
                    val parentRect = android.graphics.Rect()
                    parent.getBoundsInScreen(parentRect)
                    if (parentRect.centerX() > 0 && parentRect.centerY() > 0) {
                        val parentCoordClicked = clickAtCoordinates(parentRect.centerX().toFloat(), parentRect.centerY().toFloat())
                        if (parentCoordClicked) return true
                    }
                    if (parent.isClickable) {
                        val clicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if (clicked) return true
                    }
                    parent = parent.parent
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

        fun longClickAtCoordinates(x: Float, y: Float): Boolean {
            val service = instance ?: return false
            val builder = GestureDescription.Builder()
            val path = Path().apply {
                moveTo(x, y)
            }
            builder.addStroke(GestureDescription.StrokeDescription(path, 0, 800)) // 800ms hold gesture
            return service.dispatchGesture(builder.build(), null, null)
        }

        fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float, duration: Long = 300): Boolean {
            val service = instance ?: return false
            val builder = GestureDescription.Builder()
            val path = Path().apply {
                moveTo(fromX, fromY)
                lineTo(toX, toY)
            }
            builder.addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            return service.dispatchGesture(builder.build(), null, null)
        }

        fun getVisibleScreenTexts(): List<String> {
            val service = instance ?: return emptyList()
            val rootNode = service.rootInActiveWindow ?: return emptyList()
            val texts = mutableListOf<String>()
            extractTextsFromNode(rootNode, texts)
            return texts.distinct()
        }

        private fun extractTextsFromNode(node: AccessibilityNodeInfo, list: MutableList<String>) {
            val text = node.text?.toString()?.trim() ?: ""
            val desc = node.contentDescription?.toString()?.trim() ?: ""
            if (text.isNotEmpty() && text.length < 150) {
                list.add(text)
            }
            if (desc.isNotEmpty() && desc.length < 150) {
                list.add(desc)
            }
            val count = node.childCount
            for (i in 0 until count) {
                val child = node.getChild(i)
                if (child != null) {
                    extractTextsFromNode(child, list)
                }
            }
        }

        fun getVisibleScreenDump(): String {
            val texts = getVisibleScreenTexts()
            if (texts.isEmpty()) return "Ekran boş veya erişilebilirlik aktif değil sör."
            return texts.joinToString(" | ")
        }
    }
}
