package com.petitesroues.android.desktop

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.petitesroues.android.MainActivity
import com.petitesroues.android.Settings
import com.petitesroues.android.library.AppFolder
import com.petitesroues.android.library.LibraryUsage
import com.petitesroues.model.sync.Desktop
import com.petitesroues.model.sync.DesktopTitle
import com.petitesroues.sync.DesktopClient
import com.petitesroues.sync.DesktopException
import com.petitesroues.sync.SyncStatus
import com.petitesroues.sync.TitleDownloader
import com.petitesroues.sync.TransferFolder
import com.petitesroues.sync.TransferPlan
import java.io.IOException
import java.util.concurrent.CancellationException
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Syncing with the paired desktop: updates to every title already in the download folder, and the
 * new titles of the series the phone follows ([Settings.followed], [SyncStatus]). It runs as one
 * user-initiated data transfer job, Android's mechanism for downloads the person started, which
 * keeps going with the screen off and shows a notification. The shared [TitleDownloader] does the
 * work; this object starts and stops it, checks what a sync would fetch, and publishes both to the
 * app's screens.
 */
object Transfers {
  /**
   * The current or last download.
   *
   * @param running whether a download is scheduled or under way
   * @param title the title being fetched
   * @param done bytes downloaded so far, of [total]
   * @param message how the last download ended, or what it is waiting for
   * @param finished how many titles have finished since the app started; the library refreshes when
   *   it grows
   */
  data class Status(
    val running: Boolean = false,
    val title: String? = null,
    val done: Long = 0,
    val total: Long = 0,
    val message: String? = null,
    val finished: Int = 0,
  )

  private const val JOB = 1
  internal val state = MutableStateFlow(Status())
  val status: StateFlow<Status> = state

  private val pendingTitles = MutableStateFlow(0)

  /** How many titles the last [check] found a sync would fetch; the library badges its desktop button. */
  val pending: StateFlow<Int> = pendingTitles

  /**
   * Compares the paired desktop's titles with the download folder, and publishes the count as
   * [pending]. Reaches the desktop and reads files, so it runs off the main thread.
   *
   * @throws IllegalStateException without a download folder
   */
  fun check(context: Context, settings: Settings): SyncStatus {
    val tree = downloadFolder(settings) ?: error("Allow file access so titles can be copied into ${AppFolder.LABEL}")
    val titles = reachDesktop(context, settings, DesktopClient()::titles)
    val sync = SyncStatus.of(titles, settings.followed, transferFolder(context.contentResolver, tree))
    pendingTitles.value = sync.toSync().size
    return sync
  }

  /**
   * Starts a sync; [bytes] is the estimate the system is told. A sync already under way is left to
   * run: it goes round again if the followed series changed meanwhile.
   */
  fun start(context: Context, bytes: Long) {
    if (state.value.running) return
    val job =
      JobInfo.Builder(JOB, ComponentName(context, TransferJobService::class.java))
        .setUserInitiated(true)
        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
        .setEstimatedNetworkBytes(bytes, 0)
        .build()
    val scheduled = context.getSystemService(JobScheduler::class.java).schedule(job) == JobScheduler.RESULT_SUCCESS
    state.update {
      if (scheduled) it.copy(running = true, title = null, done = 0, total = 0, message = "Waiting for Wi-Fi…")
      else it.copy(running = false, message = "Android refused to start the download")
    }
  }

  /** Stops the download; what was fetched stays, and downloading again resumes it. */
  fun stop(context: Context) {
    context.getSystemService(JobScheduler::class.java).cancel(JOB)
    state.update { it.copy(running = false, message = "Stopped. Download again to resume.") }
  }
}

/** Runs [Transfers]' job on a worker thread, with a progress notification. */
class TransferJobService : JobService() {
  @Volatile private var stopped = false

  private val notifications by lazy { getSystemService(NotificationManager::class.java) }

  override fun onStartJob(params: JobParameters): Boolean {
    stopped = false
    notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads from the desktop", NotificationManager.IMPORTANCE_LOW))
    setNotification(params, NOTIFICATION, notification("Preparing…", 0, 0), JOB_END_NOTIFICATION_POLICY_REMOVE)
    thread(name = "transfer") { download(params) }
    return true
  }

