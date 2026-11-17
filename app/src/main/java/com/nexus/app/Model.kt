package com.nexus.app

import android.graphics.Bitmap
import android.graphics.Matrix
import kotlin.math.abs

const val P_X = 0
const val P_Y = 1
const val P_SC = 2
const val P_ROT = 3
const val P_OP = 4

class Param(val name: String, val lo: Float, val hi: Float, val def: Float)

class EffectDef(
    val id: String,
    val name: String,
    val category: String,
    val params: List<Param> = emptyList(),
    val implemented: Boolean = false,
    val plugin: String? = null
) {
    var matrixFn: ((FloatArray) -> FloatArray)? = null
}

class Effect(val def: EffectDef) {
    var enabled = true
    val v = FloatArray(def.params.size) { def.params[it].def }
}

object Registry {
    val categories = linkedMapOf<String, MutableList<EffectDef>>()

    // Scripts and plugins will call this to add effects.
    fun register(d: EffectDef) {
        val list = categories.getOrPut(d.category) { mutableListOf() }
        if (list.none { it.name == d.name }) list.add(d)
    }
        fun find(id: String): EffectDef? =
        categories.values.flatten().firstOrNull { it.id == id }

    private fun planned(cat: String, vararg names: String) {
        for (n in names) register(EffectDef("$cat:$n", n, cat))
    }

    init {
        for (c in listOf(
            "Blur", "Color Correction", "Glow & Lighting", "Distortion", "Glitch & RGB",
            "Motion & Camera", "Particles", "Stylize", "Keying & Masking", "Transform",
            "Transitions", "Texture & Film", "Audio Reactive"
        )) categories[c] = mutableListOf()

        // Implemented (real rendering)
                register(EffectDef("blur_box", "Fast Box Blur", "Blur",
            listOf(Param("Amount", 1f, 40f, 8f)), true))
        register(EffectDef("bright_contrast", "Brightness & Contrast", "Color Correction",
            listOf(Param("Brightness", -100f, 100f, 0f), Param("Contrast", -100f, 100f, 0f)), true))
        register(EffectDef("exposure", "Exposure", "Color Correction",
            listOf(Param("Stops", -3f, 3f, 0f)), true))
        register(EffectDef("hue_sat", "Hue/Saturation", "Color Correction",
            listOf(Param("Hue", -180f, 180f, 0f), Param("Saturation", 0f, 200f, 100f)), true))
        register(EffectDef("bw", "Black & White", "Color Correction", emptyList(), true))
        register(EffectDef("rgb_split", "RGB Split", "Glitch & RGB",
            listOf(Param("Distance", 0f, 80f, 20f)), true))
        register(EffectDef("blur_gauss", "Gaussian Blur", "Blur",
            listOf(Param("Amount", 0f, 100f, 20f)), true))
        register(EffectDef("glow", "Glow", "Glow & Lighting",
            listOf(Param("Intensity", 0f, 100f, 60f), Param("Radius", 1f, 100f, 30f),
                Param("Threshold", 0f, 255f, 140f)), true))
        // Planned (shown as unavailable)
        planned("Blur", "Gaussian Blur", "Fast Box Blur", "Directional Blur", "Radial Blur", "Motion Blur",
            "Camera Lens Blur", "Compound Blur", "Bilateral Blur", "Channel Blur", "Smart Blur", "Zoom Blur", "Bokeh Blur")
        planned("Color Correction", "Brightness & Contrast", "Exposure", "Levels", "Curves", "Hue/Saturation",
            "Color Balance", "Vibrance", "Tint", "Tritone", "Color Temperature", "Selective Color", "LUT", "Black & White")
        planned("Glow & Lighting", "Glow", "Advanced Glow", "Layered Glow", "Neon Glow", "Bloom", "Light Rays",
            "God Rays", "Lens Flare", "Lens Flare Pro", "Light Leaks", "Light Sweep", "Spotlight")
        planned("Distortion", "Turbulent Displacement", "Displacement Map", "Wave Warp", "Twirl", "Bulge", "Ripple",
            "Liquify", "Spherize", "Pinch", "Mirror", "Corner Pin", "Heat Distortion", "Lens Distortion")
        planned("Glitch & RGB", "RGB Split", "Chromatic Aberration", "Digital Glitch", "VHS Glitch", "Pixel Sorting",
            "Datamosh", "Scanlines", "Signal Distortion", "Pixel Displacement", "Color Separation")
        planned("Motion & Camera", "Camera Shake", "Smooth Shake", "Motion Blur", "Directional Motion Blur",
            "Zoom Motion", "Camera Zoom", "Camera Rotation", "Camera Tracking", "Handheld Movement", "Motion Trails", "Echo")
        planned("Particles", "Particle Systems", "Particle World", "Particle Emitter", "Snow", "Rain", "Fire Particles",
            "Smoke Particles", "Sparks", "Energy Particles", "Star Particles", "Magic Particles", "Dust")
        planned("Stylize", "Cartoon", "Comic", "Anime Shading", "Edge Detection", "Emboss", "Mosaic", "Pixelate",
            "Halftone", "Posterize", "Film Grain", "Noise", "Sharpen", "Vignette", "Outline")
        planned("Keying & Masking", "Chroma Key", "Green Screen Key", "Luma Key", "Color Range", "Spill Suppression",
            "Background Removal", "Mask Blur", "Mask Feather", "Mask Expansion", "Edge Refinement")
        planned("Transform", "Position", "Scale", "Rotation", "Anchor Point", "Perspective", "Mesh Warp",
            "Corner Pin", "Flip", "Mirror", "Repeat", "Offset")
        planned("Transitions", "Linear Wipe", "Radial Wipe", "Zoom Transition", "Blur Transition", "Glitch Transition",
            "RGB Transition", "Flash Transition", "Light Leak Transition", "Spin Transition", "Shake Transition",
            "Distortion Transition", "Dissolve")
        planned("Texture & Film", "Film Grain", "VHS", "Retro Film", "Dust & Scratches", "Film Damage", "Noise",
            "Paper Texture", "Old TV", "Scanlines", "Light Leaks")
        planned("Audio Reactive", "Audio Spectrum", "Audio Waveform", "Bass Reactive", "Beat Pulse", "Audio Shake",
            "Audio Glow", "Frequency Displacement")
    }
}

