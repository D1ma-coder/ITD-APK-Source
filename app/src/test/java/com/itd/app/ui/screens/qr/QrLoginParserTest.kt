package com.itd.app.ui.screens.qr

import org.junit.Assert.*
import org.junit.Test

class QrLoginParserTest {
    private val id = "76b41a55-b920-4431-8d85-fddc39961643"
    private val secret = "a4".repeat(32)
    private val valid = "https://xn--d1ah4a.com/qr#i=$id&s=$secret"

    @Test fun acceptsCanonicalPayload() { assertEquals(QrLoginPayload(id, secret), QrLoginParser.parse(valid)) }
    @Test fun refusesOtherHostsAndPlainHttp() {
        assertNull(QrLoginParser.parse(valid.replace("xn--d1ah4a.com", "xn--d1ah4a.com.example.invalid")))
        assertNull(QrLoginParser.parse(valid.replace("https://", "http://")))
        assertNull(QrLoginParser.parse(valid.replace("/qr#", "/other#")))
    }
    @Test fun requiresFragmentAndLowercaseHexSecret() {
        assertNull(QrLoginParser.parse(valid.replace('#', '?')))
        assertNull(QrLoginParser.parse(valid.replace(secret, "z".repeat(64))))
        assertNull(QrLoginParser.parse(valid.replace(secret, secret.uppercase())))
        assertNull(QrLoginParser.parse(valid.replace(id, "1-1-1-1-1")))
    }
}
