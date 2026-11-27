package com.ominix.vidiio.data.repository

import com.ominix.vidiio.data.db.UserProfileDao
import com.ominix.vidiio.data.model.KidsMaturity
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

    /**
     * When a correct parent PIN was last entered, so one prompt covers switching, creating and
     * editing without asking again for every tap. Expires, so a PIN entry cannot be reused later.
     */
    @Volatile
    private var unlockedAt = 0L

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

    suspend fun hasParentPin(): Boolean = settingsRepository.parentPinHashFlow.first() != null

    /**
     * True when profiles may be managed from the active Kids profile: no PIN is configured, or
     * the PIN was entered within the last few minutes. Adult profiles never need the PIN.
     */
    private suspend fun canManageProfiles(): Boolean {
        if (!getActiveProfile().isKids) return true
        if (!hasParentPin()) return true
        return System.currentTimeMillis() - unlockedAt in 0 until UNLOCK_WINDOW_MS
    }

    /** Checks a parent PIN and, when correct, opens profile management for a short while. */
    suspend fun unlockProfileManagement(pin: String): Boolean {
        if (!getActiveProfile().isKids || !hasParentPin()) return true
        val valid = settingsRepository.verifyParentPin(pin)
        if (valid) unlockedAt = System.currentTimeMillis()
        return valid
    }

    /** Switching out of a locked Kids profile needs [unlockProfileManagement] first. */
    suspend fun switchProfile(id: String): Boolean {
        if (!canManageProfiles()) return false
        settingsRepository.setActiveProfileId(id)
        return true
    }

    /** Null when profile management is locked; the profile is not created in that case. */
    suspend fun createProfile(name: String, avatarColorHex: Long, isKids: Boolean): UserProfile? {
        if (!canManageProfiles()) return null
        val newProfile = UserProfile(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "Profile" },
            avatarColorHex = avatarColorHex,
            avatarIconName = if (isKids) "face_kids" else "face_1",
            isKids = isKids,
            isDefault = false,
            kidsMaturity = KidsMaturity.OLDER.dbValue
        )
        userProfileDao.insertProfile(newProfile)
        // Already authorized above; calling switchProfile again would only re-check the gate.
        settingsRepository.setActiveProfileId(newProfile.id)
        return newProfile
    }

    /** Changing a profile (including a Kids level) also needs the gate when Kids is active. */
    suspend fun updateProfile(profile: UserProfile): Boolean {
        if (!canManageProfiles()) return false
        userProfileDao.updateProfile(profile)
        return true
    }

    suspend fun deleteProfile(id: String): Boolean {
        if (!canManageProfiles()) return false
        val active = activeProfileId.first()
        userProfileDao.deleteProfile(id)
        if (active == id) {
            settingsRepository.setActiveProfileId("default")
        }
        return true
    }

    private companion object {
        /** Long enough to change a setting after unlocking, short enough not to outlive the parent. */
        const val UNLOCK_WINDOW_MS = 5 * 60 * 1000L
    }
}
