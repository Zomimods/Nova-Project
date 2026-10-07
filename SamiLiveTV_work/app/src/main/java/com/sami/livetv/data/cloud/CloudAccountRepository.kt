package com.sami.livetv.data.cloud

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.sami.livetv.data.preferences.SettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

data class CloudUser(
    val id: String,
    val email: String?,
    val displayName: String?,
)

data class CloudLibrary(
    val favoriteIds: Set<String> = emptySet(),
    val watchHistory: List<String> = emptyList(),
)

data class ChannelComment(
    val id: String,
    val text: String,
    val createdAtMillis: Long?,
)

data class CloudUsageStats(
    val playbackStarts: Long = 0,
    val favoriteChanges: Long = 0,
)

interface CloudAccountRepository {
    val isConfigured: Boolean
    val currentUser: Flow<CloudUser?>

    suspend fun signIn(email: String, password: String)
    suspend fun createAccount(email: String, password: String)
    suspend fun signOut()

    suspend fun loadLibrary(): CloudLibrary
    suspend fun saveLibrary(library: CloudLibrary)
    suspend fun loadComments(channelId: String): List<ChannelComment>
    suspend fun addComment(channelId: String, text: String)
    suspend fun recordPlaybackStarted()
    suspend fun recordFavoriteChanged()
    suspend fun loadUsageStats(): CloudUsageStats
}

class FirebaseSetupRequiredException : IllegalStateException(
    "Firebase is not configured for this Android app.",
)

object CloudAccountRepositoryFactory {
    fun create(context: Context): CloudAccountRepository {
        val defaultApp = FirebaseApp.getApps(context)
            .firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            ?: return UnconfiguredCloudAccountRepository

        return FirebaseCloudAccountRepository(
            auth = FirebaseAuth.getInstance(defaultApp),
            firestore = FirebaseFirestore.getInstance(defaultApp),
        )
    }
}

private object UnconfiguredCloudAccountRepository : CloudAccountRepository {
    override val isConfigured: Boolean = false
    override val currentUser: Flow<CloudUser?> = flowOf(null)

    override suspend fun signIn(email: String, password: String): Nothing =
        throw FirebaseSetupRequiredException()

    override suspend fun createAccount(email: String, password: String): Nothing =
        throw FirebaseSetupRequiredException()

    override suspend fun signOut(): Nothing = throw FirebaseSetupRequiredException()
    override suspend fun loadLibrary(): Nothing = throw FirebaseSetupRequiredException()
    override suspend fun saveLibrary(library: CloudLibrary): Nothing =
        throw FirebaseSetupRequiredException()

    override suspend fun loadComments(channelId: String): Nothing =
        throw FirebaseSetupRequiredException()

    override suspend fun addComment(channelId: String, text: String): Nothing =
        throw FirebaseSetupRequiredException()

    override suspend fun recordPlaybackStarted() = Unit
    override suspend fun recordFavoriteChanged() = Unit
    override suspend fun loadUsageStats(): CloudUsageStats = CloudUsageStats()
}

private class FirebaseCloudAccountRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : CloudAccountRepository {
    override val isConfigured: Boolean = true

