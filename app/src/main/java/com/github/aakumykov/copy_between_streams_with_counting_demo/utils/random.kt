package com.github.aakumykov.copy_between_streams_with_counting_demo.utils

import java.util.UUID
import kotlin.random.Random

val random: Random by lazy { Random }

val newRandomId: String get() = UUID.randomUUID().toString()

val randomBool: Boolean get() = random.nextBoolean()