class Key(var t: Float, val v: FloatArray)

class Layer(
    val name: String,
    val kind: Int,
    val bmp: Bitmap?,
    val w: Float,
    val h: Float,
    val color: Int
) {
    companion object {
        const val IMAGE = 0
        const val SOLID = 1
        const val NULL = 2
    }

    var parent: Layer? = null
    val base = floatArrayOf(540f, 960f, 1f, 0f, 1f)
    val keys = mutableListOf<Key>()
    val effects = mutableListOf<Effect>()
    private var blurKey = -1
    private var blurBmp: Bitmap? = null

    fun propsAt(t: Float): FloatArray {
        if (keys.isEmpty()) return base.copyOf()
        if (t <= keys.first().t) return keys.first().v.copyOf()
        if (t >= keys.last().t) return keys.last().v.copyOf()
        for (i in 0 until keys.size - 1) {
            val a = keys[i]
            val b = keys[i + 1]
            if (t >= a.t && t <= b.t) {
                val f = (t - a.t) / (b.t - a.t)
                val e = f * f * (3f - 2f * f)
                return FloatArray(5) { a.v[it] + (b.v[it] - a.v[it]) * e }
            }
        }
        return base.copyOf()
    }

    fun setProps(t: Float, v: FloatArray) {
        if (keys.isEmpty()) {
            v.copyInto(base)
            return
        }
        val k = keys.firstOrNull { abs(it.t - t) < 0.02f }
        if (k != null) v.copyInto(k.v) else {
            keys.add(Key(t, v.copyOf()))
            keys.sortBy { it.t }
        }
    }

    fun matrix(t: Float): Matrix {
        val m = parent?.matrix(t) ?: Matrix()
        val p = propsAt(t)
        m.preTranslate(p[P_X], p[P_Y])
        m.preRotate(p[P_ROT])
        m.preScale(p[P_SC], p[P_SC])
        return m
    }

    fun blurred(amount: Float): Bitmap {
        val src = bmp!!
        val f = amount.toInt().coerceIn(1, 40)
        var b = blurBmp
        if (f != blurKey || b == null) {
            b = Bitmap.createScaledBitmap(src, maxOf(1, src.width / f), maxOf(1, src.height / f), true)
            blurBmp = b
            blurKey = f
        }
        return b!!
    }
}

class Project {
    val layers = mutableListOf<Layer>()
    var selected: Layer? = null
    var time = 0f
    val duration = 10f
    val w = 1080f
    val h = 1920f
}
