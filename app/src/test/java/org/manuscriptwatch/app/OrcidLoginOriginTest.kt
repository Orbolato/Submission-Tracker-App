package org.manuscriptwatch.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrcidLoginOriginTest {
    @Test fun onlyPermitsExactOrcidHosts() {
        assertTrue(OrcidLoginOrigin.isTrusted("https://orcid.org/signin"))
        assertTrue(OrcidLoginOrigin.isTrusted("https://login.orcid.org/signin"))
        assertFalse(OrcidLoginOrigin.isTrusted("http://orcid.org/signin"))
        assertFalse(OrcidLoginOrigin.isTrusted("https://orcid.org.attacker.example/signin"))
        assertFalse(OrcidLoginOrigin.isTrusted("https://springernature.com/login"))
    }
}
