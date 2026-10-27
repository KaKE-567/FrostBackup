package io.github.project.vault.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.project.vault.core.backup.BackupProgress
import io.github.project.vault.core.backup.BackupSubStep
import io.github.project.vault.core.restore.RestoreProgress
import io.github.project.vault.core.restore.RestoreSubStep
import io.github.project.vault.data.model.AppInfo

enum class ItemCheckStatus {
    PENDING,
    ACTIVE,
    COMPLETED
}

data class ChecklistItem(
    val title: String,
    val subtitle: String = "",
    val status: ItemCheckStatus
)

/**
 * DataBackup-style Execution Flow for Backup (insp1.png and insp2.png)
 */
@Composable
fun BackupExecutionFlow(
    queuedApps: List<AppInfo>,
    isProcessing: Boolean,
    progress: BackupProgress?,
    onStartProcessing: () -> Unit,
    onDismiss: () -> Unit
) {
    val isFinished = progress?.isFinished == true
    val isFailed = progress?.isFailed == true

    BackHandler(enabled = true) {
        if (!isProcessing || isFinished || isFailed) {
            onDismiss()
        }
    }

    val stepOrder = listOf(
        BackupSubStep.PREPARING,
        BackupSubStep.APK,
        BackupSubStep.USER,
        BackupSubStep.USER_DE,
        BackupSubStep.DATA,
        BackupSubStep.SSAID,
        BackupSubStep.KEYSTORE,
        BackupSubStep.COMPRESSING,
        BackupSubStep.ENCRYPTING,
        BackupSubStep.UPLOAD,
        BackupSubStep.DONE
    )

    fun statusFor(step: BackupSubStep): ItemCheckStatus {
        if (isFinished) return ItemCheckStatus.COMPLETED
        if (progress == null) return ItemCheckStatus.PENDING
        val itemIndex = stepOrder.indexOf(step)
        val currentIndex = stepOrder.indexOf(progress.subStep)
        return when {
            currentIndex > itemIndex -> ItemCheckStatus.COMPLETED
            currentIndex == itemIndex -> ItemCheckStatus.ACTIVE
            else -> ItemCheckStatus.PENDING
        }
    }

    val checklist = listOf(
        ChecklistItem("Backup APK", "App installation binaries", statusFor(BackupSubStep.APK)),
        ChecklistItem("Backup USER", "Internal app data & databases", statusFor(BackupSubStep.USER)),
        ChecklistItem("Backup USER_DE", "Device-encrypted app data", statusFor(BackupSubStep.USER_DE)),
        ChecklistItem("Backup DATA", "External storage & media", statusFor(BackupSubStep.DATA)),
        ChecklistItem("Backup SSAID", "Android device login identifier", statusFor(BackupSubStep.SSAID)),
        ChecklistItem("Backup Keystore", "Cryptographic authentication keys", statusFor(BackupSubStep.KEYSTORE))
    )

    val currentApp = progress?.currentApp ?: queuedApps.firstOrNull()?.appName ?: "Backup"
    val currentPackageName = progress?.currentPackageName ?: queuedApps.firstOrNull()?.packageName ?: ""
    val currentStepText = progress?.subStep?.label ?: "Backup USER"
    val progressFraction = progress?.progressFraction ?: 0f
    val appIndex = progress?.appIndex ?: 1
    val totalApps = progress?.totalApps ?: queuedApps.size.coerceAtLeast(1)

    ExecutionFlowSurface(
        operationType = "Backup",
        queuedApps = queuedApps,
        isProcessing = isProcessing,
        isFinished = isFinished,
        isFailed = isFailed,
        errorMessage = progress?.errorMessage,
        currentApp = currentApp,
        currentPackageName = currentPackageName,
        currentStepText = currentStepText,
        progressFraction = progressFraction,
        appIndex = appIndex,
        totalApps = totalApps,
        checklist = checklist,
        onActionClick = {
            if (!isProcessing) {
                onStartProcessing()
            } else if (isFinished || isFailed) {
                onDismiss()
            }
        },
        onDismiss = onDismiss
    )
}

