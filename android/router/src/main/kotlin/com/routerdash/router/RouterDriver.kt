package com.routerdash.router

/**
 * What the app needs from a router. Each supported model implements this;
 * the screens only ever talk to this interface.
 */
interface RouterDriver {
    /** Checks the router is there and reads what it shares without a password. */
    suspend fun preLogin(): PreLoginInfo

    /** Log in with the admin password. Throws [RouterException]. */
    suspend fun login(password: String)

    suspend fun info(): RouterInfo

    /** Current signal. Logs in again by itself if the session expired. */
    suspend fun signal(): SignalSnapshot

    /** Allow only these 4G bands (band numbers, e.g. 3, 7). Must not be empty. */
    suspend fun setLteBands(bands: Collection<Int>)

    /** Allow only these 5G bands ("78" or "n78"). Must not be empty. */
    suspend fun setNrBands(bands: Collection<String>)
}

class RouterException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    enum class Kind {
        /** Not on the router's network, wrong address, or router off. */
        UNREACHABLE,
        /** Router rejected the password. Don't retry automatically: it locks after a few tries. */
        BAD_PASSWORD,
        /** Router has temporarily locked the admin login. */
        LOCKED,
        /** No password stored yet. */
        NOT_LOGGED_IN,
        /** The router answered with something we didn't expect. */
        PROTOCOL,
    }
}
