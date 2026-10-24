package com.csa.xreadpdf

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Informations attachées à un document de « Mes PDF » (clé = nom du fichier). */
data class DocMeta(
    val folder: String? = null,
    val favorite: Boolean = false,
    val lastPage: Int = 0,
    val lastOpened: Long = 0L,
    val bookmarks: Set<Int> = emptySet(),
)

data class Library(val docs: Map<String, DocMeta> = emptyMap(), val folders: List<String> = emptyList()) {
    fun of(file: File): DocMeta = docs[file.name] ?: DocMeta()
}

/** Dossiers, favoris, récents, dernière page lue et signets : un fichier JSON dans filesDir. */
class LibraryMetaStore(context: Context) {

    private val file = File(context.filesDir, "library.json")

    fun load(): Library = loadFrom(file)

    fun loadFrom(source: File): Library = runCatching {
        val o = JSONObject(source.readText())
        val docs = o.optJSONObject("docs") ?: JSONObject()
        val map = docs.keys().asSequence().associateWith { k ->
            val d = docs.getJSONObject(k)
            val bm = d.optJSONArray("bookmarks") ?: JSONArray()
            DocMeta(
                folder = d.optString("folder").takeIf { it.isNotEmpty() },
                favorite = d.optBoolean("favorite"),
                lastPage = d.optInt("lastPage"),
                lastOpened = d.optLong("lastOpened"),
                bookmarks = (0 until bm.length()).map { bm.getInt(it) }.toSet(),
            )
        }
        val f = o.optJSONArray("folders") ?: JSONArray()
        Library(map, (0 until f.length()).map { f.getString(it) })
    }.getOrDefault(Library())

    fun save(lib: Library) {
        val docs = JSONObject()
        lib.docs.forEach { (k, d) ->
            if (d == DocMeta()) return@forEach
            docs.put(k, JSONObject().apply {
                d.folder?.let { put("folder", it) }
                if (d.favorite) put("favorite", true)
                if (d.lastPage > 0) put("lastPage", d.lastPage)
                if (d.lastOpened > 0) put("lastOpened", d.lastOpened)
                if (d.bookmarks.isNotEmpty()) put("bookmarks", JSONArray(d.bookmarks.sorted()))
            })
        }
        val json = JSONObject().put("docs", docs).put("folders", JSONArray(lib.folders))
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(file)) { file.writeText(json.toString()); tmp.delete() }
    }

    val path: File get() = file
}
