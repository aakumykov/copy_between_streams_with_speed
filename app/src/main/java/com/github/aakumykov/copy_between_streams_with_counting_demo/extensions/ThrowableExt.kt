package com.github.aakumykov.copy_between_streams_with_counting_demo.extensions

val Throwable.errorMsg: String get() = message ?: javaClass.name

val Throwable.errorMsgExtended: String get() =
        if (null != message) "${message} (${javaClass.name})"
        else javaClass.name