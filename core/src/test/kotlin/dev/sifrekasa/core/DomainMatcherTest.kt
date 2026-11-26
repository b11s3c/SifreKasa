package dev.sifrekasa.core

import dev.sifrekasa.core.autofill.DomainMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainMatcherTest {

    @Test
    fun `hostOf normalizes scheme, case, trailing dot, and www prefix`() {
        assertEquals("example.com", DomainMatcher.hostOf("https://WWW.Example.COM/login"))
        assertEquals("example.com", DomainMatcher.hostOf("example.com."))
        assertEquals("sub.example.com", DomainMatcher.hostOf("http://sub.example.com:8443/path"))
        assertNull(DomainMatcher.hostOf(""))
        assertNull(DomainMatcher.hostOf("   "))
        assertNull(DomainMatcher.hostOf("https://"))
    }

    @Test
    fun `matches exact host and legitimate subdomains`() {
        assertTrue(DomainMatcher.matches("https://example.com", "example.com"))
        assertTrue(DomainMatcher.matches("https://www.example.com/auth", "login.example.com"))
        assertTrue(DomainMatcher.matches("example.com", "a.b.example.com"))
    }

    @Test
    fun `rejects phishing domains, sibling subdomains, and single-label TLDs`() {
        assertFalse(DomainMatcher.matches("https://example.com", "evil-example.com"))
        assertFalse(DomainMatcher.matches("https://example.com", "example.com.evil.net"))
        assertFalse(DomainMatcher.matches("https://accounts.example.com", "mail.example.com"))
        assertFalse(DomainMatcher.matches("https://login.example.com", "example.com"))
        assertFalse(DomainMatcher.matches("com", "example.com"))
        assertFalse(DomainMatcher.matches("", "example.com"))
    }
}
