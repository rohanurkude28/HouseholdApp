package com.houseaffairs.app.feature.add

import androidx.lifecycle.ViewModel
import com.houseaffairs.app.core.data.PhraseRepository
import com.houseaffairs.app.feature.quickadd.Assistant
import com.houseaffairs.app.feature.quickadd.AssistContext
import com.houseaffairs.app.feature.quickadd.KindClassifier
import com.houseaffairs.app.feature.quickadd.LineKind
import com.houseaffairs.app.feature.quickadd.SEED_EXAMPLES
import com.houseaffairs.app.feature.quickadd.phraseKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.lifecycle.viewModelScope
import com.houseaffairs.app.core.data.CookPlan
import com.houseaffairs.app.core.data.DishIdea
import com.houseaffairs.app.core.data.MoneyRepository
import com.houseaffairs.app.core.data.MoneyState
import com.houseaffairs.app.core.data.Pocket
import com.houseaffairs.app.core.data.available
import com.houseaffairs.app.core.data.KeptItem
import com.houseaffairs.app.core.data.ThingsRepository
import com.houseaffairs.app.core.data.whereIs
import com.houseaffairs.app.core.data.cravingIdeas
import com.houseaffairs.app.core.data.privateTopicOf
import com.houseaffairs.app.core.data.privateVisibleTo
import com.houseaffairs.app.core.data.GeoRepository
import com.houseaffairs.app.core.data.LatLng
import com.houseaffairs.app.core.data.PlaceItem
import com.houseaffairs.app.core.data.nearbyPlaces
import com.houseaffairs.app.core.data.CARE
import com.houseaffairs.app.core.data.MedicineInfo
import com.houseaffairs.app.core.data.medicinesFor
import com.houseaffairs.app.core.data.isOngoing
import com.houseaffairs.app.core.data.sameNeed
import com.houseaffairs.app.core.data.cookIdeas
import com.houseaffairs.app.core.data.useItUp
import com.houseaffairs.app.core.util.formatMinor
import com.houseaffairs.app.core.util.toMinorUnits
import com.houseaffairs.app.feature.quickadd.EXPENSE_CATEGORIES
import com.houseaffairs.app.core.data.EntryRepository
import com.houseaffairs.app.core.data.PantryRepository
import com.houseaffairs.app.core.data.PantryStock
import com.houseaffairs.app.core.data.Recipe
import com.houseaffairs.app.core.data.planCooking
import com.houseaffairs.app.core.data.sameIngredient
import com.houseaffairs.app.core.model.AppModule
import com.houseaffairs.app.core.model.RolePolicy
import kotlinx.coroutines.flow.flowOf
import com.houseaffairs.app.core.data.HouseholdRepository
import com.houseaffairs.app.core.data.SessionManager
import com.houseaffairs.app.core.data.SessionState
import com.houseaffairs.app.core.data.SettingsRepository
import com.houseaffairs.app.core.model.Member
import com.houseaffairs.app.core.model.Visibility
import com.houseaffairs.app.feature.quickadd.QuickAddDraft
import com.houseaffairs.app.feature.quickadd.QuickAddParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import com.houseaffairs.app.core.data.FoundEntry
import com.houseaffairs.app.feature.quickadd.ListTemplate

/** Pause in typing before an unsure line goes to the assistant. */
private const val ASSIST_PAUSE_MS = 800L

private val EXAMPLES = listOf(
    "450 groceries DMart", "Netflix 649 monthly", "milk over", "crocin for Riya expires March",
    "what can I cook?", "move 2000 from gifts to travel", "can we afford Bali trip?", "dream Bali trip 150000 by March",
    "got Amazon gift card 2000 for gifting", "gold necklace is in the bank locker", "where are the car keys?",
    "walked 30 min", "I'm feeling feverish", "going to Indiranagar", "what's near me?", "AAA battery 5 units expiry 2028", "add milk, eggs and bread to shopping list", "delete the Uber expense",
    "Riya birthday 12 Dec", "pay rent tomorrow", "add Dune 2 to watch list",
)

enum class WhoCanSee(val label: String) { EVERYONE("Everyone"), ONLY_ME("Only me"), CHOSEN("Choose people") }

/** Where a suggestion came from, shown on the card so a guess reads as a guess. */
enum class SuggestionSource { RULES, LEARNED, GUESS, AI }

data class Suggestion(
    val type: EntryType,
    val values: FormValues,
    val title: String,
    val summary: String,
    val missing: List<String>,
    val source: SuggestionSource = SuggestionSource.RULES,
    /** The kind picked for an unsure line; the card offers the others to switch to. */
    val kind: LineKind? = null,
    /** "Jeera is in the spice box" about an item already saved: saving sets its place. */
    val placeOf: PantryStock? = null,
) {
    val unsure: Boolean get() = kind != null
}

data class FormState(
    val type: EntryType,
    val values: FormValues,
    val showMore: Boolean = false,
    val showErrors: Boolean = false,
    val whoCanSee: WhoCanSee = WhoCanSee.EVERYONE,
    val visibleMembers: List<String> = emptyList(),
    val source: String = "form",
    /** Set when editing an existing entry; saving updates it instead of adding a new one. */
    val editId: String? = null,
    val original: FormValues? = null,
    val originalVisibleTo: List<String>? = null,
) {
    val missing: List<FieldSpec> get() = type.fields.filter { it.required && it.visibleWhen(values) && !it.isFilled(values[it.key]) }
}

/** A money move the assistant understood, ready to confirm. */
data class MoveAction(
    val amountMinor: Long,
    val currency: String,
    val from: Pocket,
    val fromLabel: String,
    val to: Pocket?,
    val toLabel: String,
    /** Set when it can't be done as said (unknown dream, not enough in the envelope…). */
    val problem: String? = null,
    /** Overrides the "from → to" line, e.g. for interest or a value update. */
    val headline: String? = null,
)

/** The assistant's answer to a question, optionally with a follow-up move. */
data class Answer(
    val title: String,
    val lines: List<String>,
    val ideas: List<DishIdea> = emptyList(),
    val move: MoveAction? = null,
    /** "What's near me" without location permission: the card offers to allow it. */
    val needsLocation: Boolean = false,
    val loading: Boolean = false,
    /** Dishes grouped by the part of a craving they answer ("Crispy", "Savoury curry"). */
    val groups: List<Pair<String, List<DishIdea>>> = emptyList(),
    /** Gemini's reason for a dish, by title, when smart suggestions are on. */
    val why: Map<String, String> = emptyMap(),
)

/** "Made paneer sabzi": the matching recipe (if any) and what it takes from the pantry. */
data class CookSuggestion(
    val dish: String,
    /** People cooked for: chosen family members plus guests, or the number said ("for 4"). */
    val eaters: List<String>,
    val guests: Int,
    val recipe: Recipe?,
    val plan: CookPlan?,
    val showError: Boolean = false,
) {
    val people: Int get() = eaters.size + guests
}

/** "Delete the Uber expense": what was found, which one is picked, and what will happen. */
data class ChangeProposal(
    val draft: QuickAddDraft.Change,
    val matches: List<FoundEntry> = emptyList(),
    val picked: Int = 0,
    val loading: Boolean = false,
    /** For "add this expense to Goa trip": the trip's name as saved. */
    val linkTo: String? = null,
    val problem: String? = null,
    /** Nothing matched an edit: maybe it was something new ("update passport" as a task). */
    val fallback: Suggestion? = null,
) {
    val entry: FoundEntry? get() = matches.getOrNull(picked)
}

private data class LocalState(
    val text: String = "",
    val suggestion: Suggestion? = null,
    /** Several things said at once, each its own suggestion. */
    val batch: List<Suggestion>? = null,
    val change: ChangeProposal? = null,
    val cook: CookSuggestion? = null,
    val move: MoveAction? = null,
    val answer: Answer? = null,
    val form: FormState? = null,
    val voiceUnavailable: Boolean = false,
    /** Waiting for the assistant's reading of an unsure line. */
    val thinking: Boolean = false,
    /** The assistant was already asked about this text. */
    val asked: Boolean = false,
)

