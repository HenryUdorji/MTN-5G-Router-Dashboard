package com.routerdash.app.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routerdash.router.PreLoginInfo
import com.routerdash.router.RouterException
import com.routerdash.router.RouterException.Kind
import com.routerdash.router.SignalSnapshot
import com.routerdash.router.zte.ZteMc888Driver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One poll's worth of the values the charts draw. */
data class SignalPoint(
    val timeMs: Long,
    val lteRsrp: Double?, val lteRsrq: Double?, val lteSinr: Double?,
    val nrRsrp: Double?, val nrRsrq: Double?, val nrSinr: Double?,
) {
    companion object {
        fun of(s: SignalSnapshot) = SignalPoint(
            s.timestampMs,
            s.lte?.rsrp, s.lte?.rsrq, s.lte?.sinr,
            s.nr?.rsrp, s.nr?.rsrq, s.nr?.sinr,
        )
    }
}

/** Result of looking for the router before sign-in. */
sealed interface RouterProbe {
    data object Checking : RouterProbe
    data class Found(val host: String, val info: PreLoginInfo) : RouterProbe
    data class NotFound(val host: String, val message: String) : RouterProbe
}

sealed interface DashState {
    data object Loading : DashState
    data class NeedsSetup(val host: String, val error: String?) : DashState
    data class Live(
        val host: String,
        val snapshot: SignalSnapshot?,
        val history: List<SignalPoint>,
        /** Set when the latest poll failed; the last good snapshot stays on screen. */
        val problem: String?,
    ) : DashState
}

/**
 * Polls the router while the app is on screen (stops 5 s after it leaves),
 * keeping the last hour of SINR readings for the chart.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(private val settings: SettingsStore) : ViewModel() {

    private val setupError = MutableStateFlow<String?>(null)
    private val _connecting = MutableStateFlow(false)
    val connecting: StateFlow<Boolean> = _connecting.asStateFlow()

    private val _probe = MutableStateFlow<RouterProbe>(RouterProbe.Checking)
    val probe: StateFlow<RouterProbe> = _probe.asStateFlow()

    /** Looks for the router at [host] without signing in (model, firmware, tries left). */
    fun probe(host: String) {
        val h = cleanHost(host)
        viewModelScope.launch {
            _probe.value = RouterProbe.Checking
            _probe.value = try {
                RouterProbe.Found(h, ZteMc888Driver("http://$h").preLogin())
            } catch (e: RouterException) {
                RouterProbe.NotFound(h, e.message ?: "Router not found")
            }
        }
    }

    val state: StateFlow<DashState> = settings.settings
        .flatMapLatest { s ->
            if (s.password == null) setupError.map { DashState.NeedsSetup(s.host, it) }
            else poll(s.host, s.password)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashState.Loading)

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    /** Forget the saved password and go back to the login screen. */
    fun signOut() {
        viewModelScope.launch {
            setupError.value = null
            settings.clearPassword()
        }
    }

    /** Called from the setup screen. Only saves the password once the router accepts it. */
    fun connect(host: String, password: String) {
        val target = cleanHost(host)
        viewModelScope.launch {
            _connecting.value = true
            setupError.value = null
            try {
                ZteMc888Driver("http://$target").login(password)
                settings.save(target, password)
            } catch (e: RouterException) {
                setupError.value = e.message
                probe(target) // refresh "tries left"
            } finally {
                _connecting.value = false
            }
        }
    }

    private fun poll(host: String, password: String): Flow<DashState> = flow {
        val driver = ZteMc888Driver("http://$host")
        val history = ArrayDeque<SignalPoint>()
        var last: SignalSnapshot? = null
        var loggedIn = false
        emit(DashState.Live(host, null, emptyList(), null))
        while (true) {
            try {
                if (!loggedIn) {
                    driver.login(password)
                    loggedIn = true
                }
                val snap = driver.signal()
                last = snap
                history.addLast(SignalPoint.of(snap))
                while (history.size > HISTORY_POINTS) history.removeFirst()
                emit(DashState.Live(host, snap, history.toList(), null))
            } catch (e: RouterException) {
                when (e.kind) {
                    Kind.BAD_PASSWORD, Kind.LOCKED -> {
                        // Password changed on the router, or login locked: stop and ask again.
                        // Never keep retrying, or the router locks the admin login.
                        setupError.value = e.message
                        settings.clearPassword()
                        return@flow
                    }
                    Kind.NOT_LOGGED_IN -> loggedIn = false
                    else -> emit(DashState.Live(host, last, history.toList(), e.message))
                }
            }
            delay(POLL_MS)
        }
    }

    private fun cleanHost(host: String) =
        host.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')

    companion object {
        const val POLL_MS = 3_000L
        const val HISTORY_POINTS = 1_200 // one hour at 3 s
    }
}
