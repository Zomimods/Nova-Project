package com.sami.livetv.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sami.livetv.R
import com.sami.livetv.data.cloud.ChannelComment
import com.sami.livetv.data.cloud.CloudAccountRepository
import com.sami.livetv.data.preferences.AppLanguage
import com.sami.livetv.ui.localization.localizedString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ChannelCommentsSection(
    repository: CloudAccountRepository,
    channelId: String,
    language: AppLanguage,
    onOpenAccount: () -> Unit,
) {
    val user by repository.currentUser.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var comments by remember(channelId, repository) { mutableStateOf(emptyList<ChannelComment>()) }
    var draft by remember(channelId) { mutableStateOf("") }
    var isLoading by remember(channelId, repository) { mutableStateOf(false) }
    var isPosting by remember { mutableStateOf(false) }
    var loadFailed by remember(channelId) { mutableStateOf(false) }
    var statusMessageId by remember(channelId) { mutableStateOf<Int?>(null) }

    LaunchedEffect(repository, channelId, repository.isConfigured) {
        if (!repository.isConfigured) return@LaunchedEffect
        isLoading = true
        loadFailed = false
        try {
            comments = repository.loadComments(channelId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            loadFailed = true
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = localizedString(R.string.comments_title, language),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        if (!repository.isConfigured) {
            Text(
                text = localizedString(R.string.comments_firebase_required, language),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            if (user == null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = localizedString(R.string.comments_sign_in_required, language),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onOpenAccount) {
                        Text(localizedString(R.string.account_sign_in, language))
                    }
                }
            }

            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= MAX_COMMENT_LENGTH) draft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(localizedString(R.string.comment_hint, language)) },
                enabled = user != null && !isPosting,
                minLines = 2,
                maxLines = 4,
            )
            Button(
                onClick = {
                    val commentText = draft.trim()
                    if (commentText.isEmpty() || user == null || isPosting) return@Button
                    scope.launch {
                        isPosting = true
                        statusMessageId = null
                        try {
                            repository.addComment(channelId, commentText)
                            draft = ""
                            statusMessageId = R.string.comment_posted
                            try {
                                comments = repository.loadComments(channelId)
                                loadFailed = false
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                loadFailed = true
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            statusMessageId = R.string.account_action_failed
                        } finally {
                            isPosting = false
                        }
                    }
                },
                enabled = user != null && draft.isNotBlank() && !isPosting,
            ) {
                Text(localizedString(R.string.comment_submit, language))
            }

            if (isPosting || isLoading) {
                CircularProgressIndicator()
            }
            statusMessageId?.let { messageId ->
                Text(
                    text = localizedString(messageId, language),
                    color = if (messageId == R.string.account_action_failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }

            when {
                loadFailed -> Text(
                    text = localizedString(R.string.comments_load_failed, language),
                    color = MaterialTheme.colorScheme.error,
                )
                !isLoading && comments.isEmpty() -> Text(
                    text = localizedString(R.string.comments_empty, language),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> comments.forEach { comment ->
                    CommentCard(comment = comment, language = language)
                }
            }
        }
    }
}

@Composable
private fun CommentCard(comment: ChannelComment, language: AppLanguage) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = localizedString(R.string.comment_anonymous_author, language),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private const val MAX_COMMENT_LENGTH = 500
