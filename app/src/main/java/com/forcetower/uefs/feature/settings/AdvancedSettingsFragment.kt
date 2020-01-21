/*
 * This file is part of the UNES Open Source Project.
 * UNES is licensed under the GNU GPLv3.
 *
 * Copyright (c) 2020. João Paulo Sena <joaopaulo761@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.forcetower.uefs.feature.settings

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.forcetower.core.injection.Injectable
import com.forcetower.uefs.R
import com.forcetower.uefs.core.util.VersionUtils
import com.forcetower.uefs.core.util.isStudentFromUEFS
import com.forcetower.uefs.core.vm.UViewModelFactory
import com.forcetower.uefs.feature.messages.MessagesDFMViewModel
import com.forcetower.uefs.feature.web.CustomTabActivityHelper
import com.google.android.material.snackbar.Snackbar
import com.judemanutd.autostarter.AutoStartPermissionHelper
import java.util.Locale
import javax.inject.Inject

class AdvancedSettingsFragment : PreferenceFragmentCompat(), Injectable {
    @Inject
    lateinit var factory: UViewModelFactory
    private val viewModel: SettingsViewModel by activityViewModels { factory }

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { shared, key ->
        onPreferenceChange(shared, key)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_advanced, rootKey)
        if (VersionUtils.isMarshmallow()) {
            findPreference<SwitchPreference>("stg_advanced_ignore_doze")?.let {
                val pm = context?.getSystemService(Context.POWER_SERVICE) as PowerManager?
                val ignoring = pm?.isIgnoringBatteryOptimizations(context!!.packageName) ?: false
                it.isChecked = ignoring
            }
        }

        if (!getSharedPreferences().isStudentFromUEFS()) {
            findPreference<SwitchPreference>("stg_advanced_aeri_tab")?.isVisible = false
        }

        findPreference<Preference>("stg_advanced_auto_start")?.let {
            it.setOnPreferenceClickListener {
                val result = AutoStartPermissionHelper.getInstance().getAutoStartPermission(requireContext())
                if (!result) {
                    Snackbar.make(view!!, getString(R.string.settings_auto_start_manager_not_found), Snackbar.LENGTH_SHORT).show()
                }
                true
            }
        }

        findPreference<Preference>("stg_advanced_battery_optimization")?.let {
            it.setOnPreferenceClickListener {
                CustomTabActivityHelper.openCustomTab(
                    requireActivity(),
                    CustomTabsIntent.Builder()
                        .setToolbarColor(ContextCompat.getColor(requireContext(), R.color.blue_accent))
                        .addDefaultShareMenuItem()
                        .build(),
                    Uri.parse("https://dontkillmyapp.com/${Build.BRAND.toLowerCase(Locale.getDefault())}"))
                true
            }
        }
    }

    private fun onPreferenceChange(preference: SharedPreferences, key: String) {
        when (key) {
            "stg_advanced_aeri_tab" -> aeriTabs(preference.getBoolean(key, true))
        }
    }

    private fun aeriTabs(enabled: Boolean) {
        if (!enabled) {
            viewModel.uninstallModuleIfExists(MessagesDFMViewModel.AERI_MODULE)
        }
    }

//    private fun ignoreDoze(ignore: Boolean) {
//        if (!ignore) {
//            view?.let {
//                Snackbar.make(it, "Para mudar, vá até Configurações > Apps e notificações no seu celular", Snackbar.LENGTH_LONG).show()
//            }
//        } else {
//            if (VersionUtils.isMarshmallow()) {
//                val pm = context?.getSystemService(Context.POWER_SERVICE) as PowerManager?
//                val ignoring = pm?.isIgnoringBatteryOptimizations(context!!.packageName) ?: false
//                Timber.d("Ignoring? $ignoring")
//                if (!ignoring) {
//                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
//                        data = Uri.parse("package:${context?.packageName}")
//                    }
//                    startActivity(intent)
//                }
//            }
//        }
//    }

    override fun onResume() {
        super.onResume()
        getSharedPreferences().registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onPause() {
        super.onPause()
        getSharedPreferences().unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun getSharedPreferences() = preferenceManager.sharedPreferences
}