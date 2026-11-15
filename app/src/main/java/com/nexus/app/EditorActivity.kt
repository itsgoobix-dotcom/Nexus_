package com.nexus.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class EditorActivity : Activity() {
    private val project = Project()
    private lateinit var preview: NexusCanvas
    private lateinit var timeline: TimelineView
    private lateinit var timeText: TextView
    private lateinit var playBtn: TextView
    private lateinit var toolRow: LinearLayout
    private lateinit var panel: LinearLayout
    private var mode = 0 // 0 none, 1 effects, 2 transform, 3 animate

    private val bg = Color.parseColor("#121214")
    private val card = Color.parseColor("#1E1E22")
    private val accent = Color.parseColor("#FF4081")
    private val dim = Color.parseColor("#9A9AA3")
    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT

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

    private fun hint(s: String) = tv(s, 13f, dim).apply { setPadding(dp(12), dp(10), dp(12), dp(10)) }

    private fun btn(label: String, active: Boolean = false, onClick: () -> Unit) =
        TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = rounded(if (active) accent else card, 12)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(WC, WC).apply {
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
        }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun fmt(v: Float) = String.format(Locale.US, "%.2f", v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ScriptStore.loadAll(this)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        preview = NexusCanvas(this, project)
        timeline = TimelineView(this, project)
        timeText = tv("0:00.0", 14f, Color.WHITE, true)
        playBtn = btn("▶") { togglePlay() }
        toolRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        preview.onSelect = { onSelectionChanged() }
        preview.onEdit = { refresh() }
        preview.onEditEnd = { rebuildPanel() }
        preview.onTick = { refresh() }
        timeline.onScrub = { refresh() }
        timeline.onSelect = { onSelectionChanged() }
        timeline.onEnd = { rebuildPanel() }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(36), dp(12), dp(8))
        }
        top.addView(tv("←", 22f).apply {
            setPadding(dp(8), dp(4), dp(16), dp(4))
            setOnClickListener { finish() }
        })
        top.addView(tv("Nexus", 18f, accent, true), LinearLayout.LayoutParams(0, WC, 1f))
        top.addView(btn("Export") { toast("Export: planned") })

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(4))
        }
        controls.addView(btn("⏮") { project.time = 0f; refresh(); rebuildPanel() })
        controls.addView(playBtn)
        controls.addView(timeText.apply { setPadding(dp(12), 0, dp(12), 0) })
        controls.addView(btn("◆ Key") { addKeyframe() })

        val toolScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(toolRow)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        root.addView(top)
        root.addView(preview, LinearLayout.LayoutParams(MP, 0, 1f))
        root.addView(controls)
        root.addView(timeline, LinearLayout.LayoutParams(MP, WC))
        root.addView(toolScroll)
        root.addView(ScrollView(this).apply { addView(panel) }, LinearLayout.LayoutParams(MP, dp(220)))
        setContentView(root)

        rebuildTools()
        rebuildPanel()
        refresh()
    }

    private fun togglePlay() {
        preview.playing = !preview.playing
        playBtn.text = if (preview.playing) "⏸" else "▶"
        if (!preview.playing) rebuildPanel()
    }

    private fun refresh() {
        val t = project.time
        timeText.text = String.format(Locale.US, "%d:%04.1f", t.toInt() / 60, t % 60)
        preview.invalidate()
        timeline.invalidate()
    }

    private fun onSelectionChanged() {
        if (project.selected == null) mode = 0
        rebuildTools()
        rebuildPanel()
        refresh()
    }

    private fun rebuildTools() {
        toolRow.removeAllViews()
        toolRow.addView(btn("＋ Add") { showAdd() })
        val l = project.selected ?: return
        if (l.kind != Layer.NULL) toolRow.addView(btn("Effects", mode == 1) { mode = 1; rebuildTools(); rebuildPanel() })
        toolRow.addView(btn("Transform", mode == 2) { mode = 2; rebuildTools(); rebuildPanel() })
        toolRow.addView(btn("Animate", mode == 3) { mode = 3; rebuildTools(); rebuildPanel() })
        if (l.kind != Layer.NULL) toolRow.addView(btn("Parent") { chooseParent(l) })
        toolRow.addView(btn("Delete") { deleteLayer(l) })
    }

    private fun rebuildPanel() {
        panel.removeAllViews()
        val l = project.selected
        if (l == null) {
            panel.addView(hint("Tap ＋ Add to put an image, solid or null on the canvas. Tap a layer to edit it."))
            return
        }
        if (l.kind == Layer.NULL && mode == 1) mode = 0
        when (mode) {
            1 -> effectsPanel(l)
            2 -> transformPanel(l)
            3 -> animatePanel(l)
            else -> panel.addView(hint("${l.name} selected. Pick a tool above, or drag it on the canvas."))
        }
    }

    private fun slider(name: String, lo: Float, hi: Float, value: Float, onChange: (Float) -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(2), dp(8), dp(2))
        }
        val title = tv("$name: ${fmt(value)}", 12f, dim)
        val bar = SeekBar(this).apply {
            max = 1000
            progress = (((value - lo) / (hi - lo)) * 1000f).toInt().coerceIn(0, 1000)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val v = lo + (hi - lo) * p / 1000f
                    title.text = "$name: ${fmt(v)}"
                    onChange(v)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        box.addView(title)
        box.addView(bar)
        return box
    }

    private fun transformPanel(l: Layer) {
        val p = l.propsAt(project.time)
        fun put(i: Int, v: Float) {
            val q = l.propsAt(project.time)
            q[i] = v
            l.setProps(project.time, q)
            refresh()
        }
        panel.addView(hint(
            if (l.keys.isEmpty()) "Editing base values. Add a keyframe in Animate to make it move."
            else "Animated layer: edits create keyframes at the playhead."))
        panel.addView(slider("X", 0f, project.w, p[P_X]) { put(P_X, it) })
        panel.addView(slider("Y", 0f, project.h, p[P_Y]) { put(P_Y, it) })
        panel.addView(slider("Scale", 0.1f, 4f, p[P_SC]) { put(P_SC, it) })
        panel.addView(slider("Rotation", -360f, 360f, p[P_ROT]) { put(P_ROT, it) })
        panel.addView(slider("Opacity", 0f, 1f, p[P_OP]) { put(P_OP, it) })
    }

    private fun animatePanel(l: Layer) {
        panel.addView(hint("Keyframes: ${l.keys.size}. Move the playhead, then change position, scale, rotation or opacity. Values blend smoothly between keyframes."))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(btn("◆ Add at ${fmt(project.time)}s") { addKeyframe() })
        row.addView(btn("Remove nearest") { removeKeyframe(l) })
        val sc = HorizontalScrollView(this).apply { addView(row) }
        panel.addView(sc)
        panel.addView(btn("Clear all keyframes") {
            if (l.keys.isNotEmpty()) l.keys.last().v.copyInto(l.base)
            l.keys.clear()
            refresh()
            rebuildPanel()
        })
    }

    private fun addKeyframe() {
        val l = project.selected ?: return toast("Select a layer first")
        val t = project.time
        val v = l.propsAt(t)
        val k = l.keys.firstOrNull { Math.abs(it.t - t) < 0.02f }
        if (k != null) v.copyInto(k.v) else {
            l.keys.add(Key(t, v))
            l.keys.sortBy { it.t }
        }
        refresh()
        rebuildPanel()
    }

    private fun removeKeyframe(l: Layer) {
        val k = l.keys.minByOrNull { Math.abs(it.t - project.time) } ?: return
        if (l.keys.size == 1) k.v.copyInto(l.base)
        l.keys.remove(k)
        refresh()
        rebuildPanel()
    }

    private fun effectsPanel(l: Layer) {
        panel.addView(btn("＋ Effect") { showCategories(l) })
        if (l.effects.isEmpty()) panel.addView(hint("Effects run as a chain, top to bottom, on what the layer draws."))
        for (e in l.effects) {
            val head = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(8), dp(8), 0)
            }
            head.addView(CheckBox(this).apply {
                isChecked = e.enabled
                setOnCheckedChangeListener { _, on -> e.enabled = on; refresh() }
            })
            val tag = if (e.def.plugin != null) "  [PLUGIN: ${e.def.plugin}]" else ""
            head.addView(tv(e.def.name + tag, 14f, Color.WHITE, true), LinearLayout.LayoutParams(0, WC, 1f))
            head.addView(tv("✕", 18f, dim).apply {
                setPadding(dp(12), dp(4), dp(12), dp(4))
                setOnClickListener { l.effects.remove(e); refresh(); rebuildPanel() }
            })
            panel.addView(head)
            e.def.params.forEachIndexed { i, pr ->
                panel.addView(slider(pr.name, pr.lo, pr.hi, e.v[i]) { e.v[i] = it; refresh() })
            }
        }
    }

    private fun showCategories(l: Layer) {
        val names = Registry.categories.keys.toList()
        val labels = names.map { "$it  (${Registry.categories[it]!!.size})" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Effects")
            .setItems(labels) { _, i -> showEffects(l, names[i]) }
            .show()
    }

    private fun showEffects(l: Layer, cat: String) {
        val defs = Registry.categories[cat] ?: return
        val labels = defs.map { d ->
            d.name + (if (d.plugin != null) "  [plugin]" else "") + (if (d.implemented) "" else "  · planned")
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle(cat)
            .setItems(labels) { _, i ->
                val d = defs[i]
                if (!d.implemented) {
                    toast("${d.name} is planned, not built yet")
                } else {
                    l.effects.add(Effect(d))
                    mode = 1
                    rebuildTools()
                    rebuildPanel()
                    refresh()
                }
            }.show()
    }

    private fun showAdd() {
        AlertDialog.Builder(this).setTitle("Add")
            .setItems(arrayOf("Image", "Solid color", "Null object")) { _, which ->
                val n = project.layers.size + 1
                when (which) {
                    0 -> pickImage()
                    1 -> addLayer(Layer("Solid $n", Layer.SOLID, null, 800f, 800f, Color.parseColor("#FF4081")))
                    2 -> addLayer(Layer("Null $n", Layer.NULL, null, 160f, 160f, Color.WHITE))
                }
            }.show()
    }

    private fun pickImage() {
        val i = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(i, 1)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode == 1 && resultCode == RESULT_OK && uri != null) loadImage(uri)
    }

    private fun loadImage(uri: Uri) {
        try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var sample = 1
            while (maxOf(o.outWidth, o.outHeight) / sample > 1600) sample *= 2
            val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
            if (bmp == null) {
                toast("Couldn't open image")
                return
            }
            val fit = minOf(project.w * 0.8f / bmp.width, project.h * 0.8f / bmp.height)
            addLayer(Layer("Image ${project.layers.size + 1}", Layer.IMAGE, bmp,
                bmp.width * fit, bmp.height * fit, Color.WHITE))
        } catch (e: Exception) {
            toast("Couldn't open image")
        }
    }

    private fun addLayer(l: Layer) {
        project.layers.add(l)
        project.selected = l
        mode = 2
        timeline.requestLayout()
        onSelectionChanged()
    }

    private fun deleteLayer(l: Layer) {
        project.layers.remove(l)
        project.layers.forEach { if (it.parent === l) it.parent = null }
        project.selected = null
        mode = 0
        timeline.requestLayout()
        onSelectionChanged()
    }

    private fun chooseParent(l: Layer) {
        val nulls = project.layers.filter { it.kind == Layer.NULL }
        if (nulls.isEmpty()) {
            toast("Add a Null object first")
            return
        }
        val names = arrayOf("None") + nulls.map { it.name }
        AlertDialog.Builder(this).setTitle("Parent to…")
            .setItems(names) { _, i ->
                l.parent = if (i == 0) null else nulls[i - 1]
                onSelectionChanged()
            }.show()
    }
}
