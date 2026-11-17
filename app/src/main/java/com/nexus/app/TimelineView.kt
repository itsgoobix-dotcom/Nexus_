package com.nexus.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View

class TimelineView(context: Context, private val project: Project) : View(context) {
    var onScrub: () -> Unit = {}
    var onSelect: (Layer?) -> Unit = {}
    var onEnd: () -> Unit = {}

    private val d = resources.displayMetrics.density
    private val rowH = 38f * d
    private val barP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val headP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF4081"); strokeWidth = 2f * d
    }
    private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 12f * d
    }
    private val path = Path()

    override fun onMeasure(w: Int, h: Int) {
        val rows = maxOf(project.layers.size, 1)
        setMeasuredDimension(MeasureSpec.getSize(w), (rows * rowH).toInt())
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.parseColor("#161619"))
        project.layers.forEachIndexed { i, l ->
            val top = i * rowH
            barP.color = if (l === project.selected) Color.parseColor("#5A1030") else Color.parseColor("#26262B")
            c.drawRoundRect(2 * d, top + 4 * d, width - 2 * d, top + rowH - 4 * d, 8 * d, 8 * d, barP)
            val label = l.name + (l.parent?.let { "  ↳ " + it.name } ?: "")
            c.drawText(label, 10 * d, top + rowH / 2 + 4 * d, textP)
            for (k in l.keys) {
                val x = k.t / project.duration * width
                val y = top + rowH / 2
                val r = 7 * d
                path.reset()
                path.moveTo(x, y - r)
                path.lineTo(x + r, y)
                path.lineTo(x, y + r)
                path.lineTo(x - r, y)
                path.close()
                c.drawPath(path, keyP)
            }
        }
        val px = project.time / project.duration * width
        c.drawLine(px, 0f, px, height.toFloat(), headP)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        project.time = (e.x / width * project.duration).coerceIn(0f, project.duration)
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            project.selected = project.layers.getOrNull((e.y / rowH).toInt())
            onSelect(project.selected)
        }
        if (e.actionMasked == MotionEvent.ACTION_UP) onEnd()
        onScrub()
        invalidate()
        return true
    }
}
