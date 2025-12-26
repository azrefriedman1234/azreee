package com.example.tglive

import org.drinkless.tdlib.TdApi

data class UiMessage(
    val chatId: Long,
    val messageId: Long,
    val date: Long,
    val rawText: String,
    val textHe: String,
    val hasMedia: Boolean,
    val content: TdApi.MessageContent?
)

data class NormRect(val x: Float, val y: Float, val w: Float, val h: Float) // normalized 0..1
