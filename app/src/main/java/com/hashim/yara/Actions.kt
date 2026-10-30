package com.hashim.yara

import android.content.Context
import android.content.Intent
import android.net.Uri

object Actions {
    fun handle(c: Context, t: String): String? {
        val s = t.trim().lowercase()
        fun go(i: Intent) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(i) }
        try {
            Regex("^(open|launch)\\s+(.+)$").find(s)?.let { m ->
                val name = m.groupValues[2].trim(); val pm = c.packageManager
                val app = pm.getInstalledApplications(0).firstOrNull {
                    pm.getApplicationLabel(it).toString().lowercase().contains(name) && pm.getLaunchIntentForPackage(it.packageName) != null
                }
                return if (app != null) {
                    go(pm.getLaunchIntentForPackage(app.packageName)!!)
                    "ശരി, ${pm.getApplicationLabel(app)} തുറക്കുന്നു 💕"
                } else "ആ ആപ്പ് കണ്ടില്ല ഡാ 🥺"
            }
            Regex("^whatsapp\\s+(.+)$").find(s)?.let {
                val msg = t.trim().substring(9).trim()
                go(Intent(Intent.ACTION_SEND).setType("text/plain").setPackage("com.whatsapp").putExtra(Intent.EXTRA_TEXT, msg))
                return "WhatsApp തുറന്നു, ആരെ അയക്കണം എന്ന് സെലക്ട് ചെയ്യൂ 💕"
            }
            Regex("^call\\s+([+0-9 ]+)$").find(s)?.let {
                go(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + it.groupValues[1].replace(" ", ""))))
                return "ഡയലർ തുറന്നു ഡാ 📞"
            }
            Regex("^search\\s+(.+)$").find(s)?.let {
                go(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(it.groupValues[1]))))
                return "തിരയുന്നു 🔍"
            }
        } catch (e: Exception) { return "അത് ചെയ്യാൻ പറ്റിയില്ല ഡാ 🥺" }
        return null
    }
}
