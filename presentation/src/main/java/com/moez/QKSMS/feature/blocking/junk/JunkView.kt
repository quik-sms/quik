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

import dev.octoshrimpy.quik.common.base.QkViewContract
import io.reactivex.Observable
import io.reactivex.subjects.Subject

interface JunkView : QkViewContract<JunkState> {

    val menuReadyIntent: Subject<Unit>
    val optionsItemIntent: Subject<Int>
    val selectionChanges: Observable<List<Long>>
    val confirmDeleteIntent: Subject<List<Long>>
    val backClicked: Subject<Unit>

    fun clearSelection()
    fun showDeleteDialog(messageIds: List<Long>)
    fun goBack()

}
