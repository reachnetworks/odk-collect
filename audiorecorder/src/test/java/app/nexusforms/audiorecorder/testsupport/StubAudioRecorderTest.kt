package app.nexusforms.audiorecorder.testsupport

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nexusforms.audiorecorder.testsupport.StubAudioRecorder
import org.junit.runner.RunWith
import app.nexusforms.audiorecorder.recording.AudioRecorder
import app.nexusforms.audiorecorder.recording.AudioRecorderTest
import java.io.File

@RunWith(AndroidJUnit4::class)
class StubAudioRecorderTest : AudioRecorderTest() {

    private val stubViewAudioRecorderViewModel: StubAudioRecorder by lazy {
        val tempFile = File.createTempFile("blah", ".whatever")
        StubAudioRecorder(tempFile.absolutePath)
    }

    override val viewModel: AudioRecorder by lazy {
        stubViewAudioRecorderViewModel
    }

    override fun runBackground() {
        // No op
    }

    override fun getLastRecordedFile(): File? {
        return stubViewAudioRecorderViewModel.lastRecording
    }
}