package com.pleos.challenge.concierge

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.time.ZoneId

class NavigationPanel(context: Context, compact: Boolean, private val font: Typeface, private val numbers: Typeface = font) : LinearLayout(context) {
    private val map: NavigationMapView = NavigationMapView(context)
    private val arrow: TextView = text(if (compact) 17f else 25f, NAVY, font).apply {
        gravity = Gravity.CENTER; background = rounded(MINT, 10)
    }
    private val distance: TextView = text(if (compact) 16f else 24f, INK, numbers)
    private val road: TextView = text(if (compact) 13f else 16f, MUTED, font)
    private val place: TextView = text(if (compact) 13f else 16f, INK, font)
    private val clock: TextView = text(if (compact) 13f else 16f, MINT, numbers)
    private val detail: TextView = text(if (compact) 13f else 16f, MUTED, font)
    init {
        orientation = VERTICAL
        background = GradientDrawable().apply { setColor(NAVY); cornerRadius = dp(20).toFloat() }
        clipToOutline = true
        val stage = FrameLayout(context)
        stage.addView(map, FrameLayout.LayoutParams(-1, -1))
        stage.addView(turnCard(if (compact) 30 else 46), FrameLayout.LayoutParams(-2, -2).apply { setMargins(dp(12), dp(10), dp(12), 0) })
        addView(stage, LayoutParams(-1, 0, 1f))
        addView(arrivalBar(if (compact) 7 else 11), LayoutParams(-1, -2))
    }
    private fun turnCard(icon: Int): LinearLayout = LinearLayout(context).apply {
        tag = "nav-turn-card"; gravity = Gravity.CENTER_VERTICAL
        background = rounded(Color.argb(225, 14, 40, 53), 14).apply { setStroke(dp(1), Color.argb(70, 163, 247, 223)) }
        setPadding(dp(8), dp(8), dp(14), dp(8))
        addView(arrow, LayoutParams(dp(icon), dp(icon)))
        addView(LinearLayout(context).apply {
            orientation = VERTICAL; setPadding(dp(10), 0, 0, 0)
            addView(distance); addView(road.apply { setPadding(0, dp(3), 0, 0) })
        })
    }
    private fun arrivalBar(vertical: Int): LinearLayout = LinearLayout(context).apply {
        tag = "nav-arrival-bar"; gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Color.rgb(13, 37, 50))
        setPadding(dp(16), dp(vertical), dp(16), dp(vertical))
        addView(place, LayoutParams(0, -2, 1f))
        addView(clock); addView(detail)
    }
    fun update(route: DemoRoute, now: Long = System.currentTimeMillis()): Unit {
        arrow.text = route.maneuver.arrow
        distance.text = if (route.arrived) "목적지 도착" else "${route.nextMeters} m"
        distance.typeface = if (route.arrived) font else numbers
        road.text = "${route.nextRoad} ${route.maneuver.verb}"
        road.visibility = if (route.arrived) GONE else VISIBLE
        place.text = route.destination
        clock.text = "도착 ${route.arrivalClock(now, ZoneId.systemDefault())}"
        detail.text = "  ·  ${route.minutes}분  ·  ${route.distanceText}"
        detail.visibility = if (route.arrived) GONE else VISIBLE
        map.update(route.progress)
    }
    fun stop(): Unit { map.stop() }
    private fun text(size: Float, color: Int, face: Typeface): TextView = TextView(context).apply {
        textSize = size; setTextColor(color); typeface = face; includeFontPadding = false
    }
    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private companion object {
        val NAVY: Int = Color.rgb(10, 31, 43)
        val INK: Int = Color.rgb(225, 247, 242)
        val MUTED: Int = Color.rgb(168, 195, 205)
        val MINT: Int = Color.rgb(163, 247, 223)
    }
}
