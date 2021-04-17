/*
 * Copyright (C) 2012 University of Washington
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

package app.nexusforms.android.widgets;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;

import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.IntegerData;
import app.nexusforms.android.R;

import app.nexusforms.android.external.ExternalAppsUtils;
import app.nexusforms.android.formentry.questions.QuestionDetails;
import app.nexusforms.android.utilities.ApplicationConstants;
import app.nexusforms.android.utilities.ToastUtils;
import app.nexusforms.android.widgets.utilities.StringWidgetUtils;
import app.nexusforms.android.widgets.utilities.WaitingForDataRegistry;

import timber.log.Timber;

/**
 * Launch an external app to supply an integer value. If the app
 * does not launch, enable the text area for regular data entry.
 * <p>
 * See {@link ExStringWidget} for usage.
 */
@SuppressLint("ViewConstructor")
public class ExIntegerWidget extends ExStringWidget {

    public ExIntegerWidget(Context context, QuestionDetails questionDetails, WaitingForDataRegistry waitingForDataRegistry) {
        super(context, questionDetails, waitingForDataRegistry);
        StringWidgetUtils.adjustEditTextAnswerToIntegerWidget(answerText, questionDetails.getPrompt());
    }

    @Override
    protected void fireActivity(Intent i) throws ActivityNotFoundException {
        i.putExtra(DATA_NAME, StringWidgetUtils.getIntegerAnswerValueFromIAnswerData(getFormEntryPrompt().getAnswerValue()));
        try {
            ((Activity) getContext()).startActivityForResult(i, ApplicationConstants.RequestCodes.EX_INT_CAPTURE);
        } catch (SecurityException e) {
            Timber.i(e);
            ToastUtils.showLongToast(R.string.not_granted_permission);
        }
    }

    @Override
    public IAnswerData getAnswer() {
        return StringWidgetUtils.getIntegerData(answerText.getText().toString(), getFormEntryPrompt());
    }

    @Override
    public void setData(Object answer) {
        IntegerData integerData = ExternalAppsUtils.asIntegerData(answer);
        answerText.setText(integerData == null ? null : integerData.getValue().toString());
        widgetValueChanged();
    }
}
