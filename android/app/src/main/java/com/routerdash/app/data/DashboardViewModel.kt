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

/** Progress of a band-lock change, shown at the top of the Bands screen. */
sealed interface BandAction {
    data object Idle : BandAction
    data object Applying : BandAction
    data class WaitingForInternet(val seconds: Int, val undoAt: Int?) : BandAction
    data class Done(val message: String, val before: SignalSnapshot?) : BandAction
    data class Undone(val message: String) : BandAction
    data class Failed(val message: String) : BandAction
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

    // ---- band locking ----

    /** The driver the poller is using, so changes share its session. */
    private var activeDriver: ZteMc888Driver? = null
    private var latest: SignalSnapshot? = null

    private val _band = MutableStateFlow<BandAction>(BandAction.Idle)
    val band: StateFlow<BandAction> = _band.asStateFlow()

    fun dismissBandResult() {
        if (_band.value !is BandAction.Applying && _band.value !is BandAction.WaitingForInternet) {
            _band.value = BandAction.Idle
        }
    }

    fun backToAuto() = applyBands(
        ZteMc888Driver.SUPPORTED_LTE_BANDS.toSet(), ZteMc888Driver.SUPPORTED_NR_BANDS.toSet(), autoUndo = false,
    )

    /**
     * Sends the band lock, then waits for the internet to come back. With [autoUndo], if it isn't
     * back within [UNDO_AFTER_S] seconds, the previous bands are restored. The wait asks the router
     * directly, so it keeps working if the user leaves the app.
     */
    fun applyBands(lte: Set<Int>, nr: Set<String>, autoUndo: Boolean) {
        val d = activeDriver ?: run { _band.value = BandAction.Failed("Not connected to the router"); return }
        if (_band.value is BandAction.Applying || _band.value is BandAction.WaitingForInternet) return
        val before = latest
        val prevLte = before?.lteBandsAllowed?.toSet().takeUnless { it.isNullOrEmpty() }
            ?: ZteMc888Driver.SUPPORTED_LTE_BANDS.toSet()
        val prevNr = before?.nrBandsAllowed?.toSet().takeUnless { it.isNullOrEmpty() }
            ?: ZteMc888Driver.SUPPORTED_NR_BANDS.toSet()
        val nrClean = nr.map { it.removePrefix("n") }.toSet()

        viewModelScope.launch {
            _band.value = BandAction.Applying
            try {
                if (lte != prevLte) d.setLteBands(lte)
                if (nrClean != prevNr) d.setNrBands(nrClean)
            } catch (e: RouterException) {
                _band.value = BandAction.Failed(e.message ?: "The router refused the change")
                return@launch
            } catch (e: IllegalArgumentException) {
                _band.value = BandAction.Failed(e.message ?: "Invalid band choice")
                return@launch
            }

            val start = System.currentTimeMillis()
            delay(8_000) // give the router time to drop and re-register before checking
            while (true) {
                val secs = ((System.currentTimeMillis() - start) / 1000).toInt()
                val connected = try { d.signal().connected } catch (e: RouterException) { false }
                if (connected) {
                    _band.value = BandAction.Done("Done. Internet is back after $secs s.", before)
                    return@launch
                }
                if (secs >= UNDO_AFTER_S) {
                    if (autoUndo) {
                        try {
                            d.setLteBands(prevLte)
                            d.setNrBands(prevNr)
                            _band.value = BandAction.Undone("No internet after ${UNDO_AFTER_S / 60} minutes, so the previous bands were put back.")
                        } catch (e: RouterException) {
                            _band.value = BandAction.Failed("No internet, and undoing failed: ${e.message}. Tap Back to auto.")
                        }
                    } else {
                        _band.value = BandAction.Done("Applied, but the internet isn't back yet. Tap Back to auto if it stays down.", before)
                    }
                    return@launch
                }
                _band.value = BandAction.WaitingForInternet(secs, if (autoUndo) UNDO_AFTER_S else null)
                delay(3_000)
            }
        }
    }

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
        activeDriver = driver
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
                latest = snap
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
        const val UNDO_AFTER_S = 120
    }
}
