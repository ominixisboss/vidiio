package com.ominix.vidiio.data.repository

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * A recovery code is the parent's fallback when the four-digit PIN is forgotten. It must work
 * on a TV remote as well as a phone, so it is a short, unambiguous, hand-writable code rather
 * than an account or a device credential.
 */
internal const val PARENT_RECOVERY_CODE_LENGTH = 12

/** No 0/O or 1/I: a written code should never be ambiguous. 32 symbols, 12 chars = 60 bits. */
private const val RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

internal fun generateParentRecoveryCode(random: SecureRandom = SecureRandom()): String =
    buildString(PARENT_RECOVERY_CODE_LENGTH) {
        repeat(PARENT_RECOVERY_CODE_LENGTH) {
            append(RECOVERY_ALPHABET[random.nextInt(RECOVERY_ALPHABET.length)])
        }
    }

/** Accepts lower case and the displayed dashes, so typing a written code just works. */
internal fun normalizeParentRecoveryCode(input: String): String =
    input.uppercase().filter { it in RECOVERY_ALPHABET }

/** Displays four-character groups; hashing always uses [normalizeParentRecoveryCode]. */
internal fun formatParentRecoveryCode(code: String): String =
    normalizeParentRecoveryCode(code).chunked(4).joinToString("-")

internal fun hashParentRecoveryCode(code: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest("vidiio-parent-recovery:${normalizeParentRecoveryCode(code)}".toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
