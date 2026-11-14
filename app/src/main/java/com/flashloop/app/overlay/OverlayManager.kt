package com.flashloop.app.overlay

import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.flashloop.app.FlashLoopApp
import com.flashloop.app.R
import com.flashloop.app.data.Flashcard
import com.flashloop.app.data.ReviewMode
import com.flashloop.app.data.ReviewResult
import com.flashloop.app.scheduling.Scheduler
import kotlinx.coroutines.launch

/** Root view that turns the Back key into "show snooze options" instead of closing. */
private class OverlayRoot(context: Context) : FrameLayout(context) {
    var onBack: () -> Unit = {}

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true // swallow taps so nothing behind is touched by accident
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) onBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

/**
 * Draws the floating flashcard with plain Android views (no Compose) because an overlay lives
 * outside any Activity, so Compose's lifecycle plumbing would add fragile complexity.
 * All methods must be called on the main thread.
 */
object OverlayManager {
    private var windowManager: WindowManager? = null
    private var root: View? = null

    fun isShowing(): Boolean = root != null

    fun hide() {
        val r = root ?: return
        try {
            windowManager?.removeView(r)
        } catch (_: Exception) {
        }
        root = null
    }

    fun show(context: Context, card: Flashcard): Boolean {
        if (root != null) return true
        val app = context.applicationContext
        if (!Settings.canDrawOverlays(app)) return false

        val ctx = ContextThemeWrapper(app, R.style.Theme_FlashLoop)
        val density = app.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val dark = (app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val panelColor = if (dark) 0xFF1E1E24.toInt() else 0xFFFFFFFF.toInt()
        val textColor = if (dark) 0xFFECECF1.toInt() else 0xFF1B1B1F.toInt()
        val mutedColor = if (dark) 0xFF9A9AA5.toInt() else 0xFF6B6B76.toInt()
        val accent = if (dark) 0xFF9FA8DA.toInt() else 0xFF3F51B5.toInt()

        fun label(text: String, sp: Float, color: Int, bold: Boolean = false): TextView =
            TextView(ctx).apply {
                this.text = text
                textSize = sp
                setTextColor(color)
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

        val rootView = OverlayRoot(ctx)
        rootView.setBackgroundColor(0xB3000000.toInt())

        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(16))
            background = GradientDrawable().apply {
                setColor(panelColor)
                cornerRadius = dp(24).toFloat()
            }
        }
        val panelLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER
        ).apply { setMargins(dp(20), dp(20), dp(20), dp(20)) }
        rootView.addView(panel, panelLp)

        fun gap(h: Int) = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(h) }

        val header = label("FLASHCARD", 12f, accent, true).apply { letterSpacing = 0.15f }
        val question = label(card.question, 22f, textColor, true)
        val answer = label(card.answer, 18f, accent).apply { visibility = View.GONE }
        val reveal = Button(ctx).apply { text = "Reveal answer"; isAllCaps = false }

        val remembered = Button(ctx).apply { text = "Remembered"; isAllCaps = false }
        val forgot = Button(ctx).apply { text = "Didn't remember"; isAllCaps = false }
        val resultRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            addView(forgot, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(remembered, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        val snoozeLink = label("Snooze…", 13f, mutedColor).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(8))
        }
        val snoozeTitle = label("Snooze this card? It stays pending until you answer it.", 14f, mutedColor)
        val snooze5 = Button(ctx).apply { text = "5 min"; isAllCaps = false }
        val snooze15 = Button(ctx).apply { text = "15 min"; isAllCaps = false }
        val keep = Button(ctx).apply { text = "Keep reviewing"; isAllCaps = false }
        val snoozeButtons = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(snooze5, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(snooze15, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(keep, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        val snoozePanel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(snoozeTitle)
            addView(snoozeButtons, gap(8))
        }

        panel.addView(header)
        panel.addView(question, gap(12))
        panel.addView(answer, gap(16))
        panel.addView(reveal, gap(20))
        panel.addView(resultRow, gap(20))
        panel.addView(snoozeLink, gap(4))
        panel.addView(snoozePanel, gap(4))

        val a = app as FlashLoopApp

        fun toggleSnooze() {
            snoozePanel.visibility = if (snoozePanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        fun answerWith(result: ReviewResult) {
            hide()
            a.appScope.launch { a.repository.review(card.id, result, ReviewMode.AUTOMATIC) }
        }

        fun snoozeFor(minutes: Int) {
            hide()
            a.appScope.launch { Scheduler.snooze(a, minutes) }
        }

        reveal.setOnClickListener {
            answer.visibility = View.VISIBLE
            reveal.visibility = View.GONE
            resultRow.visibility = View.VISIBLE
        }
        remembered.setOnClickListener { answerWith(ReviewResult.REMEMBERED) }
        forgot.setOnClickListener { answerWith(ReviewResult.FORGOTTEN) }
        snoozeLink.setOnClickListener { toggleSnooze() }
        snooze5.setOnClickListener { snoozeFor(5) }
        snooze15.setOnClickListener { snoozeFor(15) }
        keep.setOnClickListener { snoozePanel.visibility = View.GONE }
        rootView.onBack = { toggleSnooze() }

        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(rootView, params)
        windowManager = wm
        root = rootView
        rootView.requestFocus()
        return true
    }
}
