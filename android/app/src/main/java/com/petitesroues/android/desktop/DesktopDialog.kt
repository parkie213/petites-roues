package com.petitesroues.android.desktop

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Android 17's permission to reach devices on the local network, which the desktop is. Without it
 * every connection to the desktop fails, though the phone and computer share a Wi-Fi.
 */
private const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

internal fun mayReachLocalNetwork(context: Context): Boolean =
  Build.VERSION.SDK_INT < 37 ||
    context.checkSelfPermission(LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

/**
 * Pairs this phone with a desktop by scanning the QR code from the desktop's "Connect a phone"
 * dialog, then tests the connection. Once paired it shows the desktop, tests the connection again
 * on demand, and can forget it (the desktop keeps listing the phone until it is forgotten there
 * too). Scanning uses Google Play services' scanner, so the app needs no camera permission; it does
 * need Android 17's local network permission, which it asks for before scanning or testing. Once
 * paired, [onGetTitles] opens syncing with the desktop ([DesktopTitlesScreen]). The work is
 * [DesktopPairingViewModel]'s.
 */
@Composable
fun DesktopDialog(
  onGetTitles: () -> Unit,
  onDismiss: () -> Unit,
  model: DesktopPairingViewModel = hiltViewModel(),
) {
  val context = LocalContext.current
  val state by model.state.collectAsStateWithLifecycle()
  LaunchedEffect(Unit) { model.onShown() }
  // What to do once the local network permission is granted.
  var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
  val askPermission =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      val then = afterPermission
      afterPermission = null
      if (granted) then?.invoke()
      else
        model.report(
          "petites roues needs permission to find devices on your local network to reach the " +
            "desktop. Allow it in the app's settings under Permissions."
        )
    }

  fun withLocalNetwork(action: () -> Unit) {
    if (mayReachLocalNetwork(context)) action()
    else {
      afterPermission = action
      askPermission.launch(LOCAL_NETWORK)
    }
  }

  fun scan() {
    val options =
      GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    GmsBarcodeScanning.getClient(context, options)
      .startScan()
      .addOnSuccessListener { code ->
        code.rawValue?.let(model::pair) ?: model.report("That QR code holds no text.")
      }
      .addOnFailureListener {
        Log.w("Desktop", "Scan failed: ${(it as? MlKitException)?.errorCode}", it)
        model.report(
          if ((it as? MlKitException)?.errorCode == MlKitException.CODE_SCANNER_UNAVAILABLE)
            "Google Play services is still installing the QR scanner. Try again in a minute, " +
              "with the phone online."
          else "Could not scan: ${it.message}"
        )
      }
  }

  val paired = state.desktop
  val busy = state.busy
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Desktop") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (paired == null)
          Text(
            "Pair with petites roues on your computer: in its library, click the phone icon " +
              "(Connect a phone), then scan the QR code it shows. The phone and the computer must " +
              "be on the same Wi-Fi."
          )
        else {
          Text(paired.name(), style = MaterialTheme.typography.titleMedium)
          Text(
            paired.url(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        state.status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
      }
    },
    confirmButton = {
      if (paired == null)
        TextButton(onClick = { withLocalNetwork(::scan) }, enabled = !busy) { Text("Scan QR code") }
      else
        TextButton(onClick = { withLocalNetwork(model::test) }, enabled = !busy) {
          Text("Test connection")
        }
    },
    dismissButton = {
      Row {
        if (paired != null)
          TextButton(onClick = { withLocalNetwork(onGetTitles) }, enabled = !busy) { Text("Sync") }
        if (paired != null)
          TextButton(
            onClick = model::forget,
            enabled = !busy,
          ) {
            Text("Forget")
          }
        TextButton(onClick = onDismiss) { Text("Close") }
      }
    },
  )
}
