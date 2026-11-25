package aarnav100.developer.biblescroll.core.content

import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers downloads that failed while the phone was offline and retries each one once when the connection comes
 * back, so a study or devotional just appears after reconnecting. Only the offline-to-online change triggers a retry:
 * a failure while online (server error, bad file) stays a failure with its own Try again, so this can't loop.
 */
internal class ReconnectRetry<K : Any>(private val retry: (K) -> Unit) {
    private val waiting: MutableSet<K> = ConcurrentHashMap.newKeySet()

    /**
     * Call whenever a network or integrity failure for [key] is resolved into a state. Returns true when it just
     * started a retry (show loading), false when the failure stands.
     */
    fun onFailure(key: K, online: Boolean): Boolean {
        if (!online) {
            waiting.add(key)
            return false
        }
        if (!waiting.remove(key)) return false
        retry(key)
        return true
    }
}
