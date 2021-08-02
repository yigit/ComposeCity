package com.birbit.composecity.data

import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.toDuration

@OptIn(ExperimentalTime::class)
private fun now() = System.nanoTime().toDuration(DurationUnit.NANOSECONDS)

@OptIn(ExperimentalTime::class)
suspend fun timedLoop(
    period: Duration,
    block : (delta: Duration) -> Unit
) {
    var prev = now()

    while(true) {
        val current = now()
        block(current.minus(prev))
        prev = current
        // how much do we wait?
        val now = now()
        val waitTime = period.minus(
            now.minus(current)
        )
        if (waitTime > Duration.ZERO) {
            delay(waitTime)
        }
    }
}