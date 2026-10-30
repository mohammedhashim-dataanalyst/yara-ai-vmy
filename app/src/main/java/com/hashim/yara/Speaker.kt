package com.hashim.yara

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

object Speaker {
    private var tts: TextToSpeech? = null
    private var ready = false
    fun init(c: Context) {
        if (tts == null) tts = TextToSpeech(c.applicationContext) { ready = it == TextToSpeech.SUCCESS }
    }
    fun locale(): Locale = when (Store.get("lang", "ml")) {
        "en" -> Locale("en", "IN"); "ar" -> Locale("ar"); "hi" -> Locale("hi", "IN"); "ta" -> Locale("ta", "IN")
        else -> Locale("ml", "IN")
    }
    fun say(text: String, done: () -> Unit = {}) {
        val t = tts
        if (t == null || !ready || !Store.flag("voice")) { done(); return }
        val clean = text.replace(Regex("[\\p{So}\\p{Cn}]"), "").trim()
        if (clean.isEmpty()) { done(); return }
        val finish = { Handler(Looper.getMainLooper()).post { done() }; Unit }
        t.language = locale()
        t.setPitch(1.15f)
        t.setSpeechRate(Store.get("rate", "1.0").toFloatOrNull() ?: 1f)
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { finish() }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { finish() }
        })
        t.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "yara")
    }
    fun stop() { tts?.stop() }
}
