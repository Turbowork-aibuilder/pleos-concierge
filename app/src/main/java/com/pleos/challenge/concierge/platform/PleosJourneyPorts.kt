package com.pleos.challenge.concierge.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import com.pleos.challenge.concierge.core.SourceMode
import com.pleos.challenge.concierge.sdk.*

class PleosNavigationPort(context: Context) : NavigationPort {
    override val source = SourceMode.LIVE
    private val sdk = NaviHelper(context.applicationContext)
    private val gate = CallbackGate()
    private var ready = false
    private var pending: ((Result<Unit>) -> Unit)? = null
    private val listener = object : NaviHelperEventListener {
        override fun onRoutingCompleted() { gate.dispatch { pending?.invoke(Result.success(Unit)); pending = null } }
        override fun onError(errorCode: NaviErrorCode) { gate.dispatch {
            pending?.invoke(Result.failure(IllegalStateException("Route failed"))); pending = null
        } }
    }
    fun start() {
        runCatching { sdk.addListener(listener); sdk.initialize({ gate.dispatch { ready = true } }, { gate.dispatch { ready = false } }) }
    }
    override fun requestRoute(request: RouteRequest, callback: (Result<Unit>) -> Unit) {
        if (!ready || pending != null) { callback(Result.failure(IllegalStateException("Navigation unavailable/busy"))); return }
        gate.request<Unit>(15_000, { result ->
            pending = null
            if (result.isFailure) ready = false // No next route after an uncorrelated timeout; reconnect explicitly.
            callback(result)
        }) { done ->
            pending = done
            val destination = RouteInfo(latitude = request.destination.latitude, longitude = request.destination.longitude,
                poiName = "설정한 목적지", poiId = "", address = "", poiSubId = "")
            if (request.waypoints.isEmpty()) sdk.requestRoute(destination)
            else sdk.requestRouteWithWaypoints(RouteWithWaypointsRequest(destination, request.waypoints.mapIndexed { index, p ->
                RequestWaypointInfo(latitude = p.latitude, longitude = p.longitude, poiName = "경유지 ${index+1}",
                    waypointIndex = if (index == 0) WaypointIndex.FIRST else WaypointIndex.SECOND,
                    poiId = "", address = "", poiSubId = "")
            }))
        }
    }
    fun close() { ready = false; gate.close(); pending = null; runCatching { sdk.removeListener(listener); sdk.release() } }
}

class PleosVoicePort(context: Context) : VoicePort {
    override val source = SourceMode.LIVE
    private val sdk = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val gate = CallbackGate()
    private var ready = false
    private var pending: ((Result<Unit>) -> Unit)? = null
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change -> if (change < 0) gate.dispatch { runCatching { sdk.stop() }; finish(Result.failure(IllegalStateException("Audio focus lost"))) } }.build()
    private val listener = object : EventListener {
        override fun onStart() = Unit
        override fun onReady() = Unit
        override fun onDone() { gate.dispatch { finish(Result.success(Unit)) } }
        override fun onError(errorType: ErrorType) { gate.dispatch { finish(Result.failure(IllegalStateException("TTS failed"))) } }
        override fun onStop() { gate.dispatch { finish(Result.failure(IllegalStateException("TTS stopped"))) } }
        override fun onUpdatedRms(rms: Double) = Unit
    }
    fun start() {
        runCatching {
            sdk.initialize(object : OnInitializeListener {
                override fun onSuccess() { gate.dispatch { ready = runCatching { sdk.setAudioAttributes(attributes); sdk.addEventListener(listener) }.isSuccess } }
                override fun onFailure(errorType: ErrorType) { gate.dispatch { ready = false } }
            })
        }
    }
    private fun finish(result: Result<Unit>) { audio.abandonAudioFocusRequest(focus); pending?.invoke(result); pending = null }
    override fun speakOnDevice(text: String, callback: (Result<Unit>) -> Unit) {
        if (!ready || pending != null) { callback(Result.failure(IllegalStateException("TTS unavailable/busy"))); return }
        gate.request<Unit>(12_000, { result ->
            if (result.isFailure) { ready = false; runCatching { sdk.stop() } }
            audio.abandonAudioFocusRequest(focus); pending = null; callback(result)
        }) { done ->
            check(audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            pending = done; sdk.speak(text, false)
        }
    }
    fun close() { ready = false; gate.close(); pending = null; audio.abandonAudioFocusRequest(focus); runCatching { sdk.stop(); sdk.removeEventListener(listener); sdk.release() } }
}
