package com.pleos.challenge.concierge

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.content.pm.PackageManager
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.view.animation.PathInterpolator
import android.animation.ValueAnimator
import android.widget.*
import com.pleos.challenge.concierge.core.*
import com.pleos.challenge.concierge.sdk.*

/** A Telemetry: native layout fitted to the allocated vehicle app window. No WebView. */
class MainActivity : Activity() {
    private val ink = Color.rgb(238,245,251)
    private val muted = Color.rgb(168,187,207)
    private val mint = Color.rgb(163,247,223)
    private lateinit var font: Typeface
    private lateinit var displayFont: Typeface
    private lateinit var numberFont: Typeface
    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private lateinit var status: TextView
    private lateinit var badge: TextView
    private var speedText: TextView? = null
    private var batteryText: TextView? = null
    private var safetyText: TextView? = null
    private var brakeText: TextView? = null
    private var navigationPanel: NavigationPanel? = null
    private var permissionButton: Button? = null
    private var controller: AppController? = null
    private var shownMode: AppMode? = null
    private val actionButtons = mutableListOf<View>()
    private var destinationDialog: AlertDialog? = null
    private var demoDialog: AlertDialog? = null
    private var orbitTab = 0
    private var orbitDock = 0
    private val requestedValues = mutableMapOf<Control, Double>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        font = Typeface.createFromAsset(assets, "fonts/Pretendard-SemiBold.otf")
        displayFont = Typeface.createFromAsset(assets, "fonts/Geist-Regular.ttf")
        numberFont = Typeface.createFromAsset(assets, "fonts/JetBrainsMono-Regular.ttf")
        orbitTab = savedInstanceState?.getInt("orbitTab",0) ?: 0
        orbitDock = savedInstanceState?.getInt("orbitDock",0) ?: 0
        Control.entries.forEach { type ->
            if (savedInstanceState?.containsKey(type.name) == true) requestedValues[type] = savedInstanceState.getDouble(type.name)
        }
        root = column().apply {
            background = OrbitAmbientDrawable()
            setPadding(dp(16),dp(18),dp(16),dp(16))
            setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(dp(16)+insets.systemWindowInsetLeft,dp(18)+insets.systemWindowInsetTop,
                    dp(16)+insets.systemWindowInsetRight,dp(16)+insets.systemWindowInsetBottom)
                insets
            }
        }
        val top = row()
        top.addView(column().apply {
            addView(label("concierge",26f,ink).apply { typeface = displayFont
                if (BuildConfig.DEMO_ONLY) { contentDescription="시연 설정 열기";setOnLongClickListener { showDemoSettings();true } }
            })
        }, LinearLayout.LayoutParams(0,-2,1f))
        badge = label(if (BuildConfig.DEMO_ONLY) "DEMO · 가상 체험" else "LIVE · 확인 중",14f,muted)
        badge.visibility=View.GONE; root.addView(top)
        if (BuildConfig.DEMO_ONLY) {
            // Presenter tools stay outside the driver's normal screen.
            badge.minHeight = dp(44)
            badge.gravity = Gravity.CENTER_VERTICAL
            badge.contentDescription = null
            badge.setOnLongClickListener { showDemoSettings(); true }
        }
        body = column(); root.addView(body,LinearLayout.LayoutParams(-1,0,1f))
        status = label("",14f,muted).apply { setPadding(0,dp(10),0,0) ;visibility=View.GONE }; root.addView(status)
        setContentView(root)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("orbitTab", orbitTab); outState.putInt("orbitDock", orbitDock)
        requestedValues.forEach { (key, value) -> outState.putDouble(key.name, value) }
        super.onSaveInstanceState(outState)
    }
    override fun onStart() {
        super.onStart()
        shownMode = null
        controller = AppController(this, BuildConfig.DEMO_ONLY, ::refresh).also { it.start() }
        refresh()
    }
    override fun onStop() {
        navigationPanel?.stop()
        cancelMotion(root)
        demoDialog?.dismiss(); demoDialog = null
        destinationDialog?.dismiss(); destinationDialog = null; controller?.close(); controller = null; super.onStop()
    }
    override fun onDestroy() {
        demoDialog?.dismiss(); demoDialog = null
        destinationDialog?.dismiss(); destinationDialog = null
        controller?.close(); controller = null
        super.onDestroy()
    }
    private fun showDemoSettings() {
        if (!BuildConfig.DEMO_ONLY || controller == null || demoDialog?.isShowing == true) return
        destinationDialog?.dismiss(); destinationDialog = null
        // Each item names the vehicle conditions it feeds in (the long-form shows this input on screen).
        val items = arrayOf("정차 · P단", "주행 · 60 km/h", "도착 · 목적지 접근 + 정차", "신호 없음")
        val title = column().apply {
            setPadding(dp(18),dp(16),dp(18),dp(6))
            addView(label("PRESENTER",10f,muted).apply { typeface = displayFont; letterSpacing = .18f })
            addView(label("가상 차량 상태",17f,ink).apply { setPadding(0,dp(6),0,0) })
        }
        val rows = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup) =
                label(items[position],14f,if (position == 2) mint else ink).apply {
                    // Background first: an InsetDrawable resets the padding to its insets.
                    background = InsetDrawable(shape(Color.argb(150,20,48,66),12),dp(10),dp(3),dp(10),dp(3))
                    setPadding(dp(24),dp(14),dp(24),dp(14))
                }
        }
        demoDialog = AlertDialog.Builder(this)
            .setCustomTitle(title)
            .setAdapter(rows) { _, index -> controller?.selectScenario(index) }
            .create().also { dialog ->
                dialog.setOnDismissListener { demoDialog = null }
                dialog.show()
                // App-styled compact panel instead of the stock grey sheet; tapping outside closes it.
                dialog.window?.apply {
                    setBackgroundDrawable(GradientDrawable(GradientDrawable.Orientation.TL_BR,
                        intArrayOf(Color.argb(250,28,58,76),Color.argb(250,13,32,48))).apply {
                        cornerRadius = dp(20).toFloat(); setStroke(dp(1),Color.argb(95,185,231,243)) })
                    setLayout(dp(300),ViewGroup.LayoutParams.WRAP_CONTENT)
                    setDimAmount(.35f)
                }
                dialog.listView?.apply { divider = null; dividerHeight = 0; selector = ColorDrawable(Color.TRANSPARENT); setPadding(0,dp(2),0,dp(12)) }
            }
    }
    private fun refresh() {
        val app = controller ?: return
        val decision = app.decision()
        if (!decision.canControl) { destinationDialog?.dismiss(); destinationDialog = null }
        if (shownMode != decision.mode) {
            if (decision.mode == AppMode.SMART_PICKUP) orbitTab = 2
            shownMode = decision.mode; render(decision.mode)
        }
        badge.text = if (BuildConfig.DEMO_ONLY) "DEMO · 가상 데이터" else if (app.snapshot.connected) "LIVE · 차량 연결" else "LIVE · 연결 없음"
        speedText?.text = app.snapshot.speed?.takeIf { app.now()-it.observedAtMs in 0..999 }?.value?.let { speedKmH(it) }?.let { "%.0f".format(it) } ?: "—"
        batteryText?.text = app.snapshot.soc?.value?.let { "$it%" } ?: "—"
        brakeText?.text = when (app.brake) { true -> "브레이크 작동"; false -> "브레이크 해제"; null -> "브레이크 정보 없음" }
        safetyText?.text = decision.reason
        app.navigationState()?.let { navigationPanel?.update(it) }
        permissionButton?.isEnabled = app.snapshot.ux?.let {
            app.now()-it.observedAtMs in 0..999 && !it.value.requiresOptimization && it.value.activeRestrictions == 0
        } == true
        actionButtons.forEach { it.isEnabled = decision.canControl; it.alpha = if (it.isEnabled) 1f else 0.45f }
        status.text = if (decision.mode == AppMode.ECO_COACHING) AppCopy.DRIVING_STATUS else app.message
        status.visibility = if (decision.mode == AppMode.ECO_COACHING) View.GONE else View.VISIBLE
    }
    private fun render(mode: AppMode) {
        navigationPanel?.stop(); navigationPanel = null
        cancelMotion(body)
        body.removeAllViews(); actionButtons.clear()
        speedText = null; batteryText = null; brakeText = null; permissionButton = null; safetyText = null
        if (BuildConfig.DEMO_ONLY && mode == AppMode.ECO_COACHING) { renderNavigation(); return }
        if (mode == AppMode.CABIN_CARE || mode == AppMode.SMART_PICKUP) {
            renderOrbit(mode)
            return
        }
        val content = column().apply { setPadding(0,dp(24),0,0) }
        body.addView(ScrollView(this).apply { isFillViewport = true; addView(content) },LinearLayout.LayoutParams(-1,-1))
        content.addView(label(if (mode == AppMode.ECO_COACHING) AppCopy.DRIVING_TITLE else "차량 정보를 기다리고 있어요.",28f,ink))
        safetyText = label("",16f,mint).also { it.setPadding(0,dp(12),0,dp(20)); content.addView(it) }
        if (mode == AppMode.UNAVAILABLE) {
            content.addView(card().apply {
                addView(label(AppCopy.UNAVAILABLE,16f,muted))
                if (!BuildConfig.DEMO_ONLY) addView(button("권한 확인 및 다시 연결") { requestVehiclePermissions() }.also { permissionButton = it })
            })
        } else {
            val metrics = row()
            metrics.addView(card().apply { addView(label("현재 속도 · km/h",14f,muted)); speedText = label("—",40f,ink); addView(speedText) },weighted())
            metrics.addView(card().apply { addView(label("배터리 잔량",14f,muted)); batteryText = label("—",40f,mint); addView(batteryText) },weighted())
            content.addView(metrics)
            brakeText = label("",16f,muted).also { content.addView(it) }
        }
    }
    private fun renderOrbit(mode: AppMode) {
        val heading = column().apply { setPadding(0,dp(12),0,0) }
        heading.addView(label("TELEMETRY / 01",12f,muted).apply { typeface=numberFont;letterSpacing=.12f;setPadding(0,0,0,dp(8)) })
        if (orbitTab!=2) heading.addView(label("Ready for",32f,ink).apply { typeface=displayFont })
        if (orbitTab!=2) heading.addView(label("your next.",32f,mint).apply { typeface=displayFont })

        val visual = column().apply { gravity=Gravity.CENTER }
        if (BuildConfig.DEMO_ONLY && mode == AppMode.SMART_PICKUP) {
            // Arrival: the map takes the whole visual column instead of sharing it with the vehicle.
            navigationPanel = NavigationPanel(this,true,font,numberFont).also { visual.addView(it,LinearLayout.LayoutParams(-1,0,1f)) }
        } else {
            val vehicle = TelemetryVehicleView(this)
            visual.addView(if (BuildConfig.DEMO_ONLY) AssistantStage(this,vehicle,font) else vehicle,LinearLayout.LayoutParams(-1,0,1f))
        }

        val panel = card().apply {
            tag="telemetry-panel";setPadding(dp(14),dp(12),dp(14),dp(10));elevation=dp(6).toFloat()
            background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.argb(235,38,70,82),Color.argb(232,17,42,56))).apply {
                cornerRadius=dp(22).toFloat();setStroke(dp(1),Color.argb(80,183,222,229))
            }
        }
        panel.addView(label("COMFORT CONTROL",12f,muted).apply { typeface=numberFont;letterSpacing=.10f;setPadding(dp(4),0,0,dp(8)) })
        val tabs=row().apply { background=shape(Color.argb(180,10,34,47),14);setPadding(dp(4),dp(4),dp(4),dp(4)) }
        listOf("캐빈", "시트", "도착").forEachIndexed { index, title ->
            val tab=button(title) {
                if (controller?.decision()?.canControl == true) { orbitTab=index;render(mode);refresh() }
            }.apply {
                isSelected=orbitTab==index;contentDescription="$title 탭";setPadding(0,0,0,0)
                background=if (isSelected) shape(Color.rgb(62,98,111),10) else shape(Color.TRANSPARENT,10)
                setTextColor(if (isSelected) ink else muted)
            }
            actionButtons.add(tab);tabs.addView(tab,LinearLayout.LayoutParams(0,dp(48),1f))
        }
        panel.addView(tabs)
        val settings=column().apply { setPadding(dp(4),dp(10),dp(4),0) }
        settings.addView(label(listOf("캐빈 케어","시트 컴포트","도착 준비")[orbitTab],18f,ink))
        when (orbitTab) {
            0 -> {
                settings.addView(control("실내 온도",Control.CABIN_TEMPERATURE,22.0,1.0,"°C"))
                settings.addView(control("바람 세기",Control.FAN_SPEED,2.0,1.0,"단계"))
            }
            1 -> {
                settings.addView(control("시트 온열",Control.SEAT_HEAT,0.0,1.0,"단계"))
                settings.addView(control("시트 통풍",Control.SEAT_VENTILATION,0.0,1.0,"단계"))
                settings.addView(control("스티어링 온열",Control.STEERING_HEAT,0.0,1.0,"단계"))
            }
            2 -> {
                addArrivalSettings(settings, mode)
            }
        }
        panel.addView(ScrollView(this).apply { isFillViewport=false;addView(settings);isVerticalScrollBarEnabled=true;isVerticalFadingEdgeEnabled=true;setFadingEdgeLength(dp(16)) },LinearLayout.LayoutParams(-1,0,1f))
        val energy=row().apply {
            gravity=Gravity.END or Gravity.TOP
            addView(column().apply {
                addView(label("BATTERY",12f,muted).apply { typeface=numberFont;letterSpacing=.1f })
                batteryText=label("—",28f,ink).also { it.typeface=numberFont;it.setPadding(0,dp(6),0,0);addView(it) }
            },LinearLayout.LayoutParams(dp(106),-2))
            addView(column().apply {
                addView(label("GEAR",12f,muted).apply { typeface=numberFont;letterSpacing=.1f })
                addView(label("P",28f,mint).apply { typeface=numberFont;setPadding(0,dp(6),0,0) })
            })
        }
        body.addView(TelemetryStage(this,visual,panel,energy,heading),LinearLayout.LayoutParams(-1,0,1f))
        enter(visual,0);enter(panel,80);enter(energy,160)
    }
    private fun renderNavigation(): Unit {
        val metrics = row().apply { setPadding(dp(6),dp(12),dp(6),dp(12)) }
        speedText = label("—",38f,ink).apply { typeface = numberFont }
        batteryText = label("—",14f,muted).apply { typeface = numberFont; gravity = Gravity.END }
        metrics.addView(speedText)
        metrics.addView(label(" km/h",16f,muted),LinearLayout.LayoutParams(0,-2,1f))
        metrics.addView(batteryText)
        body.addView(metrics)
        navigationPanel = NavigationPanel(this,false,font,numberFont).also { body.addView(it,LinearLayout.LayoutParams(-1,0,1f)) }
    }
    private fun addArrivalSettings(settings: LinearLayout, mode: AppMode) {
        settings.addView(label(if (mode==AppMode.SMART_PICKUP) "목적지 주변에 도착했어요." else "목적지와 경유지를 설정하세요.",14f,muted).apply { setPadding(0,dp(8),0,dp(4)) })
        if (BuildConfig.DEMO_ONLY && mode == AppMode.SMART_PICKUP) {
            val pickup = PickupNoticeView(this,
                { controller?.canShowLocalPickup() == true },
                { text, size -> label(text, size, mint) }, ::button)
            settings.addView(pickup)
            actionButtons.add(pickup.trigger)
        } else {
            settings.addView(button("목적지 · 경유지 설정") { showDestination() }.also { actionButtons.add(it) })
        }
        settings.addView(control("창문 위치",Control.WINDOW_POSITION,0.0,10.0,""))
    }
    private fun control(title: String, type: Control, initial: Double, step: Double, unit: String): View {
        var requested = requestedValues.getOrPut(type) { initial }
        val layout = column().apply { setPadding(0,dp(8),0,dp(6)) }
        layout.addView(label("$title · 요청값",16f,muted))
        val line = row()
        val value = label("${requested.toInt()} $unit",26f,ink).apply { gravity = Gravity.CENTER; tag = "request-${type.name}"; typeface = numberFont }
        fun send(delta: Double) {
            if (controller?.decision()?.canControl != true) return
            val next = requested + delta
            requested = if (BuildConfig.DEMO_ONLY) when(type) {
                Control.CABIN_TEMPERATURE -> next.coerceIn(18.0,28.0)
                Control.FAN_SPEED -> next.coerceIn(0.0,7.0)
                Control.SEAT_HEAT, Control.SEAT_VENTILATION -> next.coerceIn(0.0,3.0)
                Control.STEERING_HEAT -> next.coerceIn(0.0,1.0)
                Control.WINDOW_POSITION -> next.coerceIn(0.0,100.0)
            } else next
            requestedValues[type] = requested
            value.text = "${requested.toInt()} $unit"
            controller?.control(type,requested)
        }
        line.addView(button("−") { send(-step) }.also { it.background=shape(Color.rgb(32,73,87),26);it.textSize=22f;actionButtons.add(it); it.contentDescription = "$title 낮추기" },LinearLayout.LayoutParams(dp(48),dp(52)))
        line.addView(value,LinearLayout.LayoutParams(0,dp(52),1f))
        line.addView(button("＋") { send(step) }.also { it.background=shape(Color.rgb(32,73,87),26);it.textSize=22f;actionButtons.add(it); it.contentDescription = "$title 높이기" },LinearLayout.LayoutParams(dp(48),dp(52)))
        layout.addView(line); return layout
    }
    private fun showDestination() {
        if (controller?.decision()?.canControl != true) return
        val fields = column().apply { setPadding(dp(16),dp(12),dp(16),dp(12)) }
        fun field(hint: String) = EditText(this).apply { this.hint = hint; setTextColor(ink); setHintTextColor(muted); textSize = 16f; typeface = font
            inputType = InputType.TYPE_CLASS_TEXT; minHeight = dp(52); fields.addView(this) }
        val target = field("목적지 위도, 경도")
        val first = field("경유지 1 위도, 경도 (선택)")
        val second = field("경유지 2 위도, 경도 (선택)")
        val dialog = AlertDialog.Builder(this).setTitle("목적지 설정").setView(fields).setNegativeButton("취소",null).setPositiveButton("경로 요청",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (controller?.decision()?.canControl != true) { dialog.dismiss(); return@setOnClickListener }
            fun parse(text: String): Coordinate { val parts = text.split(','); require(parts.size == 2); return Coordinate(parts[0].trim().toDouble(),parts[1].trim().toDouble()) }
            runCatching { val dest = parse(target.text.toString()); val stops = listOf(first,second).filter { it.text.isNotBlank() }.map { parse(it.text.toString()) }; controller?.route(dest,stops) }
                .onSuccess { dialog.dismiss() }.onFailure { target.error = "위도, 경도 범위를 확인해 주세요" }
        } }
        destinationDialog = dialog; dialog.show()
    }
    private fun requestVehiclePermissions() {
        if (permissionButton?.isEnabled != true) return
        val needed = arrayOf("android.car.permission.CAR_SPEED","pleos.car.permission.FUSED_LOCATION").filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(),42) else restartSession()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if (requestCode == 42) restartSession()
    }
    private fun restartSession() { controller?.close(); shownMode = null; controller = AppController(this,BuildConfig.DEMO_ONLY,::refresh).also { it.start() }; refresh() }
    private fun dp(value: Int) = (value*resources.displayMetrics.density).toInt()
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun weighted() = LinearLayout.LayoutParams(0,-2,1f).apply { setMargins(dp(4),dp(8),dp(4),dp(8)) }
    private fun card() = column().apply {
        setPadding(dp(20),dp(18),dp(20),dp(18))
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.argb(245,33,67,84),Color.argb(235,15,38,57))).apply {
            cornerRadius = dp(24).toFloat(); setStroke(dp(1),Color.argb(95,185,231,243))
        }
        elevation = dp(14).toFloat()
        setOnHoverListener { view, event ->
            if (ValueAnimator.areAnimatorsEnabled()) {
                view.animate().rotationY(if (event.action == MotionEvent.ACTION_HOVER_EXIT) 0f else 1.5f).setDuration(280).setInterpolator(PathInterpolator(.16f,1f,.3f,1f)).start()
            }; false
        }
    }
    private fun enter(view: View, delay: Long) {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        view.alpha = 0f; view.translationY = dp(10).toFloat()
        view.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(480).setInterpolator(PathInterpolator(.16f,1f,.3f,1f)).start()
    }
    private fun cancelMotion(view: View) {
        view.animate().cancel(); view.alpha = 1f; view.translationY = 0f
        if (view is ViewGroup) for (i in 0 until view.childCount) cancelMotion(view.getChildAt(i))
    }
    private fun shape(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat(); setStroke(dp(1),Color.argb(65,155,211,222)) }
    private fun label(value: String, size: Float, color: Int) = TextView(this).apply { text = value; textSize = size; setTextColor(color); typeface = font; setLineSpacing(0f,1f); includeFontPadding = false }
    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value; textSize = 16f; typeface = font; isAllCaps = false; setTextColor(mint); minHeight = dp(52)
        setPadding(dp(12),dp(8),dp(12),dp(8)); background = shape(Color.rgb(29,56,67),14); setOnClickListener { action() }
        setOnTouchListener { view, event ->
            if (ValueAnimator.areAnimatorsEnabled()) {
                val scale = if (event.actionMasked == MotionEvent.ACTION_DOWN) .98f else 1f
                view.animate().scaleX(scale).scaleY(scale).setStartDelay(0).setDuration(160).setInterpolator(PathInterpolator(.16f,1f,.3f,1f)).start()
            }; false
        }
        layoutParams = LinearLayout.LayoutParams(-1,-2).apply { setMargins(dp(3),dp(8),dp(3),dp(6)) }
    }
}
