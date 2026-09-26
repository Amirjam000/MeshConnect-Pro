package com.meshconnect.pro.audio

import android.annotation.SuppressLint
import android.media.*
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.android.gms.nearby.connection.Payload
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import kotlin.math.abs

/**
 * مدیر انتقال صوتی واکی-تاکی زنده (Walkie-Talkie Live Audio)
 * ضبط مستقیم PCM 16-bit 16kHz و پخش بدون تاخیر
 */
class AudioStreamManager {

    companion object {
        private const val TAG = "AudioStreamManager"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isRecording = false
    private var recordingJob: Job? = null
    private var playingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isTransmitting = MutableStateFlow(false)
    val isTransmitting: StateFlow<Boolean> = _isTransmitting.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startStreamingAudio(onStreamPayloadCreated: (Payload) -> Unit) {
        if (isRecording) return

        val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT)
        val bufferSize = minBufSize * 2

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG_IN,
                AUDIO_FORMAT,
                bufferSize
            )

            val pipe = ParcelFileDescriptor.createPipe()
            val readPipe = pipe[0]
            val writePipe = pipe[1]

            val streamPayload = Payload.fromStream(readPipe)
            onStreamPayloadCreated(streamPayload)

            val outputStream = ParcelFileDescriptor.AutoCloseOutputStream(writePipe)

            audioRecord?.startRecording()
            isRecording = true
            _isTransmitting.value = true

            recordingJob = scope.launch {
                val buffer = ByteArray(minBufSize)
                try {
                    while (isRecording && isActive) {
                        val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (readBytes > 0) {
                            outputStream.write(buffer, 0, readBytes)
                            // محاسبه قدرت صدا برای انیمیشن امواج
                            var maxAmp = 0
                            for (i in 0 until readBytes step 2) {
                                val sample = (buffer[i].toInt() or (buffer[i + 1].toInt() shl 8)).toShort()
                                if (abs(sample.toInt()) > maxAmp) maxAmp = abs(sample.toInt())
                            }
                            _amplitude.value = (maxAmp / 32768f).coerceIn(0f, 1f)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "خطا در جریان ضبط صدا: ${e.message}")
                } finally {
                    try { outputStream.close() } catch (e: Exception) {}
                    _amplitude.value = 0f
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "خطا در راه‌اندازی ضبط صدا: ${e.message}")
            stopStreamingAudio()
        }
    }

    fun stopStreamingAudio() {
        isRecording = false
        _isTransmitting.value = false
        _amplitude.value = 0f
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {}
        audioRecord = null
    }

    fun playIncomingAudioStream(inputStream: InputStream) {
        val minBufSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT)

        playingJob = scope.launch {
            try {
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AUDIO_FORMAT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_CONFIG_OUT)
                            .build()
                    )
                    .setBufferSizeInBytes(minBufSize * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack?.play()
                val buffer = ByteArray(minBufSize)
                var bytesRead: Int
                while (isActive && inputStream.read(buffer).also { bytesRead = it } != -1) {
                    audioTrack?.write(buffer, 0, bytesRead)
                }
            } catch (e: Exception) {
                Log.e(TAG, "خطا در پخش صدای ورودی: ${e.message}")
            } finally {
                stopPlayback()
            }
        }
    }

    fun stopPlayback() {
        playingJob?.cancel()
        playingJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {}
        audioTrack = null
    }
}
