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
            try {
                val service = instance ?: return false
                val rootNode = service.rootInActiveWindow ?: return false
                return findAndClickNodeByText(rootNode, text)
            } catch (t: Throwable) {
                Log.e("Accessibility", "Error clicking by text: ${t.message}")
            }
            return false
        }

        private fun findAndClickNodeByText(node: AccessibilityNodeInfo, text: String): Boolean {
            try {
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

                    // Fallback 1.5: Traverse up parents to click clickable wrappers safely
                    var parent = try { node.parent } catch (e: Exception) { null }
                    var depth = 0
                    while (parent != null && depth < 5) {
                        try {
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
                        } catch (e: Exception) {
                            parent = null
                        }
                        depth++
                    }
                }
                val count = node.childCount
                for (i in 0 until count) {
                    val child = try { node.getChild(i) } catch (e: Exception) { null }
                    if (child != null) {
                        val clicked = findAndClickNodeByText(child, text)
                        if (clicked) return true
                    }
                }
            } catch (t: Throwable) {
                Log.e("Accessibility", "Error during findAndClickNodeByText: ${t.message}")
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

        fun longClickByText(text: String): Boolean {
            try {
                val service = instance ?: return false
                val rootNode = service.rootInActiveWindow ?: return false
                return findAndLongClickNodeByText(rootNode, text)
            } catch (t: Throwable) {
                Log.e("Accessibility", "Error long clicking by text: ${t.message}")
            }
            return false
        }

        private fun findAndLongClickNodeByText(node: AccessibilityNodeInfo, text: String): Boolean {
            try {
                val contentDesc = node.contentDescription?.toString() ?: ""
                val nodeText = node.text?.toString() ?: ""
                if (nodeText.contains(text, ignoreCase = true) || contentDesc.contains(text, ignoreCase = true)) {
                    val rect = android.graphics.Rect()
                    node.getBoundsInScreen(rect)
                    if (rect.centerX() > 0 && rect.centerY() > 0) {
                        val coordinateClicked = longClickAtCoordinates(rect.centerX().toFloat(), rect.centerY().toFloat())
                        if (coordinateClicked) return true
                    }
                    if (node.isClickable) {
                        val clicked = node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                        if (clicked) return true
                    }
                }
                val count = node.childCount
                for (i in 0 until count) {
                    val child = try { node.getChild(i) } catch (e: Exception) { null }
                    if (child != null) {
                        val clicked = findAndLongClickNodeByText(child, text)
                        if (clicked) return true
                    }
                }
            } catch (t: Throwable) {
                Log.e("Accessibility", "Error in findAndLongClickNodeByText: ${t.message}")
            }
            return false
        }

        fun inputTextByText(targetText: String, textToInject: String): Boolean {
            try {
                val service = instance ?: return false
                val rootNode = service.rootInActiveWindow ?: return false
                return findAndSetTextOnNode(rootNode, targetText, textToInject)
            } catch (t: Throwable) {
                Log.e("Accessibility", "Error inputting text: ${t.message}")
            }
            return false
        }

        private fun findAndSetTextOnNode(node: AccessibilityNodeInfo, targetText: String, textToInject: String): Boolean {
            try {
                val contentDesc = node.contentDescription?.toString() ?: ""
                val nodeText = node.text?.toString() ?: ""
                if (nodeText.contains(targetText, ignoreCase = true) || contentDesc.contains(targetText, ignoreCase = true)) {
                    if (node.isEditable) {
                        val arguments = android.os.Bundle()
                        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToInject)
                        val set = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                        if (set) return true
                    }
                }
                val count = node.childCount
                for (i in 0 until count) {
                    val child = try { node.getChild(i) } catch (e: Exception) { null }
                    if (child != null) {
                        val set = findAndSetTextOnNode(child, targetText, textToInject)
                        if (set) return true
                    }
                }
            } catch (t: Throwable) {
                Log.e("Accessibility", "Error in findAndSetTextOnNode: ${t.message}")
            }
            return false
        }

        fun typeIntoFocusedNode(textToInject: String): Boolean {
            try {
                val service = instance ?: return false
                val rootNode = service.rootInActiveWindow ?: return false
                return findAndSetTextOnFocusedNode(rootNode, textToInject)
            } catch (t: Throwable) {
                Log.e("Accessibility", "Error typing into focused: ${t.message}")
            }
            return false
        }

        private fun findAndSetTextOnFocusedNode(node: AccessibilityNodeInfo, textToInject: String): Boolean {
            try {
                if (node.isFocused && node.isEditable) {
                    val arguments = android.os.Bundle()
                    arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToInject)
                    val set = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                    if (set) return true
                }
                val count = node.childCount
                for (i in 0 until count) {
                    val child = try { node.getChild(i) } catch (e: Exception) { null }
                    if (child != null) {
                        val set = findAndSetTextOnFocusedNode(child, textToInject)
                        if (set) return true
                    }
                }
            } catch (t: Throwable) {
                Log.e("Accessibility", "Error in findAndSetTextOnFocusedNode: ${t.message}")
            }
            return false
        }

        fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float, duration: Long = 300): Boolean {
            try {
                val service = instance ?: return false
                val builder = GestureDescription.Builder()
                val path = Path().apply {
                    moveTo(fromX, fromY)
                    lineTo(toX, toY)
                }
                builder.addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                return service.dispatchGesture(builder.build(), null, null)
            } catch (t: Throwable) {
                Log.e("Accessibility", "Error in swipe: ${t.message}")
            }
            return false
        }

        fun swipeByName(direction: String, duration: Long = 300): Boolean {
            try {
                val service = instance ?: return false
                val metrics = service.resources.displayMetrics
                val w = metrics.widthPixels.toFloat()
                val h = metrics.heightPixels.toFloat()
                
                return when (direction.uppercase()) {
                    "DOWN" -> swipe(w / 2f, h * 0.3f, w / 2f, h * 0.8f, duration)
                    "UP" -> swipe(w / 2f, h * 0.8f, w / 2f, h * 0.3f, duration)
                    "LEFT" -> swipe(w * 0.8f, h / 2f, w * 0.2f, h / 2f, duration)
                    "RIGHT" -> swipe(w * 0.2f, h / 2f, w * 0.8f, h / 2f, duration)
                    else -> false
                }
            } catch (t: Throwable) {
                Log.e("Accessibility", "Error in swipeByName: ${t.message}")
            }
            return false
        }

        fun getVisibleScreenTexts(): List<String> {
            try {
                val service = instance ?: return emptyList()
                val rootNode = service.rootInActiveWindow ?: return emptyList()
                val texts = mutableListOf<String>()
                extractTextsFromNode(rootNode, texts)
                return texts.distinct()
            } catch (t: Throwable) {
                Log.e("Accessibility", "Error in getVisibleScreenTexts: ${t.message}")
            }
            return emptyList()
        }

        private fun extractTextsFromNode(node: AccessibilityNodeInfo, list: MutableList<String>) {
            try {
                val text = try { node.text?.toString()?.trim() ?: "" } catch (e: Exception) { "" }
                val desc = try { node.contentDescription?.toString()?.trim() ?: "" } catch (e: Exception) { "" }
                if (text.isNotEmpty() && text.length < 150) {
                    list.add(text)
                }
                if (desc.isNotEmpty() && desc.length < 150) {
                    list.add(desc)
                }
                val count = try { node.childCount } catch (e: Exception) { 0 }
                for (i in 0 until count) {
                    val child = try { node.getChild(i) } catch (e: Exception) { null }
                    if (child != null) {
                        extractTextsFromNode(child, list)
                    }
                }
            } catch (t: Throwable) {
                Log.e("Accessibility", "Error in extractTextsFromNode: ${t.message}")
            }
        }

        fun getVisibleScreenDump(): String {
            val texts = getVisibleScreenTexts()
            if (texts.isEmpty()) return "Ekran boş veya erişilebilirlik aktif değil sör."
            return texts.joinToString(" | ")
        }
    }
}
