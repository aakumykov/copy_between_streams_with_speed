package com.github.aakumykov.copy_between_streams_with_counting_demo.extensions

import android.util.Log

fun Any.logD(message: String) {
    val tag = tag()
    Log.d(tag, message)
}

fun Any.logI(message: String) {
    val tag = tag()
    Log.i(tag, message)
}

fun Any.logW(message: String) {
    val tag = tag()
    Log.w(tag, message)
}

fun Any.logE(message: String) {
    val tag = tag()
    Log.e(tag, message)
}

fun Any.logE(throwable: Throwable) {
    val tag = tag()
    Log.e(tag, throwable.errorMsgExtended, throwable)
}

fun Any.tag(): String = this.javaClass.simpleName

fun Any.printMethodName(name: String) {
    logD("")
    logD("")
    logD(name)
}