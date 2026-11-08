package com.petitesroues.android.desktop

import android.content.Context
import android.os.Build
import android.util.Log
import android.provider.Settings as SystemSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.petitesroues.android.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The name this phone gives the desktop when pairing: the one set in its system settings, else its model. */
private fun phoneName(context: Context): String =
  SystemSettings.Global.getString(context.contentResolver, SystemSettings.Global.DEVICE_NAME)?.ifBlank { null }
    ?: Build.MODEL

/**
 * Pairs this phone with a desktop by scanning the QR code from the desktop's "Connect a phone"
 * dialog, then tests the connection. Once paired it shows the desktop, tests the connection again
 * on demand, and can forget it (the desktop keeps listing the phone until it is forgotten there
 * too). Scanning uses Google Play services' scanner, so the app needs no camera permission.
 */
@Composable
fun DesktopDialog(settings: Settings, onDismiss: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var desktop by remember { mutableStateOf(settings.desktop) }
  var status by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }

  fun test(paired: Desktop) {
    busy = true
    status = "Connecting to ${paired.url}…"
    scope.launch {
      val result = withContext(Dispatchers.IO) { runCatching { DesktopClient.hello(paired) } }
      status =
        result.fold(
          { "Connected to ${it.name}: ${it.titles} study documents, ${it.media} media files." },
          { "Could not connect: ${it.message ?: it.javaClass.simpleName}" },
        )
      busy = false
    }
  }

  fun pair(scanned: String) {
    busy = true
    status = "Pairing…"
    scope.launch {
      val result =
        withContext(Dispatchers.IO) {
          runCatching { DesktopClient.pair(PairingCode.parse(scanned), phoneName(context)) }
        }
      result.fold(
        { paired ->
          settings.desktop = paired
          desktop = paired
          test(paired)
        },
        {
          status = "Could not pair: ${it.message ?: it.javaClass.simpleName}"
          busy = false
        },
      )
    }
  }

  fun scan() {
    val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    GmsBarcodeScanning.getClient(context, options)
      .startScan()
      .addOnSuccessListener { code -> code.rawValue?.let(::pair) ?: run { status = "That QR code holds no text." } }
      .addOnFailureListener {
        Log.w("Desktop", "Scan failed: ${(it as? MlKitException)?.errorCode}", it)
        status =
          if ((it as? MlKitException)?.errorCode == MlKitException.CODE_SCANNER_UNAVAILABLE)
            "Google Play services is still installing the QR scanner. Try again in a minute, " +
              "with the phone online."
          else "Could not scan: ${it.message}"
      }
  }

  val paired = desktop
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
          Text(paired.name, style = MaterialTheme.typography.titleMedium)
          Text(paired.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
      }
    },
    confirmButton = {
      if (paired == null) TextButton(onClick = ::scan, enabled = !busy) { Text("Scan QR code") }
      else TextButton(onClick = { test(paired) }, enabled = !busy) { Text("Test connection") }
    },
    dismissButton = {
      Row {
        if (paired != null)
          TextButton(
            onClick = {
              settings.desktop = null
              desktop = null
              status = null
            },
            enabled = !busy,
          ) {
            Text("Forget")
          }
        TextButton(onClick = onDismiss) { Text("Close") }
      }
    },
  )
}
