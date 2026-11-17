package com.houseaffairs.app.core.data

import com.houseaffairs.app.core.model.Household
import com.houseaffairs.app.core.model.Member
import com.houseaffairs.app.core.model.Role
import com.houseaffairs.app.core.model.RolePolicy
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FieldPath
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.where
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlin.random.Random

/** Homes, members and invites: the same documents on both phones. */
class HouseholdRepository {
    private val users get() = store.collection("users")
    private val invites get() = store.collection("invites")
    private fun members(householdId: String) = household(householdId).collection("members")

    fun observeHouseholdId(uid: String): Flow<String?> = users.document(uid).snapshots.map { it.str("householdId") }

    fun observeHousehold(id: String): Flow<Household?> = household(id).snapshots.map { if (it.exists) householdOf(it) else null }

    /**
     * Only once the server has the membership: right after creating or joining a home, the phone's
     * own unsaved copy shows first, and every list queried then would be refused by the rules.
     */
    fun observeMember(householdId: String, uid: String): Flow<Member?> =
        members(householdId).document(uid).snapshots
            .filter { !it.metadata.hasPendingWrites }
            .map { if (it.exists) memberOf(it) else null }

    /** Emits an empty list once access is lost (signed out, removed) instead of failing the collector. */
    fun observeMembers(householdId: String): Flow<List<Member>> =
        members(householdId).snapshots
            .map { snap -> snap.documents.map(::memberOf).sortedWith(compareBy({ it.role.ordinal }, { it.displayName })) }
            .catch { emit(emptyList()) }

    /** Creates a household with the signed-in person as owner; returns its id. */
    suspend fun createHousehold(name: String, currency: String): String {
        val user = requireUser()
        val ref = store.collection("households").document
        store.batch().apply {
            set(ref, mapOf("name" to name.trim(), "baseCurrency" to currency, "budgetCycleStartDay" to 1, "createdBy" to user.uid, "createdAt" to FieldValue.serverTimestamp))
            set(members(ref.id).document(user.uid), memberFields(user, Role.OWNER, inviteCode = null))
            set(users.document(user.uid), mapOf("householdId" to ref.id), merge = true)
        }.commit()
        return ref.id
    }

    /** Creates a 6-character code valid for 48 hours. */
    suspend fun createInvite(householdId: String, role: Role): String {
        require(role != Role.OWNER) { "Owners can't be invited" }
        val code = (1..6).map { CODE_ALPHABET[Random.nextInt(CODE_ALPHABET.length)] }.joinToString("")
        invites.document(code).set(
            mapOf("householdId" to householdId, "role" to role.name, "createdBy" to requireUser().uid, "expiresAt" to Timestamp(Clock.System.now().epochSeconds + 48 * 3600, 0)),
        )
        return code
    }

    suspend fun joinWithCode(rawCode: String) {
        val user = requireUser()
        val code = rawCode.trim().uppercase()
        val invite = invites.document(code).get()
        if (!invite.exists) throw IllegalArgumentException("We couldn't find that code. Check it and try again.")
        val householdId = invite.str("householdId") ?: throw IllegalArgumentException("We couldn't find that code. Check it and try again.")
        val role = invite.str("role")?.let { r -> Role.entries.firstOrNull { it.name == r } } ?: Role.ADULT
        if ((invite.millis("expiresAt") ?: 0L) < Clock.System.now().toEpochMilliseconds()) throw IllegalStateException("This invite has expired. Ask for a new one.")
        runCatching {
            store.batch().apply {
                set(members(householdId).document(user.uid), memberFields(user, role, code))
                set(users.document(user.uid), mapOf("householdId" to householdId), merge = true)
            }.commit()
        }.onFailure { throw IllegalStateException("That code isn't valid any more. Ask for a new invite.", it) }
    }

