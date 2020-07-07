package com.maddy.cache.core


import com.maddy.cache.common.TimestampProvider
import com.maddy.cache.store.Store
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers
import java.util.*
import kotlin.collections.HashMap

/**
 * Created by Madhusudan Sapkota on 5/10/2019.
 */

class PreferenceCache<Key, Value> : Store.DiskStore<Key, Value> {

    private val timestampProvider: TimestampProvider

    private val extractKeyFromModel: Function<Value, Key>

    private val itemLifespanMs: Optional<Long>

    private val cache: PreferenceCacheManager<Key, Value>

    private val disposables: HashMap<String, Disposable> = HashMap()

    constructor(
        preferenceManager: PreferenceCacheManager<Key, Value>,
        extractKeyFromModel: Function<Value, Key>,
        timestampProvider: TimestampProvider,
        timeoutMs: Optional<Long> = Optional.empty()
    ) {
        this.cache = preferenceManager
        this.timestampProvider = timestampProvider
        this.itemLifespanMs = timeoutMs
        this.extractKeyFromModel = extractKeyFromModel
    }

    override fun putSingular(value: Value) {
        val identifier = UUID.randomUUID().toString()
        disposables[identifier] = Single.fromCallable { extractKeyFromModel.apply(value) }
            .subscribeOn(Schedulers.computation())
            .observeOn(Schedulers.computation())
            .subscribe { key ->
                cache.put(key, createCacheEntry(value))
                if (disposables.containsKey(identifier)) {
                    disposables[identifier]?.dispose()
                }
            }
    }

    override fun put(value: Value): Completable {
        return cache.put(extractKeyFromModel.apply(value), createCacheEntry(value))
    }

    override fun putAll(valueList: List<Value>) {
        val identifier = UUID.randomUUID().toString()
        disposables[identifier] = Observable.fromIterable(valueList)
            .toMap(
                extractKeyFromModel,
                Function<Value, CacheEntry<Value>> { this.createCacheEntry(it) })
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.computation())
            .subscribe(Consumer<Map<Key, CacheEntry<Value>>> {
                cache.putAll(it)
                if (disposables.containsKey(identifier)) {
                    disposables[identifier]?.dispose()
                }
            })
    }

    override fun getSingular(key: Key): Maybe<Value> {
        return Maybe.fromCallable<CacheEntry<Value>> { cache.get(key) }
            .filter { item ->
                this.notExpired(item)
            }
            .map { it.cachedObject() }
            .subscribeOn(Schedulers.computation())
    }


    override fun removeSingular(value: Value) {
        val key: Key = extractKeyFromModel.apply(value)
        cache.remove(key)
    }

    override fun remove(value: Value): Completable {
        val key: Key = extractKeyFromModel.apply(value)
        return cache.remove(key)
    }

    override fun getAll(): Maybe<List<Value>> {
        return Observable.fromIterable(cache.get())
            .filter { this.notExpired(it) }
            .map { it.cachedObject() }
            .toList()
            .filter { items -> items.isNotEmpty() }
            .defaultIfEmpty(emptyList())
            .subscribeOn(Schedulers.computation())
    }

    override fun clear(): Completable {
        return cache.clear()
    }

    private fun createCacheEntry(value: Value): CacheEntry<Value> {
        return CacheEntry(value, timestampProvider.currentTimeMillis())
    }

    private fun notExpired(cacheEntry: CacheEntry<Value>): Boolean {
        return itemLifespanMs.map {
            ((cacheEntry.creationTimestamp() + (it)) > timestampProvider.currentTimeMillis())
        }.orElse(true)
    }
}
