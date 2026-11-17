package com.nexus.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class EffectsActivity : Activity() {
    private val bg = Color.parseColor("#121214")
    private val card = Color.parseColor("#1E1E22")
    private val accent = Color.parseColor("#FF4081")
    private val dim = Color.parseColor("#9A9AA3")
    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT

    private lateinit var body: LinearLayout
    private lateinit var search: EditText
    private lateinit var heading: TextView
    private lateinit var chips: LinearLayout
    private var mode = 0
    private var category: String? = null
    private val favs = HashSet<String>()
    private var recents = mutableListOf<String>()
    private val modes = listOf("Categories", "Favorites", "Recent", "Plugins", "Built-in")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, r: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(r).toFloat()
    }

    private fun tv(s: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun prefs() = getSharedPreferences("fx", MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        favs.addAll(prefs().getStringSet("favs", emptySet()) ?: emptySet())
        val r = prefs().getString("recent", "") ?: ""
        recents = r.split("|").filter { it.isNotEmpty() }.toMutableList()

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(36), dp(12), dp(8))
        }
        top.addView(tv("←", 22f).apply {
            setPadding(dp(8), dp(4), dp(16), dp(4))
            setOnClickListener { onBackPressed() }
        })
        heading = tv("Effects", 20f, Color.WHITE, true)
        top.addView(heading)

        search = EditText(this).apply {
            hint = "Search effects or categories..."
            setHintTextColor(dim)
            setTextColor(Color.WHITE)
            setSingleLine()
            background = rounded(card, 12)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { render() }
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            })
        }

        chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(24))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        root.addView(top)
        root.addView(search, LinearLayout.LayoutParams(MP, WC).apply {
            setMargins(dp(12), 0, dp(12), dp(8))
        })
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
            setPadding(dp(8), 0, dp(8), dp(8))
        })
        root.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(MP, 0, 1f))
        setContentView(root)

        buildChips()
        render()
    }

    private fun buildChips() {
        chips.removeAllViews()
        modes.forEachIndexed { i, label ->
            chips.addView(tv(label, 13f).apply {
                background = rounded(if (i == mode) accent else card, 16)
                setPadding(dp(14), dp(8), dp(14), dp(8))
                setOnClickListener {
                    mode = i
                    category = null
                    buildChips()
                    render()
                }
                layoutParams = LinearLayout.LayoutParams(WC, WC).apply {
                    setMargins(dp(4), 0, dp(4), 0)
                }
            })
        }
    }

    private fun render() {
        body.removeAllViews()
        val q = search.text.toString().trim().lowercase()
        val all = Registry.categories.values.flatten()
        if (q.isNotEmpty()) {
            heading.text = "Results"
            showList(
                all.filter { it.name.lowercase().contains(q) || it.category.lowercase().contains(q) },
                "No effects match \"$q\"."
            )
            return
        }
        when (mode) {
            1 -> {
                heading.text = "Favorites"
                showList(all.filter { favs.contains(it.id) }, "No favorites yet. Tap ☆ on an effect.")
            }
            2 -> {
                heading.text = "Recent"
                showList(recents.mapNotNull { Registry.find(it) }, "Nothing used yet.")
            }
            3 -> {
                heading.text = "Installed plugins"
                showList(all.filter { it.plugin != null }, "No plugin effects yet. Add one in Scripts.")
            }
            4 -> {
                heading.text = "Built-in effects"
                showList(all.filter { it.plugin == null && it.implemented }, "None.")
            }
            else -> {
                val c = category
                if (c == null) {
                    heading.text = "Effects"
                    showCategories()
                } else {
                    heading.text = c
                    showList(Registry.categories[c] ?: emptyList<EffectDef>(), "Empty.")
                }
            }
        }
    }

    private fun showCategories() {
        val names = Registry.categories.keys.toList()
        names.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (n in pair) row.addView(catCard(n))
            if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            body.addView(row, LinearLayout.LayoutParams(MP, WC))
        }
    }

    private fun catCard(n: String): View {
        val list = Registry.categories[n] ?: mutableListOf()
        val ready = list.count { it.implemented }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(card, 14)
            setPadding(dp(14), dp(16), dp(14), dp(16))
            addView(tv(n, 15f, Color.WHITE, true))
            addView(tv("${list.size} effects · $ready ready", 12f, dim))
            setOnClickListener {
                category = n
                render()
            }
            layoutParams = LinearLayout.LayoutParams(0, WC, 1f).apply {
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
        }
    }

    private fun showList(list: List<EffectDef>, empty: String) {
        if (list.isEmpty()) {
            body.addView(tv(empty, 14f, dim).apply { setPadding(dp(12), dp(20), dp(12), dp(20)) })
            return
        }
        for (d in list) body.addView(effectRow(d))
    }

    private fun effectRow(d: EffectDef): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(card, 12)
            setPadding(dp(14), dp(10), dp(6), dp(10))
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply {
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(tv(d.name, 15f, if (d.implemented) Color.WHITE else dim, true))
        val tags = mutableListOf(d.category)
        if (d.plugin != null) tags.add("PLUGIN: ${d.plugin}")
        if (!d.implemented) tags.add("planned")
        col.addView(tv(tags.joinToString(" · "), 12f, if (d.plugin != null) accent else dim))
        row.addView(col, LinearLayout.LayoutParams(0, WC, 1f))

        val star = tv(if (favs.contains(d.id)) "★" else "☆", 22f, accent).apply {
            setPadding(dp(14), dp(4), dp(14), dp(4))
        }
        star.setOnClickListener {
            toggleFav(d.id)
            render()
        }
        row.addView(star)
        row.setOnClickListener { pick(d) }
        return row
    }

    private fun toggleFav(id: String) {
        if (!favs.remove(id)) favs.add(id)
        prefs().edit().putStringSet("favs", HashSet(favs)).apply()
    }

    private fun pick(d: EffectDef) {
        if (!d.implemented) {
            Toast.makeText(this, "${d.name} is planned, not built yet", Toast.LENGTH_SHORT).show()
            return
        }
        recents.remove(d.id)
        recents.add(0, d.id)
        while (recents.size > 12) recents.removeAt(recents.size - 1)
        prefs().edit().putString("recent", recents.joinToString("|")).apply()
        setResult(RESULT_OK, Intent().putExtra("effect_id", d.id))
        finish()
    }

    override fun onBackPressed() {
        if (category != null && search.text.isEmpty()) {
            category = null
            render()
        } else {
            super.onBackPressed()
        }
    }
}
