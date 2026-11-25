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

import dev.octoshrimpy.quik.model.Message
import io.realm.RealmResults

data class JunkState(
    val data: RealmResults<Message>? = null,
    val selected: Int = 0
)
