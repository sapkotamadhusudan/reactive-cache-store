package com.maddy.cache.core

import com.maddy.cache.common.TimestampProvider
import com.maddy.cache.store.Store
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.functions.Consumer
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by Madhusudan Sapkota on 3/24/2019.
 */
class Cache<Key, Value> : Store.MemoryStore<Key, Value> {

    private val timestampProvider: TimestampProvider

    private val extractKeyFromModel: Function<Value, Key>

    private val itemLifespanMs: Optional<Long>

    private val cache = ConcurrentHashMap<Key, CacheEntry<Value>>()

    constructor(
        extractKeyFromModel: Function<Value, Key>,
        timestampProvider: TimestampProvider
    ) : this(extractKeyFromModel, timestampProvider, Optional.empty())

    constructor(
        extractKeyFromModel: Function<Value, Key>,
        timestampProvider: TimestampProvider,
        timeoutMs: Long
    ) : this(extractKeyFromModel, timestampProvider, Optional.of(timeoutMs))

    private constructor(
        extractKeyFromModel: Function<Value, Key>,
        timestampProvider: TimestampProvider,
        timeoutMs: Optional<Long>
    ) {
        this.timestampProvider = timestampProvider
        this.itemLifespanMs = timeoutMs
        this.extractKeyFromModel = extractKeyFromModel
    }

    override fun putSingular(value: Value) {
        val disposable = Single.fromCallable { extractKeyFromModel.apply(value) }
            .subscribeOn(Schedulers.computation())
            .subscribe { key -> cache[key] = createCacheEntry(value) }
    }

    override fun put(value: Value): Completable {
        return Single.fromCallable { extractKeyFromModel.apply(value) }
            .flatMapCompletable {
                cache[it] = createCacheEntry(value)
                Completable.complete()
            }
    }

    override fun removeSingular(value: Value) {
        val disposable = Single.fromCallable { extractKeyFromModel.apply(value) }
            .subscribeOn(Schedulers.computation())
            .subscribe { key -> cache.remove(key) }
    }

    override fun remove(value: Value): Completable {
        return Single.fromCallable { extractKeyFromModel.apply(value) }
            .map { cache.remove(it) }
            .ignoreElement()
    }

    override fun putAll(values: List<Value>) {
        val disposable = Observable.fromIterable(values)
            .toMap(
                extractKeyFromModel,
                Function<Value, CacheEntry<Value>> { this.createCacheEntry(it) })
            .subscribeOn(Schedulers.computation())
            .subscribe(Consumer<Map<Key, CacheEntry<Value>>> { cache.putAll(it) })
    }

    override fun getSingular(key: Key): Maybe<Value> {
        return Maybe.fromCallable { cache.containsKey(key) }
            .filter { isPresent -> isPresent }
            .map<CacheEntry<Value>> { cache[key] }
            .filter { this.notExpired(it) }
            .map { it.cachedObject() }
            .subscribeOn(Schedulers.computation())
    }

    override fun getAll(): Maybe<List<Value>> {
        return Observable.fromIterable(cache.values)
            .filter { this.notExpired(it) }
            .map { it.cachedObject() }
            .toList()
            .filter { items -> items.isNotEmpty() }
            .subscribeOn(Schedulers.computation())
    }

    override fun clear(): Completable {
        cache.clear()
        return Completable.complete()
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
