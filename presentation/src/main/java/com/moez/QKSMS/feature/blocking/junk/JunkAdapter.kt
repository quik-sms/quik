/*
 * Copyright (C) 2025
 *
 * This file is part of QUIK.
 *
 * QUIK is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package dev.octoshrimpy.quik.feature.blocking.junk

import android.view.LayoutInflater
import android.view.ViewGroup
import dev.octoshrimpy.quik.common.base.QkBindingViewHolder
import dev.octoshrimpy.quik.common.base.QkRealmAdapter
import dev.octoshrimpy.quik.common.util.DateFormatter
import dev.octoshrimpy.quik.databinding.JunkListItemBinding
import dev.octoshrimpy.quik.model.Message
import javax.inject.Inject

class JunkAdapter @Inject constructor(
    private val dateFormatter: DateFormatter
) : QkRealmAdapter<Message, QkBindingViewHolder<JunkListItemBinding>>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QkBindingViewHolder<JunkListItemBinding> {
        val binding = JunkListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return QkBindingViewHolder(binding).apply {
            binding.root.setOnClickListener {
                val message = getItem(adapterPosition) ?: return@setOnClickListener
                if (toggleSelection(message.id, force = false)) {
                    binding.root.isActivated = isSelected(message.id)
                }
            }
            binding.root.setOnLongClickListener {
                val message = getItem(adapterPosition) ?: return@setOnLongClickListener true
                toggleSelection(message.id)
                binding.root.isActivated = isSelected(message.id)
                true
            }
        }
    }

    override fun onBindViewHolder(holder: QkBindingViewHolder<JunkListItemBinding>, position: Int) {
        val message = getItem(position) ?: return
        val binding = holder.binding

        holder.itemView.isActivated = isSelected(message.id)

        binding.address.text = message.address
        binding.date.text = dateFormatter.getConversationTimestamp(message.date)
        binding.body.text = message.getText()
    }

}
