package com.example.dopaminecut2.logic.manager

import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

abstract class BaseAppManager : AppManagerInterface {

    protected fun findNodeByText(root: AccessibilityNodeInfo?, keyword: String): Boolean {
        if (root == null) return false

        if (root.text?.toString()?.contains(keyword, ignoreCase = true) == true ||
            root.contentDescription?.toString()?.contains(keyword, ignoreCase = true) == true
        ) {
            return true
        }

        for (i in 0 until root.childCount) {
            if (findNodeByText(root.getChild(i), keyword)) return true
        }
        return false
    }

    protected fun findLongestText(root: AccessibilityNodeInfo?): String? {
        if (root == null) return null

        var longest: String? = root.text?.toString()

        for (i in 0 until root.childCount) {
            val childText = findLongestText(root.getChild(i))
            if (childText != null && (longest == null || childText.length > longest.length)) {
                longest = childText
            }
        }

        return longest
    }
}
