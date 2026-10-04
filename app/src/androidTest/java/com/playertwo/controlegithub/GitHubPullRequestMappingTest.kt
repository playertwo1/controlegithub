package com.playertwo.controlegithub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubPullRequestMappingTest {
    @Test fun searchKeepsOnlyValidPullRequestsAndDeduplicatesRepositoryNumber() {
        val result = parseGitHubPullRequestSearch(
            """{"total_count":3,"incomplete_results":true,"items":[
                {"number":7,"title":"First","state":"open","repository_url":"https://api.github.com/repos/owner/repo","user":{"login":"author"},"pull_request":{}},
                {"number":7,"title":"Duplicate","state":"open","repository_url":"https://api.github.com/repos/owner/repo","pull_request":{}},
                {"number":8,"title":"Issue","state":"open","repository_url":"https://api.github.com/repos/owner/repo"},
                {"number":9,"title":"Untrusted host","state":"open","repository_url":"https://evil.example/repos/owner/repo","pull_request":{}}
            ]}""",
            GitHubPullRequestFilter.OPEN
        )

        assertEquals(3, result.totalCount)
        assertTrue(result.incomplete)
        assertEquals(1, result.items.size)
        assertEquals("owner/repo", result.items.single().repository)
        assertEquals(7, result.items.single().number)
        assertEquals(GitHubPullRequestState.OPEN, result.items.single().state)
    }

    @Test fun detailDistinguishesClosedAndMergedAndPreservesForkBranches() {
        val merged = parseGitHubPullRequestDetail(
            """{"number":12,"title":"Merged","state":"closed","merged_at":"2026-10-01T10:00:00Z","body":"body","user":{"login":"author"},"labels":[{"name":"ready"}],"head":{"ref":"feature","repo":{"full_name":"contributor/repo"}},"base":{"ref":"main","repo":{"full_name":"owner/repo"}}}""",
            "owner/repo",
            12
        )
        val closed = parseGitHubPullRequestDetail(
            """{"number":13,"title":"Closed","state":"closed","merged_at":null,"head":{"ref":"work","repo":{"full_name":"owner/repo"}},"base":{"ref":"main","repo":{"full_name":"owner/repo"}}}""",
            "owner/repo",
            13
        )

        assertEquals(GitHubPullRequestState.MERGED, merged.state)
        assertEquals("contributor/repo", merged.headRepository)
        assertEquals("feature", merged.headBranch)
        assertEquals("owner/repo", merged.baseRepository)
        assertEquals("main", merged.baseBranch)
        assertEquals(listOf("ready"), merged.labels)
        assertEquals(GitHubPullRequestState.CLOSED, closed.state)
    }
}