data class AddUiState(
    val text: String = "",
    val suggestion: Suggestion? = null,
    val batch: List<Suggestion>? = null,
    val change: ChangeProposal? = null,
    val cook: CookSuggestion? = null,
    val move: MoveAction? = null,
    val answer: Answer? = null,
    /** Runtime options for choice fields, e.g. dream names for "Save it for". */
    val dynamicOptions: Map<String, List<String>> = emptyMap(),
    val form: FormState? = null,
    val voiceUnavailable: Boolean = false,
    val thinking: Boolean = false,
    /** Smart understanding is off, but this unsure line can still be sent once, on request. */
    val canAskAi: Boolean = false,
    val members: List<Member> = emptyList(),
    val myUid: String = "",
    /** Kinds this member's role may add; others are hidden. */
    val allowedTypes: List<EntryType> = emptyList(),
    /** Example phrases, only for kinds this member can add. */
    val examples: List<String> = emptyList(),
)

data class SavedEntry(val message: String, val undo: () -> Unit)

/** How the Add sheet opens: blank, or straight into a form with some values set ("Plan a spend"). */
data class AddRequest(val type: EntryType? = null, val preset: FormValues = emptyMap(), val editId: String? = null)

/**
 * Drives the Add sheet. Voice or text becomes a [Suggestion] that pre-fills a form; a tile opens a
 * blank form; both end in the same [FormState] and the same save.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AddViewModel @Inject constructor(
    private val entries: EntryRepository,
    private val pantry: PantryRepository,
    private val moneyRepo: MoneyRepository,
    private val things: ThingsRepository,
    private val geo: GeoRepository,
    private val session: SessionManager,
    households: HouseholdRepository,
    private val settings: SettingsRepository,
    private val phraseRepo: PhraseRepository,
    private val assistant: Assistant,
) : ViewModel() {
    private val parser = QuickAddParser()
    private val local = MutableStateFlow(LocalState())
    private val ready = session.state.filterIsInstance<SessionState.Ready>()
    private val members = ready.map { it.household.id }.distinctUntilChanged().flatMapLatest { households.observeMembers(it) }

    // Budget, envelopes, credits and dreams, kept warm for "move 2000…" and "can we afford…".
    private val money: StateFlow<MoneyState?> = ready
        .map { Triple(it.household.id, it.user.uid, it.me.canAccess(AppModule.MONEY)) }
        .distinctUntilChanged()
        .flatMapLatest { (householdId, uid, canMoney) -> if (canMoney) moneyRepo.observeMoney(householdId, uid) else flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // "Where is it kept" list, for "where are the car keys?". Vault access only.
    private val kept: StateFlow<List<KeptItem>> = ready
        .map { Triple(it.household.id, it.user.uid, it.me.canAccess(AppModule.VAULT)) }
        .distinctUntilChanged()
        .flatMapLatest { (householdId, uid, can) -> if (can) things.observeKept(householdId, uid) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Medicine cabinet with "used for" tags, for "I'm feeling feverish". Medicine access only.
    private val cabinet: StateFlow<List<MedicineInfo>> = ready
        .map { Triple(it.household.id, it.user.uid, it.me.canAccess(AppModule.MEDICINE) to RolePolicy.ownMedicinesOnly(it.me.role)) }
        .distinctUntilChanged()
        .flatMapLatest { (householdId, uid, access) -> if (access.first) pantry.observeMedicineInfo(householdId, uid, access.second) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Saved places, for "going to Indiranagar". Plans access only.
    private val places: StateFlow<List<PlaceItem>> = ready
        .map { Triple(it.household.id, it.user.uid, it.me.canAccess(AppModule.PLANS)) }
        .distinctUntilChanged()
        .flatMapLatest { (householdId, uid, can) -> if (can) things.observePlaces(householdId, uid) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Recipes, pantry and shopping list, kept warm so "made X" can be matched while typing.
    private data class Kitchen(val recipes: List<Recipe> = emptyList(), val stock: List<PantryStock> = emptyList(), val shopping: List<String> = emptyList())

    private val kitchen: StateFlow<Kitchen> = ready
        .map { Triple(it.household.id, it.user.uid, it.me.canAccess(AppModule.STOCK)) }
        .distinctUntilChanged()
        .flatMapLatest { (householdId, uid, canStock) ->
            if (!canStock) {
                flowOf(Kitchen())
            } else {
                combine(
                    pantry.observeRecipes(householdId, uid),
                    pantry.observePantry(householdId, uid),
                    pantry.observeShopping(householdId, uid),
                ) { recipes, stock, shopping -> Kitchen(recipes, stock, shopping.map { it.title }) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Kitchen())

    // Pantry, supplies and medicines, for "jeera is in the spice box" and "where is the jeera?".
    private val homeStock: StateFlow<List<Pair<EntryType, PantryStock>>> = ready
        .map { Triple(it.household.id, it.user.uid, Triple(it.me.canAccess(AppModule.STOCK), it.me.canAccess(AppModule.MEDICINE), RolePolicy.ownMedicinesOnly(it.me.role))) }
        .distinctUntilChanged()
        .flatMapLatest { (householdId, uid, access) ->
            val (canStock, canMedicine, ownMeds) = access
            combine(
                if (canStock) pantry.observePantry(householdId, uid) else flowOf(emptyList()),
                if (canStock) pantry.observeSupplies(householdId, uid) else flowOf(emptyList()),
                if (canMedicine) pantry.observeMedicines(householdId, uid, ownMeds) else flowOf(emptyList()),
            ) { food, supplies, medicines ->
                food.map { EntryType.GROCERY to it } + supplies.map { EntryType.HOUSEHOLD to it } + medicines.map { EntryType.MEDICINE to it }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Birthdays and other events, so a gift idea can say which occasion it's for.
    private val eventTitles: StateFlow<List<String>> = ready
        .map { Triple(it.household.id, it.user.uid, it.me.canAccess(AppModule.PLANS)) }
        .distinctUntilChanged()
        .flatMapLatest { (householdId, uid, can) -> if (can) things.observeEventTitles(householdId, uid) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // What the family has taught quick add, and the classifier trained on it.
    private val phrases: StateFlow<Map<String, LineKind>> = ready
        .map { it.household.id }
        .distinctUntilChanged()
        .flatMapLatest { phraseRepo.observe(it) }
        .map { saved -> saved.mapNotNull { (key, kind) -> runCatching { LineKind.valueOf(kind) }.getOrNull()?.let { key to it } }.toMap() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // The family's own phrases count three times, so they outweigh the starter examples.
    private val classifier: StateFlow<KindClassifier> = phrases
        .map { learned -> KindClassifier(SEED_EXAMPLES + List(3) { learned.toList() }.flatten()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, KindClassifier(SEED_EXAMPLES))

    private val smartAdd: StateFlow<Boolean> = settings.smartAdd.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val smartSuggest: StateFlow<Boolean> = settings.smartSuggest.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private var assistJob: Job? = null

    // Declared after `money` and `kitchen`: the combine below reads them as soon as it starts.
    val state: StateFlow<AddUiState> = combine(local, ready, members, money) { l, r, m, money ->
        AddUiState(
            text = l.text,
            suggestion = l.suggestion,
            batch = l.batch,
            change = l.change,
            cook = l.cook,
            move = l.move,
            answer = l.answer,
            dynamicOptions = mapOf(
                "dreams" to money?.dreams?.map { it.dream.title }.orEmpty(),
                "cards" to money?.budget?.cards?.map { it.name }.orEmpty(),
                // Ongoing and upcoming trips; finished ones drop off after a week.
                "trips" to money?.trips?.map { it.trip }
                    ?.filter { !(it.end ?: it.start).isBefore(LocalDate.now().minusDays(7)) }
                    ?.map { it.title }.orEmpty(),
                "events" to eventTitles.value,
            ),
            form = l.form,
            voiceUnavailable = l.voiceUnavailable,
            thinking = l.thinking,
            canAskAi = l.suggestion?.unsure == true && l.suggestion.source !in setOf(SuggestionSource.AI, SuggestionSource.LEARNED) && !l.asked && !l.thinking,
            members = m,
            myUid = r.user.uid,
            allowedTypes = EntryType.entries.filter { r.me.canAccess(it.module) },
            examples = EXAMPLES.filter { example ->
                when (val d = parser.parse(example)) {
                    null -> false
                    is QuickAddDraft.Move, is QuickAddDraft.Fund, is QuickAddDraft.Growth, is QuickAddDraft.Revalue -> r.me.canAccess(AppModule.MONEY)
                    is QuickAddDraft.Change -> (d.kind != "expense" || r.me.canAccess(AppModule.MONEY))
                    is QuickAddDraft.Ask -> when (d.question) {
                        QuickAddDraft.Question.COOK, QuickAddDraft.Question.CRAVING -> true
                        QuickAddDraft.Question.WHERE -> true
                        QuickAddDraft.Question.NEARBY -> r.me.canAccess(AppModule.PLANS)
                        QuickAddDraft.Question.SYMPTOM -> true
                        else -> r.me.canAccess(AppModule.MONEY)
                    }
                    else -> r.me.canAccess(prefill(d).first.module)
                }
            },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AddUiState())

    private val ctx get() = session.state.value as? SessionState.Ready



    // ---------------------------------------------------------------------------------------
    // Picker

    fun onTextChange(text: String) {
        assistJob?.cancel()
        val draft = parser.parse(text)
        local.update { applyDraft(it, text, draft) }
        if (draft is QuickAddDraft.Task && parser.isUnsure(text)) secondOpinion(text)
        if (draft is QuickAddDraft.Kept) placeExisting(draft)?.let { s -> local.update { if (it.text == text) it.copy(suggestion = s) else it } }
        if (draft is QuickAddDraft.Change) {
            viewModelScope.launch {
                val found = lookUp(draft, text)
                local.update { if (it.text == text) it.copy(change = found) else it }
            }
        }
        if (draft is QuickAddDraft.Ask && draft.question == QuickAddDraft.Question.CRAVING && smartSuggest.value) pairDishes(text, draft.subject.orEmpty())
        if (draft is QuickAddDraft.Ask && draft.question == QuickAddDraft.Question.NEARBY) {
            viewModelScope.launch {
                val result = nearbyAnswer(draft.subject)
                // Only if the user hasn't typed something else meanwhile.
                local.update { if (it.text == text) it.copy(answer = result) else it }
            }
        }
    }

    private fun applyDraft(it: LocalState, text: String, draft: QuickAddDraft?): LocalState {
        val cleared = it.copy(
            text = text, suggestion = null, batch = null, change = null, cook = null, move = null, answer = null,
            voiceUnavailable = false, thinking = false, asked = false,
        )
        return when (draft) {
            is QuickAddDraft.Batch -> cleared.copy(batch = draft.drafts.map(::suggestionFor))
            is QuickAddDraft.Change -> cleared.copy(change = ChangeProposal(draft, loading = true))
            is QuickAddDraft.Cooked -> cleared.copy(cook = cookSuggestion(draft))
            is QuickAddDraft.Move -> cleared.copy(move = moveFor(draft.amount, draft.from, draft.to))
            is QuickAddDraft.Fund -> cleared.copy(move = moveFor(draft.amount, "Gullak", draft.target))
            is QuickAddDraft.Growth -> cleared.copy(move = growthFor(draft.target, toMinorUnits(draft.amount, currency()), revalue = false))
            is QuickAddDraft.Revalue -> cleared.copy(move = growthFor(draft.target, toMinorUnits(draft.amount, currency()), revalue = true))
            is QuickAddDraft.Ask -> cleared.copy(answer = answerFor(draft))
            null -> cleared
            else -> cleared.copy(suggestion = suggestionFor(draft))
        }
    }

    // -------------------------------------------------------------------------------------
    // Unsure lines: what the family taught, then the on-phone classifier, then (if allowed) Gemini

    private fun secondOpinion(text: String) {
        val learned = phrases.value[phraseKey(text)]
        val guess = classifier.value.classify(text)?.takeIf { learned == null && it.confidence >= KindClassifier.CONFIDENT }
        val kind = learned ?: guess?.kind ?: LineKind.TASK
        val source = when {
            learned != null -> SuggestionSource.LEARNED
            guess != null -> SuggestionSource.GUESS
            else -> SuggestionSource.RULES
        }
        val suggestion = suggestionFor(parser.asKind(kind, text)).copy(source = source, kind = kind)
        local.update { if (it.text == text) it.copy(suggestion = suggestion) else it }
        // Something the family already taught needs no second opinion.
        if (learned == null && smartAdd.value) askAssistant(text, waitForTyping = true)
    }

    /** An item already in the pantry, supplies or medicines gets its place set instead of a new entry. */
    private fun placeExisting(draft: QuickAddDraft.Kept): Suggestion? {
        val (type, stock) = homeStock.value.firstOrNull { (_, s) -> s.title.equals(draft.item, ignoreCase = true) }
            ?: homeStock.value.firstOrNull { (_, s) -> sameIngredient(s.title, draft.item) }
            ?: return null
        return Suggestion(
            type = type,
            values = mapOf("keptAt" to draft.location),
            title = stock.title,
            summary = "📍 Kept at ${draft.location}" + (stock.keptAt?.let { " (was $it)" } ?: ""),
            missing = emptyList(),
            placeOf = stock,
        )
    }

    /** Sends the current line to the assistant: automatically when allowed, or once when asked. */
    fun askAssistant(text: String = local.value.text, waitForTyping: Boolean = false) {
        val r = ctx ?: return
        assistJob?.cancel()
        local.update { if (it.text == text) it.copy(thinking = true, asked = true) else it }
        assistJob = viewModelScope.launch {
            // While typing, wait for a pause so each keystroke isn't a request.
            if (waitForTyping) delay(ASSIST_PAUSE_MS)
            val context = AssistContext(LocalDate.now(), state.value.members.map { it.displayName.substringBefore(' ') }, r.household.baseCurrency)
            val draft = assistant.understand(text, context)
            local.update { s ->
                when {
                    s.text != text -> s
                    draft == null -> s.copy(thinking = false)
                    else -> s.copy(thinking = false, suggestion = suggestionFor(draft).copy(source = SuggestionSource.AI, kind = lineKindOf(draft) ?: LineKind.TASK))
                }
            }
        }
    }

    /** Gemini picks dishes from the craving's matches that go well together, and says why. */
    private fun pairDishes(text: String, craving: String) {
        val answer = local.value.answer ?: return
        val options = answer.groups.flatMap { it.second }.map { it.recipe.title }.distinct()
        if (options.isEmpty()) return
        assistJob?.cancel()
        assistJob = viewModelScope.launch {
            delay(ASSIST_PAUSE_MS)
            val kitchen = this@AddViewModel.kitchen.value.stock.take(25).joinToString { it.title }
            val picks = assistant.pick(
                "a meal for someone who said: \"$craving\". Pick dishes that go well together to make it",
                "In their kitchen: ${kitchen.ifEmpty { "not tracked" }}. Now: ${java.time.LocalTime.now().hour}:00.",
                options, 3,
            )
            local.update { s ->
                val a = s.answer
                if (s.text != text || a == null) return@update s
                val order = picks.map { it.title }
                fun rank(i: DishIdea) = order.indexOf(i.recipe.title).let { if (it < 0) Int.MAX_VALUE else it }
                s.copy(answer = a.copy(loading = false, why = picks.associate { it.title to it.why }, groups = a.groups.map { (label, ideas) -> label to ideas.sortedBy(::rank) }))
            }
        }
    }

    /** "Not a task, it's shopping": switches the card and teaches the whole household. */
    fun changeKind(kind: LineKind) {
        val r = ctx ?: return
        val text = local.value.text
        assistJob?.cancel()
        phraseRepo.remember(r.household.id, r.user.uid, phraseKey(text), kind.name)
        local.update { s -> s.copy(thinking = false, suggestion = suggestionFor(parser.asKind(kind, text)).copy(source = SuggestionSource.LEARNED, kind = kind)) }
    }

    private fun lineKindOf(draft: QuickAddDraft): LineKind? = when (draft) {
        is QuickAddDraft.Shopping -> LineKind.SHOPPING
        is QuickAddDraft.Task -> LineKind.TASK
        is QuickAddDraft.Event -> LineKind.EVENT
        is QuickAddDraft.Medicine -> LineKind.MEDICINE
        else -> null
    }

    // -------------------------------------------------------------------------------------
    // Assistant: money moves and answers

    private fun currency() = money.value?.budget?.currency ?: ctx?.household?.baseCurrency ?: "USD"

    private fun fmt(minor: Long) = formatMinor(minor, currency())

    /** Resolves a spoken name to an envelope, the gullak, or a dream. */
    private fun pocketFor(name: String): Pair<Pocket, String>? {
        val m = money.value
        if (name.equals("Gullak", ignoreCase = true)) return Pocket.Pot to "Gullak"
        m?.budget?.categories?.keys?.firstOrNull { it.equals(name, ignoreCase = true) }?.let { return Pocket.Category(it) to it }
        m?.dreams?.firstOrNull { sameIngredient(it.dream.title, name) }?.let { return Pocket.Dream(it.dream.id) to it.dream.title }
        m?.investments?.firstOrNull { sameIngredient(it.investment.title, name) }?.let { return Pocket.Investment(it.investment.id) to it.investment.title }
        if (m != null && name.lowercase() in setOf("fd", "fixed deposit", "mutual fund", "mf", "sip", "gold", "rd")) {
            val kind = when (name.lowercase()) { "fd" -> "Fixed deposit"; "mf", "sip" -> "Mutual fund"; "rd" -> "Recurring deposit"; else -> null }
            m.investments.firstOrNull { it.investment.kind.equals(kind ?: name, ignoreCase = true) }?.let { return Pocket.Investment(it.investment.id) to it.investment.title }
        }
        EXPENSE_CATEGORIES.firstOrNull { it.equals(name, ignoreCase = true) }?.let { return Pocket.Category(it) to it }
        return null
    }

    private fun availableIn(pocket: Pocket): Long? = money.value?.available(pocket)

    private fun moveFor(amount: java.math.BigDecimal, fromName: String, toName: String): MoveAction {
        val minor = toMinorUnits(amount, currency())
        if (money.value == null) {
            return MoveAction(minor, currency(), Pocket.Pot, fromName, null, toName, problem = "Set a monthly budget first. Moving money works between its envelopes, savings and dreams.")
        }
        val from = pocketFor(fromName)
        val to = pocketFor(toName)
        val problem = when {
            from == null -> "There's no envelope or dream called “$fromName”."
            to == null -> "There's no envelope or dream called “$toName”. Add it as a dream first."
            (availableIn(from.first) ?: 0) < minor -> "${from.second} only has ${fmt((availableIn(from.first) ?: 0).coerceAtLeast(0))}."
            else -> null
        }
        return MoveAction(minor, currency(), from?.first ?: Pocket.Pot, from?.second ?: fromName, to?.first, to?.second ?: toName, problem)
    }

    /** Interest on an investment, or setting its value (recorded as a gain or loss in its ledger). */
    private fun growthFor(target: String, amountMinor: Long, revalue: Boolean): MoveAction {
        val inv = (pocketFor(target)?.first as? Pocket.Investment)?.let { p -> money.value?.investments?.firstOrNull { it.investment.id == p.id } }
            ?: return MoveAction(
                amountMinor = amountMinor, currency = currency(), from = Pocket.Growth, fromLabel = "", to = null, toLabel = target,
                problem = "There's no investment called “$target”. Add it first from the + button → Investment.",
            )
        val delta = if (revalue) amountMinor - inv.currentMinor else amountMinor
        return MoveAction(
            headline = if (revalue) "${inv.investment.title}: ${fmt(inv.currentMinor)} → ${fmt(amountMinor)}" else "+${fmt(amountMinor)} interest · ${inv.investment.title}",
            amountMinor = delta,
            currency = currency(),
            from = Pocket.Growth,
            fromLabel = if (revalue) "Value update" else "Interest",
            to = Pocket.Investment(inv.investment.id),
            toLabel = inv.investment.title,
            problem = if (revalue && delta == 0L) "That's already its value." else null,
        )
    }

    /** Confirms a move from the assistant card. */
    fun performMove(action: MoveAction? = local.value.move ?: local.value.answer?.move): SavedEntry? {
        val r = ctx ?: return null
        val a = action ?: return null
        val to = a.to ?: return null
        if (a.problem != null) return null
        val ref = moneyRepo.transfer(r.household.id, r.user.uid, a.from, to, a.amountMinor, note = a.headline ?: local.value.text.ifBlank { null })
        local.value = LocalState()
        return SavedEntry(a.headline ?: "Moved ${fmt(a.amountMinor)} · ${a.fromLabel} → ${a.toLabel}") { ref.delete() }
    }

    private fun answerFor(ask: QuickAddDraft.Ask): Answer {
        val today = LocalDate.now()
        val k = kitchen.value
        val m = money.value
        return when (ask.question) {
            QuickAddDraft.Question.COOK -> {
                val ideas = (useItUp(k.stock, k.recipes, today) + cookIdeas(k.stock, k.recipes, today)).distinctBy { it.recipe.title }.take(4)
                Answer(
                    title = if (ideas.isEmpty()) "Add a few groceries first" else "You could make",
                    lines = if (ideas.isEmpty()) listOf("Tell me what's in the kitchen (“bought 500g paneer”) and I'll suggest dishes.") else emptyList(),
                    ideas = ideas,
                )
            }
            QuickAddDraft.Question.CRAVING -> {
                val groups = cravingIdeas(ask.subject.orEmpty(), k.stock, k.recipes, today)
                Answer(
                    title = if (groups.isEmpty()) "Nothing matches that yet" else "For “${ask.subject}”",
                    lines = if (groups.isEmpty()) listOf("Try “something crispy”, “a spicy curry” or “something with paneer”, or save your own recipes.") else emptyList(),
                    groups = groups,
                    loading = groups.isNotEmpty() && smartSuggest.value,
                )
            }
            QuickAddDraft.Question.EXPIRING -> {
                val soon = k.stock.filter { s -> s.expiry?.let { !it.isAfter(today.plusDays(3)) } == true }
                Answer(
                    title = if (soon.isEmpty()) "Nothing is about to expire" else "Use these soon",
                    lines = soon.map { s -> "${s.title} · ${if (s.expiry!!.isBefore(today)) "expired" else "use by ${formatDate(s.expiry)}"}" },
                    ideas = useItUp(k.stock, k.recipes, today).take(3),
                )
            }
            QuickAddDraft.Question.LEFT -> when {
                m == null -> Answer("No budget yet", listOf("Set a monthly budget in Money and I'll keep track of what's left."))
                ask.subject == null -> Answer(
                    "${fmt(m.summary.leftMinor)} left this month",
                    listOf("Gullak: ${fmt(m.envelopes.potMinor)}", "Unused so far this year: ${fmt(m.envelopes.savedThisYearMinor)}"),
                )
                else -> {
                    val pocket = pocketFor(ask.subject)
                    when (val p = pocket?.first) {
                        is Pocket.Category -> {
                            val e = m.envelopes.envelopes.firstOrNull { it.category == p.name }
                            if (e == null) {
                                Answer("${p.name} has no budget", listOf("Add a ${p.name} amount when you edit the budget."))
                            } else {
                                Answer(
                                    "${fmt(e.availableMinor)} for ${e.category}",
                                    listOfNotNull(
                                        "${fmt(e.spentMinor)} spent of ${fmt(e.limitMinor)} this month",
                                        if (e.creditsMinor > 0) "+ ${fmt(e.creditsMinor)} in gift cards and coupons" else null,
                                    ),
                                )
                            }
                        }
                        is Pocket.Dream -> affordAnswer(p.id)
                        is Pocket.Investment -> investmentAnswer(p.id)
                        Pocket.Growth -> Answer("", emptyList())
                        Pocket.Pot -> Answer("${fmt(m.envelopes.potMinor)} in the gullak", listOf("Move it to a dream: “save 5000 for Bali trip”."))
                        null -> Answer("I couldn't find “${ask.subject}”", listOf("Try a category like dining or gifts, or a dream's name."))
                    }
                }
            }
            // The search itself runs after the text is stored; see onTextChange.
            QuickAddDraft.Question.NEARBY ->
                Answer(if (ask.subject == null) "Looking around you…" else "Looking around ${ask.subject}…", emptyList(), loading = true)
            QuickAddDraft.Question.SYMPTOM -> symptomAnswer(ask.subject.orEmpty(), ask.person)
            QuickAddDraft.Question.CARD_BILL -> cardBillAnswer(ask.subject == "paid")
            QuickAddDraft.Question.WHERE -> {
                // Valuables (Vault) only for adults; pantry, supplies and medicines for whoever can see them.
                val found = whereIs(ask.subject.orEmpty(), kept.value, homeStock.value.map { it.second })
                when {
                    found.isEmpty() -> Answer(
                        "I don't know where “${ask.subject}” is yet",
                        listOf("Next time, say “${ask.subject} is in the …” and I'll remember."),
                    )
                    else -> Answer(
                        found.first().let { "${it.title} → ${it.place}" },
                        found.drop(1).map { "${it.title} → ${it.place}" } + listOfNotNull(found.first().note),
                    )
                }
            }
            QuickAddDraft.Question.AFFORD -> {
                val dream = m?.dreams?.firstOrNull { sameIngredient(it.dream.title, ask.subject.orEmpty()) }
                when {
                    m == null -> Answer("No budget yet", listOf("Set a budget so I can tell what you can afford."))
                    dream == null -> Answer("“${ask.subject}” isn't on the vision board", listOf("Add it: “dream ${ask.subject} 150000 by March”."))
                    else -> affordAnswer(dream.dream.id)
                }
            }
        }
    }

    /** Each card's bill and what's on the open statement. Paying it isn't new spending. */
    private fun cardBillAnswer(paying: Boolean): Answer {
        val m = money.value ?: return Answer("Set a budget first", listOf("Card bills show once the budget is set up in Money."))
        if (m.budget.cards.isEmpty()) {
            return Answer("No cards set up yet", listOf("Add your card's statement date in Money → edit budget → Credit cards, and bills will show up with their due dates."))
        }
        val lines = m.cardBills.groupBy { it.card }.flatMap { (card, bills) ->
            listOfNotNull(
                bills.firstOrNull { !it.open }?.let { b ->
                    "${card.name}: bill ${fmt(b.amountMinor)} for ${formatDate(b.cycle.start)} – ${formatDate(b.closesOn)}, " +
                        if (b.paid) "paid ✓" else "due ${formatDate(b.dueDate)}"
                },
                bills.firstOrNull { it.open }?.let { b -> "${card.name}: ${fmt(b.amountMinor)} so far on the statement closing ${formatDate(b.closesOn)}" },
            )
        }
        return Answer(
            if (paying) "Mark it paid in Money" else "Your card bills",
            lines + if (paying) listOf("Paying the bill isn't new spending: those expenses already count in your budget. Tap “Mark paid” on the card in Money → This month.") else emptyList(),
        )
    }

    private fun symptomAnswer(tag: String, person: String?): Answer {
        val care = CARE.firstOrNull { it.tag == tag } ?: return Answer("Take care", emptyList())
        val who = person?.let(::memberNamed)
        val canSeeMeds = ctx?.me?.canAccess(AppModule.MEDICINE) == true
        val meds = if (canSeeMeds) medicinesFor(tag, cabinet.value, who?.uid ?: ctx?.user?.uid, LocalDate.now()) else emptyList()
        val members = state.value.members
        val lines = buildList {
            when {
                !canSeeMeds -> add("Tell an adult at home. They can see the medicine cabinet.")
                meds.isEmpty() -> add("Nothing tagged “${care.tag}” in the cabinet. Add one with its “Used for” tag.")
                else -> {
                    add("At home:")
                    meds.take(3).forEach { m ->
                        val forNames = m.forWhom.mapNotNull { uid -> members.firstOrNull { it.uid == uid }?.displayName?.substringBefore(' ') }
                        add(
                            "💊 " + listOfNotNull(
                                m.title,
                                forNames.takeIf { it.isNotEmpty() }?.let { "for ${it.joinToString()}" },
                                m.dosage?.takeIf { it.isNotBlank() }?.let { "your note: $it" },
                                m.expiry?.let { "use by ${formatDate(it)}" },
                            ).joinToString(" · "),
                        )
                    }
                }
            }
            add("Home care:")
            care.homeCare.forEach { add("• $it") }
            add("See a doctor if: ${care.seeDoctor}.")
            add("This is general first aid, not medical advice. Check the label and ask a doctor or pharmacist if unsure.")
        }
        val name = who?.displayName?.substringBefore(' ') ?: person
        return Answer(if (name != null) "For $name's ${care.tag.lowercase()}" else "For ${care.tag.lowercase()}", lines)
    }

    /** Re-runs the current question, e.g. after location permission is granted. */
    fun refresh() = onTextChange(local.value.text)

    private suspend fun nearbyAnswer(subject: String?): Answer {
        val r = ctx ?: return Answer("Not signed in", emptyList())
        if (!r.me.canAccess(AppModule.PLANS)) return Answer("Ask an adult", listOf("Saved places are part of Plans."))
        val saved = geo.fillCoordinates(r.household.id, places.value)
        val destination = subject?.let { s -> saved.firstOrNull { sameIngredient(it.title, s) } }
        val center = when {
            subject == null -> geo.currentLocation() ?: return Answer(
                "Where are you?",
                listOf("Allow location so I can show saved places around you."),
                needsLocation = !geo.hasLocationPermission,
            )
            destination?.lat != null && destination.lng != null -> LatLng(destination.lat, destination.lng)
            else -> geo.geocode(subject)
        }
        val radius = 2.0
        val found = nearbyPlaces(saved, center, destination?.area ?: subject, radius, destination)
        val lines = found.take(6).map { n ->
            listOfNotNull(
                n.place.title,
                n.place.kind,
                n.km?.let { when {
                    it < 0.05 -> "right next door"
                    it < 1 -> "${(it * 1000).toInt() / 50 * 50} m"
                    else -> "%.1f km".format(it)
                } },
                n.place.goodFor?.let { "try $it" },
            ).joinToString(" · ")
        }.toMutableList()
        // Going shopping: what's on the list. Going anywhere else: a reminder if a grocer is nearby.
        val shopping = kitchen.value.shopping
        if (shopping.isNotEmpty() && (destination?.kind?.let { sameNeed(it, "Grocery") } == true || found.any { sameNeed(it.place.kind, "Grocery") })) {
            lines += "While you're there, buy: ${shopping.take(5).joinToString()}"
        }
        val where = destination?.title ?: subject ?: "you"
        return when {
            found.isEmpty() && center == null && subject != null -> Answer(
                "I couldn't find “$subject” on the map",
                listOf("Save places with an area (or a Maps link) and I'll match them by area."),
            )
            found.isEmpty() -> Answer(
                "Nothing saved within ${radius.toInt()} km of $where",
                lines.ifEmpty { listOf("Save restaurants and shops you want to try from Plans → Places.") },
            )
            else -> Answer("Near $where, you wanted to try", lines)
        }
    }

    private fun investmentAnswer(id: String): Answer {
        val v = money.value?.investments?.firstOrNull { it.investment.id == id } ?: return Answer("Not found", emptyList())
        return Answer(
            "${v.investment.title} is worth ${fmt(v.currentMinor)}",
            listOfNotNull(
                "${fmt(v.investedMinor)} put in · ${if (v.growthMinor >= 0) "+" else "−"}${fmt(kotlin.math.abs(v.growthMinor))} earned",
                v.estimateAtMaturity(LocalDate.now())?.let { "About ${fmt(it)} when it matures on ${formatDate(v.investment.maturity!!)}" },
                v.investment.forDream?.let { "Counts toward $it" },
            ),
        )
    }

    private fun affordAnswer(dreamId: String): Answer {
        val m = money.value ?: return Answer("No budget yet", emptyList())
        val p = m.dreams.first { it.dream.id == dreamId }
        val d = p.dream
        val lines = buildList {
            add("${fmt(p.savedMinor)} saved" + if (p.creditsMinor > 0) " + ${fmt(p.creditsMinor)} in credits" else "")
            add("Gullak has ${fmt(m.envelopes.potMinor)}")
            p.monthlyNeededMinor?.takeIf { p.remainingMinor > 0 }?.let { add("${fmt(it)} a month gets you there by ${d.targetDate?.let { date -> formatDate(date) }}") }
        }
        return when {
            p.remainingMinor == 0L -> Answer("Yes! ${d.title} is fully funded 🎉", lines)
            p.affordableNow -> Answer(
                "Yes, you can afford ${d.title}",
                lines,
                move = MoveAction(p.remainingMinor, currency(), Pocket.Pot, "Gullak", Pocket.Dream(d.id), d.title),
            )
            else -> Answer("Not yet: ${fmt(p.remainingMinor)} to go for ${d.title}", lines)
        }
    }

    private fun cookSuggestion(draft: QuickAddDraft.Cooked): CookSuggestion {
        val k = kitchen.value
        val recipe = k.recipes.firstOrNull { sameIngredient(it.title, draft.dish) }
        // "for 4" counts as 4 guests until people are picked; otherwise nobody is assumed.
        return withPlan(CookSuggestion(draft.dish, emptyList(), draft.servings ?: 0, recipe, null))
    }

    private fun withPlan(c: CookSuggestion): CookSuggestion {
        val plan = c.recipe?.let { planCooking(it.ingredients, c.people.coerceAtLeast(1).toDouble() / it.servings.coerceAtLeast(1), kitchen.value.stock) }
        return c.copy(plan = plan)
    }

    fun setEaters(eaters: List<String>) = local.update { s -> s.cook?.let { s.copy(cook = withPlan(it.copy(eaters = eaters, showError = false))) } ?: s }

    fun setGuests(guests: Int) = local.update { s -> s.cook?.let { s.copy(cook = withPlan(it.copy(guests = guests, showError = false))) } ?: s }

    /** Applies the cooking suggestion to the pantry. */
    suspend fun cook(): SavedEntry? {
        val c = local.value.cook ?: return null
        val r = ctx ?: return null
        val plan = c.plan ?: return null
        if (c.people == 0) {
            local.update { it.copy(cook = c.copy(showError = true)) }
            return null
        }
        val result = pantry.cook(r.household.id, r.user.uid, c.recipe?.title ?: c.dish, plan, kitchen.value.shopping, c.eaters, c.people)
        local.value = LocalState()
        return SavedEntry(result.message) { viewModelScope.launch { result.undo() } }
    }

    /** No recipe yet for what was cooked: start one so next time the pantry updates itself. */
    fun saveCookAsRecipe() = local.update { s ->
        val c = s.cook ?: return@update s
        s.copy(form = newForm(EntryType.RECIPE, mapOf("title" to c.dish), "voice"))
    }

    fun onVoiceUnavailable() = local.update { it.copy(voiceUnavailable = true) }

    // -------------------------------------------------------------------------------------
    // Changing what's already saved, and several things at once

    private fun typesFor(change: QuickAddDraft.Change): List<EntryType> = when (change.kind) {
        "expense" -> listOf(EntryType.EXPENSE)
        "task" -> listOf(EntryType.TASK)
        "event" -> listOf(EntryType.EVENT)
        "subscription" -> listOf(EntryType.SUBSCRIPTION)
        "shopping" -> listOf(EntryType.SHOPPING)
        "list" -> listOf(EntryType.WISH)
        "recipe" -> listOf(EntryType.RECIPE)
        "medicine" -> listOf(EntryType.MEDICINE)
        "dream" -> listOf(EntryType.DREAM)
        "trip" -> listOf(EntryType.TRIP)
        "place" -> listOf(EntryType.PLACE)
        else -> when (change.action) {
            QuickAddDraft.ChangeAction.DONE -> listOf(EntryType.TASK, EntryType.SHOPPING, EntryType.WISH, EntryType.DREAM, EntryType.TRIP)
            else -> listOf(
                EntryType.TASK, EntryType.SHOPPING, EntryType.EXPENSE, EntryType.SUBSCRIPTION, EntryType.EVENT, EntryType.WISH,
                EntryType.GROCERY, EntryType.HOUSEHOLD, EntryType.MEDICINE, EntryType.RECIPE, EntryType.DREAM, EntryType.TRIP,
                EntryType.PLACE, EntryType.KEPT, EntryType.CREDIT,
            )
        }
    }

    private fun simplified(name: String) = name.lowercase().replace(Regex("\\b(trip|outing|the|our|my)\\b"), "").trim()

    private suspend fun lookUp(change: QuickAddDraft.Change, text: String): ChangeProposal {
        val r = ctx ?: return ChangeProposal(change, problem = "Sign in first.")
        val types = typesFor(change).filter { r.me.canAccess(it.module) }
        val what = listOf(change.target.ifEmpty { "the latest" }, change.kind ?: "").joinToString(" ").trim()
        if (types.isEmpty()) return ChangeProposal(change, problem = "Your role can't change ${change.kind ?: "that"}s.")
        val matches = runCatching { entries.find(r.household.id, r.user.uid, types, change.target) }.getOrDefault(emptyList()).take(4)
        if (matches.isEmpty()) {
            val fallback = if (change.action == QuickAddDraft.ChangeAction.EDIT) parser.parseAsNew(text)?.takeIf { it !is QuickAddDraft.Batch }?.let(::suggestionFor) else null
            return ChangeProposal(change, problem = "Couldn't find $what.", fallback = fallback)
        }
        if (change.action == QuickAddDraft.ChangeAction.LINK) {
            val to = simplified(change.to.orEmpty())
            val trip = money.value?.trips?.map { it.trip }?.firstOrNull { simplified(it.title).let { t -> t.isNotEmpty() && (t.contains(to) || to.contains(t)) } }
                ?: return ChangeProposal(change, matches, problem = "No trip called “${change.to}”. Start it first: “start trip ${change.to}”.")
            return ChangeProposal(change, matches, linkTo = trip.title)
        }
        if (change.action == QuickAddDraft.ChangeAction.DONE && matches.none { doneChanges(it) != null }) {
            return ChangeProposal(change, matches, problem = "That can't be ticked off. Tap it to edit instead.")
        }
        return ChangeProposal(change, matches)
    }

    fun pickMatch(index: Int) = local.update { s -> s.change?.let { s.copy(change = it.copy(picked = index)) } ?: s }

    /** What "done" means for each kind; null when it doesn't apply. */
    private fun doneChanges(entry: FoundEntry): Map<String, Any?>? = when (entry.type) {
        EntryType.TASK -> mapOf("done" to true, "completedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())
        EntryType.SHOPPING -> mapOf("done" to true)
        EntryType.WISH -> mapOf("status" to "DONE")
        EntryType.DREAM -> mapOf("achieved" to true)
        // Ends today unless it already ended, so later expenses don't join it.
        EntryType.TRIP -> mapOf("done" to true) + if ((entry.data["end"] as? com.google.firebase.Timestamp)?.toDate()?.after(java.util.Date()) != false) {
            mapOf("end" to com.google.firebase.Timestamp(java.util.Date.from(LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant())))
        } else {
            emptyMap()
        }
        EntryType.EXPENSE -> if (entry.data["status"] == "Planned") mapOf("status" to "Paid") else null
        else -> null
    }

    fun doneLabel(entry: FoundEntry): String = when (entry.type) {
        EntryType.SHOPPING -> "Mark bought"
        EntryType.WISH -> ListTemplate.of(entry.data["template"] as? String)?.doneLabel ?: "Mark done"
        EntryType.DREAM -> "Mark achieved"
        EntryType.TRIP -> "Mark trip done"
        EntryType.EXPENSE -> "Mark paid"
        else -> "Mark done"
    }

    /** Deletes, ticks off or links the picked entry, with Undo. Edits open the form instead. */
    fun applyChange(): SavedEntry? {
        val r = ctx ?: return null
        val proposal = local.value.change ?: return null
        val entry = proposal.entry ?: return null
        val hid = r.household.id
        val saved = when (proposal.draft.action) {
            QuickAddDraft.ChangeAction.DELETE -> SavedEntry("${entry.title} · deleted", entries.remove(hid, entry))
            QuickAddDraft.ChangeAction.DONE -> {
                val changes = doneChanges(entry) ?: return null
                if (entry.type == EntryType.SHOPPING) {
                    // Ticking off a shopping item also restocks the pantry.
                    var undoBought: (() -> Unit)? = null
                    viewModelScope.launch { undoBought = runCatching { pantry.markBought(hid, r.user.uid, entry.id) }.getOrNull()?.undo }
                    SavedEntry("${entry.title} · bought, now in the pantry") { undoBought?.invoke() ?: entries.patch(hid, entry, mapOf("done" to false)) }
                } else {
                    SavedEntry("${entry.title} · done", entries.patch(hid, entry, changes))
                }
            }
            QuickAddDraft.ChangeAction.LINK -> {
                val trip = proposal.linkTo ?: return null
                SavedEntry("${entry.title} · added to $trip", entries.patch(hid, entry, mapOf("trip" to trip)))
            }
            QuickAddDraft.ChangeAction.EDIT -> return null
        }
        local.value = LocalState()
        return saved
    }

    /** "Change rent task to tomorrow": opens the entry's form with the new value already in. */
    fun editChange() {
        val proposal = local.value.change ?: return
        val entry = proposal.entry ?: return
        val to = proposal.draft.to
        openEdit(entry.type, entry.id) { values ->
            if (to == null) return@openEdit values
            val amount = parser.amountIn(to)
            val dateKey = entry.type.fields.filterIsInstance<DateSpec>().firstOrNull()?.key
            val date = parser.dateIn(to)
            when {
                amount != null && entry.type.fields.any { it is MoneySpec && it.key == "amount" } ->
                    values + ("amount" to MoneyValue(amount.toPlainString(), (values["amount"] as? MoneyValue)?.currency ?: currency()))
                date != null && dateKey != null -> values + (dateKey to date)
                entry.type.titleKey != null -> values + (entry.type.titleKey to to.replaceFirstChar { it.uppercase() })
                else -> values
            }
        }
    }

    /** Saves every complete item of a batch at once; Undo removes them all. */
    fun saveBatch(): SavedEntry? {
        val r = ctx ?: return null
        val forms = local.value.batch.orEmpty().map { newForm(it.type, it.values, "voice") }
            .filter { it.missing.isEmpty() && r.me.canAccess(it.type.module) }
        if (forms.isEmpty()) return null
        val refs = forms.map { entries.save(r.household.id, r.user.uid, it.type, it.values, Visibility.everyone, it.source) }
        viewModelScope.launch { settings.markQuickAddTried() }
        local.value = LocalState()
        val titles = forms.joinToString(", ") { titleOf(it.type, it.values) }
        return SavedEntry("${forms.size} saved · $titles") { refs.forEach(entries::delete) }
    }

    fun dropFromBatch(index: Int) = local.update { s ->
        val left = s.batch.orEmpty().filterIndexed { i, _ -> i != index }
        s.copy(batch = left.takeIf { it.isNotEmpty() })
    }

    /** Opens one item of a batch in its form (the rest stay unsaved). */
    fun editFromBatch(index: Int) = local.update { s ->
        val item = s.batch?.getOrNull(index) ?: return@update s
        s.copy(form = newForm(item.type, item.values, "voice"))
    }

    fun useFallback() = local.update { s ->
        val f = s.change?.fallback ?: return@update s
        s.copy(change = null, suggestion = f)
    }

    fun openForm(type: EntryType, preset: FormValues = emptyMap()) =
        local.update { it.copy(form = newForm(type, preset, "form")) }

    fun editSuggestion() {
        val placed = local.value.suggestion?.takeIf { it.placeOf != null }
        if (placed != null) return openEdit(placed.type, placed.placeOf!!.id) { it + placed.values }
        editNewSuggestion()
    }

    private fun editNewSuggestion() = local.update { s ->
        val suggestion = s.suggestion ?: return@update s
        s.copy(form = newForm(suggestion.type, suggestion.values, "voice"))
    }

    /** One-tap save from the suggestion card; opens the form instead if something required is missing. */
    fun quickSave(): SavedEntry? {
        val suggestion = local.value.suggestion ?: return null
        suggestion.placeOf?.let { stock ->
            val r = ctx ?: return null
            val place = suggestion.values["keptAt"] as String
            val undo = entries.setPlace(r.household.id, suggestion.type, stock.id, place, stock.keptAt)
            local.value = LocalState()
            return SavedEntry("${stock.title} · kept at $place") { undo() }
        }
        // Saving a guess confirms it: next time the same words go straight to that kind.
        val confirmed = suggestion.kind?.takeIf { suggestion.source == SuggestionSource.GUESS || suggestion.source == SuggestionSource.AI }
        ctx?.let { r -> confirmed?.let { phraseRepo.remember(r.household.id, r.user.uid, phraseKey(local.value.text), it.name) } }
        val form = newForm(suggestion.type, suggestion.values, "voice")
        if (form.missing.isNotEmpty()) {
            local.update { it.copy(form = form.copy(showErrors = true)) }
            return null
        }
        return persist(form)
    }

    fun backToPicker() = local.update { it.copy(form = null) }

    /** The unit chip a shopping or grocery form starts on: g, ml or pcs (forms say "L" for litres). */
    private fun formUnit(title: String) = com.houseaffairs.app.core.data.naturalUnit(title)

    /** Older list items saved the list's name ("WATCH"); the form's chips use the label ("Watch together"). */
    private fun listLabel(values: FormValues): FormValues =
        (values["template"] as? String)?.let { ListTemplate.of(it)?.label }?.let { mapOf("template" to it) } ?: emptyMap()

    /** Opens an existing entry in its form for editing. */
    fun openEdit(type: EntryType, id: String, adjust: (FormValues) -> FormValues = { it }) {
        val r = ctx ?: return
        viewModelScope.launch {
            val (values, visibleTo) = runCatching { entries.load(r.household.id, type, id) }.getOrNull() ?: return@launch
            val who = when {
                Visibility.EVERYONE in visibleTo -> WhoCanSee.EVERYONE
                visibleTo == listOf(r.user.uid) -> WhoCanSee.ONLY_ME
                else -> WhoCanSee.CHOSEN
            }
            local.update {
                it.copy(
                    form = FormState(
                        type = type,
                        values = adjust(type.defaults(r.user.uid, r.household.baseCurrency) + values + listLabel(values)),
                        showMore = true,
                        whoCanSee = who,
                        visibleMembers = visibleTo.filter { v -> v != r.user.uid && v != Visibility.EVERYONE },
                        editId = id,
                        original = values,
                        originalVisibleTo = visibleTo,
                    ),
                )
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Form

    fun update(key: String, value: Any?) = updateForm { it.copy(values = it.values + (key to value)) }

    fun toggleMore() = updateForm { it.copy(showMore = !it.showMore) }

    fun setWhoCanSee(who: WhoCanSee) = updateForm { it.copy(whoCanSee = who) }

    fun setVisibleMembers(uids: List<String>) = updateForm { it.copy(visibleMembers = uids) }

    /** Speak into an open form: fills the fields this kind has, keeps what's already there otherwise. */
    fun fillByVoice(text: String) = updateForm { form ->
        val draft = parser.parse(text) ?: return@updateForm form
        val (_, parsed) = prefill(draft)
        val keys = form.type.fields.map { it.key }.toSet()
        // A task's "due" and an event's "date" are the same idea; carry dates across kinds.
        val dateKey = form.type.fields.filterIsInstance<DateSpec>().firstOrNull()?.key
        val parsedDate = parsed.entries.firstOrNull { it.value is LocalDate }?.value
        val merged = parsed.filterKeys { it in keys }.filterValues { it != null } +
            (if (dateKey != null && parsedDate != null && dateKey !in parsed) mapOf(dateKey to parsedDate) else emptyMap())
        form.copy(values = form.values + merged, source = "voice")
    }

    /** Deletes the entry open for editing, with Undo. */
    suspend fun deleteEditing(): SavedEntry? {
        val r = ctx ?: return null
        val form = local.value.form ?: return null
        val id = form.editId ?: return null
        val undo = runCatching { entries.removeById(r.household.id, form.type, id) }.getOrNull() ?: return null
        local.value = LocalState()
        return SavedEntry("${titleOf(form.type, form.values)} · deleted", undo)
    }

    fun save(): SavedEntry? {
        val form = local.value.form ?: return null
        if (form.missing.isNotEmpty()) {
            updateForm { it.copy(showErrors = true, showMore = it.showMore || it.missing.any { spec -> !spec.essential }) }
            return null
        }
        return persist(form)
    }

    // ---------------------------------------------------------------------------------------

    private fun persist(form: FormState): SavedEntry? {
        val r = ctx ?: return null
        if (!r.me.canAccess(form.type.module)) return null
        val uid = r.user.uid
        val visibleTo = when (form.whoCanSee) {
            WhoCanSee.EVERYONE -> Visibility.everyone
            WhoCanSee.ONLY_ME -> listOf(uid)
            WhoCanSee.CHOSEN -> (listOf(uid) + form.visibleMembers).distinct()
        }
        val title = titleOf(form.type, form.values)
        // A new entry on a topic the owner made private is only seen by the chosen people.
        val topic = if (form.editId == null && form.whoCanSee == WhoCanSee.EVERYONE) {
            privateTopicOf(form.type.collection, title, (form.values["category"] ?: form.values["supplyType"] ?: form.values["aisle"]) as? String)
                ?.takeIf { it in r.household.privateTopics }
        } else {
            null
        }
        val shownTo = topic?.let { privateVisibleTo(r.household.privateTopics.getValue(it), uid) } ?: visibleTo
        if (form.editId != null) {
            entries.update(r.household.id, form.type, form.editId, form.values, visibleTo)
            local.value = LocalState()
            val before = form.original
            val beforeVisible = form.originalVisibleTo ?: visibleTo
            return SavedEntry("$title · changes saved") {
                if (before != null) entries.update(r.household.id, form.type, form.editId, before, beforeVisible)
            }
        }
        val ref = entries.save(r.household.id, uid, form.type, form.values, shownTo, form.source)
        viewModelScope.launch { settings.markQuickAddTried() }
        local.value = LocalState()
        return SavedEntry("$title · ${form.type.label} saved" + (topic?.let { " privately ($it)" } ?: "")) { entries.delete(ref) }
    }

    private fun updateForm(block: (FormState) -> FormState) = local.update { s -> s.form?.let { s.copy(form = block(it)) } ?: s }

    private fun newForm(type: EntryType, values: FormValues, source: String): FormState {
        val r = ctx
        val ongoing = money.value?.trips?.firstOrNull { it.trip.isOngoing(LocalDate.now()) }?.trip?.title
        // On a trip or outing today: expenses are clubbed into it automatically (one tap to untag).
        val defaults = type.defaults(r?.user?.uid.orEmpty(), r?.household?.baseCurrency ?: "USD") +
            if (type == EntryType.EXPENSE && ongoing != null) mapOf("trip" to ongoing) else emptyMap()
        return FormState(type, defaults + values.filterValues { it != null }, source = source)
    }

    private fun suggestionFor(draft: QuickAddDraft): Suggestion {
        val (type, values) = prefill(draft)
        val form = newForm(type, values, "voice")
        return Suggestion(
            type = type,
            values = values,
            title = titleOf(type, form.values),
            summary = summaryOf(type, form.values),
            missing = form.missing.map { it.label },
        )
    }

    private fun titleOf(type: EntryType, values: FormValues): String {
        val members = state.value.members
        return type.titleKey?.let { values[it] as? String }?.takeIf { it.isNotBlank() }
            ?: type.fields.firstNotNullOfOrNull { it.display(values[it.key], members) }
            ?: type.label
    }

    private fun summaryOf(type: EntryType, values: FormValues): String {
        val members = state.value.members
        return type.fields
            .filter { it.essential && it.key != type.titleKey && it.visibleWhen(values) }
            .mapNotNull { it.display(values[it.key], members) }
            .joinToString(" · ")
    }

    /** Maps a parsed sentence onto a kind of entry and its form values. */
    private fun prefill(draft: QuickAddDraft): Pair<EntryType, FormValues> {
        val currency = ctx?.household?.baseCurrency ?: "USD"
        return when (draft) {
            is QuickAddDraft.Expense -> EntryType.EXPENSE to mapOf(
                "amount" to MoneyValue(draft.amount.toPlainString(), draft.currency ?: currency),
                "category" to draft.category,
                "subcategory" to draft.subcategory,
                "merchant" to draft.merchant,
                // "On HDFC card" → the saved card called HDFC Regalia; "by card" with one card → that card.
                "paymentMethod" to run {
                    val cards = money.value?.budget?.cards.orEmpty()
                    when {
                        draft.paidWith != "Card" -> draft.paidWith
                        draft.card != null -> cards.firstOrNull { it.name.contains(draft.card, ignoreCase = true) }?.name ?: if (cards.isEmpty()) "Card" else cards.first().name
                        cards.size == 1 -> cards.first().name
                        else -> "Card"
                    }
                },
            )
            is QuickAddDraft.Subscription -> EntryType.SUBSCRIPTION to mapOf(
                "title" to draft.title,
                "amount" to MoneyValue(draft.amount.toPlainString(), draft.currency ?: currency),
                "frequency" to draft.frequency,
            )
            is QuickAddDraft.Shopping -> EntryType.SHOPPING to mapOf(
                "title" to draft.item,
                // No quantity said: preselect how it's usually measured (g for paneer, ml for milk).
                "quantity" to (draft.quantity?.split(" ")?.let { QuantityValue(it[0], it.getOrElse(1) { "pcs" }) } ?: QuantityValue("", formUnit(draft.item))),
            )
            is QuickAddDraft.Task -> EntryType.TASK to mapOf("title" to draft.title, "due" to draft.due, "repeat" to draft.repeat, "priority" to (draft.priority ?: "Low"))
            is QuickAddDraft.Event -> EntryType.EVENT to mapOf(
                "title" to draft.title,
                "kind" to draft.kind,
                "date" to draft.date,
                "repeat" to if (draft.kind in setOf("Birthday", "Anniversary", "Renewal")) "Yearly" else "Never",
            )
            is QuickAddDraft.Medicine -> {
                val person = draft.forName?.let(::memberNamed)
                EntryType.MEDICINE to mapOf(
                    "title" to draft.title,
                    "forWhom" to person?.let { listOf(it.uid) },
                    "expiry" to draft.expiry,
                    "dosage" to draft.forName?.takeIf { person == null }?.let { "For $it" },
                )
            }
            is QuickAddDraft.Grocery -> EntryType.GROCERY to mapOf(
                "title" to draft.title,
                "quantity" to (draft.quantity?.split(" ")?.let { QuantityValue(it[0], it.getOrElse(1) { "pcs" }) } ?: QuantityValue("", formUnit(draft.title))),
                "expiry" to draft.expiry,
            )
            is QuickAddDraft.ListItem -> EntryType.WISH to mapOf("template" to draft.template.label, "title" to draft.title)
            is QuickAddDraft.Cooked -> EntryType.RECIPE to mapOf("title" to draft.dish)
            is QuickAddDraft.Credit -> EntryType.CREDIT to mapOf(
                "title" to draft.title,
                "kind" to draft.kind,
                "amount" to MoneyValue(draft.amount.toPlainString(), currency),
                "earmark" to (draft.forWhat?.let { pocketFor(it)?.second } ?: draft.forWhat ?: "Anything"),
                "expiry" to draft.expiry,
            )
            is QuickAddDraft.Dream -> EntryType.DREAM to mapOf(
                "title" to draft.title,
                "kind" to draft.kind,
                "amount" to draft.amount?.let { MoneyValue(it.toPlainString(), currency) },
                "targetDate" to draft.date,
                "emoji" to DREAM_KINDS[draft.kind],
            )
            is QuickAddDraft.Activity -> EntryType.ACTIVITY to mapOf(
                "kind" to draft.kind,
                "minutes" to (draft.minutes ?: draft.steps?.let { it / 100 })?.toString(),
                "steps" to draft.steps?.toString(),
            )
            is QuickAddDraft.Trip -> EntryType.TRIP to mapOf("title" to draft.title, "start" to draft.start)
            is QuickAddDraft.Kept -> EntryType.KEPT to mapOf("title" to draft.item, "location" to draft.location, "kind" to com.houseaffairs.app.core.data.keptKindOf(draft.item))
            is QuickAddDraft.Household -> EntryType.HOUSEHOLD to mapOf(
                "title" to draft.title,
                "quantity" to draft.quantity?.split(" ")?.let { QuantityValue(it[0], it.getOrElse(1) { "units" }) },
                "expiry" to draft.expiry,
                "supplyType" to draft.type,
            )
            is QuickAddDraft.Batch -> prefill(draft.drafts.first())
            is QuickAddDraft.Move, is QuickAddDraft.Fund, is QuickAddDraft.Ask, is QuickAddDraft.Change,
            is QuickAddDraft.Growth, is QuickAddDraft.Revalue -> EntryType.TASK to emptyMap()
        }
    }

    private fun memberNamed(name: String): Member? =
        state.value.members.firstOrNull { it.displayName.substringBefore(' ').equals(name, ignoreCase = true) }
}
