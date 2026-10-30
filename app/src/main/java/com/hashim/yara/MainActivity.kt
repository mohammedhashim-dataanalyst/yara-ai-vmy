package com.hashim.yara

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

val Pink = Color(0xFFC2185B)

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Store.init(this); Speaker.init(this); Chat.load()
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Pink)) { App() } }
    }
}

@Composable
fun App() {
    var screen by remember { mutableStateOf("chat") }
    Box(Modifier.fillMaxSize().background(Color.White).systemBarsPadding()) {
        when (screen) {
            "settings" -> SettingsScreen { screen = "chat" }
            "about" -> AboutScreen { screen = "chat" }
            else -> ChatScreen { screen = it }
        }
    }
}

fun toB64(c: Context, u: Uri): String? = try {
    val bm = BitmapFactory.decodeStream(c.contentResolver.openInputStream(u))!!
    val s = 1024f / maxOf(bm.width, bm.height).coerceAtLeast(1)
    val b = if (s < 1f) Bitmap.createScaledBitmap(bm, (bm.width * s).toInt(), (bm.height * s).toInt(), true) else bm
    val o = java.io.ByteArrayOutputStream()
    b.compress(Bitmap.CompressFormat.JPEG, 80, o)
    Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP)
} catch (e: Exception) { null }

