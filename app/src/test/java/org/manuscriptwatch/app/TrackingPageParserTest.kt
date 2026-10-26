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

    @Test fun rejectsUntrustedSchemeForExtraLinks() {
        assertTrue(!TrackingSources.isSupportedUrl("http://track.authorhub.elsevier.com/?uuid=demo"))
        assertTrue(!TrackingSources.isSupportedUrl("https://person@track.authorhub.elsevier.com/?uuid=demo"))
    }
}
