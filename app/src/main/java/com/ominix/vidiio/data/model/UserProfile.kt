package com.ominix.vidiio.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey val id: String,
    val name: String,
    val avatarColorHex: Long = 0xFFE50914,
    val avatarIconName: String = "face_1",
    val isKids: Boolean = false,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** How strict this kids profile is; ignored for adult profiles. See [KidsMaturity]. */
    val kidsMaturity: Int = KidsMaturity.OLDER.dbValue,
)
