package fan.x0.focus.feature.library

import fan.x0.focus.core.mvi.UiAction
import fan.x0.focus.core.mvi.UiEffect
import fan.x0.focus.core.mvi.UiState

enum class LibrarySection { Articles, Feeds, Files }

data class ArticleRowUi(
    val id: String,
    val title: String,
    val feed: String,
    val time: String,
    val summary: String?,
    val read: Boolean,
)

data class ArticleReaderUi(
    val id: String,
    val title: String,
    val feed: String,
    val time: String,
    val content: String,
    val url: String,
)

data class FeedRowUi(
    val id: String,
    val title: String,
    val url: String,
    val error: String?,
)

data class FileRowUi(
    val uri: String,
    val name: String,
    val sizeLabel: String,
    val isDirectory: Boolean,
)

data class FileReaderUi(
    val uri: String,
    val name: String,
    val content: String,
)

data class LibraryUiState(
    val loading: Boolean = true,
    val section: LibrarySection = LibrarySection.Articles,
    val articles: List<ArticleRowUi> = emptyList(),
    val unreadOnly: Boolean = false,
    val reader: ArticleReaderUi? = null,
    val feeds: List<FeedRowUi> = emptyList(),
    val refreshing: Boolean = false,
    val addFeedOpen: Boolean = false,
    val feedUrl: String = "",
    val addingFeed: Boolean = false,
    val folderName: String? = null,
    val files: List<FileRowUi> = emptyList(),
    val fileReader: FileReaderUi? = null,
    val noteOpen: Boolean = false,
    val noteName: String = "",
    val noteBody: String = "",
    val savingNote: Boolean = false,
    val deleteTarget: DeleteTarget? = null,
) : UiState

sealed interface DeleteTarget {
    data class Article(val id: String, val title: String) : DeleteTarget
    data class Feed(val id: String, val title: String) : DeleteTarget
    data class File(val uri: String, val name: String) : DeleteTarget
}

sealed interface LibraryAction : UiAction {
    data class SectionChanged(val section: LibrarySection) : LibraryAction
    data object Refresh : LibraryAction
    data object ToggleUnread : LibraryAction
    data class OpenArticle(val id: String) : LibraryAction
    data object CloseReader : LibraryAction
    data class AskDeleteArticle(val id: String) : LibraryAction
    data object OpenAddFeed : LibraryAction
    data object DismissAddFeed : LibraryAction
    data class FeedUrlChanged(val value: String) : LibraryAction
    data object SubmitFeed : LibraryAction
    data class AskRemoveFeed(val id: String) : LibraryAction
    data class FolderPicked(val uri: String) : LibraryAction
    data object ClearFolder : LibraryAction
    data class OpenFile(val uri: String) : LibraryAction
    data object CloseFile : LibraryAction
    data object OpenNote : LibraryAction
    data object DismissNote : LibraryAction
    data class NoteNameChanged(val value: String) : LibraryAction
    data class NoteBodyChanged(val value: String) : LibraryAction
    data object SaveNote : LibraryAction
    data class AskDeleteFile(val uri: String) : LibraryAction
    data object ConfirmDelete : LibraryAction
    data object CancelDelete : LibraryAction
}

sealed interface LibraryEffect : UiEffect
