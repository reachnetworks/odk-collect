package app.nexusforms.android.formentry.questions;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import app.nexusforms.android.R;

import app.nexusforms.android.databinding.WidgetSimpleInputLayoutBinding;
import app.nexusforms.android.utilities.MultiClickGuard;
import app.nexusforms.android.utilities.ThemeUtils;
import app.nexusforms.android.utilities.ViewUtils;
import app.nexusforms.android.widgets.QuestionWidget;
import app.nexusforms.android.widgets.interfaces.ButtonClickListener;

import static android.view.View.GONE;

public class WidgetViewUtils {

    private static final int WIDGET_ANSWER_STANDARD_MARGIN_MODIFIER = 4;

    private WidgetViewUtils() {

    }

    public static int getStandardMargin(Context context) {
        Resources resources = context.getResources();
        int marginStandard = ViewUtils.dpFromPx(context, resources.getDimensionPixelSize(R.dimen.margin_standard));

        return marginStandard - WIDGET_ANSWER_STANDARD_MARGIN_MODIFIER;
    }

    public static TextView getCenteredAnswerTextView(Context context, int answerFontSize) {
        TextView textView = createAnswerTextView(context, answerFontSize);
        textView.setGravity(Gravity.CENTER);

        return textView;
    }

    public static TextView createAnswerTextView(Context context, int answerFontSize) {
        return createAnswerTextView(context, "", answerFontSize);
    }

    public static TextView createAnswerTextView(Context context, String text, int answerFontSize) {
        TextView textView = new TextView(context);

        textView.setId(R.id.answer_text);
        textView.setTextColor(new ThemeUtils(context).getColorOnSurface());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, answerFontSize);
        textView.setPadding(20, 20, 20, 20);
        textView.setText(text);

        return textView;
    }

    public static ImageView createAnswerImageView(Context context, Bitmap bitmap) {
        final ImageView imageView = new ImageView(context);
        imageView.setId(View.generateViewId());
        imageView.setTag("ImageView");
        imageView.setPadding(10, 10, 10, 10);
        imageView.setAdjustViewBounds(true);
        imageView.setImageBitmap(bitmap);
        return imageView;
    }

    public static Button createSimpleButton(Context context, @IdRes final int withId, boolean readOnly, String text, int answerFontSize, ButtonClickListener listener) {
        final MaterialButton button = (MaterialButton) LayoutInflater
                .from(context)
                .inflate(R.layout.widget_answer_button, null, false);

        if (readOnly) {
            button.setVisibility(GONE);
        } else {
            button.setId(withId);
            button.setText(text);
            button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, answerFontSize);

            TableLayout.LayoutParams params = new TableLayout.LayoutParams();
            params.setMargins(7, 5, 7, 5);

            button.setLayoutParams(params);

            button.setOnClickListener(v -> {
                if (MultiClickGuard.allowClick(QuestionWidget.class.getName())) {
                    listener.onButtonClick(withId);
                }
            });
        }

        return button;
    }

    public static Button createSimpleButton(Context context, @IdRes int id, boolean readOnly, int answerFontSize, ButtonClickListener listener) {
        return createSimpleButton(context, id, readOnly, null, answerFontSize, listener);
    }

    public static Button createSimpleButton(Context context, boolean readOnly, String text, int answerFontSize, ButtonClickListener listener) {
        return createSimpleButton(context, R.id.simple_button, readOnly, text, answerFontSize, listener);
    }

    public static TextInputLayout createSimpleTextInputLayout(Context context, @IdRes final int withId, boolean readOnly, String text, int answerFontSize, ButtonClickListener listener) {
        final WidgetSimpleInputLayoutBinding binding = WidgetSimpleInputLayoutBinding.inflate(LayoutInflater.from(context),
                null,
                false
        );

        /*final TextInputLayout textLayout = (TextInputLayout) LayoutInflater.from(context)
                .inflate(R.layout.widget_simple_input_layout, null, false);

        EditText editText = textLayout.getEditText();
        editText.setHint(text);
        textLayout.setEndIconOnClickListener(v -> {
            listener.onButtonClick(withId);
        });*/

        binding.textImageWidget.setText(text);
        binding.textImageWidget.setOnClickListener(v ->
                listener.onButtonClick(withId)
        );

        binding.layoutImageWidget.setEndIconOnClickListener(v ->
                listener.onButtonClick(withId)
        );

        return binding.getRoot();
    }
}