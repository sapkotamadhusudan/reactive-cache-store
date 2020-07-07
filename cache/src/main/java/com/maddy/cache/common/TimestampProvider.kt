package com.maddy.cache.common

/**
 * Created by Madhusudan Sapkota on 3/24/2019.
 *
 *
 * Class to be able to test timestamp related features. Inject this instead of using System.currentTimeMillis()
 */
class TimestampProvider {

    fun currentTimeMillis(): Long {
        return System.currentTimeMillis()
    }
}
