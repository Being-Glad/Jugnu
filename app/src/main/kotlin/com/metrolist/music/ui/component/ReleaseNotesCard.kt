/**
 * Jugnu Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.constants.PureBlackKey
import com.metrolist.music.utils.Updater
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.ui.utils.glassCard

@Composable
fun ReleaseNotesCard() {
    val releaseInfo = Updater.getCachedLatestRelease() ?: return

    val isSystemDark = isSystemInDarkTheme()
    val pureBlack by rememberPreference(PureBlackKey, defaultValue = false)
    val isPureBlack = pureBlack && isSystemDark
    val shape = RoundedCornerShape(20.dp)

    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .then(
                if (isPureBlack) {
                    Modifier.background(Color.Black, shape)
                } else {
                    Modifier.glassCard(shape = shape)
                }
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.release_notes),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = releaseInfo.description,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}
