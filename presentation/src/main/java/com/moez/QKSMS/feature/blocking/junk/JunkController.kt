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

import android.content.Context
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.base.QkController
import dev.octoshrimpy.quik.databinding.JunkControllerBinding
import dev.octoshrimpy.quik.injection.appComponent
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject

class JunkController : QkController<JunkControllerBinding, JunkView, JunkState, JunkPresenter>(),
    JunkView {

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup): JunkControllerBinding =
        JunkControllerBinding.inflate(inflater, container, false)

    override val menuReadyIntent: Subject<Unit> = PublishSubject.create()
    override val optionsItemIntent: Subject<Int> = PublishSubject.create()
    override val selectionChanges: Observable<List<Long>> by lazy { adapter.selectionChanges }
    override val confirmDeleteIntent: Subject<List<Long>> = PublishSubject.create()
    override val backClicked: Subject<Unit> = PublishSubject.create()

    @Inject lateinit var adapter: JunkAdapter
    @Inject lateinit var context: Context
    @Inject override lateinit var presenter: JunkPresenter

    init {
        appComponent.inject(this)
        retainViewMode = RetainViewMode.RETAIN_DETACH
    }

    override fun onViewCreated() {
        super.onViewCreated()
        adapter.emptyView = binding.empty
        binding.messages.adapter = adapter
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        presenter.bindIntents(this)
        setTitle(R.string.junk_title)
        showBackButton(true)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.junk, menu)
        menuReadyIntent.onNext(Unit)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        optionsItemIntent.onNext(item.itemId)
        return true
    }

    override fun handleBack(): Boolean {
        backClicked.onNext(Unit)
        return true
    }

    override fun render(state: JunkState) {
        adapter.updateData(state.data)

        val toolbarMenu = themedActivity?.findViewById<Toolbar>(R.id.toolbar)?.menu
        toolbarMenu?.findItem(R.id.restore)?.isVisible = state.selected > 0
        toolbarMenu?.findItem(R.id.delete)?.isVisible = state.selected > 0

        setTitle(when (state.selected) {
            0 -> context.getString(R.string.junk_title)
            else -> context.getString(R.string.main_title_selected, state.selected)
        })
    }

    override fun clearSelection() = adapter.clearSelection()

    override fun showDeleteDialog(messageIds: List<Long>) {
        val count = messageIds.size
        AlertDialog.Builder(activity!!)
            .setTitle(R.string.junk_delete_dialog_title)
            .setMessage(resources!!.getQuantityString(
                R.plurals.junk_delete_dialog_message, count, count
            ))
            .setPositiveButton(R.string.button_delete) { _, _ -> confirmDeleteIntent.onNext(messageIds) }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    override fun goBack() {
        router.popCurrentController()
    }

}
