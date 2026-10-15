package com.hjw.qbremote.ui

import com.hjw.qbremote.data.BackendConnectionError
import com.hjw.qbremote.data.ServerBackendType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendErrorMessageSupportTest {

    @Test
    fun messagesFollowTheRequestedLanguage() {
        val error = BackendConnectionError.HttpsMismatch(ServerBackendType.QBITTORRENT, usedHttps = true, detail = "")

        assertTrue(userFacingConnectionMessage(error, chinese = true).contains("关闭 HTTPS"))
        assertTrue(userFacingConnectionMessage(error, chinese = false).contains("Turn HTTPS off"))
    }

    @Test
    fun testTimeoutMentionsTheLimit() {
        val message = userFacingConnectionMessage(BackendConnectionError.TestTimedOut(12), chinese = false)

        assertTrue(message.contains("12 s"))
    }

    @Test
    fun unknownErrorsKeepTheirOwnMessage() {
        assertEquals("boom", userFacingConnectionMessage(IllegalStateException("boom"), chinese = false))
        assertEquals("Refresh failed", userFacingConnectionMessage(IllegalStateException(), chinese = false))
    }
}
