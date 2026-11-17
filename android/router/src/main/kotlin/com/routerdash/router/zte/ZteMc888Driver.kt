package com.routerdash.router.zte

import com.routerdash.router.RouterDriver
import com.routerdash.router.RouterException
import com.routerdash.router.RouterException.Kind
import com.routerdash.router.RouterInfo
import com.routerdash.router.SignalSnapshot
import com.routerdash.router.zte.ZteParser.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Driver for the ZTE MC888 / MC888D web API (goform endpoints).
 *
 * @param baseUrl e.g. "http://192.168.0.1"
 */
class ZteMc888Driver(
    baseUrl: String,
    private val clock: () -> Long = System::currentTimeMillis,
    client: OkHttpClient? = null,
) : RouterDriver {

    private val base: HttpUrl = baseUrl.trimEnd('/').toHttpUrl()
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private var password: String? = null

    private val http: OkHttpClient = (client?.newBuilder() ?: OkHttpClient.Builder())
        .cookieJar(MemoryCookieJar())
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun login(password: String) = lock.withLock {
        doLogin(password)
        this.password = password
    }

    override suspend fun info(): RouterInfo = lock.withLock {
        ZteParser.parseInfo(get(ZteParser.INFO_FIELDS))
    }

    override suspend fun signal(): SignalSnapshot = lock.withLock {
        var raw = get(ZteParser.SIGNAL_FIELDS)
        if (raw.str("loginfo") != "ok") {
            // Session expired (router reboot, timeout): log in again once with the saved password.
            val pw = password ?: throw RouterException(Kind.NOT_LOGGED_IN, "Not logged in")
            doLogin(pw)
            raw = get(ZteParser.SIGNAL_FIELDS)
        }
        ZteParser.parseSignal(raw, clock())
    }

    private suspend fun doLogin(password: String) {
        val pre = get(listOf("LD", "login_lock_time", "psw_fail_num_str"))
        val lockTime = pre.str("login_lock_time")?.toIntOrNull() ?: -1
        val attemptsLeft = pre.str("psw_fail_num_str")
        if (attemptsLeft == "0" && lockTime > 0) {
            throw RouterException(Kind.LOCKED, "Router login is locked for $lockTime s")
        }
        val res = post(
            mapOf(
                "isTest" to "false",
                "goformId" to "LOGIN",
                "password" to ZteAuth.loginPassword(password, pre.str("LD")),
            )
        )
        val result = res.str("result")
        if (result != "0") {
            this.password = null // never retry a rejected password on our own
            val left = attemptsLeft?.toIntOrNull()?.minus(1)
            val hint = if (left != null && left >= 0) " ($left attempts left)" else ""
            throw RouterException(Kind.BAD_PASSWORD, "Router rejected the password$hint (code $result)")
        }
    }

    private suspend fun get(fields: List<String>): JsonObject {
        val url = base.newBuilder()
            .addPathSegments("goform/goform_get_cmd_process")
            .addQueryParameter("isTest", "false")
            .addQueryParameter("cmd", fields.joinToString(","))
            .apply { if (fields.size > 1) addQueryParameter("multi_data", "1") }
            .addQueryParameter("_", clock().toString())
            .build()
        return execute(Request.Builder().url(url).headers().get().build())
    }

    private suspend fun post(form: Map<String, String>): JsonObject {
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        val url = base.newBuilder().addPathSegments("goform/goform_set_cmd_process").build()
        return execute(Request.Builder().url(url).headers().post(body).build())
    }

    private fun Request.Builder.headers() = this
        // The router ignores requests that don't look like they come from its own web page.
        .header("Referer", base.newBuilder().addPathSegment("index.html").build().toString())
        .header("X-Requested-With", "XMLHttpRequest")

    private suspend fun execute(request: Request): JsonObject = withContext(Dispatchers.IO) {
        val body = try {
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw RouterException(Kind.PROTOCOL, "HTTP ${resp.code}")
                resp.body?.string().orEmpty()
            }
        } catch (e: IOException) {
            throw RouterException(Kind.UNREACHABLE, "Can't reach the router at $base", e)
        }
        try {
            json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            throw RouterException(Kind.PROTOCOL, "Unexpected reply: ${body.take(120)}", e)
        }
    }

    /** Keeps the "stok" session cookie in memory for this driver's lifetime. */
    private class MemoryCookieJar : CookieJar {
        private val cookies = mutableMapOf<String, Cookie>()
        @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { this.cookies[it.name] = it }
        }
        @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies.values.toList()
    }
}
