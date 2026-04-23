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
package dev.octoshrimpy.quik.feature.settings.about

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.jakewharton.rxbinding2.view.clicks
import dev.octoshrimpy.quik.BuildConfig
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.base.QkController
import dev.octoshrimpy.quik.common.widget.PreferenceView
import dev.octoshrimpy.quik.databinding.AboutControllerBinding
import dev.octoshrimpy.quik.injection.appComponent
import io.reactivex.Observable
import javax.inject.Inject

class AboutController : QkController<AboutControllerBinding, AboutView, Unit, AboutPresenter>(), AboutView {

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup): AboutControllerBinding =
        AboutControllerBinding.inflate(inflater, container, false)

    @Inject override lateinit var presenter: AboutPresenter

    // ── GitHub profile URLs for the three featured contributors ────────────
    private val urlOctoshrimpy = "https://github.com/octoshrimpy"
    private val urlMoez        = "https://github.com/moezbhatti"
    private val urlGorupa      = "https://github.com/Gorupa"

    // ── GitHub avatar URLs ─────────────────────────────────────────────────
    private val avatarOctoshrimpy = "https://avatars.githubusercontent.com/u/10530368?v=4"
    private val avatarMoez        = "https://avatars.githubusercontent.com/u/4685875?v=4"
    private val avatarGorupa      = "https://avatars.githubusercontent.com/u/148386383?v=4"

    init {
        appComponent.inject(this)
    }

    override fun onViewCreated() {
        // Version badge text
        binding.versionBadge.text = "v${BuildConfig.VERSION_NAME}"
        // Keep the hidden PreferenceView summary in sync for the version row
        binding.version.summary = BuildConfig.VERSION_NAME

        // Load circular avatars using Glide
        val circleOptions = RequestOptions().transform(CircleCrop())

        Glide.with(this)
            .load(avatarOctoshrimpy)
            .apply(circleOptions)
            .placeholder(R.drawable.ic_person_black_24dp)
            .into(binding.avatarOctoshrimpy)

        Glide.with(this)
            .load(avatarMoez)
            .apply(circleOptions)
            .placeholder(R.drawable.ic_person_black_24dp)
            .into(binding.avatarMoez)

        Glide.with(this)
            .load(avatarGorupa)
            .apply(circleOptions)
            .placeholder(R.drawable.ic_person_black_24dp)
            .into(binding.avatarGorupa)

        // Open GitHub profile on contributor row tap
        binding.contributorOctoshrimpy.setOnClickListener { openUrl(urlOctoshrimpy) }
        binding.contributorMoez.setOnClickListener       { openUrl(urlMoez) }
        binding.contributorGorupa.setOnClickListener     { openUrl(urlGorupa) }
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        presenter.bindIntents(this)
        setTitle(R.string.about_title)
        showBackButton(true)
    }

    override fun preferenceClicks(): Observable<PreferenceView> = (0 until binding.preferences.childCount)
        .map { index -> binding.preferences.getChildAt(index) }
        .mapNotNull { view -> view as? PreferenceView }
        .map { preference -> preference.clicks().map { preference } }
        .let { preferences -> Observable.merge(preferences) }

    override fun render(state: Unit) {
        // No special rendering required
    }

    // ── Helper ──────────────────────────────────────────────────────────────
    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }
}
