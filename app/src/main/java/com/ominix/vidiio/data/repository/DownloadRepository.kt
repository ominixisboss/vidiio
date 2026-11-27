package com.ominix.vidiio.data.repository

import com.ominix.vidiio.data.db.DownloadDao
import com.ominix.vidiio.data.model.DownloadStatus
import com.ominix.vidiio.data.model.DownloadTask
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest

/** Downloads belong to the profile that queued them, so Kids never sees Main's files. */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadRepository(
    private val downloadDao: DownloadDao,
    private val settingsRepository: SettingsRepository,
) {
    val allDownloads: Flow<List<DownloadTask>> = settingsRepository.activeProfileIdFlow
        .distinctUntilChanged()
        .flatMapLatest { profileId -> downloadDao.getAllDownloads(profileId) }

    suspend fun getDownloadById(id: Long): DownloadTask? = downloadDao.getDownloadById(id)

    suspend fun getDownloadByUrl(url: String): DownloadTask? = downloadDao.getDownloadByUrl(url)

    fun getDownloadByUrlFlow(url: String): Flow<DownloadTask?> = downloadDao.getDownloadByUrlFlow(url)

    suspend fun getActiveDownloads(): List<DownloadTask> = downloadDao.getActiveDownloads()

    suspend fun insertDownload(task: DownloadTask): Long = downloadDao.insertDownload(task)

    suspend fun updateDownload(task: DownloadTask) = downloadDao.updateDownload(task)

    suspend fun deleteDownload(task: DownloadTask) = downloadDao.deleteDownload(task)

    suspend fun deleteDownloadById(id: Long) = downloadDao.deleteDownloadById(id)

    suspend fun updateProgress(id: Long, status: DownloadStatus, progress: Float, downloadedSize: Long) {
        downloadDao.updateProgress(id, status, progress, downloadedSize)
    }

    suspend fun pauseAllDownloading() = downloadDao.pauseAllDownloading()
}
