package org.manuscriptwatch.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusExtractorTest {
    private val html = """
      <html><body><table>
        <tr><th>Manuscript ID</th><th>Title</th><th>Current Status</th></tr>
        <tr><td>TEST-JOURNAL-0001</td><td>Example manuscript</td><td>With Editor</td></tr>
        <tr><td>OTHER-123</td><td>Other paper</td><td>Under Review</td></tr>
      </table></body></html>
    """.trimIndent()

    @Test fun matchesCorrectManuscriptRow() {
        assertEquals("With Editor", (StatusExtractor.extract(html, "TEST-JOURNAL-0001", "") as ExtractResult.Found).status)
        assertEquals("Under Review", (StatusExtractor.extract(html, "OTHER-123", "") as ExtractResult.Found).status)
    }

    @Test fun matchesCssStatusScopedToRow() {
        val page = html.replace("<td>With Editor</td>", "<td class='paper-status'>With Editor</td>")
            .replace("<td>Under Review</td>", "<td class='paper-status'>Under Review</td>")
        assertEquals("With Editor", (StatusExtractor.extract(page, "TEST-JOURNAL-0001", ".paper-status") as ExtractResult.Found).status)
    }

    @Test fun avoidsFalselyMatchingOtherSubmission() {
        assertTrue(StatusExtractor.extract(html, "UNKNOWN-ID", "") is ExtractResult.NeedsAttention)
    }

    @Test fun loginPageNotTreatedAsStatus() {
        assertTrue(StatusExtractor.extract("<html><body>Sign in with ORCID password</body></html>", "TEST-JOURNAL-0001", "") is ExtractResult.NeedsAttention)
    }

    @Test fun extractsStatusFromSpringerDetailsWithoutEditorialId() {
        val page = "<html><body><div><span>Submission status</span><span>Under Review</span></div></body></html>"
        val result = StatusExtractor.extract(page, "EDITOR-123", "",
            "https://submission.springernature.com/submission-details/11111111-2222-4333-8444-555555555555")
        assertEquals("Under Review", (result as ExtractResult.Found).status)
    }
    @Test fun cannotGuessFromUnlabelledSpringerProse() {
        val page = "<html><body>Under Review is a common status. Sign in for help.</body></html>"
        assertTrue(StatusExtractor.extract(page, "EDITOR-123", "",
            "https://submission.springernature.com/submission-details/11111111-2222-4333-8444-555555555555") is ExtractResult.NeedsAttention)
    }
    @Test fun readsSpringerCurrentStageAfterJavaScriptRendering() {
        val page = "<html><body><section><span>Current stage</span><span>Editorial assignment</span></section></body></html>"
        val result = StatusExtractor.extract(page, "EDITOR-123", "",
            "https://submission.springernature.com/submission-details/11111111-2222-4333-8444-555555555555")
        assertEquals("Editorial assignment", (result as ExtractResult.Found).status)
    }
    @Test fun readsOnlyMarkedTimelineStageNotEntireLegend() {
        val page = "<html><body><ol><li>Technical check</li><li aria-current='step'>Peer review</li><li>Decision</li></ol></body></html>"
        val result = StatusExtractor.extract(page, "EDITOR-123", "",
            "https://submission.springernature.com/submission-details/11111111-2222-4333-8444-555555555555")
        assertEquals("Peer Review", (result as ExtractResult.Found).status)
    }
    @Test fun timelineLegendWithoutMarkedCurrentStageIsUnknown() {
        val page = "<html><body><ol><li>Technical check</li><li>Peer review</li><li>Decision</li></ol></body></html>"
        val result = StatusExtractor.extract(page, "EDITOR-123", "",
            "https://submission.springernature.com/submission-details/11111111-2222-4333-8444-555555555555")
        assertTrue(result is ExtractResult.NeedsAttention)
    }
}
