package com.maddy.cache.store

import android.app.Application
import com.maddy.cache.common.TimestampProvider
import com.maddy.cache.core.PreferenceCache
import com.maddy.cache.core.PreferenceCacheManager
import com.maddy.cache.models.UserEntity
import com.maddy.cache.preference.PreferenceManager
import io.reactivex.functions.Function
import java.util.function.Supplier

/**
 * Created by Madhusudan Sapkota on 7/7/20.
 */
object StoreProvider {
    const val SESSION_STORE = "SESSION_STORE"
    const val SESSION_CACHE = "SESSION_CACHE"
    const val SESSION_PREFERENCE_MANAGER = "SESSION_PREFERENCE_MANAGER"
    const val MEMORY_CACHE_MAX_AGE = (10 * 60 * 1000).toLong() // 5 minutes
    const val PREFERENCE_CACHE_MAX_AGE = (60 * 60 * 1000).toLong() // 1 hour


    fun providePreferenceManager(application: Application): PreferenceManager {
        return PreferenceManager(application)
    }

    object UserStore {
        fun provideUserCacheManager(
            preferenceManager: PreferenceManager
        ): PreferenceCacheManager<String, UserEntity> {
            return PreferenceCacheManager(
                preferenceManager,
                Function { it.id.toString() },
                Supplier { UserEntity::class.java },
                Supplier { String::class.java }
            )
        }

        fun provideSessionCache(
            preferenceManager: PreferenceCacheManager<String, UserEntity>,
            timestampProvider: TimestampProvider
        ): Store.DiskStore<String, UserEntity> {

            return PreferenceCache(
                preferenceManager,
                Function { it.id.toString() },
                timestampProvider
            )
        }

        fun provideSessionReactiveStore(cache: Store.DiskStore<String, UserEntity>): ReactiveStore<String, UserEntity> {
            return PreferenceReactiveStore(Function { it.id.toString() }, cache)
        }
    }
}