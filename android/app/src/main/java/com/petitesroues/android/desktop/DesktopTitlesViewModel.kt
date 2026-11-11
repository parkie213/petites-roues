package com.petitesroues.android.desktop

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petitesroues.android.Settings
import com.petitesroues.android.di.IoDispatcher
import com.petitesroues.android.library.Folders
import com.petitesroues.sync.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the desktop titles screen shows, owned by [DesktopTitlesViewModel].
 *
 * @param desktop the paired desktop's name, or null
 * @param folder where titles are copied, or null until All files access is allowed
 * @param customFolder whether [folder] is one the person chose rather than the app's own
 * @param sync what the last check found on the desktop, or null before one has answered
 * @param problem why the last check failed, or null
 */
data class DesktopTitlesUiState(
  val desktop: String?,
  val folder: Uri?,
  val customFolder: Boolean,
  val sync: SyncStatus? = null,
  val problem: String? = null,
)

/**
 * Syncing with the paired desktop: which series are followed and which titles chosen, where they
 * are copied, and what a sync would fetch. It checks the desktop when it is created, after every
 * change, and whenever a download starts, stops or finishes a title; [Transfers] does the copying.
 * One lives as long as the desktop titles screen is on the back stack.
 */
@HiltViewModel
class DesktopTitlesViewModel
@Inject
constructor(
  private val settings: Settings,
  private val folders: Folders,
  private val transfers: Transfers,
  private val resolver: ContentResolver,
  @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
  private val _state =
    MutableStateFlow(
      DesktopTitlesUiState(
        settings.desktop?.name(),
        folders.download,
        settings.downloadFolder != null,
      )
    )

  /** The folder, the last check and any problem. */
  val state: StateFlow<DesktopTitlesUiState> = _state.asStateFlow()

  /** The current or last download. */
  val transfer: StateFlow<Transfers.Status> = transfers.status

  private var checking: Job? = null

  // Set by following a series or choosing a title: start once the check has counted what it adds.
  private var startAfterCheck = false

  init {
    viewModelScope.launch {
      transfers.status.map { it.finished to it.running }.distinctUntilChanged().collect { check() }
    }
  }

  /** Follows the series (or standalone title) [key], or stops following it; following syncs. */
  fun follow(key: String, follow: Boolean) {
    settings.followed = if (follow) settings.followed + key else settings.followed - key
    startAfterCheck = follow
    check()
  }

  /** Chooses the title [id] on its own, or unchooses it; choosing syncs. */
  fun choose(id: String, choose: Boolean) {
    settings.chosen = if (choose) settings.chosen + id else settings.chosen - id
    startAfterCheck = choose
    check()
  }

  /** Fetches what the last check found. */
  fun sync() {
    transfers.start(_state.value.sync?.remaining() ?: 0)
  }

  /** Stops the download; what was fetched stays. */
  fun stop() = transfers.stop()

  /** Copies into [tree], a folder just granted for writing, which joins the content folders. */
  fun chooseFolder(tree: Uri) {
    resolver.takePersistableUriPermission(
      tree,
      Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
    )
    settings.downloadFolder = tree
    if (tree !in settings.contentTrees) settings.contentTrees += tree
    folderChanged()
  }

  /** Goes back to copying into the app's own folder. */
  fun useDefaultFolder() {
    settings.downloadFolder = null
    folderChanged()
  }

  /** Back from the system's All files access page, allowed or not. */
  fun fileAccessChanged() = folderChanged()

  private fun folderChanged() {
    _state.update {
      it.copy(folder = folders.download, customFolder = settings.downloadFolder != null)
    }
    check()
  }

  /** Checks the desktop again, replacing any check under way. */
  private fun check() {
    checking?.cancel()
    if (settings.desktop == null || _state.value.folder == null) return
    checking = viewModelScope.launch {
      withContext(io) { runCatching { transfers.check() } }
        .onSuccess { sync ->
          _state.update { it.copy(desktop = settings.desktop?.name(), sync = sync, problem = null) }
          if (startAfterCheck) {
            startAfterCheck = false
            if (sync.toSync().isNotEmpty()) transfers.start(sync.remaining())
          }
        }
        .onFailure { failure ->
          Log.w("Transfers", "Checking the desktop's titles failed", failure)
          _state.update {
            it.copy(
              problem =
                "Could not reach the desktop: ${failure.message ?: failure.javaClass.simpleName}"
            )
          }
        }
    }
  }
}
