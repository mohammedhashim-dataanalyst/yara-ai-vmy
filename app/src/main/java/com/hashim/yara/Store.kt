package com.hashim.yara

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object Chat {
    val msgs = mutableStateListOf<Pair<String, String>>()
    var busy by mutableStateOf(false)
    var listening by mutableStateOf(false)
    fun load() { if (msgs.isEmpty()) msgs.addAll(Store.history()) }
    fun add(role: String, text: String) { msgs.add(role to text); Store.saveHistory(msgs.toList()) }
}

object Store {
    lateinit var sp: SharedPreferences
    lateinit var dir: File
    fun init(c: Context) {
        if (!::sp.isInitialized) {
            sp = c.applicationContext.getSharedPreferences("yara", 0)
            dir = c.applicationContext.filesDir
        }
    }
    fun get(k: String, d: String = "") = sp.getString(k, d) ?: d
    fun put(k: String, v: String) = sp.edit().putString(k, v).apply()
    fun flag(k: String, d: Boolean = true) = sp.getBoolean(k, d)
    fun setFlag(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()

    private val memFile get() = File(dir, "memories.txt")
    fun memories(): List<String> = if (memFile.exists()) memFile.readLines().filter { it.isNotBlank() } else emptyList()
    fun addMemory(s: String) {
        val t = s.trim().replace("\n", " ")
        if (t.isNotBlank() && t !in memories()) memFile.appendText(t + "\n")
    }
    fun clearMemories() { memFile.delete() }

    private val histFile get() = File(dir, "history.json")
    fun history(): List<Pair<String, String>> = try {
        val a = JSONArray(histFile.readText())
        (0 until a.length()).map { a.getJSONObject(it).let { o -> o.getString("r") to o.getString("t") } }
    } catch (e: Exception) { emptyList() }
    fun saveHistory(l: List<Pair<String, String>>) {
        val a = JSONArray()
        l.takeLast(500).forEach { a.put(JSONObject().put("r", it.first).put("t", it.second)) }
        histFile.writeText(a.toString())
    }

    fun export(): String {
        val o = JSONObject()
        o.put("memories", JSONArray(memories()))
        val h = JSONArray()
        history().forEach { h.put(JSONObject().put("r", it.first).put("t", it.second)) }
        o.put("history", h)
        val p = JSONObject()
        sp.all.forEach { (k, v) -> if (!k.startsWith("key_")) p.put(k, v) }
        o.put("prefs", p)
        return o.toString()
    }

    fun import(s: String) {
        val o = JSONObject(s)
        o.optJSONArray("memories")?.let { a -> for (i in 0 until a.length()) addMemory(a.getString(i)) }
        o.optJSONArray("history")?.let { h ->
            val l = (0 until h.length()).map { h.getJSONObject(it).let { x -> x.getString("r") to x.getString("t") } }
            saveHistory(l); Chat.msgs.clear(); Chat.msgs.addAll(l)
        }
        o.optJSONObject("prefs")?.let { p ->
            val e = sp.edit()
            p.keys().forEach { k ->
                when (val v = p.get(k)) {
                    is Boolean -> e.putBoolean(k, v)
                    is String -> e.putString(k, v)
                }
            }
            e.apply()
        }
    }
}
