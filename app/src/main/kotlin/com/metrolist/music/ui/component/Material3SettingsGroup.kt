/**
 * Jugnu Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.ui.utils.glassCard
import androidx.compose.material3.HorizontalDivider

/**
 * A seamless, borderless Niagara-style settings group component
 * @param title The title of the settings group
 * @param items List of settings items to display
 */
@Composable
fun Material3SettingsGroup(
    title: String? = null,
    items: List<Material3SettingsItem>,
    useLowContrast: Boolean = false
) {
    if (items.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Section header title
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 4.dp)
            )
        }

        // Seamless, boxless list items
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            items.forEachIndexed { index, item ->
                Material3SettingsItemRow(item = item)
                if (index < items.size - 1) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f),
                        modifier = Modifier.padding(start = 68.dp, end = 12.dp)
                    )
                }
            }
        }
    }
}

/**
 * Individual settings item row with Material 3 styling
 */
@Composable
private fun Material3SettingsItemRow(
    item: Material3SettingsItem
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                enabled = item.enabled && item.onClick != null,
                onClick = { item.onClick?.invoke() }
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Custom leading content or Icon with subtle background
        if (item.leadingContent != null) {
            item.leadingContent.invoke()
            Spacer(modifier = Modifier.width(16.dp))
        } else if (item.icon != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (item.isHighlighted)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (item.showBadge) {
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        }
                    ) {
                        Icon(
                            painter = item.icon,
                            contentDescription = null,
                            tint = if (!item.enabled)
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            else if (item.isHighlighted)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    Icon(
                        painter = item.icon,
                        contentDescription = null,
                        tint = if (!item.enabled)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        else if (item.isHighlighted)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))
        }

        // Title and description
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Title content
            ProvideTextStyle(
                MaterialTheme.typography.titleMedium.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    fontSize = 16.sp,
                    color = if (!item.enabled) 
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            ) {
                item.title()
            }

            // Description if provided
            item.description?.let { desc ->
                Spacer(modifier = Modifier.height(2.dp))
                ProvideTextStyle(
                    MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        color = if (!item.enabled)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                ) {
                    desc()
                }
            }
        }

        // Trailing content or navigation chevron
        if (item.trailingContent != null) {
            Spacer(modifier = Modifier.width(8.dp))
            item.trailingContent.invoke()
        } else if (item.onClick != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                painter = painterResource(com.metrolist.music.R.drawable.navigate_next),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Data class for Material 3 settings item
 */
data class Material3SettingsItem(
    val icon: Painter? = null,
    val leadingContent: (@Composable () -> Unit)? = null,
    val title: @Composable () -> Unit,
    val description: (@Composable () -> Unit)? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
    val showBadge: Boolean = false,
    val isHighlighted: Boolean = false,
    val enabled: Boolean = true,
    val onClick: (() -> Unit)? = null
)
