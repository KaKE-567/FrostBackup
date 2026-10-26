package io.github.project.vault.ui.components

import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                val internalPath = Environment.getExternalStorageDirectory().absolutePath
                val targetPath = storageDirectory?.absolutePath ?: internalPath
                val statPath = if (targetPath.startsWith(internalPath) || targetPath.startsWith("/storage/emulated/0")) {
                    targetPath
                } else if (File(targetPath).exists() && (activeTarget != null || !targetPath.contains("/storage/"))) {
                    targetPath
                } else {
                    internalPath
                }
                val stat = StatFs(statPath)
                val totalBytes = stat.totalBytes
                val availableBytes = stat.availableBytes
                val usedBytes = (totalBytes - availableBytes).coerceAtLeast(0L)

                val total = totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
                val free = availableBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
                val fraction = (usedBytes.toDouble() / totalBytes.toDouble()).coerceIn(0.0, 1.0).toFloat()
                Triple(total, free, fraction)
            }
        } catch (_: Exception) {
            Triple(128.0, 64.0, 0.50f)
        }
    }

    val usedGb = (totalGb - freeGb).coerceAtLeast(0.0)
    val usedPercentage = (usedFraction * 100).toInt()

    val (storageTitle, storageIcon) = remember(activeTarget, storageDirectory) {
        if (activeTarget != null) {
            when (activeTarget.type) {
                StorageType.USB_DRIVE -> Pair(activeTarget.name.ifBlank { "USB Drive" }, Icons.Default.Usb)
                StorageType.SD_CARD -> Pair(activeTarget.name.ifBlank { "MicroSD Card" }, Icons.Default.SdCard)
                StorageType.CUSTOM -> Pair("Custom Storage", Icons.Default.Folder)
                StorageType.INTERNAL -> Pair("Internal storage", Icons.Default.PhoneAndroid)
            }
        } else {
            val path = storageDirectory?.absolutePath ?: ""
            if (path.contains("emulated/0") || path.contains("/sdcard")) {
                Pair("Internal storage", Icons.Default.PhoneAndroid)
            } else {
                Pair("Backup Storage", Icons.Default.Folder)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Section Header (DataBackup style)
        Text(
            text = "Overview",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        // Card 1: Last backup / Status (DataBackup style pastel container)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Backup status",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "$backupCount apps backed up",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "$userAppCount user applications installed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Card 2: Storage overview (DataBackup style with progress bar & byte stats)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = storageIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Storage",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = storageTitle,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Clean Material 3 Linear Progress
                val animatedProgress by animateFloatAsState(
                    targetValue = usedFraction,
                    label = "storageProgress"
                )

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    strokeCap = StrokeCap.Round
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "$usedPercentage% used (${String.format(Locale.US, "%.1f", usedGb)} GB / ${String.format(Locale.US, "%.1f", totalGb)} GB)",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
