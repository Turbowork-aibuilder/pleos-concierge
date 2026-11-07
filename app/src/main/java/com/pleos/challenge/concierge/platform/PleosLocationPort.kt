package com.pleos.challenge.concierge.platform

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.*
import com.pleos.challenge.concierge.core.AlertLifetime
import com.pleos.challenge.concierge.core.SourceMode
import com.pleos.challenge.concierge.sdk.*

/** SDK calls and lifecycle transitions are serialized on the application's main thread. */
class PleosLocationPort(private val context: Context, private val onProximity: (Boolean) -> Unit) : LocationPort {
    override val source = SourceMode.LIVE
    private val sdk = FusedLocation(context.applicationContext)
    private val gate = CallbackGate()
    private val main = Handler(Looper.getMainLooper())
    private var initialized = false
    private val life = AlertLifetime({ sdk.removeProximityAlert(it) }, {
        main.removeCallbacksAndMessages(null); sdk.release()
    }, { Log.e("PleosLocation", it) })
    fun start() { if (!gate.closed) initialized = runCatching { sdk.initialize() }.isSuccess }
    override fun isSupported(): Boolean = !gate.closed && initialized && runCatching { sdk.isSupported() }.getOrDefault(false)
    private fun permitted(): Boolean = context.checkSelfPermission("pleos.car.permission.FUSED_LOCATION") == PackageManager.PERMISSION_GRANTED
    private fun usable(alert: PickupAlert): Boolean = isSupported() && permitted() && alert.radiusMeters.toFloat().isFinite()
    override fun addAlert(alert: PickupAlert, callback: (Result<Unit>) -> Unit) {
        val token = if (usable(alert)) life.reserve(alert.id) else null
        if (token == null) { callback(Result.failure(IllegalStateException("Location unavailable/busy"))); return }
        gate.request<Unit>(callback = { result ->
            if (result.isFailure) life.cancel(alert.id)
            callback(result)
        }) { done ->
            try { sdk.addProximityAlert(request(alert), listener(token, done)) }
            catch (error: Exception) { life.failed(token); done(Result.failure(error)) }
        }
    }
    private fun request(alert: PickupAlert): ProximityAlertRequest =
        ProximityAlertRequest(alert.center.latitude, alert.center.longitude, alert.radiusMeters.toFloat(),
            alert.transitions.map { if (it == Transition.ENTER) ProximityTransition.ENTER else ProximityTransition.EXIT }.toSet())
    private fun listener(token: Long, done: (Result<Unit>) -> Unit): ProximityAlertListener = object : ProximityAlertListener {
        override fun onRegistered(alertId: String) { main.post {
            if (life.registered(token, alertId)) done(Result.success(Unit))
        } }
        override fun onTransition(event: ProximityAlertEvent) { gate.dispatch {
            if (life.matches(token, event.alertId) && permitted()) onProximity(event.transition == ProximityTransition.ENTER)
        } }
        override fun onError(error: Exception) { main.post {
            life.failed(token)
            gate.dispatch { onProximity(false); done(Result.failure(error)) }
        } }
    }
    override fun removeAlert(id: String, callback: (Result<Unit>) -> Unit) {
        onProximity(false)
        callback(life.cancel(id))
    }
    fun close() {
        if (gate.closed) return
        initialized = false
        gate.close()
        // A bounded cleanup deadline is reported as unconfirmed, never as successful removal.
        main.postDelayed({ life.expire() }, 15_000)
        life.close()
    }
}
