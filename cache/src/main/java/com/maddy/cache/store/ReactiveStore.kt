package com.maddy.cache.store

import io.reactivex.Completable
import io.reactivex.Flowable
import java.util.*

/**
 * Created by Madhusudan Sapkota on 3/24/2019.
 */
interface ReactiveStore<Key: Any, Value: Any> {

    fun getAll() : Flowable<Optional<List<Value>>>

    fun storeSingular(model: Value)

    fun storeAll(modelList: List<Value>)

    fun replaceAll(modelList: List<Value>)

    fun remove(model: Value) : Completable

    fun remove() : Completable

    fun getSingular(key: Key): Flowable<Optional<Value>>
}
