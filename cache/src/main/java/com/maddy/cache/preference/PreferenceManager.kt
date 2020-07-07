package com.maddy.cache.preference

import android.app.Application
import android.content.Context
import android.content.SharedPreferences

import com.google.gson.Gson
import com.google.gson.GsonBuilder


class PreferenceManager(application: Application) {

    private val DEFAULT_APP_STORE_NAME: String = "DEFAULT_APP_STORE_NAME"

    private val sharedPreferences: SharedPreferences =
        application.getSharedPreferences(DEFAULT_APP_STORE_NAME, Context.MODE_PRIVATE)
    private val gsonConverter: Gson = GsonBuilder().create()

    fun getSharedPreferences(): SharedPreferences {
        return sharedPreferences
    }

    fun getGson(): Gson {
        return gsonConverter
    }
}
