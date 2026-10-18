package com.playertwo.controlegithub

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BetaRobustnessAndroidTest {

    @Test
    fun scenario1_repositoryParsingWithMissingOptionalFieldsDoesNotCrash() {
        val minimalRepoJson = """
            [
                {
                    "id": 9991,
                    "name": "barebones-repo",
                    "full_name": "octocat/barebones-repo",
                    "private": false,
                    "stargazers_count": 0,
                    "owner": {
                        "login": "octocat"
                    },
                    "description": null,
                    "language": null
                }
            ]
        """.trimIndent()

        val repos = parseGitHubRepositories(minimalRepoJson)
        assertEquals(1, repos.size)
        val repo = repos[0]
        assertEquals("barebones-repo", repo.name)
        assertEquals("octocat", repo.owner)
        assertEquals(null, repo.description)
        assertEquals(0, repo.stars)
    }

    @Test
    fun scenario2_issueParsingWithMissingOptionalFieldsDoesNotCrash() {
        val minimalIssueJson = """
            [
                {
                    "number": 42,
                    "title": "Bug without assignee or labels",
                    "state": "open",
                    "html_url": "https://github.com/octocat/hello-world/issues/42",
                    "repository": {
                        "full_name": "octocat/hello-world"
                    },
                    "user": {
                        "login": "reporter"
                    },
                    "assignees": [],
                    "labels": [],
                    "updated_at": "2026-10-18T10:00:00Z"
                }
            ]
        """.trimIndent()

        val issues = parseGitHubIssues(minimalIssueJson)
        assertEquals(1, issues.size)
        val issue = issues[0]
        assertEquals(42, issue.number)
        assertEquals("Bug without assignee or labels", issue.title)
        assertEquals(GitHubIssueState.OPEN, issue.state)
        assertEquals("octocat/hello-world", issue.repository)
        assertEquals(0, issue.assignees.size)
        assertEquals(0, issue.labels.size)
    }

    @Test
    fun scenario3_pullRequestDetailParsingWithNullBodyAndMinimalData() {
        val minimalPrDetailJson = """
            {
                "number": 7,
                "title": "Minimal PR Detail",
                "state": "open",
                "user": {
                    "login": "contributor"
                },
                "body": null,
                "labels": [],
                "head": {
                    "ref": "feature-branch",
                    "sha": "1234567890abcdef1234567890abcdef12345678",
                    "repo": {
                        "full_name": "octocat/hello-world"
                    }
                },
                "base": {
                    "ref": "main",
                    "repo": {
                        "full_name": "octocat/hello-world"
                    }
                }
            }
        """.trimIndent()

        val prDetail = parseGitHubPullRequestDetail(
            json = minimalPrDetailJson,
            repository = "octocat/hello-world",
            expectedNumber = 7
        )

        assertNotNull(prDetail)
        assertEquals(7, prDetail.number)
        assertEquals("Minimal PR Detail", prDetail.title)
        assertEquals(null, prDetail.body)
        assertEquals(GitHubPullRequestState.OPEN, prDetail.state)
        assertEquals("feature-branch", prDetail.headBranch)
    }
}