    @OptIn(ExperimentalCoroutinesApi::class)
    override val currentUser: Flow<CloudUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.let { user ->
                CloudUser(
                    id = user.uid,
                    email = user.email,
                    displayName = user.displayName,
                )
            })
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun signIn(email: String, password: String) {
        validateCredentials(email, password)
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    override suspend fun createAccount(email: String, password: String) {
        validateCredentials(email, password)
        auth.createUserWithEmailAndPassword(email.trim(), password).await()
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    override suspend fun loadLibrary(): CloudLibrary {
        val snapshot = userDocument().get().await()
        val favorites = snapshot.get("favoriteIds") as? List<*>
        val history = snapshot.get("watchHistory") as? List<*>
        return CloudLibrary(
            favoriteIds = favorites.orEmpty().mapNotNull { it as? String }.toSet(),
            watchHistory = history.orEmpty().mapNotNull { it as? String },
        )
    }

    override suspend fun saveLibrary(library: CloudLibrary) {
        userDocument().set(
            mapOf(
                "favoriteIds" to library.favoriteIds.sorted(),
                "watchHistory" to library.watchHistory.distinct().take(HISTORY_LIMIT),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
    }

    override suspend fun loadComments(channelId: String): List<ChannelComment> {
        validateChannelId(channelId)
        return firestore.collection(CHANNELS_COLLECTION)
            .document(channelId)
            .collection(COMMENTS_COLLECTION)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(COMMENT_LIMIT)
            .get()
            .await()
            .documents
            .map { document ->
                ChannelComment(
                    id = document.id,
                    text = document.getString("text").orEmpty(),
                    createdAtMillis = document.getTimestamp("createdAt")?.toDate()?.time,
                )
            }
    }

    override suspend fun addComment(channelId: String, text: String) {
        validateChannelId(channelId)
        val cleanText = text.trim()
        require(cleanText.isNotEmpty() && cleanText.length <= MAX_COMMENT_LENGTH) {
            "Comments must contain between 1 and $MAX_COMMENT_LENGTH characters."
        }
        check(auth.currentUser != null) { "Sign in before posting a comment." }

        firestore.collection(CHANNELS_COLLECTION)
            .document(channelId)
            .collection(COMMENTS_COLLECTION)
            .add(
                mapOf(
                    "text" to cleanText,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
            .await()
    }

    override suspend fun recordPlaybackStarted() {
        recordUsageCounter("playbackStarts")
    }

    override suspend fun recordFavoriteChanged() {
        recordUsageCounter("favoriteChanges")
    }

    override suspend fun loadUsageStats(): CloudUsageStats {
        val user = auth.currentUser ?: return CloudUsageStats()
        val snapshot = firestore.collection(USERS_COLLECTION)
            .document(user.uid)
            .collection(USAGE_COLLECTION)
            .document(USAGE_SUMMARY_DOCUMENT)
            .get()
            .await()
        return CloudUsageStats(
            playbackStarts = snapshot.getLong("playbackStarts") ?: 0L,
            favoriteChanges = snapshot.getLong("favoriteChanges") ?: 0L,
        )
    }

    private suspend fun recordUsageCounter(field: String) {
        val user = auth.currentUser ?: return
        firestore.collection(USERS_COLLECTION)
            .document(user.uid)
            .collection(USAGE_COLLECTION)
            .document(USAGE_SUMMARY_DOCUMENT)
            .set(mapOf(field to FieldValue.increment(1L)), SetOptions.merge())
            .await()
    }

    private fun userDocument() =
        firestore.collection(USERS_COLLECTION).document(requireSignedInUserId())

    private fun requireSignedInUserId(): String =
        auth.currentUser?.uid ?: error("Sign in before accessing account data.")

    private fun validateChannelId(channelId: String) {
        require(channelId.isNotBlank() && '/' !in channelId) {
            "Channel id must be a non-empty Firestore document id."
        }
    }

    private fun validateCredentials(email: String, password: String) {
        require(email.trim().contains('@')) { "Enter a valid email address." }
        require(password.length >= MIN_PASSWORD_LENGTH) {
            "Password must contain at least $MIN_PASSWORD_LENGTH characters."
        }
    }

    private companion object {
        const val USERS_COLLECTION = "users"
        const val CHANNELS_COLLECTION = "channels"
        const val COMMENTS_COLLECTION = "comments"
        const val USAGE_COLLECTION = "usage"
        const val USAGE_SUMMARY_DOCUMENT = "summary"
        const val HISTORY_LIMIT = 10
        const val COMMENT_LIMIT = 50L
        const val MAX_COMMENT_LENGTH = 500
        const val MIN_PASSWORD_LENGTH = 6
    }
}

suspend fun synchronizeAccountLibrary(
    settingsStore: SettingsStore,
    repository: CloudAccountRepository,
) {
    check(repository.isConfigured) { "Firebase is not configured." }
    val accountId = repository.currentUser.first()?.id
        ?: error("Sign in before syncing account data.")
    val localLibrary = CloudLibrary(
        favoriteIds = settingsStore.favoriteIds.first(),
        watchHistory = settingsStore.watchHistory.first(),
    )
    val remoteLibrary = repository.loadLibrary()
    val baseline = settingsStore.loadSyncedLibraryBaseline(accountId)
    val mergedLibrary = mergeAccountLibraries(
        local = localLibrary,
        remote = remoteLibrary,
        baseline = baseline?.let {
            CloudLibrary(
                favoriteIds = it.favoriteIds,
                watchHistory = it.watchHistory,
            )
        },
    )

    settingsStore.replaceLibrary(
        favoriteIds = mergedLibrary.favoriteIds,
        watchHistory = mergedLibrary.watchHistory,
    )
    repository.saveLibrary(mergedLibrary)
    settingsStore.saveSyncedLibraryBaseline(
        accountId = accountId,
        favoriteIds = mergedLibrary.favoriteIds,
        watchHistory = mergedLibrary.watchHistory,
    )
}

internal fun mergeAccountLibraries(
    local: CloudLibrary,
    remote: CloudLibrary,
    baseline: CloudLibrary?,
): CloudLibrary {
    val mergedFavorites = if (baseline == null) {
        local.favoriteIds + remote.favoriteIds
    } else {
        (baseline.favoriteIds + local.favoriteIds + remote.favoriteIds)
            .filterTo(linkedSetOf()) { channelId ->
                val previous = channelId in baseline.favoriteIds
                val localHas = channelId in local.favoriteIds
                val remoteHas = channelId in remote.favoriteIds
                when {
                    localHas == remoteHas -> localHas
                    localHas == previous -> remoteHas
                    else -> localHas
                }
            }
    }

    val mergedHistory = if (baseline == null) {
        (local.watchHistory + remote.watchHistory).distinct()
    } else {
        val newLocalEntries = local.watchHistory
            .filterNot { it in baseline.watchHistory }
        val newRemoteEntries = remote.watchHistory
            .filterNot { it in baseline.watchHistory }
        (newLocalEntries + newRemoteEntries + baseline.watchHistory).distinct()
    }.take(HISTORY_LIMIT)

    return CloudLibrary(
        favoriteIds = mergedFavorites,
        watchHistory = mergedHistory,
    )
}

private const val HISTORY_LIMIT = 10
