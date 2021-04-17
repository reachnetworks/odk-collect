/*
 * Copyright (C) 2017 Shobhit
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package app.nexusforms.android.preferences.screens;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;

import app.nexusforms.android.R;
import app.nexusforms.android.configure.qr.QRCodeTabsActivity;
import app.nexusforms.android.fragments.dialogs.MovingBackwardsDialog;
import app.nexusforms.android.fragments.dialogs.SimpleDialog;
import app.nexusforms.android.injection.DaggerUtils;
import app.nexusforms.android.preferences.dialogs.ChangeAdminPasswordDialog;
import app.nexusforms.android.preferences.FormUpdateMode;
import app.nexusforms.android.preferences.keys.GeneralKeys;
import app.nexusforms.android.preferences.dialogs.ResetDialogPreference;
import app.nexusforms.android.preferences.dialogs.ResetDialogPreferenceFragmentCompat;
import app.nexusforms.android.utilities.DialogUtils;
import app.nexusforms.android.utilities.MultiClickGuard;

import static app.nexusforms.android.configure.SettingsUtils.getFormUpdateMode;
import static app.nexusforms.android.fragments.dialogs.MovingBackwardsDialog.MOVING_BACKWARDS_DIALOG_TAG;
import static app.nexusforms.android.preferences.keys.AdminKeys.ALLOW_OTHER_WAYS_OF_EDITING_FORM;
import static app.nexusforms.android.preferences.keys.AdminKeys.KEY_CHANGE_ADMIN_PASSWORD;
import static app.nexusforms.android.preferences.keys.AdminKeys.KEY_EDIT_SAVED;
import static app.nexusforms.android.preferences.keys.AdminKeys.KEY_GET_BLANK;
import static app.nexusforms.android.preferences.keys.AdminKeys.KEY_IMPORT_SETTINGS;
import static app.nexusforms.android.preferences.keys.AdminKeys.KEY_JUMP_TO;
import static app.nexusforms.android.preferences.keys.AdminKeys.KEY_MOVING_BACKWARDS;
import static app.nexusforms.android.preferences.keys.AdminKeys.KEY_SAVE_MID;
import static app.nexusforms.android.preferences.keys.GeneralKeys.CONSTRAINT_BEHAVIOR_ON_SWIPE;
import static app.nexusforms.android.preferences.screens.GeneralPreferencesActivity.INTENT_KEY_ADMIN_MODE;
import static app.nexusforms.android.preferences.utilities.PreferencesUtils.displayDisabled;

public class AdminPreferencesFragment extends BaseAdminPreferencesFragment implements Preference.OnPreferenceClickListener {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.admin_preferences, rootKey);

        findPreference("odk_preferences").setOnPreferenceClickListener(this);
        findPreference(KEY_CHANGE_ADMIN_PASSWORD).setOnPreferenceClickListener(this);
        findPreference(KEY_IMPORT_SETTINGS).setOnPreferenceClickListener(this);
        findPreference("main_menu").setOnPreferenceClickListener(this);
        findPreference("user_settings").setOnPreferenceClickListener(this);
        findPreference("form_entry").setOnPreferenceClickListener(this);
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        if (MultiClickGuard.allowClick(getClass().getName())) {
            ResetDialogPreference resetDialogPreference = null;
            if (preference instanceof ResetDialogPreference) {
                resetDialogPreference = (ResetDialogPreference) preference;
            }
            if (resetDialogPreference != null) {
                ResetDialogPreferenceFragmentCompat dialogFragment = ResetDialogPreferenceFragmentCompat.newInstance(preference.getKey());
                dialogFragment.setTargetFragment(this, 0);
                dialogFragment.show(getParentFragmentManager(), null);
            } else {
                super.onDisplayPreferenceDialog(preference);
            }
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (MultiClickGuard.allowClick(getClass().getName())) {
            switch (preference.getKey()) {
                case "odk_preferences":
                    Intent intent = new Intent(getActivity(), GeneralPreferencesActivity.class);
                    intent.putExtra(INTENT_KEY_ADMIN_MODE, true);
                    startActivity(intent);
                    break;

                case KEY_CHANGE_ADMIN_PASSWORD:
                    DialogUtils.showIfNotShowing(ChangeAdminPasswordDialog.class,
                            getActivity().getSupportFragmentManager());
                    break;

                case KEY_IMPORT_SETTINGS:
                    Intent pref = new Intent(getActivity(), QRCodeTabsActivity.class);
                    startActivity(pref);
                    break;
                case "main_menu":
                    displayPreferences(new MainMenuAccessPreferences());
                    break;
                case "user_settings":
                    displayPreferences(new UserSettingsAccessPreferences());
                    break;
                case "form_entry":
                    displayPreferences(new FormEntryAccessPreferences());
                    break;
            }

            return true;
        }

        return false;
    }

    private void displayPreferences(Fragment fragment) {
        if (fragment != null) {
            fragment.setArguments(getArguments());
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.preferences_fragment_container, fragment)
                    .addToBackStack(null)
                    .commit();
        }
    }

    public static class MainMenuAccessPreferences extends BaseAdminPreferencesFragment {

        @Override
        public void onAttach(@NonNull Context context) {
            super.onAttach(context);
            DaggerUtils.getComponent(context).inject(this);
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(R.xml.main_menu_access_preferences, rootKey);
            findPreference(KEY_EDIT_SAVED).setEnabled(settingsProvider.getAdminSettings().getBoolean(ALLOW_OTHER_WAYS_OF_EDITING_FORM));

            FormUpdateMode formUpdateMode = getFormUpdateMode(requireContext(), settingsProvider.getGeneralSettings());
            if (formUpdateMode == FormUpdateMode.MATCH_EXACTLY) {
                displayDisabled(findPreference(KEY_GET_BLANK), false);
            }
        }
    }

    public static class UserSettingsAccessPreferences extends BaseAdminPreferencesFragment {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            setPreferencesFromResource(R.xml.user_settings_access_preferences, rootKey);
        }
    }

    public static class FormEntryAccessPreferences extends BaseAdminPreferencesFragment {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);
            addPreferencesFromResource(R.xml.form_entry_access_preferences);

            findPreference(KEY_MOVING_BACKWARDS).setOnPreferenceChangeListener((preference, newValue) -> {
                if (((CheckBoxPreference) preference).isChecked()) {
                    new MovingBackwardsDialog().show(getActivity().getSupportFragmentManager(), MOVING_BACKWARDS_DIALOG_TAG);
                } else {
                    SimpleDialog.newInstance(getActivity().getString(R.string.moving_backwards_enabled_title), 0, getActivity().getString(R.string.moving_backwards_enabled_message), getActivity().getString(R.string.ok), false).show(((AdminPreferencesActivity) getActivity()).getSupportFragmentManager(), SimpleDialog.COLLECT_DIALOG_TAG);
                    onMovingBackwardsEnabled();
                }
                return true;
            });
            findPreference(KEY_JUMP_TO).setEnabled(settingsProvider.getAdminSettings().getBoolean(ALLOW_OTHER_WAYS_OF_EDITING_FORM));
            findPreference(KEY_SAVE_MID).setEnabled(settingsProvider.getAdminSettings().getBoolean(ALLOW_OTHER_WAYS_OF_EDITING_FORM));
        }

        private void preventOtherWaysOfEditingForm() {
            settingsProvider.getAdminSettings().save(ALLOW_OTHER_WAYS_OF_EDITING_FORM, false);
            settingsProvider.getAdminSettings().save(KEY_EDIT_SAVED, false);
            settingsProvider.getAdminSettings().save(KEY_SAVE_MID, false);
            settingsProvider.getAdminSettings().save(KEY_JUMP_TO, false);
            settingsProvider.getGeneralSettings().save(GeneralKeys.KEY_CONSTRAINT_BEHAVIOR, CONSTRAINT_BEHAVIOR_ON_SWIPE);

            findPreference(KEY_JUMP_TO).setEnabled(false);
            findPreference(KEY_SAVE_MID).setEnabled(false);

            ((CheckBoxPreference) findPreference(KEY_JUMP_TO)).setChecked(false);
            ((CheckBoxPreference) findPreference(KEY_SAVE_MID)).setChecked(false);
        }

        private void onMovingBackwardsEnabled() {
            settingsProvider.getAdminSettings().save(ALLOW_OTHER_WAYS_OF_EDITING_FORM, true);
            findPreference(KEY_JUMP_TO).setEnabled(true);
            findPreference(KEY_SAVE_MID).setEnabled(true);
        }
    }

    public void preventOtherWaysOfEditingForm() {
        FormEntryAccessPreferences fragment = (FormEntryAccessPreferences) getFragmentManager().findFragmentById(R.id.preferences_fragment_container);
        fragment.preventOtherWaysOfEditingForm();
    }
}