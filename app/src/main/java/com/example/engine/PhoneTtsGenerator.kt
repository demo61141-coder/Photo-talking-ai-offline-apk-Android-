package com.example.engine

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

data class TtsLocaleOption(
    val locale: Locale,
    val label: String,
    val isAvailable: Boolean
)

object PhoneTtsGenerator {

    private val preferredLocales: List<Pair<Locale, String>> = listOf(
        Locale.US to "English (US)",
        Locale.UK to "English (UK)",
        Locale("bn", "BD") to "বাংলা (Bangladesh)",
        Locale("bn", "IN") to "বাংলা (India)",
        Locale("hi", "IN") to "हिन्दी (Hindi)",
        Locale("ar") to "العربية (Arabic)",
        Locale("es", "ES") to "Español",
        Locale("fr", "FR") to "Français"
    )

    suspend fun loadAvailableLocales(context: Context): List<TtsLocaleOption> =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                var ttsRef: TextToSpeech? = null
                val tts = TextToSpeech(context.applicationContext) { status ->
                    if (!cont.isActive) {
                        ttsRef?.shutdown()
                        return@TextToSpeech
                    }
                    val list = if (status == TextToSpeech.SUCCESS) {
                        val engine = ttsRef
                        val seenTags = mutableSetOf<String>()
                        val options = mutableListOf<TtsLocaleOption>()

                        for ((loc, label) in preferredLocales) {
                            val code = engine?.isLanguageAvailable(loc) ?: TextToSpeech.LANG_NOT_SUPPORTED
                            val ok = code >= TextToSpeech.LANG_AVAILABLE
                            seenTags.add(loc.toLanguageTag())
                            options.add(TtsLocaleOption(loc, label, ok))
                        }

                        // Include any extra installed voices from the phone's TTS engine
                        engine?.availableLanguages?.sortedBy { it.displayName }?.forEach { loc ->
                            val tag = loc.toLanguageTag()
                            if (seenTags.add(tag) && options.size < 20) {
                                val code = engine.isLanguageAvailable(loc)
                                if (code >= TextToSpeech.LANG_AVAILABLE) {
                                    options.add(TtsLocaleOption(loc, loc.getDisplayName(loc).replaceFirstChar { it.uppercase() }, true))
                                }
                            }
                        }
                        options
                    } else {
                        preferredLocales.map { (loc, label) -> TtsLocaleOption(loc, label, false) }
                    }
                    ttsRef?.shutdown()
                    cont.resume(list)
                }
                ttsRef = tts
                cont.invokeOnCancellation {
                    runCatching { ttsRef?.shutdown() }
                }
            }
        }

    suspend fun synthesizeToWav(
        context: Context,
        text: String,
        locale: Locale,
        speechRate: Float,
        pitch: Float,
        destFile: File
    ): Result<File> = withContext(Dispatchers.Main) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Empty text"))
        }

        suspendCancellableCoroutine { cont ->
            var ttsRef: TextToSpeech? = null
            val utteranceId = UUID.randomUUID().toString()

            val tts = TextToSpeech(context.applicationContext) { status ->
                val engine = ttsRef
                if (status != TextToSpeech.SUCCESS || engine == null) {
                    engine?.shutdown()
                    if (cont.isActive) cont.resume(Result.failure(IllegalStateException("TTS engine unavailable")))
                    return@TextToSpeech
                }

                val langResult = engine.setLanguage(locale)
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.shutdown()
                    if (cont.isActive) cont.resume(Result.failure(IllegalStateException("TTS language not installed: ${locale.displayName}")))
                    return@TextToSpeech
                }

                engine.setSpeechRate(speechRate.coerceIn(0.5f, 2.0f))
                engine.setPitch(pitch.coerceIn(0.5f, 1.8f))

                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) = Unit

                    override fun onDone(id: String?) {
                        engine.shutdown()
                        if (cont.isActive) {
                            if (destFile.exists() && destFile.length() > 44L) {
                                cont.resume(Result.success(destFile))
                            } else {
                                cont.resume(Result.failure(IllegalStateException("Empty TTS audio output")))
                            }
                        }
                    }

                    @Deprecated("Deprecated in API 21")
                    override fun onError(id: String?) {
                        engine.shutdown()
                        if (cont.isActive) cont.resume(Result.failure(IllegalStateException("TTS synthesis failed")))
                    }

                    override fun onError(id: String?, errorCode: Int) {
                        engine.shutdown()
                        if (cont.isActive) cont.resume(Result.failure(IllegalStateException("TTS synthesis error ($errorCode)")))
                    }
                })

                destFile.parentFile?.mkdirs()
                if (destFile.exists()) destFile.delete()
                val res = engine.synthesizeToFile(cleanText, Bundle(), destFile, utteranceId)
                if (res != TextToSpeech.SUCCESS) {
                    engine.shutdown()
                    if (cont.isActive) cont.resume(Result.failure(IllegalStateException("TTS synthesizeToFile rejected")))
                }
            }
            ttsRef = tts
            cont.invokeOnCancellation {
                runCatching { ttsRef?.stop() }
                runCatching { ttsRef?.shutdown() }
            }
        }
    }
}
