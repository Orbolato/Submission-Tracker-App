package org.manuscriptwatch.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalPageClassifierTest {
    @Test fun identifiesSpringerGatewayWithoutSavingOAuthState() {
        val url = "https://idp-personal-authenticator.springernature.com/gateway?response_type=code&state=example&target_redirect_uri=https%3A%2F%2Fnotifications.springernature.com"
        assertEquals(JournalPageClassifier.PageKind.AUTHENTICATION, JournalPageClassifier.classify(url))
        assertFalse(JournalPageClassifier.isSaveableStatusPage(url))
    }
    @Test fun doesNotMistakeSpringerNotificationsForEditorialDashboard() {
        assertEquals(JournalPageClassifier.PageKind.NOTIFICATIONS,
            JournalPageClassifier.classify("https://notifications.springernature.com/"))
    }
    @Test fun acceptsCandidateJournalStatusList() {
        assertTrue(JournalPageClassifier.isSaveableStatusPage("https://www.editorialmanager.com/example/default.aspx"))
    }
    @Test fun acceptsAndCleansSpringerDetailUrl() {
        val url = "https://submission.springernature.com/submission-details/11111111-2222-4333-8444-555555555555?_gl=tracking"
        assertTrue(JournalPageClassifier.isSpringerSubmissionDetail(url))
        assertEquals("https://submission.springernature.com/submission-details/11111111-2222-4333-8444-555555555555", JournalPageClassifier.canonicalStatusUrl(url))
        assertTrue(JournalPageClassifier.isSaveableStatusPage(url))
    }
    @Test fun rejectsPretendSpringerDomain() {
        assertFalse(JournalPageClassifier.isSpringerSubmissionDetail("https://submission.springernature.com.attacker.org/submission-details/11111111-2222-4333-8444-555555555555"))
    }
    @Test fun rejectsNonHttpsUrl() {
        assertFalse(JournalPageClassifier.isSaveableStatusPage("http://example.com"))
    }
}
