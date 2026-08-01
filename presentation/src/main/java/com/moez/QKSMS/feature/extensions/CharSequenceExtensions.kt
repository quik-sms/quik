package dev.octoshrimpy.quik.feature.extensions

import android.text.Spannable
import androidx.emoji2.text.EmojiCompat
import androidx.emoji2.text.EmojiCompat.REPLACE_STRATEGY_ALL
import androidx.emoji2.text.EmojiSpan


fun CharSequence.isEmojiOnly(considerWhitespace: Boolean = false): Boolean {
    val cs =
        if (considerWhitespace) this
        else this.replace(Regex("[\\s\n\r]"), "")

    if (cs.isEmpty())
        return false

    // EmojiCompat loads its font asynchronously; calling process() before it has finished loading
    // throws "Not initialized yet". Until it's ready, treat the text as not emoji-only (it'll be
    // re-evaluated once the view rebinds after loading completes).
    val emojiCompat = try {
        EmojiCompat.get()
    } catch (e: IllegalStateException) {
        return false
    }
    if (emojiCompat.loadState != EmojiCompat.LOAD_STATE_SUCCEEDED)
        return false

    return when (val spannable = emojiCompat.process(
        cs,
        0,
        (cs.length - 1),
        Int.MAX_VALUE,
        REPLACE_STRATEGY_ALL
    )) {
        is Spannable -> {
            (spannable
                .getSpans(0, (spannable.length - 1), EmojiSpan::class.java)
                .fold(0) { acc, emojiSpan ->
                    acc + (spannable.getSpanEnd(emojiSpan) - spannable.getSpanStart(emojiSpan))
                } == cs.length)
        }
        else -> false
    }
}