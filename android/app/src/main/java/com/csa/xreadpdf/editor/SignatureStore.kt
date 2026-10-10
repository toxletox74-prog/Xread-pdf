package com.csa.xreadpdf.editor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Signatures enregistrées, une par fichier JSON (tracés vectoriels) dans filesDir/signatures. */
class SignatureStore(context: Context) {

    private val dir = File(context.filesDir, "signatures").apply { mkdirs() }

    fun list(): List<Signature> =
        (dir.listFiles { f -> f.extension == "json" } ?: emptyArray())
            .sortedByDescending { it.lastModified() }
            .mapNotNull { f -> runCatching { parse(f.nameWithoutExtension, f.readText()) }.getOrNull() }

    fun save(sig: Signature) {
        val strokes = JSONArray()
        sig.strokes.forEach { s ->
            val arr = JSONArray()
            s.forEach { p -> arr.put(round(p.x)); arr.put(round(p.y)) }
            strokes.put(arr)
        }
        val json = JSONObject()
            .put("aspect", sig.aspect.toDouble())
            .put("color", sig.color)
            .put("width", sig.width.toDouble())
            .put("strokes", strokes)
        File(dir, "${sig.id}.json").writeText(json.toString())
    }

    fun delete(sig: Signature) { File(dir, "${sig.id}.json").delete() }

    private fun parse(id: String, text: String): Signature {
        val o = JSONObject(text)
        val arr = o.getJSONArray("strokes")
        val strokes = List(arr.length()) { i ->
            val s = arr.getJSONArray(i)
            List(s.length() / 2) { j -> Pt(s.getDouble(2 * j).toFloat(), s.getDouble(2 * j + 1).toFloat()) }
        }
        return Signature(
            id = id,
            strokes = strokes,
            aspect = o.getDouble("aspect").toFloat().coerceIn(0.2f, 20f),
            color = o.getInt("color"),
            width = o.getDouble("width").toFloat(),
        )
    }

    private fun round(v: Float): Double = Math.round(v * 10000.0) / 10000.0
}
