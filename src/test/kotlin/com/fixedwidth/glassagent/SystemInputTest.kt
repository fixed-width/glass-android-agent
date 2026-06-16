package com.fixedwidth.glassagent

import kotlin.test.Test
import kotlin.test.assertTrue

class SystemInputTest {
    @Test fun named_keys_present() {
        assertTrue(SystemInput.KEYS.containsKey("enter"))
        assertTrue(SystemInput.KEYS.containsKey("backspace"))
    }
}
