package com.maddy.cache.core

/**
 * Created by Madhusudan Sapkota on 3/24/2019.
 */
class CacheEntry<T>(private val cacheObject: T, private val createdDate: Long) {

    fun cachedObject(): T {
        return cacheObject
    }

    fun creationTimestamp(): Long {
        return createdDate
    }
}