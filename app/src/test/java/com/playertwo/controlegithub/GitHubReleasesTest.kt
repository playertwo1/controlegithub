package com.playertwo.controlegithub

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleasesTest {
    private val json = """[
      {"id":10,"tag_name":"v1.1.0","name":"","body":"Notas","draft":false,"prerelease":true,
       "author":{"login":"me"},"published_at":"2026-10-19T10:00:00Z","assets":[]},
      {"id":9,"tag_name":"v1.0.0","name":"Versão 1.0","body":null,"draft":false,"prerelease":false,
       "published_at":"2026-10-18T10:00:00Z","assets":[
         {"id":1,"name":"app-release.apk","size":5242880,"download_count":3,
          "content_type":"application/vnd.android.package-archive",
          "browser_download_url":"https://github.com/me/app/releases/download/v1.0.0/app-release.apk"},
         {"id":2,"name":"SHA256SUMS.txt","size":90,"download_count":1,
          "browser_download_url":"https://evil.example/x"}]}
    ]"""

    @Test
    fun parsesReleasesAndAssets() {
        val releases = parseGitHubReleases(json)
        assertEquals(2, releases.size)
        assertEquals("v1.1.0", releases[0].title)
        assertEquals("Versão 1.0", releases[1].title)
        assertEquals(Instant.parse("2026-10-18T10:00:00Z"), releases[1].publishedAt)
        val (apk, sums) = releases[1].assets
        assertTrue(apk.isApk)
        assertTrue(sums.isChecksum)
        assertEquals(1, releases[1].apkAssets.size)
        assertNull("URL fora do github.com é descartada", sums.browserDownloadUrl)
    }

    @Test
    fun latestIgnoresPrereleaseAndDraft() {
        assertEquals(9L, latestReleaseId(parseGitHubReleases(json)))
        assertNull(latestReleaseId(emptyList()))
    }

    @Test
    fun rejectsInvalidPayload() {
        assertThrows(Exception::class.java) { parseGitHubReleases("""[{"id":0,"tag_name":"v1"}]""") }
        assertThrows(Exception::class.java) { parseGitHubReleases("""[{"id":1}]""") }
    }

    @Test
    fun formatsSizes() {
        assertEquals("512 B", formatByteSize(512))
        assertEquals("5,0 MB", formatByteSize(5_242_880))
    }

    @Test
    fun encodesRepositoryPath() {
        val repo = GitHubRepository(1, "my app", "me/my app", "me", false, null, null, 0)
        assertEquals("/repos/me/my%20app/releases?per_page=30", GitHubReleasePager.firstPagePath(repo))
    }
}
