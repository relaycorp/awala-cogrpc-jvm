package tech.relaycorp.relaynet.cogrpc.test

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

object Wait {

    fun waitFor(condition: (() -> Boolean)) {
        var value = condition.invoke()
        val startTime = currentTimeDuration()
        while (!value) {
            try {
                Thread.sleep(CHECK_INTERVAL.inWholeMilliseconds)
            } catch (e: InterruptedException) {
                throw RuntimeException(e)
            }
            if (currentTimeDuration() - startTime > TIMEOUT) {
                throw AssertionError("Wait timeout.")
            }
            value = condition.invoke()
        }
    }

    fun <T> waitForNotNull(condition: (() -> T?)): T {
        var value: T? = null
        waitFor {
            value = condition.invoke()
            value != null
        }
        return value!!
    }

    private fun currentTimeDuration() = System.currentTimeMillis().milliseconds

    private val CHECK_INTERVAL = 100.milliseconds
    private val TIMEOUT = 10.seconds
}
