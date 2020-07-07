package com.maddy.cache.store

import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.functions.Function
import io.reactivex.processors.FlowableProcessor
import io.reactivex.processors.PublishProcessor
import io.reactivex.schedulers.Schedulers
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

/**
 * Created by Madhusudan Sapkota on 3/24/2019.
 *
 *
 * This reactive store has only a memory cache as form of storage.
 */
class PreferenceReactiveStore<Key : Any, Value : Any>(
    private val extractKeyFromModel: Function<Value, Key>,
    private val cache: Store.DiskStore<Key, Value>
) : ReactiveStore<Key, Value> {

    private val allProcessor: FlowableProcessor<Optional<List<Value>>> =
        PublishProcessor.create<Optional<List<Value>>>().toSerialized()

    private val processorMap = HashMap<Key, FlowableProcessor<Optional<Value>>>()

    override fun storeSingular(model: Value) {
        val key = extractKeyFromModel.apply(model)

        val disposable = cache.put(model).andThen(Completable.fromCallable {

            getOrCreateSubjectForKey(key).onNext(Optional.of(model))


            // One item has been added/updated, notify to all as well
            val allValues = cache.getAll()
                .map { Optional.of(it) }
                .blockingGet(Optional.empty())

            allProcessor.onNext(allValues)
        }).subscribe()
    }

    override fun storeAll(modelList: List<Value>) {
        cache.putAll(modelList)
        allProcessor.onNext(Optional.of(modelList))
        // Publish in all the existing single item streams.
        // This could be improved publishing only in the items that changed. Maybe use DiffUtils?
        publishInEachKey()
    }

    override fun replaceAll(modelList: List<Value>) {
        cache.clear()
        storeAll(modelList)
    }

    override fun remove(model: Value): Completable {
        return cache.remove(model)
            .andThen(Completable.fromAction {
                val allValues = cache.getAll()
                    .map { Optional.of(it) }
                    .blockingGet(Optional.empty())
                allProcessor.onNext(allValues)
                // Publish in all the existing single item streams.
                // This could be improved publishing only in the items that changed. Maybe use DiffUtils?
                publishInEachKey()
            }).observeOn(Schedulers.computation())
    }

    override fun remove(): Completable {
        return cache.clear().andThen(
            Completable.fromAction {
                allProcessor.onNext(Optional.empty())
                // Publish in all the existing single item streams.
                // This could be improved publishing only in the items that changed. Maybe use DiffUtils?
                publishInEachKey()
            }
        ).observeOn(Schedulers.computation())
    }

    override fun getSingular(key: Key): Flowable<Optional<Value>> {
        val cache = cache.getSingular(key)
            .map { Optional.of(it) }
            .blockingGet(Optional.empty())
        return getOrCreateSubjectForKey(key).startWith(cache).observeOn(Schedulers.computation())
    }

    override fun getAll(): Flowable<Optional<List<Value>>> {
        val allCache = cache.getAll()
            .map { Optional.of(it) }
            .blockingGet(Optional.empty())

        return allProcessor.startWith(allCache)
            .observeOn(Schedulers.computation())
    }

    private fun getOrCreateSubjectForKey(key: Key): FlowableProcessor<Optional<Value>> {
        return processorMap[key] ?: createAndStoreNewSubjectForKey(key)
    }

    private fun createAndStoreNewSubjectForKey(key: Key): FlowableProcessor<Optional<Value>> {
        val processor = PublishProcessor.create<Optional<Value>>().toSerialized()
        synchronized(processorMap) {
            processorMap.put(key, processor)
        }
        return processor
    }

    /**
     * Publishes the cached data in each independent stream only if it exists already.
     */
    private fun publishInEachKey() {
        val keySet: Set<Key>
        synchronized(processorMap) {
            keySet = HashSet(processorMap.keys)
        }
        for (key in keySet) {
            val value =
                cache.getSingular(key).map { Optional.of(it) }
                    .blockingGet(Optional.empty())
            publishInKey(key, value)
        }
    }

    /**
     * Publishes the cached value if there is an already existing stream for the passed key. The case where there isn't a stream for the passed key
     * means that the data for this key is not being consumed and therefore there is no need to publish.
     */
    private fun publishInKey(key: Key, model: Optional<Value>) {
        val processor: FlowableProcessor<Optional<Value>>?
        synchronized(processorMap) {
            processor = processorMap[key]
        }
        processor?.onNext(model)
    }
}