/**
 * DataBackup-style Execution Flow for Restore
 */
@Composable
fun RestoreExecutionFlow(
    progress: RestoreProgress,
    onDismiss: () -> Unit
) {
    val isFinished = progress.isFinished
    val isFailed = progress.isFailed

    BackHandler(enabled = true) {
        if (isFinished || isFailed) {
            onDismiss()
        }
    }

    val stepOrder = listOf(
        RestoreSubStep.PREPARING,
        RestoreSubStep.DECRYPTING,
        RestoreSubStep.UNPACKING,
        RestoreSubStep.APK,
        RestoreSubStep.USER,
        RestoreSubStep.USER_DE,
        RestoreSubStep.DATA,
        RestoreSubStep.SSAID,
        RestoreSubStep.KEYSTORE,
        RestoreSubStep.PERMISSIONS,
        RestoreSubStep.DONE
    )

    fun statusFor(step: RestoreSubStep): ItemCheckStatus {
        if (isFinished) return ItemCheckStatus.COMPLETED
        val itemIndex = stepOrder.indexOf(step)
        val currentIndex = stepOrder.indexOf(progress.subStep)
        return when {
            currentIndex > itemIndex -> ItemCheckStatus.COMPLETED
            currentIndex == itemIndex -> ItemCheckStatus.ACTIVE
            else -> ItemCheckStatus.PENDING
        }
    }

    val checklist = listOf(
        ChecklistItem("Restore APK", "Reinstalling base & split APKs", statusFor(RestoreSubStep.APK)),
        ChecklistItem("Restore USER", "Internal app databases & tokens", statusFor(RestoreSubStep.USER)),
        ChecklistItem("Restore USER_DE", "Device-encrypted local storage", statusFor(RestoreSubStep.USER_DE)),
        ChecklistItem("Restore DATA", "External Android storage files", statusFor(RestoreSubStep.DATA)),
        ChecklistItem("Restore SSAID", "Restoring unique device identifier", statusFor(RestoreSubStep.SSAID)),
        ChecklistItem("Restore Keystore", "Cryptographic key migration", statusFor(RestoreSubStep.KEYSTORE)),
        ChecklistItem("Apply Permissions", "Enforcing UID and SELinux contexts", statusFor(RestoreSubStep.PERMISSIONS))
    )

    ExecutionFlowSurface(
        operationType = "Restore",
        queuedApps = emptyList(),
        isProcessing = true,
        isFinished = isFinished,
        isFailed = isFailed,
        errorMessage = progress.errorMessage,
        currentApp = progress.currentApp,
        currentPackageName = progress.currentPackageName,
        currentStepText = progress.subStep.label,
        progressFraction = progress.progressFraction,
        appIndex = progress.appIndex,
        totalApps = progress.totalApps,
        checklist = checklist,
        onActionClick = {
            if (isFinished || isFailed) {
                onDismiss()
            }
        },
        onDismiss = onDismiss
    )
}

