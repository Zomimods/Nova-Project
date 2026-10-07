package com.sami.livetv.ui.browser

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import com.sami.livetv.R
import com.sami.livetv.data.cloud.CloudAccountRepository
import com.sami.livetv.data.preferences.AppLanguage
import com.sami.livetv.domain.model.ChannelCategory
import com.sami.livetv.domain.model.StreamSource
import com.sami.livetv.domain.model.TvChannel
import com.sami.livetv.ui.account.ChannelCommentsSection
import com.sami.livetv.ui.localization.localizedString

@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun ChannelDetailsSheet(
    category: ChannelCategory,
    channel: TvChannel,
    language: AppLanguage,
    darkTheme: Boolean,
    cloudAccountRepository: CloudAccountRepository,
    playbackController: MediaController?,
    isCurrentChannel: Boolean,
    currentStreamId: String?,
    isPlaying: Boolean,
    onPlayStream: (StreamSource) -> Unit,
    onEnterPictureInPicture: () -> Unit,
    onOpenAccount: () -> Unit,
    onDismiss: () -> Unit,
) {
    val languageTag = if (language == AppLanguage.English) "en" else "ar"

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name.resolve(languageTag),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = category.name.resolve(languageTag),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(localizedString(R.string.close, language))
                }
            }

            ChannelArtwork(
                channel = channel,
                darkTheme = darkTheme,
                contentDescription = channel.name.resolve(languageTag),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(156.dp),
            )

            if (isCurrentChannel && playbackController != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(androidx.compose.ui.graphics.Color.Black),
                ) {
                    AndroidView(
                        factory = { context ->
                            PlayerView(context).apply {
                                player = playbackController
                                useController = true
                                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            }
                        },
                        update = { playerView ->
                            if (playerView.player !== playbackController) {
                                playerView.player = playbackController
                            }
                        },
                        onRelease = { playerView ->
                            playerView.player = null
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (isPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    TextButton(
                        onClick = onEnterPictureInPicture,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(localizedString(R.string.picture_in_picture, language))
                    }
                }
            }

            Text(
                text = localizedString(R.string.available_qualities, language),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            channel.streams.forEach { source ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = source.quality.resolve(languageTag),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (source.audioUrl != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text(
                                    text = localizedString(R.string.separate_audio, language),
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                        if (isCurrentChannel && isPlaying && currentStreamId == source.id) {
                            Text(
                                text = localizedString(R.string.now_playing, language),
                                modifier = Modifier.padding(start = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            TextButton(
                                onClick = { onPlayStream(source) },
                                modifier = Modifier.padding(start = 2.dp),
                            ) {
                                Text(localizedString(R.string.play_stream, language))
                            }
                        }
                    }
                }
            }

            ChannelCommentsSection(
                repository = cloudAccountRepository,
                channelId = channel.id,
                language = language,
                onOpenAccount = onOpenAccount,
            )
        }
    }
}
