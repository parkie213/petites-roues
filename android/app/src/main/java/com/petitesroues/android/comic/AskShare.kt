package com.petitesroues.android.comic

import android.content.Context
import android.content.Intent

/**
 * Hands an [AskPack] message to a chat app through Android's share sheet, as plain text so the
 * receiving app can start a new chat with it or the learner can paste it into an existing one.
 */
object AskShare {
  fun share(context: Context, message: String) {
    val send =
      Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
      }
    context.startActivity(Intent.createChooser(send, "Ask with…"))
  }
}
