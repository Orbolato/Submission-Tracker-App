package org.manuscriptwatch.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorRetryPolicyTest {
    @Test fun transientErrorsRetryUpToThreeTimes() {
        for (attempt in 0 until MonitorRetryPolicy.MAX_RETRIES_PER_CYCLE) {
            assertTrue(MonitorRetryPolicy.shouldRetry(true, attempt))
        }
        assertFalse(MonitorRetryPolicy.shouldRetry(true, MonitorRetryPolicy.MAX_RETRIES_PER_CYCLE))
        assertFalse(MonitorRetryPolicy.shouldRetry(true, MonitorRetryPolicy.MAX_RETRIES_PER_CYCLE + 1))
    }

    @Test fun verifiedOrManualActionCasesNeverRetry() {
        for (attempt in 0..5) {
            assertFalse(MonitorRetryPolicy.shouldRetry(false, attempt))
        }
        assertFalse(ExtractResult.NeedsAttention("Session expired").retryable)
    }

    @Test fun retryableHttpResponsesExcludeAuthenticationAndOrdinaryClientErrors() {
        for (code in listOf(408, 425, 429, 500, 502, 503, 504)) {
            assertTrue(MonitorRetryPolicy.isTransientHttpStatus(code))
        }
        for (code in listOf(200, 301, 400, 401, 403, 404, 410, 422)) {
            assertFalse(MonitorRetryPolicy.isTransientHttpStatus(code))
        }
    }
}
