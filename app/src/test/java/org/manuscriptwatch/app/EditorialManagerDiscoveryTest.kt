package org.manuscriptwatch.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorialManagerDiscoveryTest {
    private val menu = "https://www.editorialmanager.com/demo/"
    private val html = """<html><body><h1>Author Main Menu</h1>
      <a href='/demo/folder1.aspx'>Submissions Being Processed</a>
      <a href='/demo/folder2.aspx'>Revisions Being Processed</a>
      <a href='/demo/folder3.aspx'>Submissions with a Decision</a>
      <a href='/demo/delete.aspx'>Remove Submission</a>
      <a href='javascript:__doPostBack("bad")'>Submissions Needing Revision</a>
      <a href='https://evil.invalid/'>Completed Submissions</a>
    </body></html>"""

    @Test fun discoversOnlySafeSameHostFolders() {
        val links = EditorialManagerDiscovery.folders(html, menu)
        assertEquals(3, links.size)
        assertEquals("Submissions Being Processed", links[0].title)
        assertTrue(links.all { it.url.startsWith("https://www.editorialmanager.com/demo/") })
        assertTrue(EditorialManagerDiscovery.isMenuPage(html, menu))
    }

    @Test fun readsSafePaginationButNeverAnActionLink() {
        val page = "<body><a href='/demo/paged.aspx?pg=2'>Next</a>" +
            "<a href='/demo/delete.aspx'>Delete Submission</a></body>"
        assertEquals("https://www.editorialmanager.com/demo/paged.aspx?pg=2",
            EditorialManagerDiscovery.nextPage(page, menu))
        assertNull(EditorialManagerDiscovery.nextPage("<a href='javascript:submit()'>Next</a>", menu))
    }

    @Test fun rejectsPretendEditorialManagerHost() {
        assertTrue(!EditorialManagerDiscovery.isEditorialManager("https://www.editorialmanager.com.evil.invalid/demo/"))
    }
}