    /** Sets (or clears, when blank) a family-wide service key such as "omdb". Owner only (rules). */
    suspend fun setIntegration(householdId: String, name: String, key: String?) {
        household(householdId).update(FieldPath("integrations", name) to (key?.trim()?.takeIf { it.isNotEmpty() } ?: FieldValue.delete))
    }

    /** The people who don't see the family's costs. Owner only (rules). */
    suspend fun setHideCosts(householdId: String, uids: List<String>) {
        household(householdId).update("hideCostsFrom" to uids.distinct())
    }

    /** Makes [topic] private to [uids], or public again when null. Owner only (rules). */
    suspend fun setPrivateTopic(householdId: String, topic: String, uids: List<String>?) {
        household(householdId).update(FieldPath("privateTopics", topic) to (uids ?: FieldValue.delete))
    }

    /**
     * Owner's "Reset everything": deletes every entry the owner can see, keeping the home, members,
     * settings and connections. Other members' private items aren't visible to the owner, so they stay.
     * Returns how many entries were removed.
     */
    suspend fun resetHousehold(householdId: String): Int {
        val uid = requireUser().uid
        // All collections at once: one after another took ~20 s on a phone connection.
        return coroutineScope {
            DATA_COLLECTIONS.map { name ->
                async {
                    val col = collection(householdId, name)
                    val shared = col.where { "visibleTo" containsAny listOf("*", uid) }.get().documents
                    val mine = col.where { "createdBy" equalTo uid }.get().documents
                    val refs = (shared + mine).map { it.reference }.distinctBy { it.path }
                    // Firestore batches take at most 500 writes.
                    refs.chunked(450).forEach { chunk -> store.batch().apply { chunk.forEach { delete(it) } }.commit() }
                    refs.size
                }
            }.awaitAll().sum()
        }
    }

    /**
     * "Download my data": every entry this person can see, by collection, as plain values.
     * Collections their role can't open are skipped rather than failing the whole export.
     */
    suspend fun exportEntries(householdId: String, me: Member): Map<String, List<Map<String, Any?>>> {
        val uid = requireUser().uid
        return coroutineScope {
            DATA_COLLECTIONS.map { name ->
                async {
                    val col = collection(householdId, name)
                    name to runCatching {
                        val docs = if (name == "medicines" && RolePolicy.ownMedicinesOnly(me.role)) {
                            col.where { "forWhom" contains uid }.get().documents
                        } else {
                            col.where { "visibleTo" containsAny listOf("*", uid) }.get().documents + col.where { "createdBy" equalTo uid }.get().documents
                        }
                        // Days as "2026-11-12", readable by a person or a spreadsheet.
                        docs.distinctBy { it.id }.map { d -> mapOf("id" to d.id) + d.plainData().mapValues { (_, v) -> readable(v) } }
                    }.getOrDefault(emptyList())
                }
            }.awaitAll().toMap()
        }
    }

    /**
     * Account deletion, the Firestore part. Private entries this person made always go; shared ones
     * too unless [keepShared]. An owner hands the home to [successorOf]; the last member takes the
     * whole home with them. Signing in again afterwards starts from "create or join a home".
     */
    suspend fun eraseMe(householdId: String, keepShared: Boolean) {
        val uid = requireUser().uid
        val everyone = members(householdId).get().documents.map(::memberOf)
        val me = everyone.first { it.uid == uid }
        val alone = everyone.none { it.uid != uid }
        if (alone) {
            resetHousehold(householdId)
            collection(householdId, "phrases").get().documents.chunked(450).forEach { chunk ->
                store.batch().apply { chunk.forEach { delete(it.reference) } }.commit()
            }
        } else {
            val heir = if (me.role == Role.OWNER) {
                successorOf(everyone, uid) ?: throw IllegalStateException("Make another member an adult first, so someone can look after the home.")
            } else null
            coroutineScope {
                DATA_COLLECTIONS.map { name ->
                    async {
                        // A module this person lost access to can't be cleared by them; the rest still goes.
                        runCatching {
                            val mine = collection(householdId, name).where { "createdBy" equalTo uid }.get().documents
                            val gone = if (keepShared) mine.filter { "*" !in it.strings("visibleTo") } else mine
                            gone.chunked(450).forEach { chunk -> store.batch().apply { chunk.forEach { delete(it.reference) } }.commit() }
                        }
                    }
                }.awaitAll()
            }
            heir?.let { setRole(householdId, it.uid, Role.OWNER) }
        }
        // While still a member: the rules check membership for these.
        if (alone) household(householdId).delete()
        members(householdId).document(uid).delete()
        users.document(uid).delete()
    }

