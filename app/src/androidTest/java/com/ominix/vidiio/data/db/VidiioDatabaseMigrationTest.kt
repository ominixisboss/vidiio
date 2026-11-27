package com.ominix.vidiio.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Replays every committed schema forward through [ALL_MIGRATIONS].
 *
 * When you bump the database version, add a `migrate(old, new)` case here. A missing
 * or wrong migration fails this test instead of wiping a user's library in the field.
 */
@RunWith(AndroidJUnit4::class)
class VidiioDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VidiioDatabase::class.java
    )

    /**
     * Upgrades the last committed schema with data already in it, then checks the rows survived
     * the table rebuild that gave My List and watch progress a per-profile key.
     */
    @Test
    @Throws(IOException::class)
    fun migratesFromLatestCommittedSchema() {
        helper.createDatabase(TEST_DB, PREVIOUS_VERSION).apply {
            execSQL("INSERT INTO `favorite_movies` (`id`, `title`, `posterUrl`, `synopsis`, `year`, `rating`, `type`, `source`, `profileId`, `addedAt`) VALUES ('tt1', 'Title', 'poster', NULL, 2020, 7.5, 'MOVIE', 'tmdb', 'default', 1)")
            execSQL("INSERT INTO `watch_progress` (`id`, `title`, `posterUrl`, `backdropUrl`, `synopsis`, `year`, `rating`, `type`, `source`, `imdbId`, `episodeId`, `episodeLabel`, `positionMs`, `durationMs`, `profileId`, `updatedAt`) VALUES ('tt1', 'Title', 'poster', NULL, NULL, 2020, 7.5, 'MOVIE', 'tmdb', NULL, NULL, NULL, 60000, 120000, 'default', 1)")
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            CURRENT_VERSION,
            /* validateDroppedTables = */ true,
            *ALL_MIGRATIONS
        )
        db.query("SELECT COUNT(*) FROM `favorite_movies` WHERE `profileId` = 'default'").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM `watch_progress` WHERE `profileId` = 'default'").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test"
        const val PREVIOUS_VERSION = 6
        const val CURRENT_VERSION = 7
    }
}
