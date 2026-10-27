package org.manuscriptwatch.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ElsevierProductionEventsTest {
    private val url = "https://authors.elsevier.com/tracking/article/details.do?aid=12345&jid=DEMO&surname=Example"

    /** Synthetic HTML based on the labels and dates visible in a user-supplied screenshot. */
    private val published = """
      <html><body><section><h2>Production events</h2>
        <table>
          <tr><td><span>14 Sep</span><span>2026</span></td>
              <td>Printed journal shipped to subscribers</td></tr>
          <tr><td>31 Aug<br>2026</td><td>The Share Link has been sent to you</td></tr>
          <tr><td>31 Aug<br>2026</td><td>Final version of your article published online</td></tr>
          <tr><td>29 Aug<br>2026</td><td>Your proof corrections have been returned to Elsevier</td></tr>
          <tr><td>29 Aug<br>2026</td><td>Proofs available for checking</td></tr>
          <tr><td>29 Aug<br>2026</td><td>Article published online</td></tr>
          <tr><td>29 Aug<br>2026</td><td>Rights &amp; Access form completed by you</td></tr>
          <tr><td>29 Aug<br>2026</td><td>Rights &amp; Access form sent to you for completion</td></tr>
          <tr><td>29 Aug<br>2026</td><td>Offprint order letter sent to you for completion</td></tr>
          <tr><td>20 Aug<br>2026</td><td>Received for production</td></tr>
        </table></section>
      </body></html>
    """.trimIndent()

    @Test fun readsScreenshotLikeProductionTimelineAndLatestEvent() {
        val events = ElsevierProductionEvents.events(published)
        assertEquals(10, events.size)
        assertEquals(LocalDate.of(2026, 9, 14), events.first().date)
        assertEquals("Printed journal shipped to subscribers", events.first().description)
        assertEquals("Received for production", events.last().description)
        val result = TrackingPageParser.elsevierTracking(published, url) as ExtractResult.Found
        assertEquals("Printed journal shipped to subscribers (14 Sep 2026)", result.status)
    }

    @Test fun choosesLatestDateEvenWhenTimelineNotOrdered() {
        val html = """<h2>Production events</h2><ul>
          <li>20 Aug 2026 Received for production</li>
          <li>29 Aug 2026 Article published online</li>
          <li>31 Aug 2026 Final version of your article published online</li></ul>"""
        val result = TrackingPageParser.elsevierTracking(html, url) as ExtractResult.Found
        assertEquals("Final version of your article published online (31 Aug 2026)", result.status)
    }

    @Test fun earlierArticlePublishedOnlineDoesNotOverrideFinalPublication() {
        val html = """<h2>Production events</h2>
          <div>31 Aug 2026 Final version of your article published online</div>
          <div>29 Aug 2026 Article published online</div>"""
        val latest = ElsevierProductionEvents.latest(html)!!
        assertEquals("Final version of your article published online", latest.description)
    }

    @Test fun doesNotTreatUndatedMilestoneOrUnrelatedDatesAsLatestEvent() {
        assertNull(ElsevierProductionEvents.latest(
            "<h2>Publication information</h2><p>14 Sep 2026 Printed journal shipped to subscribers</p>"))
        assertNull(ElsevierProductionEvents.latest(
            "<h2>Production events</h2><p>On 14 Sep 2026 our office is closed.</p>"))
        assertTrue(TrackingPageParser.elsevierTracking(
            "<h2>Production events</h2><p>Events will appear here soon.</p>", url
        ) is ExtractResult.NeedsAttention)
    }

    @Test fun onlyProductionSourceUsesDatedEventExtraction() {
        val hub = "https://track.authorhub.elsevier.com/?uuid=11111111-2222-4333-8444-555555555555"
        assertTrue(TrackingPageParser.elsevierTracking(published, hub) is ExtractResult.NeedsAttention)
    }

    @Test fun existingExplicitProductionStatusStillWorks() {
        val result = TrackingPageParser.elsevierTracking(
            "<div><span>Production status</span><span>Proof correction</span></div>", url
        ) as ExtractResult.Found
        assertEquals("Proof correction", result.status)
    }
}
