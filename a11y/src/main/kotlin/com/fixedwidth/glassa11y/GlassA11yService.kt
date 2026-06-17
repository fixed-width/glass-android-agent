package com.fixedwidth.glassa11y

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class GlassA11yService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        System.err.println("glass-a11y: service connected")
        // Socket server wired in Task 5.
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* polled, not event-driven */ }
    override fun onInterrupt() {}
}
