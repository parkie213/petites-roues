package app.petitesroues.android.desktop

import android.content.Context
import android.os.Build
import android.provider.Settings as SystemSettings
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.petitesroues.android.Settings
import app.petitesroues.android.di.IoDispatcher
import app.petitesroues.sync.DesktopClient
import app.petitesroues.sync.PairingCodeReader
import app.petitesroues.sync.model.Desktop
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the desktop dialog shows, owned by [DesktopPairingViewModel].
 *
 * @param desktop the paired desktop, or null
 * @param status what pairing or the last connection test said, or null
 * @param busy whether pairing or a test is under way
 */
data class DesktopPairingUiState(
  val desktop: Desktop?,
  val status: String? = null,
  val busy: Boolean = false,
)

/**
 * Pairing with a desktop from its QR code, testing the connection, and forgetting it. Pairing and
 * testing run in this ViewModel's scope, so they finish (and a pairing is saved) even if the dialog
 * is closed meanwhile. The dialog asks for the local network permission before calling here.
 */
@HiltViewModel
class DesktopPairingViewModel
@Inject
constructor(
  @ApplicationContext private val context: Context,
  private val settings: Settings,
  private val client: DesktopClient,
  private val paired: PairedDesktop,
  @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
  private val _state = MutableStateFlow(DesktopPairingUiState(settings.desktop))

  /** The paired desktop and what was last said about it. */
  val state: StateFlow<DesktopPairingUiState> = _state.asStateFlow()

  /** The dialog has opened: it shows the current pairing, without an earlier status. */
  fun onShown() {
    if (!_state.value.busy) _state.value = DesktopPairingUiState(settings.desktop)
  }

  /** Pairs with the desktop whose QR code read [scanned], then tests the connection. */
  fun pair(scanned: String) {
    _state.update { it.copy(status = "Pairing…", busy = true) }
    viewModelScope.launch {
      withContext(io) {
          runCatching { client.pair(PairingCodeReader.read(scanned), phoneName(context)) }
        }
        .onSuccess { paired ->
          settings.desktop = paired
          _state.update { it.copy(desktop = paired) }
          test()
        }
        .onFailure { failure ->
          Log.w("Desktop", "Pairing failed", failure)
          _state.update { it.copy(status = "Could not pair: ${describe(failure)}", busy = false) }
        }
    }
  }

  /** Tests the connection to the paired desktop, finding it at a new address if it moved. */
  fun test() {
    val desktop = settings.desktop ?: return
    _state.update { it.copy(status = "Connecting to ${desktop.url()}…", busy = true) }
    viewModelScope.launch {
      val result = withContext(io) { runCatching { paired.reach(client::hello) } }
      _state.update {
        it.copy(
          // Finding the desktop at a new address saves it.
          desktop = settings.desktop,
          status =
            result.fold(
              { hello ->
                "Connected to ${hello.name()}: ${hello.titles()} study documents, " +
                  "${hello.media()} media files."
              },
              { failure ->
                Log.w("Desktop", "Connection test failed", failure)
                "Could not connect: ${describe(failure)}"
              },
            ),
          busy = false,
        )
      }
    }
  }

  /** Forgets the desktop; the desktop lists the phone until it is forgotten there too. */
  fun forget() {
    settings.desktop = null
    _state.value = DesktopPairingUiState(null)
  }

  /** Shows [message], for what happened outside: the scanner or a refused permission. */
  fun report(message: String) = _state.update { it.copy(status = message) }

  private fun describe(failure: Throwable) = failure.message ?: failure.javaClass.simpleName
}

/**
 * The name this phone gives the desktop when pairing: the one set in its system settings, else its
 * model.
 */
private fun phoneName(context: Context): String =
  SystemSettings.Global.getString(context.contentResolver, SystemSettings.Global.DEVICE_NAME)
    ?.ifBlank { null } ?: Build.MODEL
