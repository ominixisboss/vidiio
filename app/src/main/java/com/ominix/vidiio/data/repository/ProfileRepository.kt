package com.ominix.vidiio.data.repository

import com.ominix.vidiio.data.db.UserProfileDao
import com.ominix.vidiio.data.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID

class ProfileRepository(
    private val userProfileDao: UserProfileDao,
    private val settingsRepository: SettingsRepository
) {
    val allProfiles: Flow<List<UserProfile>> = userProfileDao.getAllProfiles()

    val activeProfileId: Flow<String> = settingsRepository.activeProfileIdFlow

    suspend fun getActiveProfile(): UserProfile {
        val currentId = settingsRepository.activeProfileIdFlow.first()
        val profile = userProfileDao.getProfileById(currentId)
        if (profile != null) return profile

        // Default profile if not found
        val main = UserProfile(
            id = "default",
            name = "Main Profile",
            avatarColorHex = 0xFFE50914,
            avatarIconName = "face_1",
            isKids = false,
            isDefault = true
        )
        userProfileDao.insertProfile(main)
        settingsRepository.setActiveProfileId("default")
        return main
    }

    suspend fun switchProfile(id: String) {
        settingsRepository.setActiveProfileId(id)
    }

    suspend fun createProfile(name: String, avatarColorHex: Long, isKids: Boolean): UserProfile {
        val newProfile = UserProfile(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "Profile" },
            avatarColorHex = avatarColorHex,
            avatarIconName = if (isKids) "face_kids" else "face_1",
            isKids = isKids,
            isDefault = false
        )
        userProfileDao.insertProfile(newProfile)
        switchProfile(newProfile.id)
        return newProfile
    }

    suspend fun updateProfile(profile: UserProfile) {
        userProfileDao.updateProfile(profile)
    }

    suspend fun deleteProfile(id: String) {
        val active = activeProfileId.first()
        userProfileDao.deleteProfile(id)
        if (active == id) {
            switchProfile("default")
        }
    }
}
