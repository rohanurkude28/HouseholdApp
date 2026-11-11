package com.houseaffairs.app.feature.quickadd

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [Assistant] on Gemini through Firebase AI Logic: no server of our own and no API key in the app.
 * Quick add sends only the typed line, today's date, member first names and the currency.
 * Suggestions send dish and watch-list titles and habit scores, never amounts or who an entry is for.
 * Any failure (offline, not set up in the Firebase project, slow) is just "no answer".
 */
@Singleton
class GeminiAssistant @Inject constructor() : Assistant {

    private val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                temperature = 0f
            },
        )
    }

    override suspend fun understand(text: String, context: AssistContext): QuickAddDraft? =
        ask(assistPrompt(text, context))?.let { draftFromAi(it, context) }

    override suspend fun pick(goal: String, about: String, options: List<String>, count: Int): List<Pick> =
        if (options.isEmpty()) emptyList() else ask(pickPrompt(goal, about, options, count))?.let { picksFromAi(it, options) }.orEmpty()

    override suspend fun coach(about: String): String? = ask(coachPrompt(about))?.let(::coachFromAi)

    override suspend fun readBill(text: String): com.houseaffairs.app.core.data.BillScan? =
        ask(billPrompt(text))?.let { billFromAi(it, java.time.LocalDate.now()) }

    private suspend fun ask(prompt: String): String? =
        withTimeoutOrNull(TIMEOUT_MS) { runCatching { model.generateContent(prompt).text }.getOrNull() }

    private companion object {
        /** The small, fast Gemini model: plenty for sorting one line. */
        const val MODEL = "gemini-2.5-flash-lite"
        const val TIMEOUT_MS = 6_000L
    }
}
