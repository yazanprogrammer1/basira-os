package com.basira.os.core.common.logging

import android.util.Log
import javax.inject.Inject

interface Logger {
    fun d(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}

class AndroidLogger @Inject constructor() : Logger {
    override fun d(tag: String, message: String) {
        Log.d(PREFIX + tag, message)
    }

    override fun w(tag: String, message: String, throwable: Throwable?) {
        Log.w(PREFIX + tag, message, throwable)
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        Log.e(PREFIX + tag, message, throwable)
    }

    private companion object {
        const val PREFIX = "Basira/"
    }
}
