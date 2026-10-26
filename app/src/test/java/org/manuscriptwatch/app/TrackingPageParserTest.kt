package org.manuscriptwatch.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingPageParserTest {
    private val authorHub = "https://track.authorhub.elsevier.com/?uuid=11111111-2222-4333-8444-555555555555"
    private val production = "https://authors.elsevier.com/tracking/article/details.do?aid=000000&jid=DEMO&surname=Example"

    @Test fun recognizesPublicElsevierTrackingSources() {
        assertEquals(TrackingKind.ELSEVIER_HUB, TrackingKind.fromUrl(authorHub))
        assertEquals(TrackingKind.ELSEVIER_PRODUCTION, TrackingKind.fromUrl(production))
        assertEquals(TrackingKind.JOURNAL, TrackingKind.fromUrl("https://track.authorhub.elsevier.com.evil.invalid/"))
    }

    @Test fun extractsExplicitElsevierStatusAndMetrics() {
        val html = """<body><div><span>Current status</span><span>Under Review</span></div>
          <dl><dt>Reviewers invited</dt><dd>4</dd><dt>Reviewers accepted</dt><dd>2</dd>
          <dt>Reviews received</dt><dd>1</dd></dl></body>"""
        val result = TrackingPageParser.elsevierTracking(html, authorHub) as ExtractResult.Found
        assertEquals("Under Review", result.status)
        assertEquals(4, result.reviewProgress?.invited)
        assertEquals(2, result.reviewProgress?.accepted)
        assertEquals(1, result.reviewProgress?.reports)
    }

    @Test fun supportsInlineStatusField() {
        val html = "<body><p>Current status: In Peer Review</p></body>"
        assertEquals("In Peer Review", (TrackingPageParser.elsevierTracking(html, authorHub) as ExtractResult.Found).status)
    }

    @Test fun supportsLabeledProductionStage() {
        val html = "<body><p>Production status</p><p>Proof correction</p></body>"
        assertEquals("Proof correction", (TrackingPageParser.elsevierTracking(html, production) as ExtractResult.Found).status)
    }

    @Test fun unrelatedPeerReviewProseIsNotAStatus() {
        assertTrue(TrackingPageParser.elsevierTracking(
            "<body>Reviewers invited to learn more about peer review. Submit your paper.</body>", authorHub
        ) is ExtractResult.NeedsAttention)
        assertNull(TrackingPageParser.reviewProgress("<body>Reviewers invited to learn more.</body>"))
    }

    @Test fun capturesReviewerProgressEvenWithoutStatusLabel() {
        val html = "<body><dl><dt>Reviewers invited</dt><dd>5</dd>" +
            "<dt>Reviewers accepted</dt><dd>3</dd><dt>Reviews received</dt><dd>1</dd></dl></body>"
        val result = TrackingPageParser.elsevierTracking(html, authorHub) as ExtractResult.NeedsAttention
        assertEquals(5, result.reviewProgress?.invited)
        assertEquals(3, result.reviewProgress?.accepted)
        assertEquals(1, result.reviewProgress?.reports)
    }

    @Test fun recognizesOnlyExplicitlyCurrentTimelineStage() {
        val html = "<body><ol><li>With Editor</li>" +
            "<li aria-current='step'>Under Review</li><li>Decision in Process</li></ol></body>"
        assertEquals("Under Review", (TrackingPageParser.elsevierTracking(html, authorHub)
            as ExtractResult.Found).status)
        val unmarked = "<body><ol><li>With Editor</li><li>Under Review</li></ol></body>"
        assertTrue(TrackingPageParser.elsevierTracking(unmarked, authorHub) is ExtractResult.NeedsAttention)
    }

    @Test fun authorHubScreenshotPreservesAtLeastInvitationsAndZeroCompletedReviews() {
        val html = """<html><body><h2>Managing risk in airlines</h2>
          <ul><li>Reviews completed: 0</li><li>Review invitations accepted: 2</li>
          <li>Review invitations sent: 2+</li></ul>
          <aside class='review-summary'><h1>Under Review</h1>
          <p>Last review activity: 18th October 2026</p></aside></body></html>"""
        val result = TrackingPageParser.elsevierTracking(html, authorHub) as ExtractResult.Found
        assertEquals("Under Review", result.status)
        val progress = result.reviewProgress!!
        assertEquals(2, progress.invited)
        assertTrue(progress.invitedAtLeast)
        assertEquals("2+", progress.invitedLabel())
        assertEquals(2, progress.accepted)
        assertEquals(0, progress.reports)
    }

    @Test fun doesNotInventPlusWhenInvitationCountIsExact() {
        val html = "<body><div>Review invitations sent: 2</div>" +
            "<div>Review invitations accepted: 1</div><div>Reviews completed: 0</div></body>"
        val progress = TrackingPageParser.reviewProgress(html)!!
        assertEquals(2, progress.invited)
        assertTrue(!progress.invitedAtLeast)
        assertEquals("2", progress.invitedLabel())
        assertEquals(0, progress.reports)
    }

    @Test fun supportsCurrentStatusNextToReviewActivityWithoutExplicitLabel() {
        val html = "<body><section><div>Under Review</div><div>Last review activity: today</div></section></body>"
        val result = TrackingPageParser.elsevierTracking(html, authorHub) as ExtractResult.Found
        assertEquals("Under Review", result.status)
    }

    @Test fun springerNarrativeInvitedAndAcceptedCountsDoNotImplyCompletedReviews() {
        val html = """
          <html><body><section>
          <h2>News about your peer review process</h2>
          <ul>
            <li>The editor has invited 6 reviewer(s)</li>
            <li>There is 1 reviewer(s) that has accepted to review your manuscript</li>
          </ul>
          <p>After the editor has collated and reviewed all the reports they need,
          you'll be notified about their decision.</p>
          </section></body></html>
        """.trimIndent()
        val progress = TrackingPageParser.springerReviewProgress(html)!!
        assertEquals(6, progress.invited)
        assertEquals("6", progress.invitedLabel())
        assertEquals(1, progress.accepted)
        assertNull(progress.reports)
    }

    @Test fun springerCountsIncludeReceivedReportsOnlyWhenExplicitlyDisplayed() {
        val html = """
          <html><body><ul>
            <li>The editor has invited 6 reviewer(s)</li>
            <li>There are 2 reviewers who have accepted to review your manuscript</li>
            <li>Reviews received: 1</li>
          </ul></body></html>
        """.trimIndent()
        val progress = TrackingPageParser.springerReviewProgress(html)!!
        assertEquals(6, progress.invited)
        assertEquals(2, progress.accepted)
        assertEquals(1, progress.reports)
    }

    @Test fun springerRecognizesExplicitReviewerReportReceivedCount() {
        val html = "<body><ul><li>The editor has invited 6 reviewer(s)</li>" +
            "<li>There is 1 reviewer(s) that has accepted to review your manuscript</li>" +
            "<li>Reviewer report(s) received: 1</li></ul></body>"
        val progress = TrackingPageParser.springerReviewProgress(html)!!
        assertEquals(6, progress.invited)
        assertEquals(1, progress.accepted)
        assertEquals(1, progress.reports)
    }

    @Test fun springerAbsentReviewerReportCountRemainsUnknown() {
        val html = "<body><li>The editor has invited 6 reviewer(s)</li>" +
            "<li>There is 1 reviewer(s) that has accepted to review your manuscript</li>" +
            "<p>After the editor has collated and reviewed all the reports they need</p></body>"
        val progress = TrackingPageParser.springerReviewProgress(html)!!
        assertNull(progress.reports)
    }

    @Test fun completedReviewDisplayDistinguishesUnknownZeroAndReportedNumber() {
        assertEquals("Not displayed", ReviewProgress(invited = 6, accepted = 1).completedLabel())
        assertEquals("0", ReviewProgress(reports = 0).completedLabel())
        assertEquals("2", ReviewProgress(reports = 2).completedLabel())
    }

    @Test fun springerNarrativeUnrelatedNumbersDoNotCountAsReviewerProgress() {
        val html = "<body><p>6 other journals invited reviewers last year.</p>" +
            "<p>Our support FAQ says there is 1 reviewer who accepted a review in another system.</p></body>"
        assertNull(TrackingPageParser.springerReviewProgress(html))
    }

    @Test fun rejectsUntrustedSchemeForExtraLinks() {
        assertTrue(!TrackingSources.isSupportedUrl("http://track.authorhub.elsevier.com/?uuid=demo"))
        assertTrue(!TrackingSources.isSupportedUrl("https://person@track.authorhub.elsevier.com/?uuid=demo"))
    }
}
