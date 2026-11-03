package com.houseaffairs.app.di

import com.houseaffairs.app.feature.quickadd.Assistant
import com.houseaffairs.app.feature.quickadd.GeminiAssistant
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Which model reads unsure quick-add lines. Swap the binding to change providers. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AssistantModule {
    @Binds
    abstract fun assistant(impl: GeminiAssistant): Assistant
}
