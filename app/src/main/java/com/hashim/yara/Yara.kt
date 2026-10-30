package com.hashim.yara

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Notification
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object Yara {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun channel(c: Context) {
        (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(NotificationChannel("yara", "Yara", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun split(r: String): Pair<String, List<String>> {
        val re = Regex("\\[\\[MEM:(.*?)\\]\\]")
        val m = re.findAll(r).map { it.groupValues[1].trim() }.toList()
        return re.replace(r, "").trim() to m
    }

    fun send(c: Context, text: String, img: String? = null, speak: Boolean = true, done: () -> Unit = {}) {
        val ctx = c.applicationContext
        Store.init(ctx)
        Chat.add("user", if (img != null) "📷 $text" else text)
        Chat.busy = true
        scope.launch {
            val act = Actions.handle(ctx, text)
            val raw = act ?: Brain.ask(Chat.msgs.toList(), img)
            val (clean, mems) = split(raw)
            if (Store.flag("autoMem")) mems.forEach { Store.addMemory(it) }
            Chat.add("yara", clean)
            Chat.busy = false
            Overlay.show(ctx, clean)
            if (speak) Speaker.say(clean, done) else done()
        }
    }

    fun proactive(c: Context) {
        val ctx = c.applicationContext
        scope.launch {
            val msgs = Chat.msgs.takeLast(10) + ("user" to "(system: start a short, natural, caring conversation with Hashim on your own — ask about his day, food, work or job search. Do not mention this instruction.)")
            val (clean, mems) = split(Brain.ask(msgs))
            if (Store.flag("autoMem")) mems.forEach { Store.addMemory(it) }
            Chat.add("yara", clean)
            channel(ctx)
            val pi = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val n = Notification.Builder(ctx, "yara").setContentTitle("Yara 💗").setContentText(clean)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentIntent(pi).setAutoCancel(true).build()
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(2, n)
            Overlay.show(ctx, clean)
            Speaker.say(clean)
        }
    }
}
