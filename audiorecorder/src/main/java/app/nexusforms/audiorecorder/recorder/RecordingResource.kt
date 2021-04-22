package app.nexusforms.audiorecorder.recorder

import java.io.IOException
import java.lang.IllegalStateException

/**
 * Allows faking/stubbing/mocking with our interactions with Android's MediaRecorder. Could also
 * wrap multiple implementations in the future.
 */

internal interface RecordingResource {
    fun setOutputFile(path: String)

    @Throws(IOException::class)
    fun prepare()

    @Throws(IllegalStateException::class)
    fun start()

    fun pause()
    fun resume()
    fun stop()
    fun release()
    fun getMaxAmplitude(): Int
}