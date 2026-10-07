package com.sami.livetv.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sami.livetv.R
import com.sami.livetv.data.cloud.CloudAccountRepository
import com.sami.livetv.data.cloud.CloudUsageStats
import com.sami.livetv.data.cloud.synchronizeAccountLibrary
import com.sami.livetv.data.preferences.AppLanguage
import com.sami.livetv.data.preferences.SettingsStore
import com.sami.livetv.ui.localization.localizedString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(
    repository: CloudAccountRepository,
    settingsStore: SettingsStore,
    language: AppLanguage,
    onDismiss: () -> Unit,
) {
    val user by repository.currentUser.collectAsStateWithLifecycle(initialValue = null)
    val favoriteIds by settingsStore.favoriteIds.collectAsStateWithLifecycle(
        initialValue = emptySet(),
    )
    val watchHistory by settingsStore.watchHistory.collectAsStateWithLifecycle(
        initialValue = emptyList(),
    )
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var resultMessageId by remember { mutableIntStateOf(0) }
    var usageStats by remember { mutableStateOf(CloudUsageStats()) }

    LaunchedEffect(repository, user?.id) {
        usageStats = CloudUsageStats()
        if (repository.isConfigured && user != null) {
            try {
                usageStats = repository.loadUsageStats()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                usageStats = CloudUsageStats()
            }
        }
    }

    fun runAccountAction(successMessageId: Int, action: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            resultMessageId = 0
            try {
                action()
                resultMessageId = successMessageId
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                resultMessageId = R.string.account_action_failed
            } finally {
                busy = false
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = localizedString(R.string.account_title, language),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            if (!repository.isConfigured) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = localizedString(R.string.firebase_not_configured, language),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = localizedString(R.string.firebase_setup_instructions, language),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            } else if (user == null) {
                Text(
                    text = localizedString(R.string.account_signed_out, language),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(localizedString(R.string.account_email, language)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(localizedString(R.string.account_password, language)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            runAccountAction(R.string.account_action_completed) {
                                repository.signIn(email, password)
                                password = ""
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(localizedString(R.string.account_sign_in, language))
                    }
                    TextButton(
                        onClick = {
                            runAccountAction(R.string.account_action_completed) {
                                repository.createAccount(email, password)
                                password = ""
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(localizedString(R.string.account_create, language))
                    }
                }
            } else {
                Text(
                    text = localizedString(
                        R.string.account_signed_in_as,
                        language,
                        user?.displayName?.takeIf(String::isNotBlank)
                            ?: user?.email.orEmpty(),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            runAccountAction(R.string.account_signed_out) {
                                repository.signOut()
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(localizedString(R.string.account_sign_out, language))
                    }
                    Button(
                        onClick = {
                            runAccountAction(R.string.account_sync_success) {
                                synchronizeAccountLibrary(settingsStore, repository)
                                usageStats = try {
                                    repository.loadUsageStats()
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    usageStats
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(localizedString(R.string.account_sync, language))
                    }
                }
            }

            if (busy) {
                CircularProgressIndicator()
            }
            if (resultMessageId != 0) {
                Text(
                    text = localizedString(resultMessageId, language),
                    color = if (resultMessageId == R.string.account_action_failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text(
                        text = localizedString(R.string.account_local_stats, language),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = localizedString(
                            R.string.account_favorite_count,
                            language,
                            favoriteIds.size,
                        ),
                    )
                    Text(
                        text = localizedString(
                            R.string.account_history_count,
                            language,
                            watchHistory.size,
                        ),
                    )
                    if (repository.isConfigured && user != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = localizedString(R.string.account_cloud_stats, language),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = localizedString(
                                R.string.account_playback_count,
                                language,
                                usageStats.playbackStarts,
                            ),
                        )
                        Text(
                            text = localizedString(
                                R.string.account_favorite_change_count,
                                language,
                                usageStats.favoriteChanges,
                            ),
                        )
                    }
                }
            }
        }
    }
}
