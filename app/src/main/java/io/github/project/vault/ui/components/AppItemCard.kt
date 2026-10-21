package io.github.project.vault.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.project.vault.data.model.AppInfo

@Composable
fun AppItemCard(
    app: AppInfo,
    isSelected: Boolean,
    onSelectionChanged: (Boolean) -> Unit,
    onBackupClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "ExpandChevronRotation"
    )

    val outlineColor = MaterialTheme.colorScheme.outline
    val frostedBorder = BorderStroke(
        1.dp,
        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.50f)
        else outlineColor.copy(alpha = 0.18f)
    )

    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = frostedBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onSelectionChanged(!isSelected) }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Main Top Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectionChanged(it) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.outline
                    )
                )

                Spacer(modifier = Modifier.width(6.dp))

                // High-Res Native App Icon
                AppIcon(
                    packageName = app.packageName,
                    size = 46.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Title, Package, Badges
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (app.appSizeFormatted.isNotEmpty()) "v${app.versionName} • ${app.appSizeFormatted}" else "v${app.versionName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (app.hasBackup) {
                            StatusBadge(
                                text = "Backed Up",
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (app.isSystemApp) {
                            StatusBadge(
                                text = "System",
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                // Expand / Collapse Accordion Icon Button
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(rotationAngle)
                    )
                }
            }

            // Expandable Detail Accordion
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    HorizontalDivider(
                        color = outlineColor.copy(alpha = 0.12f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    // Component Breakdown Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ComponentPill(icon = Icons.Default.Layers, label = "Split APK")
                        ComponentPill(icon = Icons.Default.Storage, label = "Internal Data")
                        ComponentPill(icon = Icons.Default.Key, label = "Keystore")
                        ComponentPill(icon = Icons.Default.Folder, label = "External")
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Package details & Single Backup button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "UID: ${app.uid}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Button(
                            onClick = onBackupClicked,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Backup Now", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComponentPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
