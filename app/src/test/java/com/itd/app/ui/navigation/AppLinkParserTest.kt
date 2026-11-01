package com.itd.app.ui.navigation

import org.junit.Assert.*
import org.junit.Test

class AppLinkParserTest {
    @Test fun nativeAndWebPostLinksKeepCommentTarget() {
        val expected = AppLink.Post("post-42", "comment-7")
        assertEquals(expected, AppLinkParser.parse("itd://post/post-42?comment=comment-7"))
        assertEquals(expected, AppLinkParser.parse("https://xn--d1ah4a.com/@alice/post/post-42?comment=comment-7"))
    }

    @Test fun profilesAndSessionLinks() {
        assertEquals(AppLink.Profile("alice"), AppLinkParser.parse("https://итд.com/@alice"))
        assertEquals(AppLink.Profile("alice+one"), AppLinkParser.parse("itd://profile/alice%2Bone"))
        assertEquals(AppLink.Sessions, AppLinkParser.parse("itd://sessions"))
    }

    @Test fun qrFragmentIsPreserved() {
        val url = "https://xn--d1ah4a.com/qr#i=123&s=456"
        assertEquals(AppLink.Qr(url), AppLinkParser.parse(url))
    }

    @Test fun unrelatedAndMalformedUrlsDoNotNavigate() {
        listOf("https://example.com/@alice", "https://xn--d1ah4a.com.evil.com/@alice", "https://xn--d1ah4a.com@evil.com/@alice", "itd://post", "https://xn--d1ah4a.com/@", "javascript:alert(1)", "%").forEach {
            assertNull(it, AppLinkParser.parse(it))
        }
    }
}
