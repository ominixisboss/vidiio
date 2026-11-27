package com.ominix.vidiio.data.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ominix.vidiio.data.model.DownloadStatus
import com.ominix.vidiio.data.model.DownloadTask
import com.ominix.vidiio.data.model.DownloadType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Downloads must never cross profiles: a Kids profile cannot see what Main saved. */
@RunWith(AndroidJUnit4::class)
class DownloadProfileScopeTest {

    @Test
    fun downloadsAreListedOnlyForTheirOwnProfile() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, VidiioDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.downloadDao()
            dao.insertDownload(
                DownloadTask(
                    title = "Main film",
                    url = "https://example.com/main.mp4",
                    type = DownloadType.HTTP,
                    status = DownloadStatus.COMPLETED,
                    profileId = "default",
                )
            )
            dao.insertDownload(
                DownloadTask(
                    title = "Kids film",
                    url = "https://example.com/kids.mp4",
                    type = DownloadType.HTTP,
                    status = DownloadStatus.COMPLETED,
                    profileId = "kids",
                )
            )

            assertEquals(
                listOf("Kids film"),
                dao.getAllDownloads("kids").first().map { it.title }
            )
            assertEquals(
                listOf("Main film"),
                dao.getAllDownloads("default").first().map { it.title }
            )
        } finally {
            db.close()
        }
    }
}