    suspend fun setBaseCurrency(householdId: String, currency: String) {
        household(householdId).update("baseCurrency" to currency)
    }

    /** Changing a role resets module access to that role's defaults. */
    suspend fun setRole(householdId: String, uid: String, role: Role) {
        members(householdId).document(uid).update("role" to role.name, "moduleAccess" to RolePolicy.defaultAccess(role))
    }

    private fun readable(value: Any?): Any? = when (value) {
        is com.houseaffairs.app.core.time.LocalDate -> value.toString()
        is Map<*, *> -> value.entries.associate { (k, v) -> k.toString() to readable(v) }
        is List<*> -> value.map(::readable)
        else -> value
    }

    private fun requireUser(): FirebaseUser = checkNotNull(Firebase.auth.currentUser) { "Not signed in" }

    private fun memberFields(user: FirebaseUser, role: Role, inviteCode: String?) = mapOf(
        "displayName" to (user.displayName ?: user.email ?: "Member"),
        "photoUrl" to user.photoURL,
        "role" to role.name,
        "moduleAccess" to RolePolicy.defaultAccess(role),
        "restrictedCategories" to emptyList<String>(),
        "inviteCode" to inviteCode,
        "joinedAt" to FieldValue.serverTimestamp,
    )

    private companion object {
        // Every entry collection; must match moduleOf() in firestore.rules.
        val DATA_COLLECTIONS = listOf(
            "transactions", "budgets", "transfers", "credits", "investments", "trips", "recurring", "goals", "paymentMethods",
            "stock", "shopping", "recipes", "cookLog", "medicines",
            "tasks", "events", "lists", "listItems", "weeklyGoals", "places", "dreams", "activities", "checkins", "wellPlans", "diary",
            "documents", "keptAt",
        )

        // No 0/O or 1/I so codes are easy to read aloud.
        const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }
}

/** A household document as the shared model. */
fun householdOf(d: DocumentSnapshot): Household {
    val raw = d.plainData()
    return Household(
        id = d.id, name = d.str("name").orEmpty(), baseCurrency = d.str("baseCurrency") ?: "USD",
        budgetCycleStartDay = d.long("budgetCycleStartDay")?.toInt() ?: 1, createdBy = d.str("createdBy").orEmpty(), createdAt = d.millis("createdAt"),
        privateTopics = (raw["privateTopics"] as? Map<*, *>).orEmpty().entries.associate { (k, v) -> k.toString() to (v as? List<*>).orEmpty().filterIsInstance<String>() },
        hideCostsFrom = d.strings("hideCostsFrom"),
        integrations = (raw["integrations"] as? Map<*, *>).orEmpty().entries.mapNotNull { (k, v) -> (v as? String)?.let { k.toString() to it } }.toMap(),
    )
}

/** A member document as the shared model. */
fun memberOf(d: DocumentSnapshot): Member = Member(
    uid = d.id, displayName = d.str("displayName").orEmpty(), photoUrl = d.str("photoUrl"),
    role = d.str("role")?.let { r -> Role.entries.firstOrNull { it.name == r } } ?: Role.ADULT,
    moduleAccess = d.flags("moduleAccess"), restrictedCategories = d.strings("restrictedCategories"),
    inviteCode = d.str("inviteCode"), joinedAt = d.millis("joinedAt"),
)
