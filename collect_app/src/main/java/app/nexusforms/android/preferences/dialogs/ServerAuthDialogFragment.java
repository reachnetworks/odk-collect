package app.nexusforms.android.preferences.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import app.nexusforms.android.R;
import app.nexusforms.android.databinding.ServerAuthDialogBinding;
import app.nexusforms.android.injection.DaggerUtils;
import app.nexusforms.android.preferences.keys.GeneralKeys;
import app.nexusforms.android.preferences.source.Settings;
import app.nexusforms.android.preferences.source.SettingsProvider;

import javax.inject.Inject;

public class ServerAuthDialogFragment extends DialogFragment {

    @Inject
    SettingsProvider settingsProvider;

    private View dialogView;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        DaggerUtils.getComponent(context).inject(this);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        ServerAuthDialogBinding binding = ServerAuthDialogBinding.inflate(requireActivity().getLayoutInflater());
        dialogView = binding.getRoot();

        Settings generalSettings = settingsProvider.getGeneralSettings();
        binding.usernameEdit.setText(generalSettings.getString(GeneralKeys.KEY_USERNAME));
        binding.passwordEdit.setText(generalSettings.getString(GeneralKeys.KEY_PASSWORD));

        return new AlertDialog.Builder(requireContext())
                .setTitle(R.string.server_requires_auth)
                .setMessage(requireContext().getString(R.string.server_auth_credentials, generalSettings.getString(GeneralKeys.KEY_SERVER_URL)))
                .setView(dialogView)
                .setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                    generalSettings.save(GeneralKeys.KEY_USERNAME, binding.usernameEdit.getText().toString());
                    generalSettings.save(GeneralKeys.KEY_PASSWORD, binding.passwordEdit.getText().toString());
                })
                .create();
    }

    public View getDialogView() {
        return dialogView;
    }
}