package app.nexusforms.android.formmanagement;

import android.content.Context;

import app.nexusforms.android.R;
import app.nexusforms.android.forms.FormSourceException;

import static app.nexusforms.android.forms.FormSourceException.ParseError;
import static app.nexusforms.android.forms.FormSourceException.SecurityError;
import static app.nexusforms.android.forms.FormSourceException.ServerError;
import static app.nexusforms.android.forms.FormSourceException.Unreachable;
import static app.nexusforms.android.utilities.TranslationHandler.getString;

public class FormSourceExceptionMapper {

    private final Context context;

    public FormSourceExceptionMapper(Context context) {
        this.context = context;
    }

    public String getMessage(FormSourceException exception) {
        if (exception instanceof Unreachable) {
            return getString(context, R.string.unreachable_error, ((Unreachable) exception).getServerUrl()) + " " + getString(context, R.string.report_to_project_lead);
        } else if (exception instanceof SecurityError) {
            return getString(context, R.string.security_error, ((SecurityError) exception).getServerUrl()) + " " + getString(context, R.string.report_to_project_lead);
        } else if (exception instanceof ServerError) {
            return getString(context, R.string.server_error, ((ServerError) exception).getServerUrl(), ((ServerError) exception).getStatusCode()) + " " + getString(context, R.string.report_to_project_lead);
        } else if (exception instanceof ParseError) {
            return getString(context, R.string.invalid_response, ((ParseError) exception).getServerUrl()) + " " + getString(context, R.string.report_to_project_lead);
        } else {
            return getString(context, R.string.report_to_project_lead);
        }
    }
}