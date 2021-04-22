package app.nexusforms.android.widgets;

import androidx.annotation.NonNull;

import org.javarosa.core.model.data.IntegerData;
import app.nexusforms.android.formentry.questions.QuestionDetails;
import org.junit.Test;

import app.nexusforms.android.widgets.base.GeneralExStringWidgetTest;
import app.nexusforms.android.widgets.support.FakeWaitingForDataRegistry;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.when;
import static app.nexusforms.android.utilities.Appearances.THOUSANDS_SEP;

/**
 * @author James Knight
 */

public class ExIntegerWidgetTest extends GeneralExStringWidgetTest<ExIntegerWidget, IntegerData> {

    @NonNull
    @Override
    public ExIntegerWidget createWidget() {
        return new ExIntegerWidget(activity, new QuestionDetails(formEntryPrompt, "formAnalyticsID"), new FakeWaitingForDataRegistry());
    }

    @NonNull
    @Override
    public IntegerData getNextAnswer() {
        return new IntegerData(randomInteger());
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        when(formEntryPrompt.getAppearanceHint()).thenReturn("");
    }

    private int randomInteger() {
        return Math.abs(random.nextInt()) % 1_000_000_000;
    }

    @Test
    public void digitsAboveLimitOfNineShouldBeTruncatedFromRight() {
        getWidget().answerTextInputLayout.setText("123456789123");
        assertEquals("123456789", getWidget().getAnswerText());
    }

    @Test
    public void separatorsShouldBeAddedWhenEnabled() {
        when(formEntryPrompt.getAppearanceHint()).thenReturn(THOUSANDS_SEP);
        getWidget().answerTextInputLayout.setText("123456789");
        assertEquals("123,456,789", getWidget().answerTextInputLayout.getText().toString());
    }
}