package com.example

import com.example.data.model.ContentType
import com.example.util.ContentTypeDetector
import com.example.util.DateFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testContentTypeDetection() {
        val urlResult = ContentTypeDetector.detect("https://example.com/api?id=123")
        assertEquals(ContentType.URL, urlResult.type)

        val emailResult = ContentTypeDetector.detect("developer@clipnest.app")
        assertEquals(ContentType.EMAIL, emailResult.type)

        val phoneResult = ContentTypeDetector.detect("+1-555-432-1098")
        assertEquals(ContentType.PHONE, phoneResult.type)

        val colorResult = ContentTypeDetector.detect("#2563EB")
        assertEquals(ContentType.HEX_COLOR, colorResult.type)
        assertEquals("#2563EB", colorResult.colorHex)

        val jsonResult = ContentTypeDetector.detect("{\"key\": \"value\", \"id\": 42}")
        assertEquals(ContentType.JSON, jsonResult.type)

        val codeResult = ContentTypeDetector.detect("fun calculateTotal(items: List<Int>): Int {\n    return items.sum()\n}")
        assertEquals(ContentType.CODE, codeResult.type)

        val textResult = ContentTypeDetector.detect("Grocery list: milk, eggs, bread")
        assertEquals(ContentType.TEXT, textResult.type)
    }

    @Test
    fun testDateFormatter() {
        val now = System.currentTimeMillis()
        assertEquals("Just now", DateFormatter.formatRelativeTime(now))

        val oneHourAgo = now - (60 * 60 * 1000L)
        assertEquals("1h ago", DateFormatter.formatRelativeTime(oneHourAgo))

        val pinnedExpiry = DateFormatter.getExpiryText(now, 7, isPinned = true)
        assertTrue(pinnedExpiry.contains("Never expires"))

        val activeExpiry = DateFormatter.getExpiryText(now, 7, isPinned = false)
        assertTrue(activeExpiry.contains("Expires in 7d") || activeExpiry.contains("Expires in 6d"))
    }
}
