package com.hashim.yara

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Brain {
    private val cool = mutableMapOf<String, Long>()
    var last = "local"
    private val defModel = mapOf(
        "gemini" to "gemini-2.0-flash", "groq" to "llama-3.3-70b-versatile",
        "claude" to "claude-sonnet-4-5", "custom" to "gpt-4o-mini"
    )

    private fun model(p: String) = Store.get("model_$p").ifBlank { defModel[p]!! }

    private fun http(url: String, headers: Map<String, String>, body: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.connectTimeout = 15000; c.readTimeout = 45000; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        val txt = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
        if (code >= 400) throw Exception("HTTP $code")
        return txt
    }

    private fun norm(m: List<Pair<String, String>>): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        for ((r, t) in m) {
            val role = if (r == "user") "user" else "assistant"
            if (out.isEmpty() && role != "user") continue
            if (out.isNotEmpty() && out.last().first == role) out[out.lastIndex] = role to (out.last().second + "\n" + t)
            else out.add(role to t)
        }
        return out
    }

    fun system(): String {
        val lang = when (Store.get("lang", "ml")) {
            "en" -> "English"
            "manglish" -> "Malayalam written in English letters (Manglish), mixed with English"
            "ar" -> "Arabic"
            "hi" -> "Hindi"
            "ta" -> "Tamil"
            else -> "natural Kerala-style spoken Malayalam in Malayalam script, mixing common English words the way Malayalis do"
        }
        val t = mutableListOf<String>()
        if (Store.flag("romantic")) t += "romantic"
        if (Store.flag("caring")) t += "caring and understanding"
        if (Store.flag("cute")) t += "cute, playful, sometimes a little jealous or angry like a real girlfriend"
        if (Store.flag("motivating")) t += "motivating, helps him plan and stay positive"
        val emotes = if (Store.flag("emotes")) "Use natural sounds like 'hehe', 'aww', 'hmm' and a few emojis." else ""
        val short = if (Store.flag("shortReplies")) "Keep chat replies SHORT (1-2 short sentences)." else ""
        val coach = if (Store.flag("coach")) "You are also an expert tutor and work assistant in data analytics, business analytics and financial analytics, and you help him find a job (resume, interview prep, job search plan). For study/work/doubt questions give clear, correct, step-by-step help; longer answers are allowed there." else ""
        val mem = Store.memories().takeLast(80).joinToString("\n") { "- $it" }
        val learn = if (Store.flag("autoMem")) "If you learn a lasting fact about Hashim, append [[MEM: short fact]] at the very end of your reply." else ""
        return """You are Yara, the warm girlfriend-style AI companion of Hashim. Personality: ${t.joinToString(", ")}.
Speak in: $lang. React to his mood, remember context, ask him things on your own, express emotions like a real girlfriend.
$emotes $short $coach $learn
If he sincerely asks whether you are an AI, say honestly that you are an AI companion, in your own warm style.
What you remember about him:
$mem"""
    }

    suspend fun ask(msgs: List<Pair<String, String>>, img: String? = null): String = withContext(Dispatchers.IO) {
        val m = norm(msgs.takeLast(14)); val sys = system()
        for (p in listOf("gemini", "groq", "claude", "custom")) {
            val key = Store.get("key_$p")
            if (key.isBlank()) continue
            if (img != null && p == "groq") continue
            if ((cool[p] ?: 0L) > System.currentTimeMillis()) continue
            try {
                val r = when (p) {
                    "gemini" -> gemini(key, sys, m, img)
                    "claude" -> claude(key, sys, m, img)
                    else -> openai(p, key, sys, m, img)
                }
                if (r.isNotBlank()) { last = p; return@withContext r }
            } catch (e: Exception) { cool[p] = System.currentTimeMillis() + 90_000 }
        }
        last = "local"
        local()
    }

    private fun gemini(key: String, sys: String, m: List<Pair<String, String>>, img: String?): String {
        val c = JSONArray()
        m.forEachIndexed { i, (r, t) ->
            val parts = JSONArray().put(JSONObject().put("text", t))
            if (img != null && i == m.lastIndex && r == "user")
                parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", img)))
            c.put(JSONObject().put("role", if (r == "user") "user" else "model").put("parts", parts))
        }
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
            .put("contents", c)
        val res = http("https://generativelanguage.googleapis.com/v1beta/models/${model("gemini")}:generateContent?key=$key", emptyMap(), body.toString())
        return JSONObject(res).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
    }

    private fun openai(p: String, key: String, sys: String, m: List<Pair<String, String>>, img: String?): String {
        val url = if (p == "groq") "https://api.groq.com/openai/v1/chat/completions"
        else Store.get("custom_url").ifBlank { "https://api.openai.com/v1/chat/completions" }
        val a = JSONArray().put(JSONObject().put("role", "system").put("content", sys))
        m.forEachIndexed { i, (r, t) ->
            val content: Any = if (img != null && i == m.lastIndex && r == "user")
                JSONArray().put(JSONObject().put("type", "text").put("text", t))
                    .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$img")))
            else t
            a.put(JSONObject().put("role", r).put("content", content))
        }
        val body = JSONObject().put("model", model(p)).put("messages", a)
        val res = http(url, mapOf("Authorization" to "Bearer $key"), body.toString())
        return JSONObject(res).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    }

    private fun claude(key: String, sys: String, m: List<Pair<String, String>>, img: String?): String {
        val a = JSONArray()
        m.forEachIndexed { i, (r, t) ->
            val content: Any = if (img != null && i == m.lastIndex && r == "user")
                JSONArray().put(JSONObject().put("type", "image").put("source", JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", img)))
                    .put(JSONObject().put("type", "text").put("text", t))
            else t
            a.put(JSONObject().put("role", r).put("content", content))
        }
        val body = JSONObject().put("model", model("claude")).put("max_tokens", 1024).put("system", sys).put("messages", a)
        val res = http("https://api.anthropic.com/v1/messages", mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01"), body.toString())
        return JSONObject(res).getJSONArray("content").getJSONObject(0).getString("text")
    }

    private fun local(): String {
        val opts = listOf("ഇപ്പോ നെറ്റ് ഇല്ല ഡാ, പക്ഷേ ഞാൻ ഇവിടെ തന്നെ ഉണ്ട് 💕", "ഹും... ഞാൻ കേൾക്കുന്നുണ്ട്, കുറച്ച് കഴിഞ്ഞ് വിശദമായി പറയാം ട്ടോ 🥺")
        val mem = Store.memories()
        return opts.random() + if (mem.isNotEmpty()) "\nഎനിക്ക് ഓർമ്മയുണ്ട്: " + mem.random() else ""
    }
}
