package com.houseaffairs.app.core.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.houseaffairs.app.core.model.Household
import com.houseaffairs.app.core.model.Invite
import com.houseaffairs.app.core.model.Member
import com.houseaffairs.app.core.model.Role
import com.houseaffairs.app.core.model.RolePolicy
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HouseholdRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {
    private val users get() = db.collection("users")
    private val invites get() = db.collection("invites")
    private fun household(id: String) = db.collection("households").document(id)
    private fun members(householdId: String) = household(householdId).collection("members")

    fun observeHouseholdId(uid: String): Flow<String?> =
        users.document(uid).observeSnapshots().map { it.getString("householdId") }

    fun observeHousehold(id: String): Flow<Household?> =
        household(id).observeSnapshots().map { it.toObject(Household::class.java) }

    /**
     * Only once the server has the membership: right after creating or joining a home, the phone's
     * own unsaved copy shows first, and every list queried then would be refused by the rules.
     */
    fun observeMember(householdId: String, uid: String): Flow<Member?> =
        members(householdId).document(uid).observeSnapshots()
            .filter { !it.metadata.hasPendingWrites() }
            .map { it.toObject(Member::class.java) }

    /** Emits an empty list once access is lost (signed out, removed) instead of failing the collector. */
    fun observeMembers(householdId: String): Flow<List<Member>> =
        members(householdId).observeSnapshots()
            .map { snapshot ->
                snapshot.toObjects(Member::class.java).sortedWith(compareBy({ it.role.ordinal }, { it.displayName }))
            }
            .catch { emit(emptyList()) }

    /** Creates a household with the signed-in person as owner; returns its id. */
    suspend fun createHousehold(name: String, currency: String): String {
        val user = requireUser()
        val ref = db.collection("households").document()
        db.batch().apply {
            set(
                ref,
                mapOf(
                    "name" to name.trim(),
                    "baseCurrency" to currency,
                    "budgetCycleStartDay" to 1,
                    "createdBy" to user.uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
            set(members(ref.id).document(user.uid), memberFields(user, Role.OWNER, inviteCode = null))
            set(users.document(user.uid), mapOf("householdId" to ref.id), SetOptions.merge())
        }.commit().await()
        return ref.id
    }

    /** Creates a 6-character code valid for 48 hours. */
    suspend fun createInvite(householdId: String, role: Role): String {
        require(role != Role.OWNER) { "Owners can't be invited" }
        val code = generateCode()
        invites.document(code).set(
            mapOf(
                "householdId" to householdId,
                "role" to role.name,
                "createdBy" to requireUser().uid,
                "expiresAt" to Timestamp(Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(48))),
            ),
        ).await()
        return code
    }

    suspend fun joinWithCode(rawCode: String) {
        val user = requireUser()
        val code = rawCode.trim().uppercase()
        val invite = invites.document(code).get().await().toObject(Invite::class.java)
            ?: throw IllegalArgumentException("We couldn't find that code. Check it and try again.")
        val expiresAt = invite.expiresAt?.toDate()?.time ?: 0L
        if (expiresAt < System.currentTimeMillis()) {
            throw IllegalStateException("This invite has expired. Ask for a new one.")
        }
        try {
            db.batch().apply {
                set(members(invite.householdId).document(user.uid), memberFields(user, invite.role, code))
                set(users.document(user.uid), mapOf("householdId" to invite.householdId), SetOptions.merge())
            }.commit().await()
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                throw IllegalStateException("That code isn't valid any more. Ask for a new invite.", e)
            }
            throw e
        }
    }

    /** Sets (or clears, when blank) a family-wide service key such as "omdb". Owner only (rules). */
    suspend fun setIntegration(householdId: String, name: String, key: String?) {
        household(householdId).update(
            com.google.firebase.firestore.FieldPath.of("integrations", name),
            key?.trim()?.takeIf { it.isNotEmpty() } ?: com.google.firebase.firestore.FieldValue.delete(),
        ).await()
    }

    /** The people who don't see the family's costs. Owner only (rules). */
    suspend fun setHideCosts(householdId: String, uids: List<String>) {
        household(householdId).update("hideCostsFrom", uids.distinct()).await()
    }

    /** Makes [topic] private to [uids], or public again when null. Owner only (rules). */
    suspend fun setPrivateTopic(householdId: String, topic: String, uids: List<String>?) {
        household(householdId).update(
            com.google.firebase.firestore.FieldPath.of("privateTopics", topic),
            uids ?: com.google.firebase.firestore.FieldValue.delete(),
        ).await()
    }

    /**
     * Owner's "Reset everything": deletes every entry the owner can see, keeping the home, members,
     * settings and connections. Other members' private items aren't visible to the owner, so they stay.
     * Returns how many entries were removed.
     */
    suspend fun resetHousehold(householdId: String): Int {
        val uid = requireUser().uid
        // All collections at once: one after another took ~20 s on a phone connection.
        return kotlinx.coroutines.coroutineScope {
            DATA_COLLECTIONS.map { name ->
                async {
                    val col = household(householdId).collection(name)
                    val shared = col.whereArrayContainsAny("visibleTo", listOf("*", uid)).get().await().documents
                    val mine = col.whereEqualTo("createdBy", uid).get().await().documents
                    val refs = (shared + mine).map { it.reference }.distinctBy { it.path }
                    // Firestore batches take at most 500 writes.
                    refs.chunked(450).forEach { chunk ->
                        db.batch().apply { chunk.forEach(::delete) }.commit().await()
                    }
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
        return kotlinx.coroutines.coroutineScope {
            DATA_COLLECTIONS.map { name ->
                async {
                    val col = household(householdId).collection(name)
                    name to runCatching {
                        val docs = if (name == "medicines" && RolePolicy.ownMedicinesOnly(me.role)) {
                            col.whereArrayContains("forWhom", uid).get().await().documents
                        } else {
                            col.whereArrayContainsAny("visibleTo", listOf("*", uid)).get().await().documents +
                                col.whereEqualTo("createdBy", uid).get().await().documents
                        }
                        docs.distinctBy { it.id }.map { d -> mapOf("id" to d.id) + d.data.orEmpty().mapValues { plain(it.value) } }
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
        val everyone = members(householdId).get().await().toObjects(Member::class.java)
        val me = everyone.first { it.uid == uid }
        val alone = everyone.none { it.uid != uid }
        if (alone) {
            resetHousehold(householdId)
            household(householdId).collection("phrases").get().await().documents.chunked(450).forEach { chunk ->
                db.batch().apply { chunk.forEach { delete(it.reference) } }.commit().await()
            }
        } else {
            val heir = if (me.role == Role.OWNER) {
                successorOf(everyone, uid) ?: throw IllegalStateException("Make another member an adult first, so someone can look after the home.")
            } else null
            kotlinx.coroutines.coroutineScope {
                DATA_COLLECTIONS.map { name ->
                    async {
                        // A module this person lost access to can't be cleared by them; the rest still goes.
                        runCatching {
                            val mine = household(householdId).collection(name).whereEqualTo("createdBy", uid).get().await().documents
                            val gone = if (keepShared) mine.filter { "*" !in (it.get("visibleTo") as? List<*>).orEmpty() } else mine
                            gone.chunked(450).forEach { chunk -> db.batch().apply { chunk.forEach { delete(it.reference) } }.commit().await() }
                        }
                    }
                }.awaitAll()
            }
            heir?.let { setRole(householdId, it.uid, Role.OWNER) }
        }
        // While still a member: the rules check membership for these.
        if (alone) household(householdId).delete().await()
        members(householdId).document(uid).delete().await()
        users.document(uid).delete().await()
    }

    suspend fun setBaseCurrency(householdId: String, currency: String) {
        household(householdId).update("baseCurrency", currency).await()
    }

    /** Changing a role resets module access to that role's defaults. */
    suspend fun setRole(householdId: String, uid: String, role: Role) {
        members(householdId).document(uid)
            .update(mapOf("role" to role.name, "moduleAccess" to RolePolicy.defaultAccess(role)))
            .await()
    }

    /** Firestore types as text a person or spreadsheet can read; dates in the phone's own time zone. */
    private fun plain(value: Any?): Any? = when (value) {
        is Timestamp -> java.time.LocalDateTime.ofInstant(value.toDate().toInstant(), java.time.ZoneId.systemDefault()).withNano(0)
            .let { if (it.toLocalTime() == java.time.LocalTime.MIDNIGHT) it.toLocalDate().toString() else it.toString() }
        is Map<*, *> -> value.entries.associate { (k, v) -> k.toString() to plain(v) }
        is List<*> -> value.map(::plain)
        is com.google.firebase.firestore.DocumentReference -> value.id
        is com.google.firebase.firestore.GeoPoint -> mapOf("lat" to value.latitude, "lng" to value.longitude)
        is com.google.firebase.firestore.Blob -> null
        else -> value
    }

    private fun requireUser(): FirebaseUser = checkNotNull(auth.currentUser) { "Not signed in" }

    private fun memberFields(user: FirebaseUser, role: Role, inviteCode: String?) = mapOf(
        "displayName" to (user.displayName ?: user.email ?: "Member"),
        "photoUrl" to user.photoUrl?.toString(),
        "role" to role.name,
        "moduleAccess" to RolePolicy.defaultAccess(role),
        "restrictedCategories" to emptyList<String>(),
        "inviteCode" to inviteCode,
        "joinedAt" to FieldValue.serverTimestamp(),
    )

    private fun generateCode(): String {
        val random = SecureRandom()
        return (1..6).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }.joinToString("")
    }

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
