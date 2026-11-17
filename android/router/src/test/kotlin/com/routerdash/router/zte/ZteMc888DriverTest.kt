package com.routerdash.router.zte

import com.routerdash.router.RouterException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** A fake router that behaves like the real MC888D did during the probe. */
class ZteMc888DriverTest {
    private val ld = "0B918943DF0962BC7A1824C0555A389347B4FEBDC7CF9D1254406D80CE44E3F9"
    private val signalJson = javaClass.getResource("/mc888_signal_b05.json")!!.readText()
    private val server = MockWebServer()
    private var loggedIn = false
    private var loginPosts = 0
    private var lastReferer: String? = null

    private fun start(correctPassword: String = "secret") {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                lastReferer = request.getHeader("Referer")
                val path = request.requestUrl!!
                return when {
                    request.method == "POST" -> {
                        loginPosts++
                        val form = request.body.readUtf8()
                        val expected = ZteAuth.loginPassword(correctPassword, ld)
                        if (form.contains("password=$expected")) {
                            loggedIn = true
                            MockResponse().setBody("""{"result":"0"}""").addHeader("Set-Cookie", "stok=abc; path=/")
                        } else MockResponse().setBody("""{"result":"3"}""")
                    }
                    path.queryParameter("cmd")!!.startsWith("LD") ->
                        MockResponse().setBody("""{"LD":"$ld","login_lock_time":"-1","psw_fail_num_str":"5"}""")
                    else -> {
                        val ok = loggedIn && request.getHeader("Cookie")?.contains("stok=abc") == true
                        MockResponse().setBody(if (ok) signalJson else """{"loginfo":""}""")
                    }
                }
            }
        }
        server.start()
    }

    private fun driver() = ZteMc888Driver(server.url("/").toString(), clock = { 42L })

    @After fun tearDown() = server.shutdown()

    @Test fun `logs in and reads signal`() = runTest {
        start()
        val d = driver()
        d.login("secret")
        val s = d.signal()
        assertEquals("n78", s.nr?.band)
        assertEquals(42L, s.timestampMs)
        assertTrue(lastReferer!!.endsWith("/index.html"))
    }

    @Test fun `logs in again by itself when the session expires`() = runTest {
        start()
        val d = driver()
        d.login("secret")
        loggedIn = false // router rebooted
        assertEquals("n78", d.signal().nr?.band)
        assertEquals(2, loginPosts)
    }

    @Test fun `wrong password is reported and never retried`() = runTest {
        start()
        val d = driver()
        try {
            d.login("wrong"); fail()
        } catch (e: RouterException) {
            assertEquals(RouterException.Kind.BAD_PASSWORD, e.kind)
            assertTrue(e.message!!.contains("4 attempts left"))
        }
        try {
            d.signal(); fail()
        } catch (e: RouterException) {
            assertEquals(RouterException.Kind.NOT_LOGGED_IN, e.kind)
        }
        assertEquals(1, loginPosts)
    }

    @Test fun `unreachable router gives a clear error`() = runTest {
        val d = ZteMc888Driver("http://127.0.0.1:9")
        try {
            d.login("x"); fail()
        } catch (e: RouterException) {
            assertEquals(RouterException.Kind.UNREACHABLE, e.kind)
        }
    }
}