fun perms(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    else arrayOf(Manifest.permission.RECORD_AUDIO)

@Composable
fun ChatScreen(nav: (String) -> Unit) {
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    var on by remember { mutableStateOf(Store.flag("svc", false)) }
    val list = rememberLazyListState()
    LaunchedEffect(Chat.msgs.size) { if (Chat.msgs.isNotEmpty()) list.animateScrollToItem(Chat.msgs.size - 1) }

    fun startSvc() {
        ContextCompat.startForegroundService(ctx, Intent(ctx, VoiceService::class.java)); on = true
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startSvc()
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u: Uri? ->
        u?.let {
            val b64 = toB64(ctx, it)
            Yara.send(ctx, input.ifBlank { "ഇത് നോക്കൂ 😊" }, b64, Store.flag("speakTyped", false)); input = ""
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Yara 💗", style = MaterialTheme.typography.titleLarge, color = Pink)
                Text(if (Chat.busy) "typing..." else if (Chat.listening) "listening 👂" else if (on) "'Hey Yara' paranju vilikku" else "online", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { nav("about") }) { Text("ℹ️") }
            TextButton(onClick = { nav("settings") }) { Text("⚙️") }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), state = list, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(Chat.msgs.toList()) { (r, t) ->
                val me = r == "user"
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (me) Arrangement.End else Arrangement.Start) {
                    Box(Modifier.widthIn(max = 290.dp).background(if (me) Pink else Color(0xFFFCE4EC), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(t, color = if (me) Color.White else Color.Black)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { pick.launch("image/*") }) { Text("🖼️") }
            TextButton(onClick = {
                if (on) { ctx.stopService(Intent(ctx, VoiceService::class.java)); on = false; Chat.listening = false }
                else permLauncher.launch(perms())
            }) { Text(if (on) "🔴" else "🎙️") }
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Yara kku ezhuthu...") }, maxLines = 3)
            TextButton(onClick = {
                if (input.isNotBlank()) { Yara.send(ctx, input.trim(), null, Store.flag("speakTyped", false)); input = "" }
            }) { Text("➤") }
        }
    }
}

@Composable
fun Field(label: String, key: String, secret: Boolean = false) {
    var v by remember { mutableStateOf(Store.get(key)) }
    OutlinedTextField(
        v, { v = it; Store.put(key, it) }, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None
    )
}

@Composable
fun Toggle(label: String, key: String, def: Boolean = true) {
    var v by remember { mutableStateOf(Store.flag(key, def)) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(v, { v = it; Store.setFlag(key, it) })
    }
}

@Composable
fun Head(t: String) { Text(t, Modifier.padding(top = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.titleMedium, color = Pink) }

@Composable
fun SettingsScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    var lang by remember { mutableStateOf(Store.get("lang", "ml")) }
    val mem = remember { mutableStateListOf<String>().apply { addAll(Store.memories()) } }
    var newMem by remember { mutableStateOf("") }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u: Uri? ->
        u?.let { ctx.contentResolver.openOutputStream(it)?.use { o -> o.write(Store.export().toByteArray()) } }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        u?.let {
            try {
                Store.import(ctx.contentResolver.openInputStream(it)!!.bufferedReader().readText())
                mem.clear(); mem.addAll(Store.memories())
            } catch (e: Exception) {}
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = back) { Text("← Back") }
            Text("Yara Settings", style = MaterialTheme.typography.titleLarge)
        }
        Head("Brain (API keys) — auto switch")
        Text("Order: Gemini → Groq → Claude → Custom. Limit theernnal automatic aayi ente ammaduthathilekku maarum; ellam poyal local memory reply.", style = MaterialTheme.typography.bodySmall)
        Field("Gemini API key", "key_gemini", true); Field("Gemini model (optional)", "model_gemini")
        Field("Groq API key", "key_groq", true); Field("Groq model (optional)", "model_groq")
        Field("Claude API key", "key_claude", true); Field("Claude model (optional)", "model_claude")
        Field("Custom (OpenAI-compatible) key", "key_custom", true)
        Field("Custom URL (…/v1/chat/completions)", "custom_url"); Field("Custom model", "model_custom")

        Head("Language")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("ml" to "മലയാളം", "manglish" to "Manglish", "en" to "English", "hi" to "हिन्दी", "ta" to "தமிழ்", "ar" to "العربية").forEach { (k, n) ->
                if (lang == k) Button(onClick = {}) { Text(n) }
                else OutlinedButton(onClick = { lang = k; Store.put("lang", k) }) { Text(n) }
            }
        }
        Field("Voice speed (1.0 normal)", "rate")

        Head("Voice & display")
        Toggle("Voice replies (wake word mode)", "voice")
        Toggle("Typed chat reply speak cheyyanam", "speakTyped", false)
        Toggle("Other apps nte mukalil reply text kaanikkuka", "overlay", false)
        Toggle("Yara swayam samsaram thudangaam", "proactive")

        Head("Personality")
        Toggle("Short replies", "shortReplies"); Toggle("Romantic", "romantic"); Toggle("Caring", "caring")
        Toggle("Cute / jealous / angry moods", "cute"); Toggle("Motivating", "motivating"); Toggle("hehe / aww sounds & emojis", "emotes")
        Toggle("Work & analytics coach (data / business / finance)", "coach"); Toggle("Auto-save memories", "autoMem")

        Head("Permissions")
        Button(onClick = { permLauncher.launch(perms()) }, Modifier.fillMaxWidth()) { Text("Microphone & notifications") }
        OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName))) }, Modifier.fillMaxWidth()) { Text("Draw over other apps") }
        OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }, Modifier.fillMaxWidth()) { Text("Battery: Yara unrestricted aakkuka") }

        Head("Memories (${mem.size})")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(newMem, { newMem = it }, Modifier.weight(1f), placeholder = { Text("Yara orkkendath...") })
            TextButton(onClick = { Store.addMemory(newMem); newMem = ""; mem.clear(); mem.addAll(Store.memories()) }) { Text("Add") }
        }
        mem.takeLast(30).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        OutlinedButton(onClick = { Store.clearMemories(); mem.clear() }, Modifier.fillMaxWidth()) { Text("Clear memories") }

        Head("Backup")
        Button(onClick = { export.launch("yara-backup.json") }, Modifier.fillMaxWidth()) { Text("Export backup (memories + chat)") }
        OutlinedButton(onClick = { import.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth()) { Text("Import backup") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun AboutScreen(back: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = back) { Text("← Back") }
        Text("Yara 💗  v1.0", style = MaterialTheme.typography.headlineSmall, color = Pink)
        Text("Created by Hashim\nInstagram: @hashim_m4x")
        Text("Features: Malayalam/English voice chat, wake word 'Hey Yara' / 'Bye Yara', text + photo chat, on-screen reply overlay, proactive messages, auto-saved memories, multi-API auto failover (Gemini, Groq, Claude, custom), app/WhatsApp/call/search commands ('open youtube', 'whatsapp hi', 'call 99..', 'search ...'), backup & restore, data/business/finance analytics coach.")
    }
}
