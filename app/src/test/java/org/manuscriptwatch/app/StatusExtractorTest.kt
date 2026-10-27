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
    @Test fun springerDetailStatusAlsoIncludesNarrativeReviewerCounts() {
        val html = """
          <html><body>
          <div><span>Current status</span><span>Your submission is in peer review</span></div>
          <div><h3>News about your peer review process</h3>
          <ul>
            <li>The editor has invited 6 reviewer(s)</li>
            <li>There is 1 reviewer(s) that has accepted to review your manuscript</li>
          </ul></div></body></html>
        """.trimIndent()
        val url = "https://submission.springernature.com/submission-details/11111111-2222-4333-8444-555555555555"
        val result = StatusExtractor.extract(html, "EDITOR-123", "", url) as ExtractResult.Found
        assertEquals("Your submission is in peer review", result.status)
        assertEquals(6, result.reviewProgress?.invited)
        assertEquals(1, result.reviewProgress?.accepted)
        assertEquals(null, result.reviewProgress?.reports)
    }

    @Test fun springerHeadlineAndReviewerNewsFromRenderedSubmissionPage() {
        val page = """<html><body>
          <div class='currentStatus'><span>CURRENT STATUS</span>
          <p>Last update: 26 October 2026</p>
          <h2>Your submission is in peer review</h2></div>
          <section><h3>News about your peer review process</h3><ul>
            <li>The editor has invited 6 reviewer(s)</li>
            <li>There is 1 reviewer(s) that has accepted to review your manuscript</li>
          </ul><p>After the editor has collated and reviewed all the reports,
          you'll be notified.</p></section></body></html>"""
        val url = "https://submission.springernature.com/submission-details/11111111-2222-4333-8444-555555555555"
        // A legacy user record might still contain an old CSS selector.
        val parsed = StatusExtractor.extract(page, "EDITOR-123", ".old-selector", url)
            as ExtractResult.Found
        assertEquals("Your submission is in peer review", parsed.status)
        assertEquals(6, parsed.reviewProgress?.invited)
        assertEquals(1, parsed.reviewProgress?.accepted)
        assertEquals(null, parsed.reviewProgress?.reports)
    }

    @Test fun readsEditorialManagerCardWithManuscriptSpecificStatus() {
        val page = """<html><body><div role='grid'>
          <div role='row'><span>DEMO-D-26-00001</span>
          <span class='current-status'>With Editor</span></div>
          <div role='row'><span>DEMO-D-26-00002</span>
          <span class='current-status'>Under Review</span></div>
        </div></body></html>"""
        assertEquals("With Editor",
            (StatusExtractor.extract(page, "DEMO-D-26-00001", "",
                "https://www.editorialmanager.com/demo/processing")
                as ExtractResult.Found).status)
        assertEquals("Under Review",
            (StatusExtractor.extract(page, "DEMO-D-26-00002", "",
                "https://www.editorialmanager.com/demo/processing")
                as ExtractResult.Found).status)
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