@Composable
private fun ExecutionFlowSurface(
    operationType: String,
    queuedApps: List<AppInfo>,
    isProcessing: Boolean,
    isFinished: Boolean,
    isFailed: Boolean,
    errorMessage: String?,
    currentApp: String,
    currentPackageName: String,
    currentStepText: String,
    progressFraction: Float,
    appIndex: Int,
    totalApps: Int,
    checklist: List<ChecklistItem>,
    onActionClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "HeroTransition")
    val hourglassRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "HourglassRotation"
    )

    val animatedAppFraction by animateFloatAsState(
        targetValue = progressFraction.coerceIn(0f, 1f),
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "AppProgress"
    )

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    enabled = !isProcessing || isFinished || isFailed
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (!isProcessing || isFinished || isFailed) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = when {
                        isFailed -> "Failed"
                        isFinished -> "Completed"
                        !isProcessing -> operationType
                        else -> "Processing"
                    },
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Two-Stage Segmented Progress Bar (Preprocessing | Processing)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Segment 1: Preprocessing
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }

                // Segment 2: Processing
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    if (isProcessing) {
                        if (isFinished) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        } else {
                            val queueFraction = if (totalApps > 0) {
                                val base = (appIndex - 1).toFloat() / totalApps.toFloat()
                                val stepPortion = (animatedAppFraction / totalApps.toFloat())
                                (base + stepPortion).coerceIn(0f, 1f)
                            } else animatedAppFraction

                            LinearProgressIndicator(
                                progress = { queueFraction },
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.Transparent,
                                strokeCap = StrokeCap.Round
                            )
                        }
                    }
                }
            }

            // Body Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(28.dp))

                // Hero Center Circle (116.dp)
                Box(
                    modifier = Modifier.size(116.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isFinished -> {
                            Box(
                                modifier = Modifier
                                    .size(104.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(54.dp)
                                )
                            }
                        }

                        !isProcessing -> {
                            // Preprocessing Screen Hero Circle (insp1.png)
                            Box(
                                modifier = Modifier
                                    .size(108.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                                    .border(
                                        BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HourglassEmpty,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .rotate(hourglassRotation)
                                )
                            }
                        }

                        else -> {
                            // Processing Screen Hero Circle (insp2.png)
                            CircularProgressIndicator(
                                progress = { animatedAppFraction },
                                modifier = Modifier.size(116.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                strokeWidth = 5.dp,
                                strokeCap = StrokeCap.Round
                            )

                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (currentPackageName.isNotBlank()) {
                                    AppIcon(
                                        packageName = currentPackageName,
                                        size = 58.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.HourglassEmpty,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(42.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Hero Titles
                Text(
                    text = when {
                        isFailed -> "Error Occurred"
                        isFinished -> "Done"
                        !isProcessing -> "Preprocessing"
                        else -> currentApp.ifBlank { "Processing" }
                    },
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = when {
                        isFailed -> errorMessage ?: "Operation failed"
                        isFinished -> "All operations completed successfully"
                        !isProcessing -> "Necessary preparations"
                        else -> currentStepText.ifBlank { "Executing task…" }
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = if (isFinished || !isProcessing) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Elevated Component Detail Card
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    if (!isProcessing) {
                        // Preprocessing App List (insp1.png style)
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(queuedApps, key = { it.packageName }) { app ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppIcon(packageName = app.packageName, size = 32.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = app.appName,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    } else {
                        // Processing App Details + Checklist (insp2.png style)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            // Active App Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (currentPackageName.isNotBlank()) {
                                    AppIcon(
                                        packageName = currentPackageName,
                                        size = 36.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentApp.ifBlank { "Application" },
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (currentPackageName.isNotBlank()) {
                                        Text(
                                            text = currentPackageName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                ) {
                                    Text(
                                        text = "$appIndex / $totalApps",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            // Checklist of Steps
                            checklist.forEach { item ->
                                ChecklistItemRow(item = item)
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }

            // Bottom Full-Width Rounded Pill Button (insp1.png and insp2.png)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Button(
                    onClick = onActionClick,
                    enabled = !isProcessing || isFinished || isFailed,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Text(
                        text = if (isFailed) "Dismiss" else "Continue",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChecklistItemRow(item: ChecklistItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (item.status) {
            ItemCheckStatus.COMPLETED -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            ItemCheckStatus.ACTIVE -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            ItemCheckStatus.PENDING -> {
                Icon(
                    imageVector = Icons.Outlined.Circle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (item.status == ItemCheckStatus.ACTIVE) FontWeight.Bold else FontWeight.Normal
                ),
                color = when (item.status) {
                    ItemCheckStatus.COMPLETED -> MaterialTheme.colorScheme.onSurface
                    ItemCheckStatus.ACTIVE -> MaterialTheme.colorScheme.primary
                    ItemCheckStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                }
            )
            if (item.subtitle.isNotBlank() && item.status == ItemCheckStatus.ACTIVE) {
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
