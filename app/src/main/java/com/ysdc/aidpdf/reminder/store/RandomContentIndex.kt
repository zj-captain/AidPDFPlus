package com.ysdc.aidpdf.reminder.store

import kotlin.random.Random

internal fun nextRandomContentIndex(
    previousIndex: Int,
    size: Int,
    nextInt: (Int) -> Int = { bound -> Random.nextInt(bound) }
): Int {
    if (size <= 1) return 0
    if (previousIndex !in 0 until size) return nextInt(size)

    val candidate = nextInt(size - 1)
    return if (candidate >= previousIndex) candidate + 1 else candidate
}
