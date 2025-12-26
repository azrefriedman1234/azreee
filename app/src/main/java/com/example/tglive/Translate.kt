package com.example.tglive

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object Translate {
    private val client = OkHttpClient()
    private val jsonType = "application/json".toMediaType()

    // Free (public) LibreTranslate endpoint. You can replace it in Settings.
    var libreUrl: String = "https://libretranslate.de/translate"

    private fun containsHebrew(s: String): Boolean {
        return s.any { it.code in 0x0590..0x05FF }
    }

    fun toHebrewIfNeeded(text: String): String {
        if (text.isBlank() || containsHebrew(text)) return text
        return try {
            val body = JSONObject()
                .put("q", text)
                .put("source", "auto")
                .put("target", "he")
                .put("format", "text")
                .toString()
                .toRequestBody(jsonType)

            val req = Request.Builder().url(libreUrl).post(body).build()
            val res = client.newCall(req).execute()
            val s = res.body?.string().orEmpty()
            val j = JSONObject(s)
            j.optString("translatedText", text)
        } catch (_: Exception) {
            text
        }
    }
}
