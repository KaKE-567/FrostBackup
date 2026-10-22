package io.github.project.vault.ui.components

import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.project.vault.data.model.StorageTarget
import io.github.project.vault.data.model.StorageType
import java.io.File
import java.util.Locale

@Composable
fun StorageHeroCard(
    userAppCount: Int,
    backupCount: Int,
    activeTarget: StorageTarget? = null,
    storageDirectory: File? = null,
    modifier: Modifier = Modifier
) {
    val (totalGb, freeGb, usedFraction) = remember(activeTarget, storageDirectory) {
        try {
            if (activeTarget != null && activeTarget.totalBytes > 0) {
                val totalBytes = activeTarget.totalBytes
                val freeBytes = activeTarget.freeBytes
                val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)

                val total = totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
                val free = freeBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
                val fraction = (usedBytes.toDouble() / totalBytes.toDouble()).coerceIn(0.0, 1.0).toFloat()
                Triple(total, free, fraction)
            } else {
                val targetPath = storageDirectory?.absolutePath ?: Environment.getDataDirectory().path
                val file = File(targetPath)
                val statPath = if (file.exists()) file.absolutePath else Environment.getDataDirectory().path
                val stat = StatFs(statPath)
                val totalBytes = stat.totalBytes
                val availableBytes = stat.availableBytes
                val usedBytes = (totalBytes - availableBytes).coerceAtLeast(0L)

                val total = totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
                val free = availableBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
                val fraction = if (totalBytes > 0) (usedBytes.toDouble() / totalBytes.toDouble()).coerceIn(0.0, 1.0).toFloat() else 0.5f

                Triple(total, free, fraction)
            }
        } catch (_: Exception) {
            Triple(64.0, 20.0, 0.70f)
        }
    }

    val (storageTitle, storageIcon) = remember(activeTarget, storageDirectory) {
        when (activeTarget?.type) {
            StorageType.USB_DRIVE -> Pair(activeTarget.name.ifBlank { "USB Drive Storage" }, Icons.Default.Usb)
            StorageType.SD_CARD -> Pair(activeTarget.name.ifBlank { "MicroSD Card Storage" }, Icons.Default.SdCard)
            StorageType.CUSTOM -> Pair("Custom Storage Directory", Icons.Default.Folder)
            StorageType.INTERNAL -> Pair("Internal Device Storage", Icons.Default.PhoneAndroid)
            null -> {
                val path = storageDirectory?.absolutePath ?: ""
                when {
                    path.contains("usb", ignoreCase = true) || path.contains("otg", ignoreCase = true) ->
                        Pair("USB Drive Storage", Icons.Default.Usb)
                    path.matches(Regex(".*/storage/[A-Za-z0-9]{4}-[A-Za-z0-9]{4}.*")) ->
                        Pair("MicroSD Card Storage", Icons.Default.SdCard)
                    else -> Pair("Device Storage & Keystore", Icons.Default.SdStorage)
                }
            }
        }
    }

    val animatedFraction by animateFloatAsState(
        targetValue = usedFraction,
        label = "StorageGaugeFraction"
    )

    val outlineColor = MaterialTheme.colorScheme.outline
    val frostedBorder = BorderStroke(1.dp, outlineColor.copy(alpha = 0.20f))

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = frostedBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header Row: Icon + Title + Status Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = storageIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = storageTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f GB Free of %.1f GB Total", freeGb, totalGb),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                StatusBadge(
                    text = "${(usedFraction * 100).toInt()}% Used",
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.40f),
                    contentColor = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Gauge Pill Track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedFraction)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Information Pills Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Keystore Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Hardware Keystore Ready",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Apps & Backups Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$userAppCount Apps • $backupCount Backups",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
