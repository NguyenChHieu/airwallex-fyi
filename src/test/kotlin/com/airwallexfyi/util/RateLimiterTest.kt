package com.airwallexfyi.util

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RateLimiterTest {
    @Test
    fun `spaces out sequential calls by the configured interval`() {
        val fakeMillis = AtomicLong(0)
        val slept = AtomicLong(0)
        val limiter = RateLimiter(
            permitsPerSecond = 25.0, // 40ms interval
            clock = fakeClock(fakeMillis),
            sleeper = { duration -> slept.addAndGet(duration.toMillis()); fakeMillis.addAndGet(duration.toMillis()) },
        )

        limiter.acquire() // first call: no prior slot, no wait
        limiter.acquire() // must wait ~40ms
        limiter.acquire() // must wait ~40ms more

        assertThat(slept.get()).isEqualTo(80L)
    }

    @Test
    fun `does not wait when calls are already spaced far enough apart`() {
        val fakeMillis = AtomicLong(0)
        val slept = AtomicLong(0)
        val limiter = RateLimiter(
            permitsPerSecond = 25.0,
            clock = fakeClock(fakeMillis),
            sleeper = { duration -> slept.addAndGet(duration.toMillis()) },
        )

        limiter.acquire()
        fakeMillis.set(1000) // pretend a full second passed with no sleep needed
        limiter.acquire()

        assertThat(slept.get()).isZero()
    }

    @Test
    fun `concurrent callers are serialized, not all let through at once`() {
        val callCount = 10
        // Real clock/sleeper here: this is the one test proving actual thread-safety,
        // not just the single-threaded slot arithmetic covered above.
        val limiter = RateLimiter(permitsPerSecond = 100.0) // 10ms interval
        val executor = Executors.newFixedThreadPool(callCount)
        val ready = CountDownLatch(callCount)
        val start = CountDownLatch(1)
        val completionOrder = CopyOnWriteArrayList<Long>()

        val startNanos = System.nanoTime()
        repeat(callCount) {
            executor.submit {
                ready.countDown()
                start.await(1, TimeUnit.SECONDS)
                limiter.acquire()
                completionOrder += System.nanoTime()
            }
        }
        assertThat(ready.await(1, TimeUnit.SECONDS)).isTrue()
        start.countDown()
        executor.shutdown()
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
        val elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000

        assertThat(completionOrder).hasSize(callCount)
        // Lower bound only (avoids flakiness from scheduling jitter on the upper end):
        // 10 callers at a 10ms interval cannot all finish in under ~90ms if the
        // limiter is actually serializing them.
        assertThat(elapsedMillis).isGreaterThanOrEqualTo((callCount - 1) * 10L)
    }

    private fun fakeClock(millis: AtomicLong): Clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(millis.get())
    }
}
