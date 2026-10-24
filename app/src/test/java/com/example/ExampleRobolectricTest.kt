package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.ClipItem
import com.example.data.preferences.AppThemeMode
import com.example.data.preferences.PreferenceManager
import com.example.data.repository.ClipRepository
import com.example.data.repository.SaveResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: ClipRepository
    private lateinit var context: Context

    @Before
    fun createDb() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val pref = PreferenceManager(context)
        repository = ClipRepository(db.clipDao(), pref)
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun `read string from context`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("ClipNest", appName)
    }

    @Test
    fun `first launch clipboard history is completely clean and empty`() = runBlocking {
        val clips = repository.allClips.first()
        assertTrue("Clipboard history must start completely empty on clean launch", clips.isEmpty())
        assertEquals(0, clips.size)
    }

    @Test
    fun `theme mode and dynamic color preferences persist properly`() {
        val pref = PreferenceManager(context)
        // Test Theme Mode
        pref.themeMode = AppThemeMode.DARK
        assertEquals(AppThemeMode.DARK, pref.themeMode)

        pref.themeMode = AppThemeMode.LIGHT
        assertEquals(AppThemeMode.LIGHT, pref.themeMode)

        pref.themeMode = AppThemeMode.SYSTEM
        assertEquals(AppThemeMode.SYSTEM, pref.themeMode)

        // Test Dynamic Color
        pref.dynamicColor = false
        assertFalse(pref.dynamicColor)

        pref.dynamicColor = true
        assertTrue(pref.dynamicColor)
    }

    @Test
    fun `insert and retrieve clips`() = runBlocking {
        val saveResult = repository.saveOrUpdateClip("Test content 123", "Unit Test")
        assertEquals(SaveResult.NEW, saveResult)

        val clips = repository.allClips.first()
        assertEquals(1, clips.size)
        assertEquals("Test content 123", clips[0].content)
        assertEquals("Unit Test", clips[0].label)
    }

    @Test
    fun `duplicate clip updates copy count instead of creating new record`() = runBlocking {
        repository.saveOrUpdateClip("Duplicate test")
        val duplicateResult = repository.saveOrUpdateClip("Duplicate test")
        assertEquals(SaveResult.UPDATED_EXISTING, duplicateResult)

        val clips = repository.allClips.first()
        assertEquals(1, clips.size)
        assertEquals(2, clips[0].copyCount)
    }

    @Test
    fun `retention cleans expired unpinned clips but preserves pinned clips`() = runBlocking {
        val now = System.currentTimeMillis()
        val eightDaysAgo = now - (8 * 24L * 60L * 60L * 1000L)

        // Expired unpinned clip
        db.clipDao().insert(
            ClipItem(
                content = "Old clip to delete",
                createdAt = eightDaysAgo,
                lastUsedAt = eightDaysAgo,
                isPinned = false
            )
        )

        // Expired PINNED clip (must be protected!)
        db.clipDao().insert(
            ClipItem(
                content = "Old clip that is pinned",
                createdAt = eightDaysAgo,
                lastUsedAt = eightDaysAgo,
                isPinned = true
            )
        )

        // Fresh clip
        db.clipDao().insert(
            ClipItem(
                content = "Fresh clip",
                createdAt = now,
                lastUsedAt = now,
                isPinned = false
            )
        )

        repository.preferenceManager.retentionDays = 7
        val deletedCount = repository.cleanExpiredClips()
        assertEquals(1, deletedCount)

        val remaining = repository.allClips.first()
        assertEquals(2, remaining.size)
        assertTrue(remaining.any { it.content == "Old clip that is pinned" })
        assertTrue(remaining.any { it.content == "Fresh clip" })
        assertFalse(remaining.any { it.content == "Old clip to delete" })
    }

    @Test
    fun `update json parses version details and sha256 correctly`() {
        val jsonString = """
            {
              "versionCode": 2,
              "versionName": "1.1.0",
              "releaseNotes": "Performance improvements and bug fixes",
              "apkUrl": "https://github.com/aiwebnix-oss/ClipNest-App/releases/download/v1.1.0/ClipNest.apk",
              "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
            }
        """.trimIndent()

        val json = org.json.JSONObject(jsonString)
        val info = com.example.data.update.UpdateInfo(
            versionCode = json.getInt("versionCode"),
            versionName = json.getString("versionName"),
            releaseNotes = json.getString("releaseNotes"),
            apkUrl = json.getString("apkUrl"),
            sha256 = json.optString("sha256").ifBlank { null }
        )

        assertEquals(2, info.versionCode)
        assertEquals("1.1.0", info.versionName)
        assertEquals("Performance improvements and bug fixes", info.releaseNotes)
        assertEquals("https://github.com/aiwebnix-oss/ClipNest-App/releases/download/v1.1.0/ClipNest.apk", info.apkUrl)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", info.sha256)
    }
}
