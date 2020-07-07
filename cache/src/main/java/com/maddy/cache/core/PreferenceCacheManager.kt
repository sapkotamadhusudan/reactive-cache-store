package com.maddy.cache.core

import android.util.Base64
import com.maddy.cache.preference.PreferenceManager
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.*
import java.util.function.Supplier
import kotlin.collections.HashMap

/**
 * Created by Madhusudan Sapkota on 5/1/2019.
 */
class PreferenceCacheManager<Key, Value>(
    private val preferenceManager: PreferenceManager,
    private val extractKeyFromModel: Function<Value, Key>,
    private val extractPreferenceKey: Supplier<Class<Value>>,
    extractDataKey: Supplier<Class<Key>>
) {

    private val serializationType: Type = getType(extractPreferenceKey.get(), extractDataKey.get())
    private val disposables: HashMap<String, Disposable> = HashMap()

    fun put(key: Key, data: CacheEntry<Value>): Completable {
        return Observable.fromCallable { values() }
            .map {
                it.plus(Pair(key, data))
            }
            .flatMap {
                val serialized = serializeData(it, serializationType)
                preferenceManager.getSharedPreferences()
                    .edit()
                    .putString(extractPreferenceKey.get().simpleName, serialized)
                    .apply()
                Observable.just(true)
            }.concatMapCompletable {
                Completable.complete()
            }
    }

    fun putAll(dataMap: Map<Key, CacheEntry<Value>>) {
        saveCache(
            Single.fromCallable { values() }
                .map {
                    it.plus(dataMap)
                }
                .flatMap {
                    val serialized = serializeData(it, serializationType)
                    val response = preferenceManager.getSharedPreferences().edit()
                        .putString(extractPreferenceKey.get().simpleName, serialized).commit()
                    Single.just(response)
                }
        )
    }

    fun remove(key: Key): Completable {

        return Observable.fromCallable { values() }
            .map { data ->
                data.filter {
                    key != extractKeyFromModel.apply(it.value.cachedObject())
                }
            }
            .flatMap {
                val serialized = serializeData(it, serializationType)
                preferenceManager.getSharedPreferences().edit()
                    .putString(extractPreferenceKey.get().simpleName, serialized).apply()
                Observable.just(true)
            }.concatMapCompletable {
                Completable.complete()
            }

    }

    fun clear(): Completable {
        return Observable.fromCallable {
            preferenceManager.getSharedPreferences().edit()
                .remove(extractPreferenceKey.get().simpleName).apply()
        }.ignoreElements()
    }

    fun get(key: Key): CacheEntry<Value>? {
        val deSerialized = values()
        return if (deSerialized.containsKey(key)) deSerialized[key] else null
    }

    fun get(): Collection<CacheEntry<Value>> {
        val deSerialized = values()
        return deSerialized.values
    }

    private fun values(): Map<Key, CacheEntry<Value>> {
        val cachedData = preferenceManager.getSharedPreferences()
            .getString(extractPreferenceKey.get().simpleName, null)
        return deserializeCachedData(cachedData, serializationType)
    }

    private fun saveCache(caches: Single<Boolean>) {
        val identifier = UUID.randomUUID().toString()
        disposables[identifier] = caches
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.computation())
            .ignoreElement()
            .subscribe {
                if (disposables.containsKey(identifier)) disposables[identifier]?.dispose()
            }
    }

    private fun encode(value: String): String {
        return Base64.encode(value.toByteArray(), Base64.DEFAULT).toString()
    }

    private fun decode(value: String?): String? {
        if (value == null) {
            return null
        }
        return Base64.decode(value.toByteArray(), Base64.DEFAULT).toString()
    }

    private fun <K, V> deserializeCachedData(
        rawData: String?,
        serializationType: Type
    ): Map<K, CacheEntry<V>> {
        return preferenceManager.getGson()
            .fromJson<Map<K, CacheEntry<V>>>(rawData, serializationType) ?: HashMap()
    }

    private fun <K, V> serializeData(data: Map<K, CacheEntry<V>>, serializationType: Type): String {
        return preferenceManager.getGson().toJson(data, serializationType)
    }

    /**
     * This method provide the Type for serialization/deserialization for cache data
     *
     * @param storeKey type parameter for the caching Object
     * @return the type of provided parameter Object
     */
    private fun getType(storeKey: Class<*>, dataKey: Class<*>): Type {
        return object : ParameterizedType {
            override fun getActualTypeArguments(): Array<Type> {
                return arrayOf(dataKey, object : ParameterizedType {
                    override fun getActualTypeArguments(): Array<Type> {
                        return arrayOf(storeKey)
                    }

                    override fun getRawType(): Type {
                        return CacheEntry::class.java
                    }

                    override fun getOwnerType(): Type? {
                        return null
                    }
                })
            }

            override fun getRawType(): Type {
                return Map::class.java
            }

            override fun getOwnerType(): Type? {
                return null
            }
        }
    }
}
