package fan.x0.focus.feature.library

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.error.toAppError
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.data.sources.Article
import fan.x0.focus.data.sources.Feed
import fan.x0.focus.data.sources.FeedRepository
import fan.x0.focus.data.sources.FileRepository
import fan.x0.focus.data.sources.SourceFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class LibraryViewModel(
    private val feeds: FeedRepository,
    private val files: FileRepository,
) : BaseViewModel<LibraryAction, LibraryUiState, LibraryEffect>(LibraryUiState()) {

    init {
        viewModelScope.launch {
            loadArticles()
            loadFeeds()
            loadFiles()
            updateState { copy(loading = false) }
        }
    }

    override fun onAction(action: LibraryAction) {
        when (action) {
            is LibraryAction.SectionChanged -> updateState { copy(section = action.section) }
            LibraryAction.Refresh -> refreshFeeds()
            LibraryAction.ToggleUnread -> {
                updateState { copy(unreadOnly = !unreadOnly) }
                viewModelScope.launch { loadArticles() }
            }
            is LibraryAction.OpenArticle -> openArticle(action.id)
            LibraryAction.CloseReader -> updateState { copy(reader = null) }
            is LibraryAction.AskDeleteArticle -> {
                val row = currentState.articles.firstOrNull { it.id == action.id } ?: return
                updateState { copy(deleteTarget = DeleteTarget.Article(row.id, row.title)) }
            }
            LibraryAction.OpenAddFeed -> updateState { copy(addFeedOpen = true) }
            LibraryAction.DismissAddFeed -> updateState { copy(addFeedOpen = false, feedUrl = "") }
            is LibraryAction.FeedUrlChanged -> updateState { copy(feedUrl = action.value) }
            LibraryAction.SubmitFeed -> submitFeed()
            is LibraryAction.AskRemoveFeed -> {
                val feed = currentState.feeds.firstOrNull { it.id == action.id } ?: return
                updateState { copy(deleteTarget = DeleteTarget.Feed(feed.id, feed.title)) }
            }
            is LibraryAction.FolderPicked -> attachFolder(action.uri)
            LibraryAction.ClearFolder -> clearFolder()
            is LibraryAction.OpenFile -> openFile(action.uri)
            LibraryAction.CloseFile -> updateState { copy(fileReader = null) }
            LibraryAction.OpenNote -> updateState { copy(noteOpen = true) }
            LibraryAction.DismissNote -> updateState { copy(noteOpen = false, noteName = "", noteBody = "") }
            is LibraryAction.NoteNameChanged -> updateState { copy(noteName = action.value) }
            is LibraryAction.NoteBodyChanged -> updateState { copy(noteBody = action.value) }
            LibraryAction.SaveNote -> saveNote()
            is LibraryAction.AskDeleteFile -> {
                val file = currentState.files.firstOrNull { it.uri == action.uri } ?: return
                updateState { copy(deleteTarget = DeleteTarget.File(file.uri, file.name)) }
            }
            LibraryAction.ConfirmDelete -> confirmDelete()
            LibraryAction.CancelDelete -> updateState { copy(deleteTarget = null) }
        }
    }

    private suspend fun loadArticles() {
        val unreadOnly = currentState.unreadOnly
        val list = feeds.listArticles(limit = 60).filter { !unreadOnly || !it.read }
        updateState { copy(articles = list.map { it.toRow() }) }
    }

    private suspend fun loadFeeds() {
        val list = feeds.listFeeds()
        updateState { copy(feeds = list.map { it.toRow() }) }
    }

    private suspend fun loadFiles() {
        val name = runCatching { files.treeName() }.getOrNull()
        val tree = runCatching { files.list() }.getOrNull()
        updateState {
            copy(
                folderName = name,
                files = tree?.map { it.toRow() } ?: emptyList(),
            )
        }
    }

    private fun refreshFeeds() {
        if (currentState.refreshing) return
        updateState { copy(refreshing = true) }
        viewModelScope.launch {
            try {
                feeds.refreshAll()
                loadFeeds()
                loadArticles()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emitError(e.toAppError())
            } finally {
                updateState { copy(refreshing = false) }
            }
        }
    }

    private fun openArticle(id: String) {
        viewModelScope.launch {
            try {
                val article = feeds.getArticle(id) ?: return@launch
                if (!article.read) {
                    feeds.markRead(id)
                    loadArticles()
                }
                updateState { copy(reader = article.toReader()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emitError(e.toAppError())
            }
        }
    }

    private fun submitFeed() {
        val url = currentState.feedUrl.trim()
        if (url.isBlank() || currentState.addingFeed) return
        updateState { copy(addingFeed = true) }
        viewModelScope.launch {
            try {
                feeds.addFeed(url)
                updateState { copy(addingFeed = false, addFeedOpen = false, feedUrl = "") }
                loadFeeds()
                loadArticles()
                updateState { copy(section = LibrarySection.Feeds) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(addingFeed = false) }
                emitError(e.toAppError())
            }
        }
    }

    private fun attachFolder(uri: String) {
        viewModelScope.launch {
            try {
                files.attachTree(uri)
                loadFiles()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emitError(e.toAppError())
            }
        }
    }

    private fun clearFolder() {
        viewModelScope.launch {
            try {
                files.clearTree()
                updateState { copy(folderName = null, files = emptyList(), fileReader = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emitError(e.toAppError())
            }
        }
    }

    private fun openFile(uri: String) {
        viewModelScope.launch {
            try {
                val text = files.readText(uri, maxChars = 120_000)
                val name = currentState.files.firstOrNull { it.uri == uri }?.name.orEmpty()
                updateState { copy(fileReader = FileReaderUi(uri, name, text)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emitError(e.toAppError())
            }
        }
    }

    private fun saveNote() {
        val name = currentState.noteName.trim()
        if (name.isBlank() || currentState.savingNote) return
        updateState { copy(savingNote = true) }
        viewModelScope.launch {
            try {
                files.createText(name, currentState.noteBody)
                updateState {
                    copy(savingNote = false, noteOpen = false, noteName = "", noteBody = "")
                }
                loadFiles()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(savingNote = false) }
                emitError(e.toAppError())
            }
        }
    }

    private fun confirmDelete() {
        val target = currentState.deleteTarget ?: return
        updateState { copy(deleteTarget = null) }
        viewModelScope.launch {
            try {
                when (target) {
                    is DeleteTarget.Article -> {
                        feeds.deleteArticle(target.id)
                        updateState { copy(reader = null) }
                        loadArticles()
                    }
                    is DeleteTarget.Feed -> {
                        feeds.removeFeed(target.id)
                        loadFeeds()
                        loadArticles()
                    }
                    is DeleteTarget.File -> {
                        files.delete(target.uri)
                        updateState { copy(fileReader = null) }
                        loadFiles()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emitError(e.toAppError())
            }
        }
    }

    private fun Article.toRow(): ArticleRowUi = ArticleRowUi(
        id = id,
        title = title,
        feed = feedTitle,
        time = timeLabel(publishedAt ?: fetchedAt),
        summary = summary,
        read = read,
    )

    private fun Article.toReader(): ArticleReaderUi = ArticleReaderUi(
        id = id,
        title = title,
        feed = feedTitle,
        time = timeLabel(publishedAt ?: fetchedAt),
        content = content,
        url = url,
    )

    private fun Feed.toRow(): FeedRowUi = FeedRowUi(id = id, title = title, url = url, error = error)

    private fun SourceFile.toRow(): FileRowUi = FileRowUi(
        uri = uri,
        name = name.ifBlank { uri.substringAfterLast('/') },
        sizeLabel = sizeLabel(size),
        isDirectory = isDirectory,
    )

    private fun sizeLabel(bytes: Long): String = when {
        bytes <= 0L -> ""
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }

    private fun timeLabel(millis: Long): String {
        val minutes = (System.currentTimeMillis() - millis) / 60_000
        return when {
            minutes < 1 -> ""
            minutes < 60 -> "${minutes}m"
            minutes < 60 * 24 -> "${minutes / 60}h"
            else -> "${minutes / (60 * 24)}d"
        }
    }
}
