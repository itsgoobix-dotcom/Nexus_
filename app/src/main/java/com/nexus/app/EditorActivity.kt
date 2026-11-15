package com.nexus.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

class NexusCanvas(context: Context, private val project: Project) : View(context) {
    var onSelect: (Layer?) -> Unit = {}
    var onEdit: () -> Unit = {}
    var onEditEnd: () -> Unit = {}
    var onTick: () -> Unit = {}

    private var last = 0L
    var playing = false
        set(v) {
            field = v
            last = System.nanoTime()
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val addMode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val selP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 5f; color = Color.parseColor("#FF4081")
    }
    private val nullP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f; color = Color.parseColor("#FF4081")
        pathEffect = DashPathEffect(floatArrayOf(16f, 10f), 0f)
    }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 34f }

    private var s = 1f
    private var ox = 0f
    private var oy = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.parseColor("#0A0A0C"))
        val pad = 16f
        s = minOf((width - pad * 2) / project.w, (height - pad * 2) / project.h)
        ox = (width - project.w * s) / 2f
        oy = (height - project.h * s) / 2f
        c.save()
        c.translate(ox, oy)
        c.scale(s, s)
        c.clipRect(0f, 0f, project.w, project.h)
        c.drawColor(Color.BLACK)
        for (l in project.layers) drawLayer(c, l)
        project.selected?.let { sel ->
            c.save()
            c.concat(sel.matrix(project.time))
            c.drawRect(-sel.w / 2, -sel.h / 2, sel.w / 2, sel.h / 2, selP)
            c.restore()
        }
        c.restore()
        if (playing) {
            val now = System.nanoTime()
            project.time += (now - last) / 1e9f
            last = now
            if (project.time > project.duration) project.time = 0f
            post { onTick() }
            postInvalidateOnAnimation()
        }
    }

    private fun drawLayer(c: Canvas, l: Layer) {
        val p = l.propsAt(project.time)
        c.save()
        c.concat(l.matrix(project.time))
        if (l.kind == Layer.NULL) {
            c.drawRect(-l.w / 2, -l.h / 2, l.w / 2, l.h / 2, nullP)
            c.drawLine(-l.w / 2, 0f, l.w / 2, 0f, nullP)
            c.drawLine(0f, -l.h / 2, 0f, l.h / 2, nullP)
            c.drawText(l.name, -l.w / 2, -l.h / 2 - 12f, txt)
        } else {
            drawContent(c, l, p[P_OP])
        }
        c.restore()
    }

    private fun drawContent(c: Canvas, l: Layer, opacity: Float) {
        val cm = ColorMatrix()
        var blur = 0f
        var split = 0f
        for (e in l.effects) {
            if (!e.enabled) continue
            val fn = e.def.matrixFn
            if (fn != null) {
                cm.postConcat(ColorMatrix(fn(e.v)))
                continue
            }
            when (e.def.id) {
                "blur_box" -> blur = e.v[0]
                "bright_contrast" -> cm.postConcat(brightContrast(e.v[0], e.v[1]))
                "exposure" -> {
                    val k = Math.pow(2.0, e.v[0].toDouble()).toFloat()
                    cm.postConcat(ColorMatrix(floatArrayOf(
                        k, 0f, 0f, 0f, 0f,
                        0f, k, 0f, 0f, 0f,
                        0f, 0f, k, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f)))
                }
                "hue_sat" -> {
                    cm.postConcat(hue(e.v[0]))
                    val sm = ColorMatrix()
                    sm.setSaturation(e.v[1] / 100f)
                    cm.postConcat(sm)
                }
                "bw" -> {
                    val sm = ColorMatrix()
                    sm.setSaturation(0f)
                    cm.postConcat(sm)
                }
                "rgb_split" -> split = e.v[0]
            }
        }
        val a = (opacity.coerceIn(0f, 1f) * 255).toInt()
        val dst = RectF(-l.w / 2, -l.h / 2, l.w / 2, l.h / 2)

        if (l.kind == Layer.SOLID) {
            paint.xfermode = null
            paint.colorFilter = ColorMatrixColorFilter(cm)
            paint.color = l.color
            paint.alpha = a
            c.drawRect(dst, paint)
            paint.colorFilter = null
            return
        }

        val src = if (blur >= 1f) l.blurred(blur) else l.bmp!!
        if (split >= 1f) {
            val pad = split + 4f
            val saved = c.saveLayerAlpha(
                RectF(dst.left - pad, dst.top - pad, dst.right + pad, dst.bottom + pad), a)
            paint.alpha = 255
            paint.xfermode = addMode
            for (i in 0..2) {
                val ch = ColorMatrix(cm)
                ch.postConcat(channel(i))
                paint.colorFilter = ColorMatrixColorFilter(ch)
                c.save()
                c.translate((i - 1) * split, 0f)
                c.drawBitmap(src, null, dst, paint)
                c.restore()
            }
            paint.xfermode = null
            c.restoreToCount(saved)
        } else {
            paint.xfermode = null
            paint.colorFilter = ColorMatrixColorFilter(cm)
            paint.alpha = a
            c.drawBitmap(src, null, dst, paint)
        }
        paint.colorFilter = null
    }

    private fun channel(i: Int): ColorMatrix {
        val m = FloatArray(20)
        m[i * 5 + i] = 1f
        m[18] = 1f
        return ColorMatrix(m)
    }

    private fun brightContrast(b: Float, ct: Float): ColorMatrix {
        val k = 1f + ct / 100f
        val t = 128f * (1f - k) + b * 2.55f
        return ColorMatrix(floatArrayOf(
            k, 0f, 0f, 0f, t,
            0f, k, 0f, 0f, t,
            0f, 0f, k, 0f, t,
            0f, 0f, 0f, 1f, 0f))
    }

    private fun hue(deg: Float): ColorMatrix {
        val r = Math.toRadians(deg.toDouble())
        val cs = Math.cos(r).toFloat()
        val sn = Math.sin(r).toFloat()
        val lr = 0.213f
        val lg = 0.715f
        val lb = 0.072f
        return ColorMatrix(floatArrayOf(
            lr + cs * (1 - lr) + sn * (-lr), lg + cs * (-lg) + sn * (-lg), lb + cs * (-lb) + sn * (1 - lb), 0f, 0f,
            lr + cs * (-lr) + sn * 0.143f, lg + cs * (1 - lg) + sn * 0.140f, lb + cs * (-lb) + sn * (-0.283f), 0f, 0f,
            lr + cs * (-lr) + sn * (-(1 - lr)), lg + cs * (-lg) + sn * lg, lb + cs * (1 - lb) + sn * lb, 0f, 0f,
            0f, 0f, 0f, 1f, 0f))
    }

    private fun isHit(l: Layer, x: Float, y: Float): Boolean {
        val inv = Matrix()
        if (!l.matrix(project.time).invert(inv)) return false
        val p = floatArrayOf(x, y)
        inv.mapPoints(p)
        return Math.abs(p[0]) <= l.w / 2 && Math.abs(p[1]) <= l.h / 2
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val cx = (e.x - ox) / s
        val cy = (e.y - oy) / s
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val picked = project.layers.lastOrNull { isHit(it, cx, cy) }
                project.selected = picked
                dragging = picked != null
                lastX = cx
                lastY = cy
                onSelect(picked)
            }
            MotionEvent.ACTION_MOVE -> {
                val l = project.selected
                if (dragging && l != null) {
                    val d = floatArrayOf(cx - lastX, cy - lastY)
                    l.parent?.let { par ->
                        val inv = Matrix()
                        if (par.matrix(project.time).invert(inv)) inv.mapVectors(d)
                    }
                    val p = l.propsAt(project.time)
                    p[P_X] += d[0]
                    p[P_Y] += d[1]
                    l.setProps(project.time, p)
                    onEdit()
                }
                lastX = cx
                lastY = cy
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                onEditEnd()
            }
        }
        invalidate()
        return true
    }
}
