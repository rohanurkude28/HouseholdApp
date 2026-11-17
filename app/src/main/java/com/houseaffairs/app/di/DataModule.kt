package com.houseaffairs.app.di

import com.google.firebase.firestore.FirebaseFirestore
import com.houseaffairs.app.core.data.HomeRepository
import com.houseaffairs.app.core.data.HouseholdRepository
import com.houseaffairs.app.core.data.MoneyRepository
import com.houseaffairs.app.core.data.PantryRepository
import com.houseaffairs.app.core.data.PhraseRepository
import com.houseaffairs.app.core.data.SearchRepository
import com.houseaffairs.app.core.data.ThingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The repositories shared with iPhone (:data). Each takes Android's Firestore only so Hilt sets
 * it up (emulators in the local build) before the shared code first touches Firestore.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides @Singleton fun phrases(@Suppress("UNUSED_PARAMETER") db: FirebaseFirestore) = PhraseRepository()
    @Provides @Singleton fun things(@Suppress("UNUSED_PARAMETER") db: FirebaseFirestore) = ThingsRepository()
    @Provides @Singleton fun home(@Suppress("UNUSED_PARAMETER") db: FirebaseFirestore) = HomeRepository()
    @Provides @Singleton fun pantry(@Suppress("UNUSED_PARAMETER") db: FirebaseFirestore) = PantryRepository()
    @Provides @Singleton fun money(@Suppress("UNUSED_PARAMETER") db: FirebaseFirestore) = MoneyRepository()
    @Provides @Singleton fun households(@Suppress("UNUSED_PARAMETER") db: FirebaseFirestore, @Suppress("UNUSED_PARAMETER") auth: com.google.firebase.auth.FirebaseAuth) = HouseholdRepository()
    @Provides @Singleton fun search(@Suppress("UNUSED_PARAMETER") db: FirebaseFirestore) = SearchRepository()
    /** Live to 100, shared with the iPhone; Android adds Health Connect's steps, sleep and workouts. */
    @Provides @Singleton fun weekly(pantry: PantryRepository, health: com.houseaffairs.app.core.health.HealthRepository) =
        com.houseaffairs.app.core.data.WeeklyRepository(pantry) { from, to -> health.read(from, to) }
}
