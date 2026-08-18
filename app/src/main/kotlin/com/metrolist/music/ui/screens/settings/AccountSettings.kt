/**
 * Jugnu Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.music.BuildConfig
import com.metrolist.music.R
import com.metrolist.music.constants.AccountChannelHandleKey
import com.metrolist.music.constants.AccountEmailKey
import com.metrolist.music.constants.AccountNameKey
import com.metrolist.music.constants.DataSyncIdKey
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.constants.UseLoginForBrowse
import com.metrolist.music.constants.VisitorDataKey
import com.metrolist.music.constants.YtmSyncKey
import com.metrolist.music.ui.component.DefaultDialog
import com.metrolist.music.ui.component.InfoLabel
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.component.TextFieldDialog
import com.metrolist.music.utils.Updater
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.AccountSettingsViewModel
import com.metrolist.music.viewmodels.HomeViewModel
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun AccountSettings(
    navController: NavController,
    onClose: () -> Unit,
    latestVersionName: String
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val (accountNamePref, onAccountNameChange) = rememberPreference(AccountNameKey, "")
    val (accountEmail, onAccountEmailChange) = rememberPreference(AccountEmailKey, "")
    val (accountChannelHandle, onAccountChannelHandleChange) = rememberPreference(AccountChannelHandleKey, "")
    val (innerTubeCookie, onInnerTubeCookieChange) = rememberPreference(InnerTubeCookieKey, "")
    val (visitorData, onVisitorDataChange) = rememberPreference(VisitorDataKey, "")
    val (dataSyncId, onDataSyncIdChange) = rememberPreference(DataSyncIdKey, "")

    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }
    val (useLoginForBrowse, onUseLoginForBrowseChange) = rememberPreference(UseLoginForBrowse, true)
    val (ytmSync, onYtmSyncChange) = rememberPreference(YtmSyncKey, true)

    val homeViewModel: HomeViewModel = hiltViewModel()
    val accountSettingsViewModel: AccountSettingsViewModel = hiltViewModel()
    val accountName by homeViewModel.accountName.collectAsStateWithLifecycle()
    val accountImageUrl by homeViewModel.accountImageUrl.collectAsStateWithLifecycle()

    var showToken by remember { mutableStateOf(false) }
    var showTokenEditor by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .background(Color.Transparent)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                ),
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.close), contentDescription = null)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Logout confirmation dialog
        if (showLogoutDialog) {
            DefaultDialog(
                onDismiss = { showLogoutDialog = false },
                title = { Text(stringResource(R.string.logout_dialog_title)) },
                content = {
                    Text(
                        text = stringResource(R.string.logout_dialog_message),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                },
                buttons = {
                    TextButton(
                        onClick = {
                            Timber.d("[LOGOUT_CLEAR] User chose to clear data")
                            scope.launch {
                                Timber.d("[LOGOUT_CLEAR] Starting clear and logout process")
                                accountSettingsViewModel.clearAllLibraryData()
                                Timber.d("[LOGOUT_CLEAR] Library data cleared, now logging out")
                                accountSettingsViewModel.logoutKeepData(context, onInnerTubeCookieChange)
                                Timber.d("[LOGOUT_CLEAR] Logout complete")
                                showLogoutDialog = false
                                onClose()
                            }
                        }
                    ) {
                        Text(stringResource(R.string.logout_clear))
                    }
                    TextButton(
                        onClick = {
                            Timber.d("[LOGOUT_KEEP] User chose to keep data")
                            scope.launch {
                                Timber.d("[LOGOUT_KEEP] Starting logout process (keeping data)")
                                accountSettingsViewModel.logoutKeepData(context, onInnerTubeCookieChange)
                                Timber.d("[LOGOUT_KEEP] Logout complete")
                                showLogoutDialog = false
                                onClose()
                            }
                        }
                    ) {
                        Text(stringResource(R.string.logout_keep))
                    }
                }
            )
        }

        if (showToken) {
            val text = """
                ***INNERTUBE COOKIE*** =$innerTubeCookie
                ***VISITOR DATA*** =$visitorData
                ***DATASYNC ID*** =$dataSyncId
                ***ACCOUNT NAME*** =$accountNamePref
                ***ACCOUNT EMAIL*** =$accountEmail
                ***ACCOUNT CHANNEL HANDLE*** =$accountChannelHandle
            """.trimIndent()

            DefaultDialog(
                onDismiss = { showToken = false },
                title = { Text(stringResource(R.string.token_shown)) },
                buttons = {
                    TextButton(
                        onClick = {
                            showToken = false
                            showTokenEditor = true
                        }
                    ) {
                        Text(stringResource(R.string.edit))
                    }
                    TextButton(
                        onClick = {
                            showToken = false
                        }
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            ) {
                Text(
                    text = text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (showTokenEditor) {
            val text = """
                ***INNERTUBE COOKIE*** =$innerTubeCookie
                ***VISITOR DATA*** =$visitorData
                ***DATASYNC ID*** =$dataSyncId
                ***ACCOUNT NAME*** =$accountNamePref
                ***ACCOUNT EMAIL*** =$accountEmail
                ***ACCOUNT CHANNEL HANDLE*** =$accountChannelHandle
            """.trimIndent()

            TextFieldDialog(
                initialTextFieldValue = TextFieldValue(text),
                onDone = { data ->
                    var cookie = ""
                    var visitorDataValue = ""
                    var dataSyncIdValue = ""
                    var accountNameValue = ""
                    var accountEmailValue = ""
                    var accountChannelHandleValue = ""

                    data.split("\n").forEach {
                        when {
                            it.startsWith("***INNERTUBE COOKIE*** =") -> cookie = it.substringAfter("=")
                            it.startsWith("***VISITOR DATA*** =") -> visitorDataValue = it.substringAfter("=")
                            it.startsWith("***DATASYNC ID*** =") -> dataSyncIdValue = it.substringAfter("=")
                            it.startsWith("***ACCOUNT NAME*** =") -> accountNameValue = it.substringAfter("=")
                            it.startsWith("***ACCOUNT EMAIL*** =") -> accountEmailValue = it.substringAfter("=")
                            it.startsWith("***ACCOUNT CHANNEL HANDLE*** =") -> accountChannelHandleValue = it.substringAfter("=")
                        }
                    }
                    accountSettingsViewModel.saveTokenAndRestart(
                        context = context,
                        cookie = cookie,
                        visitorData = visitorDataValue,
                        dataSyncId = dataSyncIdValue,
                        accountName = accountNameValue,
                        accountEmail = accountEmailValue,
                        accountChannelHandle = accountChannelHandleValue,
                    )
                },
                onDismiss = { showTokenEditor = false },
                singleLine = false,
                maxLines = 20,
                isInputValid = { fullText ->
                    val cookieLine = fullText.lines()
                        .find { it.startsWith("***INNERTUBE COOKIE*** =") }
                    val cookieValue = cookieLine?.substringAfter("***INNERTUBE COOKIE*** =")?.trim() ?: ""
                    cookieValue.isNotEmpty() && "SAPISID" in parseCookieString(cookieValue)
                },
                extraContent = {
                    Spacer(Modifier.height(8.dp))
                    InfoLabel(text = stringResource(R.string.token_adv_login_description))
                }
            )
        }

        // Account Sign In / Profile Item
        Material3SettingsGroup(
            items = listOf(
                Material3SettingsItem(
                    title = {
                        Text(
                            text = if (isLoggedIn) accountName.ifEmpty { stringResource(R.string.account) } else stringResource(R.string.login)
                        )
                    },
                    description = {
                        Text(
                            text = if (isLoggedIn) {
                                accountEmail.ifEmpty { stringResource(R.string.signed_in_ytm) }
                            } else {
                                stringResource(R.string.account_login_desc)
                            }
                        )
                    },
                    leadingContent = if (isLoggedIn && accountImageUrl != null) {
                        {
                            AsyncImage(
                                model = accountImageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                            )
                        }
                    } else null,
                    icon = if (!isLoggedIn || accountImageUrl == null) painterResource(R.drawable.login) else null,
                    trailingContent = {
                        if (isLoggedIn) {
                            OutlinedButton(
                                onClick = {
                                    Timber.d("[LOGOUT] User clicked logout button, showing dialog")
                                    showLogoutDialog = true
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text(stringResource(R.string.action_logout))
                            }
                        }
                    },
                    onClick = {
                        onClose()
                        if (isLoggedIn) {
                            navController.navigate("account")
                        } else {
                            navController.navigate("login")
                        }
                    }
                )
            ),
            useLowContrast = true
        )

        // Recommendations and sync switches (only shown when logged in)
        if (isLoggedIn) {
            Spacer(Modifier.height(8.dp))

            Material3SettingsGroup(
                items = listOf(
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.more_content)) },
                        description = { Text(stringResource(R.string.more_content_desc)) },
                        icon = painterResource(R.drawable.cached),
                        trailingContent = {
                            Switch(
                                checked = useLoginForBrowse,
                                onCheckedChange = {
                                    YouTube.useLoginForBrowse = it
                                    onUseLoginForBrowseChange(it)
                                },
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (useLoginForBrowse) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        }
                    ),
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.yt_sync)) },
                        description = { Text(stringResource(R.string.yt_sync_desc)) },
                        icon = painterResource(R.drawable.cached),
                        trailingContent = {
                            Switch(
                                checked = ytmSync,
                                onCheckedChange = onYtmSyncChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (ytmSync) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        }
                    )
                ),
                useLowContrast = true
            )
        }

        Spacer(Modifier.height(8.dp))

        // Navigation options
        Material3SettingsGroup(
            items = buildList {
                add(
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.together)) },
                        description = { Text(stringResource(R.string.together_desc)) },
                        icon = painterResource(R.drawable.group_outlined),
                        onClick = {
                            onClose()
                            navController.navigate("listen_together_from_topbar")
                        }
                    )
                )
                add(
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.settings)) },
                        description = { Text(stringResource(R.string.settings_dialog_desc)) },
                        icon = painterResource(R.drawable.settings),
                        showBadge = BuildConfig.UPDATER_AVAILABLE && latestVersionName != BuildConfig.VERSION_NAME,
                        onClick = {
                            onClose()
                            navController.navigate("settings")
                        }
                    )
                )
                if (BuildConfig.UPDATER_AVAILABLE && latestVersionName != BuildConfig.VERSION_NAME) {
                    val releaseInfo = Updater.getCachedLatestRelease()
                    val downloadUrl = releaseInfo?.let { Updater.getDownloadUrlForCurrentVariant(it) }
                    
                    if (downloadUrl != null) {
                        add(
                            Material3SettingsItem(
                                title = { Text(text = stringResource(R.string.new_version_available)) },
                                description = { Text(latestVersionName) },
                                icon = painterResource(R.drawable.update),
                                showBadge = true,
                                onClick = {
                                    onClose()
                                    navController.navigate("settings/updater")
                                }
                            )
                        )
                    }
                }
            }
        )
    }
}
