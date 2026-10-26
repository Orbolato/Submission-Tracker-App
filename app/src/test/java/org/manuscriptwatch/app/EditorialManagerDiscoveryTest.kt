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

    @Test fun recognizesAuthorMenuWithJavaScriptNavigationWithoutCrawlingIt() {
        val page = """<body><h2>Author Main Menu</h2>
           <a href='javascript:goToFolder(1)'>Submissions Being Processed</a>
           <a href='javascript:goToFolder(2)'>Revisions Being Processed</a>
           <button>Submissions with a Decision</button></body>"""
        assertTrue(EditorialManagerDiscovery.isMenuPage(page, menu))
        assertEquals(0, EditorialManagerDiscovery.folders(page, menu).size)
        val paper = Manuscript(title = "Example", journal = "Example", manuscriptId = "DEMO-001")
        val answer = EditorialManagerDiscovery.locateFromRenderedMenu(paper, menu, page)
        assertTrue(answer is EditorialManagerDiscovery.Result.NeedsAttention)
        assertTrue((answer as EditorialManagerDiscovery.Result.NeedsAttention).reason.contains("JavaScript"))
    }

    @Test fun recognizesFolderTitleForPassiveCapture() {
        val page = "<h2>Submissions Being Processed</h2><table><tr><th>Status</th></tr></table>"
        assertEquals("Submissions Being Processed", EditorialManagerDiscovery.folderTitle(page))
    }

    @Test fun rejectsPretendEditorialManagerHost() {
        assertTrue(!EditorialManagerDiscovery.isEditorialManager("https://www.editorialmanager.com.evil.invalid/demo/"))
    }
}
