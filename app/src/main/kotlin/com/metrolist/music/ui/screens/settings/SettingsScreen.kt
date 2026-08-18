package com.metrolist.music.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.metrolist.music.BuildConfig
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.component.ReleaseNotesCard
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.Updater

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    latestVersionName: String,
) {
    val context = LocalContext.current
    val isAndroid12OrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val hasUpdate = BuildConfig.UPDATER_AVAILABLE && latestVersionName != BuildConfig.VERSION_NAME

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
            )
        )

        // Ergonomic One-Handed Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
                modifier = Modifier.padding(end = 12.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = stringResource(R.string.back_button_desc),
                    modifier = Modifier.size(24.dp)
                )
            }

            Column {
                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${stringResource(R.string.app_name)} • ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        // 1. LOOK & FEEL UMBRELLA
        Material3SettingsGroup(
            title = stringResource(R.string.settings_section_look_and_feel),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.palette),
                    title = { Text(stringResource(R.string.appearance)) },
                    description = { Text(stringResource(R.string.settings_appearance_desc)) },
                    onClick = { navController.navigate("settings/appearance") }
                )
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 2. SOUND & PLAYBACK ENGINE UMBRELLA
        Material3SettingsGroup(
            title = stringResource(R.string.settings_section_audio_engine),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.play),
                    title = { Text(stringResource(R.string.player_and_audio)) },
                    description = { Text(stringResource(R.string.settings_player_desc)) },
                    onClick = { navController.navigate("settings/player") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.equalizer),
                    title = { Text(stringResource(R.string.equalizer)) },
                    description = { Text(stringResource(R.string.settings_equalizer_desc)) },
                    onClick = { navController.navigate("equalizer") }
                )
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 3. LYRICS & AI INTELLIGENCE UMBRELLA
        Material3SettingsGroup(
            title = stringResource(R.string.settings_section_lyrics_ai),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.translate),
                    title = { Text(stringResource(R.string.ai_lyrics_translation)) },
                    description = { Text(stringResource(R.string.settings_ai_lyrics_desc)) },
                    onClick = { navController.navigate("settings/ai") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.language),
                    title = { Text(stringResource(R.string.lyrics_romanize_title)) },
                    description = { Text(stringResource(R.string.settings_romanization_desc)) },
                    onClick = { navController.navigate("settings/content/romanization") }
                )
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 4. LIBRARY, CONTENT & STORAGE UMBRELLA
        Material3SettingsGroup(
            title = stringResource(R.string.settings_section_content_library),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.tune),
                    title = { Text(stringResource(R.string.content)) },
                    description = { Text(stringResource(R.string.settings_content_desc)) },
                    onClick = { navController.navigate("settings/content") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.storage),
                    title = { Text(stringResource(R.string.storage)) },
                    description = { Text(stringResource(R.string.settings_storage_desc)) },
                    onClick = { navController.navigate("settings/storage") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.restore),
                    title = { Text(stringResource(R.string.backup_restore)) },
                    description = { Text(stringResource(R.string.settings_backup_desc)) },
                    onClick = { navController.navigate("settings/backup_restore") }
                )
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 5. CONNECTED SERVICES & ECOSYSTEM UMBRELLA
        Material3SettingsGroup(
            title = stringResource(R.string.settings_section_integrations),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.link),
                    title = { Text(stringResource(R.string.integrations)) },
                    description = { Text(stringResource(R.string.settings_integrations_desc)) },
                    onClick = { navController.navigate("settings/integrations") }
                )
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 6. SYSTEM & ABOUT UMBRELLA
        Material3SettingsGroup(
            title = stringResource(R.string.settings_section_system),
            items = buildList {
                if (isAndroid12OrLater) {
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.link),
                            title = { Text(stringResource(R.string.default_links)) },
                            description = { Text(stringResource(R.string.settings_default_links_desc)) },
                            onClick = {
                                try {
                                    val intent = Intent(
                                        Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                                        "package:${context.packageName}".toUri()
                                    )
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        R.string.open_app_settings_error,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        )
                    )
                }
                if (BuildConfig.UPDATER_AVAILABLE) {
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.update),
                            title = { Text(stringResource(R.string.updater)) },
                            description = { Text(stringResource(R.string.settings_updater_desc)) },
                            showBadge = hasUpdate,
                            onClick = { navController.navigate("settings/updater") }
                        )
                    )
                }
                val showChangelog = com.metrolist.music.LocalChangelogState.current
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.newspaper),
                        title = { Text(stringResource(R.string.changelog)) },
                        description = { Text(stringResource(R.string.settings_changelog_desc)) },
                        onClick = { showChangelog.value = true }
                    )
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.security),
                        title = { Text(stringResource(R.string.privacy)) },
                        onClick = { navController.navigate("settings/privacy") }
                    )
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.info),
                        title = { Text(stringResource(R.string.about)) },
                        description = { Text(stringResource(R.string.settings_about_desc)) },
                        onClick = { navController.navigate("settings/about") }
                    )
                )
            }
        )

        if (hasUpdate) {
            Spacer(modifier = Modifier.height(16.dp))
            ReleaseNotesCard()
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
