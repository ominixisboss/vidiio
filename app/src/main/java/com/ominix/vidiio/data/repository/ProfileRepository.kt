package com.ominix.vidiio.data.repository

import com.ominix.vidiio.data.db.UserProfileDao
import com.ominix.vidiio.data.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import java.util.UUID

class ProfileRepository(
    private val userProfileDao: UserProfileDao,
    private val settingsRepository: SettingsRepository
) {
    // The profile the app starts on is a row like any other. It used to exist only as a fallback
    // until a second profile was created, which then hid it and made switching back impossible.
    private val defaultProfile = UserProfile(
        id = "default",
        name = "Main Profile",
        avatarColorHex = 0xFFE50914,
        avatarIconName = "face_1",
        isKids = false,
        isDefault = true
    )

    val allProfiles: Flow<List<UserProfile>> = userProfileDao.getAllProfiles()
        .onStart {
            if (userProfileDao.getProfileById(defaultProfile.id) == null) {
                userProfileDao.insertProfile(defaultProfile)
            }
        }

    val activeProfileId: Flow<String> = settingsRepository.activeProfileIdFlow

    suspend fun getActiveProfile(): UserProfile {
        val currentId = settingsRepository.activeProfileIdFlow.first()
        val profile = userProfileDao.getProfileById(currentId)
        if (profile != null) return profile

        userProfileDao.insertProfile(defaultProfile)
        settingsRepository.setActiveProfileId(defaultProfile.id)
        return defaultProfile
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
