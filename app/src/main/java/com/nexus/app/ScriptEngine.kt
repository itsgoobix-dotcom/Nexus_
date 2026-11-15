package com.nexus.app

import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined

class ScriptHost(private val plugin: String, private val scope: Scriptable) {
    var count = 0

    private val ident = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f)

    private fun str(o: Scriptable, k: String): String? {
        val v = ScriptableObject.getProperty(o, k)
        if (v === Scriptable.NOT_FOUND || v == null || v === Undefined.instance) return null
        return Context.toString(v)
    }

    private fun num(o: Scriptable, k: String, d: Double): Double {
        val v = ScriptableObject.getProperty(o, k)
        if (v === Scriptable.NOT_FOUND || v == null || v === Undefined.instance) return d
        val n = Context.toNumber(v)
        return if (n.isNaN()) d else n
    }

    fun register(o: Any?) {
        val obj = o as? Scriptable ?: return
        val rawId = str(obj, "id") ?: return
        val name = str(obj, "name") ?: rawId
        val category = str(obj, "category") ?: "Stylize"
        val params = mutableListOf<Param>()
        val arr = ScriptableObject.getProperty(obj, "params")
        if (arr is NativeArray) {
            for (i in 0 until arr.length.toInt()) {
                val p = arr.get(i, arr) as? Scriptable ?: continue
                params.add(Param(
                    str(p, "name") ?: "Value",
                    num(p, "min", 0.0).toFloat(),
                    num(p, "max", 100.0).toFloat(),
                    num(p, "def", 0.0).toFloat()))
            }
        }
        val fn = ScriptableObject.getProperty(obj, "matrix") as? Function ?: return
        val id = "script:" + plugin + ":" + rawId
        val def = EffectDef(id, name, category, params, true, plugin)
        def.matrixFn = { v -> callMatrix(fn, v) }
        val list = Registry.categories.getOrPut(category) { mutableListOf() }
        list.removeAll { it.id == id || (!it.implemented && it.name == name) }
        list.add(def)
        count++
    }

    private fun callMatrix(fn: Function, v: FloatArray): FloatArray {
        val cx = Context.enter()
        try {
            cx.optimizationLevel = -1
            val args = Array<Any?>(v.size) { v[it].toDouble() }
            val res = fn.call(cx, scope, scope, arrayOf<Any?>(cx.newArray(scope, args)))
            if (res is NativeArray && res.length >= 20) {
                return FloatArray(20) { Context.toNumber(res.get(it, res)).toFloat() }
            }
        } catch (t: Throwable) {
        } finally {
            Context.exit()
        }
        return ident
    }
}

object ScriptEngine {
    fun run(code: String, plugin: String): String {
        val cx = Context.enter()
        try {
            cx.optimizationLevel = -1
            val scope = cx.initStandardObjects()
            val host = ScriptHost(plugin, scope)
            ScriptableObject.putProperty(scope, "host", Context.javaToJS(host, scope))
            cx.evaluateString(scope, "function registerEffect(o){ host.register(o); }", "api", 1, null)
            cx.evaluateString(scope, code, plugin, 1, null)
            return "OK: " + host.count + " effect(s) registered"
        } catch (t: Throwable) {
            return "Error: " + t.message
        } finally {
            Context.exit()
        }
    }
}
