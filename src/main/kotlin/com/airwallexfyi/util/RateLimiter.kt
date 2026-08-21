package com.airwallexfyi.util

import java.time.Clock
import java.time.Duration

/**
 * Spaces out calls to no more than [permitsPerSecond] per second, shared across every
 * caller. [acquire] blocks the calling thread until its slot arrives.
 *
 * The wait is computed inside a synchronized block (fast, no I/O), but the actual
 * sleep happens outside it - sleeping while holding the lock would serialize every
 * caller on the sleep itself and defeat the point of a shared limiter.
 */
class RateLimiter(
    permitsPerSecond: Double,
    private val clock: Clock = Clock.systemUTC(),
    private val sleeper: (Duration) -> Unit = { Thread.sleep(it.toMillis()) },
) {
    private val intervalMillis = (1000.0 / permitsPerSecond).toLong()
    private var nextFreeSlotMillis = 0L

    fun acquire() {
        val waitMillis = reserveSlot()
        if (waitMillis > 0) {
            sleeper(Duration.ofMillis(waitMillis))
        }
    }

    @Synchronized
    private fun reserveSlot(): Long {
        val now = clock.millis()
        val slot = maxOf(now, nextFreeSlotMillis)
        nextFreeSlotMillis = slot + intervalMillis
        return slot - now
    }
}