  override fun onStopJob(params: JobParameters): Boolean {
    stopped = true
    val byApp = params.stopReason == JobParameters.STOP_REASON_CANCELLED_BY_APP || params.stopReason == JobParameters.STOP_REASON_USER
    if (!byApp) Transfers.state.update { it.copy(message = "Paused until the phone is back on Wi-Fi") }
    return !byApp
  }

  private fun notification(text: String, done: Long, total: Long): Notification {
    val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    val percent = if (total > 0) (done * 100 / total).toInt() else 0
    return Notification.Builder(this, CHANNEL)
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setContentTitle("Copying from the desktop")
      .setContentText(text)
      .setProgress(100, percent, total == 0L)
      .setOngoing(true)
      .setContentIntent(open)
      .build()
  }

  private fun download(params: JobParameters) {
    val settings = Settings(this)
    var reschedule = false
    try {
      val tree = downloadFolder(settings) ?: error("Allow file access so titles can be copied into ${AppFolder.LABEL}")
      val folder = transferFolder(contentResolver, tree)
      val client = DesktopClient()
      var count = 0
      // Following or unfollowing a series during a round changes what to fetch, so it goes round again.
      do {
        val followed = settings.followed
        // The desktop as reached, at a new address if it moved.
        val (desktop, titles) = reachDesktop(this, settings) { it to client.titles(it) }
        val plan = TransferPlan.of(SyncStatus.of(titles, followed, folder).toSync(), folder)
        download(client, desktop, plan, folder, tree)
        count += plan.titles().size
      } while (settings.followed != followed && !stopped)
      Transfers.state.update {
        it.copy(
          running = false,
          title = null,
          message = if (count == 0) "Everything is up to date" else "Copied $count ${if (count == 1) "title" else "titles"}",
        )
      }
    } catch (_: CancellationException) {
      return // Stopped: onStopJob has said why, and the system decides whether to run it again.
    } catch (failure: Exception) {
      Log.w("Transfers", "Download failed", failure)
      // A lost connection is retried when the network returns; a refusal, a full phone or a missing
      // pairing or folder (IllegalStateException) is not.
      reschedule = failure is IOException && failure !is DesktopException && !stopped
      Transfers.state.update {
        it.copy(
          running = reschedule,
          message =
            if (reschedule) "Connection lost; it will resume on Wi-Fi (${failure.message})"
            else "Download failed: ${failure.message ?: failure.javaClass.simpleName}",
        )
      }
    }
    if (!stopped) jobFinished(params, reschedule)
  }

  /** Downloads one round's [plan], after checking it fits in the phone's free space. */
  private fun download(client: DesktopClient, desktop: Desktop, plan: TransferPlan, folder: TransferFolder, tree: Uri) {
    val free = runCatching { LibraryUsage.free(this, listOf(tree)).firstOrNull() }.getOrNull()
    if (free != null && plan.remaining() > free)
      error("This needs ${LibraryUsage.format(plan.remaining())} but only ${LibraryUsage.format(free)} is free on the phone")
    var shown = 0L
    TitleDownloader(client).run(
      desktop,
      plan,
      folder,
      object : TitleDownloader.Listener {
        override fun progress(title: DesktopTitle, done: Long, total: Long) {
          val now = SystemClock.elapsedRealtime()
          if (now - shown < 500 && done < total) return
          shown = now
          Transfers.state.update { it.copy(running = true, title = title.title(), done = done, total = total, message = null) }
          val text = "${title.title()} · ${LibraryUsage.format(done)} of ${LibraryUsage.format(total)}"
          notifications.notify(NOTIFICATION, notification(text, done, total))
        }

        override fun finished(title: DesktopTitle) = Transfers.state.update { it.copy(finished = it.finished + 1) }

        override fun cancelled(): Boolean = stopped
      },
    )
  }

  private companion object {
    const val CHANNEL = "transfers"
    const val NOTIFICATION = 1
  }
}
