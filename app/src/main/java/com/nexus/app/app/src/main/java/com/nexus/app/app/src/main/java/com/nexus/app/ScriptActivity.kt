package com.nexus.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class ScriptActivity : Activity() {
    private val example = """registerEffect({
  id: "warm",
  name: "Warm Tint",
  category: "Color Correction",
  params: [{ name: "Amount", min: 0, max: 100, def: 30 }],
  matrix: function (v) {
    var a = v[0] / 100;
    return [
      1 + 0.3 * a, 0, 0, 0, 10 * a,
      0, 1, 0, 0, 0,
      0, 0, 1 - 0.3 * a, 0, 0,
      0, 0, 0, 1, 0
    ];
  }
});
"""

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun box(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(12).toFloat()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bg = Color.parseColor("#121214")
        val card = Color.parseColor("#1E1E22")
        val accent = Color.parseColor("#FF4081")
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val MP = ViewGroup.LayoutParams.MATCH_PARENT
        val WC = ViewGroup.LayoutParams.WRAP_CONTENT

        val nameBox = EditText(this).apply {
            setText("My Plugin")
            setTextColor(Color.WHITE)
            setSingleLine()
            background = box(card)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val codeBox = EditText(this).apply {
            setText(example)
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            textSize = 12f
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            gravity = Gravity.TOP
            minLines = 14
            background = box(card)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val out = TextView(this).apply {
            setTextColor(Color.parseColor("#9A9AA3"))
            textSize = 13f
            text = "Write a script, then tap Run & Save."
            setPadding(dp(4), dp(10), dp(4), dp(10))
        }

        fun button(label: String, main: Boolean, onClick: () -> Unit) = TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = box(if (main) accent else card)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(10) }
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(40), dp(16), dp(24))
        }
        col.addView(TextView(this).apply {
            text = "Scripts"
            textSize = 24f
            setTextColor(accent)
            typeface = Typeface.DEFAULT_BOLD
        })
        col.addView(TextView(this).apply {
            text = "Plugin name"
            setTextColor(Color.parseColor("#9A9AA3"))
            setPadding(0, dp(14), 0, dp(6))
        })
        col.addView(nameBox)
        col.addView(TextView(this).apply {
            text = "Script (JavaScript)"
            setTextColor(Color.parseColor("#9A9AA3"))
            setPadding(0, dp(14), 0, dp(6))
        })
        col.addView(codeBox)
        col.addView(button("Run & Save", true) {
            val name = nameBox.text.toString().trim().ifEmpty { "My Plugin" }
            val code = codeBox.text.toString()
            val result = ScriptEngine.run(code, name)
            out.text = result
            if (result.startsWith("OK")) ScriptStore.save(this, name, code)
        })
        col.addView(button("Load saved script", false) {
            val names = ScriptStore.names(this)
            if (names.isEmpty()) {
                out.text = "No saved scripts yet."
            } else {
                AlertDialog.Builder(this).setTitle("Saved scripts")
                    .setItems(names.toTypedArray()) { _, i ->
                        nameBox.setText(names[i])
                        codeBox.setText(ScriptStore.get(this, names[i]) ?: "")
                    }.show()
            }
        })
        col.addView(button("Back", false) { finish() })
        col.addView(out)

        val root = ScrollView(this).apply {
            setBackgroundColor(bg)
            addView(col)
        }
        setContentView(root)
    }
}
