package com.maddy.cache.store

import io.reactivex.Completable
import io.reactivex.Maybe

/**
 * Created by Madhusudan Sapkota on 3/24/2019.
 *
 * Interface for any type of store. Don't implement this directly,
 * use [MemoryStore] or [DiskStore] so it is more
 * descriptive.
 */
interface Store<Key, Value> {

    fun putSingular(value: Value)

    fun put(value: Value) : Completable

    fun removeSingular(value: Value)

    fun remove(value: Value) : Completable

    fun putAll(valueList: List<Value>)

    fun clear() : Completable

    fun getSingular(key: Key): Maybe<Value>

    fun getAll(): Maybe<List<Value>>

    /**
     * More descriptive interface for memory based stores.
     */
    interface MemoryStore<Key, Value> : Store<Key, Value>

    /**
     * More descriptive interface for disk based stores.
     */
    interface DiskStore<Key, Value> : Store<Key, Value>
}