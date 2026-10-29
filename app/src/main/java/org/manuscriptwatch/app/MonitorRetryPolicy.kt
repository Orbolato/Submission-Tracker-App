package org.manuscriptwatch.app

/**
 * Retries a failed fetch without treating an unchanged status as a failure.
 * Every scheduled cycle has its own bounded attempts; later periodic cycles
 * continue monitoring even after this cycle exhausts temporary retries.
 */
object MonitorRetryPolicy {
    const val MAX_RETRIES_PER_CYCLE = 3

    fun shouldRetry(transientFailure: Boolean, completedRetryCount: Int): Boolean =
        transientFailure && completedRetryCount < MAX_RETRIES_PER_CYCLE

    fun isTransientHttpStatus(code: Int): Boolean =
        code == 408 || code == 425 || code == 429 || code in 500..599

    fun requiresUserAction(reason: String): Boolean {
        val normalized = reason.lowercase()
        return listOf("captcha", "human verification", "manual verification",
            "login may be required", "sign in again", "log in", "access denied")
            .any { normalized.contains(it) }
    }
}
