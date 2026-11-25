package app.ocarinatuner.audio

/**
 * Thin wrapper over the C++ engine (Oboe input + YIN on the audio thread).
 * Every buffer is preallocated so the per-frame [drain] call allocates nothing.
 */
object NativeEngine {
    init { System.loadLibrary("ocarina_engine") }

    const val MAX_FRAMES = 1024

    val times = LongArray(MAX_FRAMES)
    val hz = FloatArray(MAX_FRAMES)
    val clarity = FloatArray(MAX_FRAMES)
    val rmsDb = FloatArray(MAX_FRAMES)
    val stats = DoubleArray(17)

    /** 0 on success, otherwise an Oboe error code. */
    fun start(): Int = nativeStart()
    fun stop() = nativeStop()
    fun setGate(minRmsDb: Float, minClarity: Float) = nativeSetGate(minRmsDb, minClarity)

    /** Debug builds only: feed a synthetic tone instead of the mic. [hz] <= 0 turns it off. */
    fun setTestTone(hz: Float, dbfs: Float) = nativeSetTestTone(hz, dbfs)

    /** Copies new analysis frames into [times]/[hz]/[clarity]/[rmsDb]; returns the count. */
    fun drain(): Int = nativeDrain(times, hz, clarity, rmsDb)

    fun refreshStats() = nativeStats(stats)

    private external fun nativeStart(): Int
    private external fun nativeStop()
    private external fun nativeSetGate(db: Float, clarity: Float)
    private external fun nativeSetTestTone(hz: Float, db: Float)
    private external fun nativeDrain(times: LongArray, hz: FloatArray, clarity: FloatArray, rms: FloatArray): Int
    private external fun nativeStats(out: DoubleArray)
}

/** Named view over [NativeEngine.stats] for the debug overlay. */
class EngineStats(s: DoubleArray) {
    val sampleRate = s[0].toInt()
    val framesPerBurst = s[1].toInt()
    val bufferSize = s[2].toInt()
    val bufferCapacity = s[3].toInt()
    val inputLatencyMs = s[4]
    val analysisAvgUs = s[5]
    val analysisMaxUs = s[6]
    val callbackFrames = s[7].toInt()
    val callbacks = s[8].toLong()
    val xruns = s[9].toInt()
    val dropped = s[10].toLong()
    val hop = s[11].toInt()
    val window = s[12].toInt()
    val api = when (s[13].toInt()) { 2 -> "AAudio"; 1 -> "OpenSL ES"; else -> "?" }
    val preset = when (s[14].toInt()) { 9 -> "Unprocessed"; 6 -> "VoiceRecognition"; else -> s[14].toInt().toString() }
    val exclusive = s[15] > 0.5
    val callbackIntervalMs = s[16]
}
