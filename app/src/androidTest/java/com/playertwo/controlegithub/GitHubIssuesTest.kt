package com.playertwo.controlegithub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubIssuesTest {
    @Test fun buildsAccountIssueQueriesWithIssueOnlyFilterAndPagination() {
        assertEquals(
            "/issues?filter=assigned&state=open&pulls=false&per_page=50",
            githubIssuesPath(GitHubIssueScope.ASSIGNED, GitHubIssueState.OPEN)
        )
        assertEquals(
            "/issues?filter=created&state=closed&pulls=false&per_page=50",
            githubIssuesPath(GitHubIssueScope.CREATED, GitHubIssueState.CLOSED)
        )
        assertEquals(
            "/issues?filter=all&state=all&pulls=false&per_page=50",
            githubIssuesPath(GitHubIssueScope.ALL, GitHubIssueState.ALL)
        )
    }

    @Test fun mapsIssueAndOptionalFieldsWithoutRenderingMarkdown() {
        val issues = parseGitHubIssues(
            """[{
                "number":18,"title":"Fixture issue","state":"open",
                "html_url":"https://github.com/fixture-owner/sample/issues/18",
                "repository":{"full_name":"fixture-owner/sample"},
                "user":{"login":"fixture-author"},
                "assignees":[{"login":"fixture-assignee"}],
                "labels":[{"name":"bug"},{"name":"help wanted"}],
                "updated_at":"2026-10-04T12:30:00Z","body":"**inert markdown**"
            }]"""
        )

        assertEquals(1, issues.size)
        assertEquals(18, issues.single().number)
        assertEquals("Fixture issue", issues.single().title)
        assertEquals(GitHubIssueState.OPEN, issues.single().state)
        assertEquals("fixture-owner/sample", issues.single().repository)
        assertEquals("fixture-author", issues.single().author)
        assertEquals(listOf("fixture-assignee"), issues.single().assignees)
        assertEquals(listOf("bug", "help wanted"), issues.single().labels)
        assertEquals("fixture-owner/sample/18", issues.single().identity)
    }

    @Test fun excludesPullRequestsDeduplicatesSeparatelyAndAcceptsOptionalDataMissing() {
        val issues = parseGitHubIssues(
            """[
                {"number":18,"title":"Issue","state":"open","html_url":"https://github.com/fixture-owner/sample/issues/18","repository":{"full_name":"fixture-owner/sample"},"user":null,"assignees":[],"labels":[]},
                {"number":19,"title":"Pull request","state":"open","repository":{"full_name":"fixture-owner/sample"},"pull_request":{"url":"https://api.github.com/repos/fixture-owner/sample/pulls/19"}},
                {"number":20,"title":"Missing required repository","state":"open"}
            ]"""
        )

        assertEquals(1, issues.size)
        assertNull(issues.single().author)
        assertTrue(issues.single().assignees.isEmpty())
        assertTrue(issues.single().labels.isEmpty())
        assertNull(issues.single().updatedAt)
    }

    @Test fun dropsInvalidIssuesAndAcceptsEmptyResponse() {
        val issues = parseGitHubIssues(
            """[
                {"number":-1,"title":"Invalid number","state":"open","html_url":"https://github.com/fixture-owner/sample/issues/1","repository":{"full_name":"fixture-owner/sample"}},
                {"number":2,"title":"Unknown state","state":"merged","html_url":"https://github.com/fixture-owner/sample/issues/2","repository":{"full_name":"fixture-owner/sample"}},
                {"number":3,"title":"Invalid repository","state":"open","html_url":"https://github.com/fixture-owner/sample/issues/3","repository":{"full_name":"sample"}},
                {"number":4,"title":"Wrong host URL","state":"open","html_url":"https://evil.example/fixture-owner/sample/issues/4","repository":{"full_name":"fixture-owner/sample"}},
                {"number":5,"title":"Wrong path URL","state":"open","html_url":"https://github.com/fixture-owner/sample/issues/6","repository":{"full_name":"fixture-owner/sample"}},
                {"number":7,"title":"Missing URL","state":"open","repository":{"full_name":"fixture-owner/sample"}}
            ]"""
        )
        assertTrue(issues.isEmpty())
        assertTrue(parseGitHubIssues("[]").isEmpty())
    }
}
