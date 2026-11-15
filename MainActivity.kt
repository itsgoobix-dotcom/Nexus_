package com.nexus.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private val bg = Color.parseColor("#121214")
    private val card = Color.parseColor("#1E1E22")
    private val accent = Color.parseColor("#FF4081")
    private val dim = Color.parseColor("#9A9AA3")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, r: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(r).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun tv(s: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun soon(name: String) =
        Toast.makeText(this, "$name: planned", Toast.LENGTH_SHORT).show()

    private fun openEditor() = startActivity(Intent(this, EditorActivity::class.java))

    private fun shortcut(title: String, sub: String, onClick: () -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(card, 16, Color.parseColor("#33FF4081"))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(tv(title, 17f, Color.WHITE, true))
            addView(tv(sub, 13f, dim))
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { setMargins(dp(4), 0, dp(4), 0) }
        }

    private fun tab(icon: String, name: String, active: Boolean, onClick: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(tv(icon, 20f, if (active) accent else dim))
            addView(tv(name, 11f, if (active) accent else dim))
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(40), dp(16), dp(110))
        }

        content.addView(tv("Nexus", 24f, accent, true))
        content.addView(ImageView(this).apply {
            setImageResource(R.drawable.nexus_banner)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(160)).apply { topMargin = dp(12) })

        val welcome = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#5A1030"), Color.parseColor("#1E1E22"))
            ).apply { cornerRadius = dp(20).toFloat() }
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        welcome.addView(tv("WELCOME", 12f, dim, true))
        welcome.addView(tv("Turn your ideas into motion.", 26f, Color.WHITE, true))
        welcome.addView(tv("Mobile motion graphics, in your pocket.", 14f, dim))
        welcome.addView(tv("+  New composition", 16f, Color.WHITE, true).apply {
            background = rounded(accent, 28)
            setPadding(dp(24), dp(14), dp(24), dp(14))
            setOnClickListener { openEditor() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(20) }
        })
        content.addView(welcome, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16) })

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(shortcut("Scripts", "Write your own effects") { startActivity(Intent(this, ScriptActivity::class.java)) })
        row.addView(shortcut("Projects", "Open and organize") { soon("Projects") })
        content.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16) })

        content.addView(tv("Your projects", 20f, Color.WHITE, true).apply {
            setPadding(0, dp(28), 0, dp(12))
        })
        content.addView(tv("No projects yet. Tap New composition to start.", 14f, dim).apply {
            background = rounded(card, 16)
            setPadding(dp(20), dp(28), dp(20), dp(28))
            gravity = Gravity.CENTER
        })

        val scroll = ScrollView(this).apply { addView(content) }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#0C0C0E"))
            setPadding(dp(8), dp(10), dp(8), dp(14))
        }
        bar.addView(tab("⌂", "Home", true) {})
        bar.addView(tab("▣", "Projects", false) { soon("Projects") })
        bar.addView(tv("+", 28f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = rounded(accent, 18)
            setOnClickListener { openEditor() }
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(52)).apply {
                setMargins(dp(6), 0, dp(6), 0)
            }
        })
        bar.addView(tab("✦", "Scripts", false) { startActivity(Intent(this, ScriptActivity::class.java)) })
        bar.addView(tab("☻", "Profile", false) { soon("Profile") })

        val root = FrameLayout(this).apply { setBackgroundColor(bg) }
        root.addView(scroll)
        root.addView(bar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ))
        setContentView(root)
    }
}
