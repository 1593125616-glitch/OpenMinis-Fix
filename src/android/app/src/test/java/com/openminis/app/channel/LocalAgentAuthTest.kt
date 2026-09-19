package com.openminis.app.channel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class LocalAgentAuthTest {
    @Test
    fun `bearer is case insensitive`() {
        assertTrue(LocalAgentAuth.authorized("Bearer 123456", "123456"))
        assertTrue(LocalAgentAuth.authorized("bearer 123456", "123456"))
        assertFalse(LocalAgentAuth.authorized("Bearer 000000", "123456"))
        assertFalse(LocalAgentAuth.authorized(null, "123456"))
        assertFalse(LocalAgentAuth.authorized("Bearer 123456", ""))
    }

    @Test
    fun `readHttp uses byte content-length`() {
        val json = """{"text":"你好"}"""
        val body = json.toByteArray(StandardCharsets.UTF_8)
        val raw = buildString {
            append("POST /v1/prompt HTTP/1.1\r\n")
            append("Authorization: Bearer 123456\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("\r\n")
        }.toByteArray(StandardCharsets.US_ASCII) + body
        val req = LocalAgentServer.readHttp(ByteArrayInputStream(raw))!!
        assertEquals("POST", req.method)
        assertEquals("/v1/prompt", req.path)
        assertEquals(json, req.body)
        assertEquals("Bearer 123456", req.headers["authorization"])
    }
}
