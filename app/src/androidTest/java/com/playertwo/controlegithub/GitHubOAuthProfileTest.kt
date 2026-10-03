package com.playertwo.controlegithub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GitHubOAuthProfileTest {
    @Test fun acceptsOnlyAValidGitHubLoginFromTheUserResponse() {
        val profile = GitHubUser.parse("""{"login":"octocat","html_url":"https://github.com/octocat","id":1}""")
        assertEquals("octocat", profile.login)
        assertEquals("https://github.com/octocat", profile.profileUrl)
        assertThrows(Exception::class.java) { GitHubUser.parse("""{"login":"not a login"}""") }
        assertThrows(Exception::class.java) { GitHubUser.parse("""{"login":123,"html_url":"https://github.com/123"}""") }
        assertThrows(Exception::class.java) { GitHubUser.parse("""{"login":"octocat","html_url":"https://example.com/octocat"}""") }
        assertThrows(Exception::class.java) { GitHubUser.parse("""{"login":"octocat","html_url":"https://github.com:444/octocat"}""") }
    }
}
