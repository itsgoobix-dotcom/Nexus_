package com.nexus.app

import android.graphics.Bitmap

object Fx {
    private val cache = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > 10
    }

    private fun small(src: Bitmap): Bitmap {
        val key = "s:" + System.identityHashCode(src)
        cache[key]?.let { return it }
        val m = maxOf(src.width, src.height)
        val out = if (m <= 600) src else {
            val f = 600f / m
            Bitmap.createScaledBitmap(
                src, maxOf(1, (src.width * f).toInt()), maxOf(1, (src.height * f).toInt()), true)
        }
        cache[key] = out
        return out
    }

    fun gaussian(src: Bitmap, amount: Float): Bitmap {
        val r = (amount * 0.4f).toInt().coerceIn(1, 40)
        val key = "g:" + System.identityHashCode(src) + ":" + r
        cache[key]?.let { return it }
        val s = small(src)
        val w = s.width
        val h = s.height
        val px = IntArray(w * h)
        s.getPixels(px, 0, w, 0, 0, w, h)
        val out = blurArgb(px, w, h, r)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, w, 0, 0, w, h)
        cache[key] = bmp
        return bmp
    }

    fun glow(src: Bitmap, threshold: Float, radius: Float): Bitmap {
        val r = (radius * 0.4f).toInt().coerceIn(1, 40)
        val t = threshold.toInt()
        val key = "l:" + System.identityHashCode(src) + ":" + r + ":" + t
        cache[key]?.let { return it }
        val s = small(src)
        val w = s.width
        val h = s.height
        val px = IntArray(w * h)
        s.getPixels(px, 0, w, 0, 0, w, h)
        val black = 0xFF000000.toInt()
        for (i in px.indices) {
            val p = px[i]
            val al = p ushr 24
            val rr = ((p shr 16) and 255) * al / 255
            val gg = ((p shr 8) and 255) * al / 255
            val bb = (p and 255) * al / 255
            val lum = (rr * 299 + gg * 587 + bb * 114) / 1000
            px[i] = if (lum >= t) (black or (rr shl 16) or (gg shl 8) or bb) else black
        }
        val out = blurArgb(px, w, h, r)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, w, 0, 0, w, h)
        cache[key] = bmp
        return bmp
    }

    private fun blurArgb(px: IntArray, w: Int, h: Int, r: Int): IntArray {
        for (i in px.indices) {
            val p = px[i]
            val al = p ushr 24
            if (al < 255) {
                val rr = ((p shr 16) and 255) * al / 255
                val gg = ((p shr 8) and 255) * al / 255
                val bb = (p and 255) * al / 255
                px[i] = (al shl 24) or (rr shl 16) or (gg shl 8) or bb
            }
        }
        val cur = px
        val tmp = IntArray(px.size)
        for (n in 0 until 3) {
            pass(cur, tmp, w, h, r, true)
            pass(tmp, cur, w, h, r, false)
        }
        for (i in cur.indices) {
            val p = cur[i]
            val al = p ushr 24
            if (al in 1..254) {
                val rr = minOf(255, ((p shr 16) and 255) * 255 / al)
                val gg = minOf(255, ((p shr 8) and 255) * 255 / al)
                val bb = minOf(255, (p and 255) * 255 / al)
                cur[i] = (al shl 24) or (rr shl 16) or (gg shl 8) or bb
            }
        }
        return cur
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val len = if (horizontal) w else h
        val lines = if (horizontal) h else w
        val step = if (horizontal) 1 else w
        val div = 2 * r + 1
        for (line in 0 until lines) {
            val start = if (horizontal) line * w else line
            var sa = 0
            var sr = 0
            var sg = 0
            var sb = 0
            for (i in -r..r) {
                val p = src[start + i.coerceIn(0, len - 1) * step]
                sa += p ushr 24
                sr += (p shr 16) and 255
                sg += (p shr 8) and 255
                sb += p and 255
            }
            for (i in 0 until len) {
                dst[start + i * step] =
                    ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val add = src[start + minOf(i + r + 1, len - 1) * step]
                val rem = src[start + maxOf(i - r, 0) * step]
                sa += (add ushr 24) - (rem ushr 24)
                sr += ((add shr 16) and 255) - ((rem shr 16) and 255)
                sg += ((add shr 8) and 255) - ((rem shr 8) and 255)
                sb += (add and 255) - (rem and 255)
            }
        }
    }
}
