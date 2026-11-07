package com.pleos.challenge.concierge.core

/** Main-thread confined. Tracks cancelled registrations until their SDK completion. */
class AlertLifetime(
    private val removeRemote: (String) -> Unit,
    private val releaseSdk: () -> Unit,
    private val report: (String) -> Unit,
) {
    private data class Entry(val local: String, var remote: String? = null, var accepting: Boolean = true)
    private val entries = mutableMapOf<Long, Entry>()
    private var next = 0L
    private var closing = false
    private var released = false
    fun reserve(id: String): Long? {
        if (closing || entries.size >= 8) return null
        if (entries.values.any { it.local == id && it.accepting }) return null
        val token = ++next
        entries[token] = Entry(id)
        return token
    }
    private fun remove(id: String): Result<Unit> = runCatching { removeRemote(id) }
        .onFailure { report("proximity cleanup failed") }
    fun registered(token: Long, remote: String): Boolean {
        if (released) { report("late registration after release; remote cleanup unconfirmed"); return false }
        val entry = entries[token]
        if (entry?.accepting == true) { entry.remote = remote; return true }
        remove(remote)
        entries.remove(token)
        finish()
        return false
    }
    fun matches(token: Long, remote: String): Boolean = !closing && entries[token]?.remote == remote
    fun failed(token: Long) {
        val entry = entries.remove(token)
        entry?.remote?.let { remove(it) }
        finish()
    }
    fun cancel(id: String): Result<Unit> {
        var result = Result.success(Unit)
        entries.filterValues { it.local == id }.forEach { (token, entry) ->
            entry.accepting = false
            entry.remote?.let { remote ->
                val cleanup = remove(remote)
                if (cleanup.isFailure) result = cleanup
                entries.remove(token)
            }
        }
        finish()
        return result
    }
    fun close() {
        closing = true
        entries.values.map { it.local }.toList().forEach { cancel(it) }
        finish()
    }
    fun expire() {
        if (!closing || released) return
        if (entries.isNotEmpty()) report("registration deadline; remote cleanup unconfirmed")
        entries.clear()
        finish()
    }
    private fun finish() {
        if (!closing || released || entries.isNotEmpty()) return
        released = true
        runCatching { releaseSdk() }.onFailure { report("location release failed") }
    }
}
