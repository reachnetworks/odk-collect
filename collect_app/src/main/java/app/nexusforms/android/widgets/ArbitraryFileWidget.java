/*
 * Copyright 2018 Nafundi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.nexusforms.android.widgets;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import androidx.annotation.NonNull;

import android.util.TypedValue;
import android.view.View;

import org.javarosa.form.api.FormEntryPrompt;
import app.nexusforms.android.databinding.ArbitraryFileWidgetAnswerBinding;

import app.nexusforms.android.formentry.questions.QuestionDetails;
import app.nexusforms.android.utilities.ApplicationConstants;
import app.nexusforms.android.utilities.MediaUtils;
import app.nexusforms.android.utilities.QuestionMediaManager;
import app.nexusforms.android.widgets.interfaces.FileWidget;
import app.nexusforms.android.widgets.interfaces.WidgetDataReceiver;
import app.nexusforms.android.widgets.utilities.WaitingForDataRegistry;

@SuppressLint("ViewConstructor")
public class ArbitraryFileWidget extends BaseArbitraryFileWidget implements FileWidget, WidgetDataReceiver {
    ArbitraryFileWidgetAnswerBinding binding;

    @NonNull
    private final MediaUtils mediaUtils;

    private final WaitingForDataRegistry waitingForDataRegistry;

    ArbitraryFileWidget(Context context, QuestionDetails questionDetails, @NonNull MediaUtils mediaUtils,
                        QuestionMediaManager questionMediaManager, WaitingForDataRegistry waitingForDataRegistry) {
        super(context, questionDetails, mediaUtils, questionMediaManager, waitingForDataRegistry);
        this.mediaUtils = mediaUtils;
        this.waitingForDataRegistry = waitingForDataRegistry;
    }

    @Override
    protected View onCreateAnswerView(Context context, FormEntryPrompt prompt, int answerFontSize) {
        binding = ArbitraryFileWidgetAnswerBinding.inflate(((Activity) context).getLayoutInflater());
        setupAnswerFile(prompt.getAnswerText());

        binding.arbitraryFileButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, answerFontSize);
        binding.arbitraryFileAnswerText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, answerFontSize);

        binding.arbitraryFileButton.setVisibility(questionDetails.isReadOnly() ? GONE : VISIBLE);
        binding.arbitraryFileButton.setOnClickListener(v -> onButtonClick());
        binding.arbitraryFileAnswerText.setOnClickListener(v -> mediaUtils.openFile(getContext(), answerFile, null));

        if (answerFile != null) {
            binding.arbitraryFileAnswerText.setText(answerFile.getName());
            binding.arbitraryFileAnswerText.setVisibility(VISIBLE);
        }

        return binding.getRoot();
    }

    @Override
    public void clearAnswer() {
        binding.arbitraryFileAnswerText.setVisibility(GONE);
        deleteFile();
        widgetValueChanged();
    }

    private void onButtonClick() {
        waitingForDataRegistry.waitForData(getFormEntryPrompt().getIndex());
        mediaUtils.pickFile((Activity) getContext(), "*/*", ApplicationConstants.RequestCodes.ARBITRARY_FILE_CHOOSER);
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener listener) {
        binding.arbitraryFileButton.setOnLongClickListener(listener);
        binding.arbitraryFileAnswerText.setOnLongClickListener(listener);
    }

    @Override
    protected void showAnswerText() {
        binding.arbitraryFileAnswerText.setText(answerFile.getName());
        binding.arbitraryFileAnswerText.setVisibility(VISIBLE);
    }

    @Override
    protected void hideAnswerText() {
        binding.arbitraryFileAnswerText.setVisibility(GONE);
    }
}
