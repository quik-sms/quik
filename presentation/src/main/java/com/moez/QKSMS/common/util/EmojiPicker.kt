/*
 * Copyright (C) 2017 Moez Bhatti <moez.bhatti@gmail.com>
 *
 * This file is part of QKSMS.
 *
 * QKSMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QKSMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QKSMS.  If not, see <http://www.gnu.org/licenses/>.
 */
package dev.octoshrimpy.quik.common.util

import android.content.Context
import android.graphics.Paint
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.util.extensions.dpToPx
import dev.octoshrimpy.quik.common.util.extensions.resolveThemeColor

/**
 * A lightweight, dependency-free emoji picker. Renders a themed, scrollable grid of emoji in a
 * bottom sheet so it follows Quik's light/dark/black themes (unlike androidx emoji2-emojipicker,
 * which pulled in Guava and ignored the app theme — see project memory).
 */
object EmojiPicker {

    // Popular reactions shown first. These include emoji-presentation sequences (base char +
    // U+FE0F) that must be spelled out to render in colour — plain codepoint iteration would
    // show them as black-and-white text glyphs.
    private val commonEmojis = listOf(
        "❤️", "👍", "👎", "😂", "😮", "😢", "🙏", "🔥", "🎉", "👏",
        "💯", "😍", "🥰", "😊", "🤔", "😎", "😭", "😅", "😡", "🤣",
        "😘", "👀", "✨", "⭐", "✅", "❌", "✌️", "🤝", "🙌", "💪"
    )

    // Unicode blocks that default to emoji (colour) presentation without a variation selector,
    // so they enumerate cleanly. Sequences/flags/skin-tones aren't covered by codepoint walking.
    private val emojiRanges = listOf(
        0x1F300..0x1F5FF, // Misc Symbols and Pictographs
        0x1F600..0x1F64F, // Emoticons
        0x1F680..0x1F6FF, // Transport and Map
        0x1F900..0x1F9FF, // Supplemental Symbols and Pictographs
        0x1FA70..0x1FAFF  // Symbols and Pictographs Extended-A
    )

    /** Common emoji first, then every renderable codepoint from the colour-emoji ranges. */
    val emojis: List<String> by lazy {
        val paint = Paint()
        val generated = emojiRanges.asSequence()
            .flatMap { it.asSequence() }
            .mapNotNull { cp ->
                val s = String(Character.toChars(cp))
                if (paint.hasGlyph(s)) s else null
            }
        (commonEmojis.asSequence() + generated).distinct().toList()
    }

    /**
     * Show the emoji grid in a bottom sheet. [onPicked] receives the chosen emoji and the sheet
     * dismisses. [context] must be a themed (activity) context.
     */
    fun show(context: Context, onPicked: (String) -> Unit) {
        val backgroundColor = context.resolveThemeColor(android.R.attr.windowBackground)
        val textColor = context.resolveThemeColor(android.R.attr.textColorPrimary)
        val cell = 48.dpToPx(context)
        val span = (context.resources.displayMetrics.widthPixels / cell).coerceAtLeast(6)

        val sheet = BottomSheetDialog(context)

        val recycler = RecyclerView(context).apply {
            setBackgroundColor(backgroundColor)
            layoutManager = GridLayoutManager(context, span)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (context.resources.displayMetrics.heightPixels * 0.5f).toInt()
            )
            adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                    object : RecyclerView.ViewHolder(TextView(context).apply {
                        textSize = 22f
                        gravity = Gravity.CENTER
                        includeFontPadding = false
                        setTextColor(textColor)
                        setBackgroundResource(R.drawable.ripple)
                        layoutParams = RecyclerView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, cell
                        )
                    }) {}

                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    val emoji = emojis[position]
                    (holder.itemView as TextView).text = emoji
                    holder.itemView.setOnClickListener {
                        onPicked(emoji)
                        sheet.dismiss()
                    }
                }

                override fun getItemCount() = emojis.size
            }
        }

        sheet.setContentView(recycler)
        sheet.show()
    }
}
