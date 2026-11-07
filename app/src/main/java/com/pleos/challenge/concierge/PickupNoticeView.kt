package com.pleos.challenge.concierge

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Local demo readiness notice; no ordering or payment service is invoked. */
class PickupNoticeView(
    context: Context,
    allowed: () -> Boolean,
    makeLabel: (String, Float) -> TextView,
    makeButton: (String, () -> Unit) -> Button
) : LinearLayout(context) {
    val trigger: Button
    init {
        orientation = VERTICAL
        val notice = LinearLayout(context).apply {
            orientation = VERTICAL
            tag = "pickup-notice"
            visibility = View.GONE
            addView(makeLabel("A-12번", 22f))
            addView(makeLabel("주문하신 음식이 준비되었습니다.\n수령해 주세요.", 14f).apply {
                setLineSpacing(0f, 1.35f)
            })
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        trigger = makeButton("픽업 안내 확인") {
            if (isShown && allowed()) {
                getChildAt(0).visibility = View.GONE
                notice.visibility = View.VISIBLE
            }
        }
        addView(trigger)
        addView(notice)
    }
}
