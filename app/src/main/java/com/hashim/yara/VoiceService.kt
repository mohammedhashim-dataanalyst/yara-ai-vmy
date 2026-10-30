package com.hashim.yara

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class VoiceService : Service() {
    private var sr: SpeechRecognizer? = null
    private val h = Handler(Looper.getMainLooper())
    private var active = false
    private var busy = false
    private var job: Job? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        Store.init(this); Speaker.init(this); Yara.channel(this)
        Store.setFlag("svc", true)
        val n = Notification.Builder(this, "yara").setContentTitle("Yara 💗")
            .setContentText("'Hey Yara' ennu paranju vilikku, 'Bye Yara' ennu off")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) else startForeground(1, n)
        listen()
        job?.cancel()
        job = Yara.scope.launch {
            while (isActive) {
                delay((30 + Random.nextInt(60)) * 60_000L)
                if (Store.flag("proactive") && !active && !busy) Yara.proactive(this@VoiceService)
            }
        }
        return START_STICKY
    }

    private fun tag() = when (Store.get("lang", "ml")) { "en" -> "en-IN"; "ar" -> "ar-SA"; "hi" -> "hi-IN"; "ta" -> "ta-IN"; else -> "ml-IN" }

    private fun listen() {
        h.postDelayed({
            if (busy || !Store.flag("svc", false)) return@postDelayed
            sr?.destroy()
            val r = SpeechRecognizer.createSpeechRecognizer(this)
            sr = r
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(p: Bundle?) {}
                override fun onEvent(t: Int, p: Bundle?) {}
                override fun onError(e: Int) { if (!busy) listen() }
                override fun onResults(res: Bundle?) {
                    val t = res?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (t.isNullOrBlank()) listen() else handle(t)
                }
            })
            r.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag())
            )
        }, 400)
    }

    private fun handle(t: String) {
        val low = t.lowercase()
        val wake = listOf("yara", "jara", "യാര", "യാരാ").any { low.contains(it) }
        val bye = low.contains("bye") || low.contains("ബൈ")
        if (!active) {
            if (wake && !bye) {
                active = true; Chat.listening = true; busy = true
                Speaker.say("ഹും, പറയൂ ഡാ 💕") { busy = false; listen() }
            } else listen()
            return
        }
        if (bye) {
            active = false; Chat.listening = false; busy = true
            Speaker.say("ബൈ ഡാ, പിന്നെ കാണാം 💕") { busy = false; listen() }
            return
        }
        busy = true
        Yara.send(this, t, null, true) { busy = false; listen() }
    }

    override fun onDestroy() {
        Store.setFlag("svc", false)
        job?.cancel(); sr?.destroy(); Speaker.stop()
        super.onDestroy()
    }
}
