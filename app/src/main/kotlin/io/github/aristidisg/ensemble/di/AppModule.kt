package io.github.aristidisg.ensemble.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.aristidisg.ensemble.data.db.EnsembleDatabase
import io.github.aristidisg.ensemble.data.files.Backup
import io.github.aristidisg.ensemble.data.files.ImageStore
import io.github.aristidisg.ensemble.data.repo.FeedbackRepository
import io.github.aristidisg.ensemble.data.repo.LookRepository
import io.github.aristidisg.ensemble.data.repo.OutfitRepository
import io.github.aristidisg.ensemble.data.repo.PlanRepository
import io.github.aristidisg.ensemble.data.repo.RuleRepository
import io.github.aristidisg.ensemble.data.repo.SettingsRepository
import io.github.aristidisg.ensemble.data.repo.WardrobeRepository
import io.github.aristidisg.ensemble.data.repo.WearRepository
import io.github.aristidisg.ensemble.data.weather.OpenMeteoClient
import io.github.aristidisg.ensemble.data.weather.WeatherRepository
import io.github.aristidisg.ensemble.engine.OutfitEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun db(@ApplicationContext c: Context) = EnsembleDatabase.build(c)
    @Provides @Singleton fun settings(@ApplicationContext c: Context) = SettingsRepository.create(c)
    @Provides @Singleton fun images(@ApplicationContext c: Context) = ImageStore(c.filesDir)
    @Provides @Singleton fun wardrobe(db: EnsembleDatabase) = WardrobeRepository(db)
    @Provides @Singleton fun outfits(db: EnsembleDatabase) = OutfitRepository(db)
    @Provides @Singleton fun feedback(db: EnsembleDatabase) = FeedbackRepository(db)
    @Provides @Singleton fun wear(db: EnsembleDatabase) = WearRepository(db)
    @Provides @Singleton fun plans(db: EnsembleDatabase) = PlanRepository(db)
    @Provides @Singleton fun looks(db: EnsembleDatabase) = LookRepository(db)
    @Provides @Singleton fun rules(db: EnsembleDatabase) = RuleRepository(db)
    @Provides @Singleton fun openMeteo() = OpenMeteoClient()
    @Provides @Singleton fun weather(db: EnsembleDatabase, c: OpenMeteoClient) = WeatherRepository(db, c)
    @Provides @Singleton fun engine() = OutfitEngine()
    @Provides @Singleton fun backup(db: EnsembleDatabase, i: ImageStore, s: SettingsRepository, f: FeedbackRepository) = Backup(db, i, s, f)
    @Provides @Singleton @AppScope fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
