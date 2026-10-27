package io.github.project.vault.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class SmsMessage(
    val id: Long = 0,
    val address: String = "",
    val body: String = "",
    val date: Long = 0,
    val dateSent: Long = 0,
    val type: Int = 1, // 1 = Inbox, 2 = Sent, 3 = Draft, 4 = Outbox, 5 = Failed, 6 = Queued
    val read: Int = 1,
    val status: Int = -1,
    val threadId: Long = 0,
    val subId: Int = -1,
    val contactName: String? = null
)

@Immutable
@Serializable
data class CallLogEntry(
    val id: Long = 0,
    val number: String = "",
    val name: String? = null,
    val type: Int = 1, // 1 = Incoming, 2 = Outgoing, 3 = Missed, 4 = Voicemail, 5 = Rejected, 6 = Blocked
    val date: Long = 0,
    val duration: Long = 0, // In seconds
    val subId: Int = -1,
    val formattedNumber: String? = null
)

@Immutable
@Serializable
data class TelephonyArchiveMetadata(
    val backupTimestamp: Long,
    val smsCount: Int,
    val callCount: Int,
    val wifiCount: Int = 0,
    val archiveFileName: String,
    val archiveSizeBytes: Long = 0L,
    val deviceModel: String = "",
    val isEncrypted: Boolean = false
)

sealed class TelephonyProgress {
    data class BackingUp(val current: Int, val total: Int, val status: String) : TelephonyProgress()
    data class Restoring(val current: Int, val total: Int, val status: String) : TelephonyProgress()
    data class Completed(val message: String) : TelephonyProgress()
    data class Failed(val error: String) : TelephonyProgress()
}
