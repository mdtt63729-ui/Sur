package com.manikdev.spotify.nightly

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.eygraber.uri.toKmpUriOrNull
import com.manikdev.common.FIRST_TIME_MIGRATION
import com.manikdev.common.SELECTED_LANGUAGE
import com.manikdev.common.STATUS_DONE
import com.manikdev.common.SUPPORTED_LANGUAGE
import com.manikdev.common.SUPPORTED_LOCATION
import com.manikdev.domain.data.model.intent.GenericIntent
import com.manikdev.domain.manager.DataStoreManager
import com.manikdev.domain.mediaservice.handler.MediaPlayerHandler
import com.manikdev.domain.mediaservice.handler.ToastType
import com.manikdev.domain.repository.LibrarySyncPhoneRepository
import com.maxrave.logger.Logger
import com.manikdev.media3.di.setServiceActivitySession
import com.manikdev.spotify.nightly.di.viewModelModule
import com.manikdev.spotify.nightly.service.rss.RssFeedNotifyWork
import com.manikdev.spotify.nightly.service.test.notification.NotifyWork
import com.manikdev.spotify.nightly.utils.ComposeResUtils
import com.manikdev.spotify.nightly.update.AndroidUpdateController
import com.manikdev.spotify.nightly.utils.VersionManager
import com.manikdev.spotify.nightly.ui.component.UpdateHost
import com.manikdev.spotify.nightly.viewModel.SharedViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module
import org.spotify.crashlytics.pushPlayerError
import pub.devrel.easypermissions.EasyPermissions
import java.util.Locale
import java.util.concurrent.TimeUnit

// Process-wide, so a recreated activity (rotation, theme change) does not count as opening the app.
private var lastLibrarySyncAt = 0L

// In-app updater: checks the release feed, downloads the APK and hands it to the installer.
private lateinit var updateController: AndroidUpdateController

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {
    val viewModel: SharedViewModel by inject()
    val mediaPlayerHandler by inject<MediaPlayerHandler>()
    val dataStoreManager: DataStoreManager by inject()
    private val librarySync: LibrarySyncPhoneRepository by inject()

    private var mBound = false
    private var shouldUnbind = false
    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
//                mediaPlayerHandler.setActivitySession(this@MainActivity, MainActivity::class.java, service)
                setServiceActivitySession(this@MainActivity, MainActivity::class.java, service)
                Logger.w("MainActivity", "onServiceConnected: ")
                mBound = true
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Logger.w("MainActivity", "onServiceDisconnected: ")
                mBound = false
            }
        }

    override fun onStart() {
        super.onStart()
        startMusicService()
        // Every return to the screen asks; the pass itself runs at most once a day.
        viewModel.runSmartDownloadsIfDue()
        syncLibraryIfDue()
    }

    // ponytail: a fixed 5-minute gap between app-open rounds, since a computer that is off costs a
    // LAN sweep each time; WorkManager covers the rest every 15 minutes, and "Sync now" ignores it.
    private fun syncLibraryIfDue() {
        val now = SystemClock.elapsedRealtime()
        if (lastLibrarySyncAt != 0L && now - lastLibrarySyncAt < 5 * 60_000L) return
        lastLibrarySyncAt = now
        lifecycleScope.launch { librarySync.syncNow() }
    }

    override fun onStop() {
        super.onStop()
        if (shouldUnbind) {
            unbindService(serviceConnection)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Logger.d("MainActivity", "onNewIntent: $intent")
        viewModel.setIntent(
            GenericIntent(
                action = intent.action,
                data = (intent.data ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.toUri())?.toKmpUriOrNull(),
                type = intent.type,
            ),
        )
    }

    @ExperimentalFoundationApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableHighRefreshRate()
        // Frame timing, so the next lag report is measured rather than guessed.
        FrameMonitor.start()
        // High performance mode: while it is on, the panel stays pinned to its highest refresh
        // mode for the whole session rather than only during the first few seconds of scrolling.
        // Read once, not collected: a live collector here re-applied the stored value on every
        // emission and could flip the switch back off right after the user turned it on. From then
        // on the switch itself owns the state, and this only restores it at launch.
        lifecycleScope.launch {
            val stored = dataStoreManager.highPerformanceMode.first()
            applyHighPerformanceMode(stored == DataStoreManager.TRUE)
        }
        loadKoinModules(
            module {
                single<AppCompatActivity> { this@MainActivity }
            },
        )
        // Recreate view model to fix the issue of view model not getting data from the service
        unloadKoinModules(viewModelModule)
        loadKoinModules(viewModelModule)
        VersionManager.initialize()
        updateController = AndroidUpdateController(this, VersionManager.getVersionName())
        com.manikdev.spotify.nightly.update.updateHostController = updateController
        requestInstallPermission()
        updateController.start()
        // The legacy popup check hits the same GitHub release feed as the updater above. Firing both
        // in onCreate put two network round trips and two possible dialogs on top of the first
        // frame - the coldest, most expensive frame the app draws. The updater answers first; this
        // one waits until the app has settled, and finds nothing new when the updater already
        // showed its sheet.
        lifecycleScope.launch {
            delay(5_000)
            checkForUpdate()
        }
        if (viewModel.recreateActivity.value || viewModel.isServiceRunning) {
            viewModel.activityRecreateDone()
        } else {
            startMusicService()
        }
        Logger.d("MainActivity", "onCreate: ")
        val data = (intent?.data ?: intent?.getStringExtra(Intent.EXTRA_TEXT)?.toUri())?.toKmpUriOrNull()
        if (data != null) {
            viewModel.setIntent(
                GenericIntent(
                    action = intent.action,
                    data = data,
                    type = intent.type,
                ),
            )
        }
        Logger.d("Italy", "Key: ${Locale.ITALY.toLanguageTag()}")

        // Check if the migration has already been done or not
        if (getString(FIRST_TIME_MIGRATION) != STATUS_DONE) {
            Logger.d("Locale Key", "onCreate: ${Locale.getDefault().toLanguageTag()}")
            if (SUPPORTED_LANGUAGE.codes.contains(Locale.getDefault().toLanguageTag())) {
                Logger.d(
                    "Contains",
                    "onCreate: ${
                        SUPPORTED_LANGUAGE.codes.contains(
                            Locale.getDefault().toLanguageTag(),
                        )
                    }",
                )
                putString(SELECTED_LANGUAGE, Locale.getDefault().toLanguageTag())
                if (SUPPORTED_LOCATION.items.contains(Locale.getDefault().country)) {
                    putString("location", Locale.getDefault().country)
                } else {
                    putString("location", "US")
                }
            } else {
                putString(SELECTED_LANGUAGE, "en-US")
            }
            // Fetch the selected language from wherever it was stored. In this case its SharedPref
            getString(SELECTED_LANGUAGE)?.let {
                Logger.d("Locale Key", "getString: $it")
                // Set this locale using the AndroidX library that will handle the storage itself
                val localeList = LocaleListCompat.forLanguageTags(it)
                AppCompatDelegate.setApplicationLocales(localeList)
                // Set the migration flag to ensure that this is executed only once
                putString(FIRST_TIME_MIGRATION, STATUS_DONE)
            }
        }
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() !=
            getString(
                SELECTED_LANGUAGE,
            )
        ) {
            Logger.d(
                "Locale Key",
                "onCreate: ${AppCompatDelegate.getApplicationLocales().toLanguageTags()}",
            )
            putString(SELECTED_LANGUAGE, AppCompatDelegate.getApplicationLocales().toLanguageTags())
        }

        enableEdgeToEdge(
            navigationBarStyle =
                SystemBarStyle.dark(
                    scrim = Color.Transparent.toArgb(),
                ),
            statusBarStyle =
                SystemBarStyle.dark(
                    scrim = Color.Transparent.toArgb(),
                ),
        )
        viewModel.checkIsRestoring()
        val request =
            PeriodicWorkRequestBuilder<NotifyWork>(
                12L,
                TimeUnit.HOURS,
            ).addTag("Worker Test")
                .setConstraints(
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "Artist Worker",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
        lifecycleScope.launch {
            dataStoreManager.blogNotificationEnabled.collect { enabled ->
                if (enabled == DataStoreManager.TRUE) {
                    val rssRequest =
                        PeriodicWorkRequestBuilder<RssFeedNotifyWork>(
                            24L,
                            TimeUnit.HOURS,
                        ).addTag("Blog RSS Worker")
                            .setConstraints(
                                Constraints
                                    .Builder()
                                    .setRequiredNetworkType(NetworkType.CONNECTED)
                                    .build(),
                            ).build()
                    WorkManager.getInstance(this@MainActivity).enqueueUniquePeriodicWork(
                        "Blog RSS Worker",
                        ExistingPeriodicWorkPolicy.KEEP,
                        rssRequest,
                    )
                } else {
                    WorkManager.getInstance(this@MainActivity).cancelUniqueWork("Blog RSS Worker")
                }
            }
        }

        if (!EasyPermissions.hasPermissions(this, Manifest.permission.POST_NOTIFICATIONS)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val doNotAsk = getString("notification_permission_do_not_ask")
                if (doNotAsk != "true") {
                    val wasAsked = getString("notification_permission_asked")
                    if (wasAsked != "true") {
                        // First time: request system permission
                        EasyPermissions.requestPermissions(
                            this,
                            runBlocking { ComposeResUtils.getResString(ComposeResUtils.StringType.NOTIFICATION_REQUEST) },
                            1,
                            Manifest.permission.POST_NOTIFICATIONS,
                        )
                        putString("notification_permission_asked", "true")
                    } else {
                        // Already asked before: show custom dialog with "Don't show again"
                        viewModel.showNotificationPermissionDialog()
                    }
                }
            }
        }
        viewModel.getLocation()

        if (!BuildConfig.DEBUG) viewModel.checkOfficialBuild(packageName, signingCertSha256())
        setContent {
            // The performance readout sits above the app, so it measures what you are looking at.
            androidx.compose.foundation.layout.Box {
                App(viewModel)
                // The update sheet, hosted once for the whole app. Until now UpdateHost existed
                // but nothing called it, so the update popup could never appear.
                com.manikdev.spotify.nightly.ui.component.UpdateHost(
                    controller = updateController,
                )
                com.manikdev.spotify.nightly.ui.component.PerfOverlay(
                    modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.TopEnd),
                )
            }
            // The launcher icon is an adaptive XML icon on modern Android and painterResource
            // refuses to decode those ("Only VectorDrawables and rasterized asset types are
            // supported"). Ask the PackageManager for the icon and rasterise it instead.
            val appIcon =
                remember {
                    runCatching {
                        packageManager.getApplicationIcon(packageName).toBitmap(144, 144).asImageBitmap()
                    }.getOrNull()
                }
            UpdateHost(
                controller = updateController,
                appName = "Spotify",
                currentVersion = VersionManager.getVersionName(),
                icon = appIcon,
            )
        }
    }

    override fun onDestroy() {
        val shouldStopMusicService = viewModel.shouldStopMusicService()
        Logger.w("MainActivity", "onDestroy: Should stop service $shouldStopMusicService")

        // Always unbind service if it was bound to prevent MusicBinder leak
        if (shouldStopMusicService && shouldUnbind && isFinishing) {
            viewModel.isServiceRunning = false
        }
        unloadKoinModules(viewModelModule)
        super.onDestroy()
        Logger.d("MainActivity", "onDestroy: ")
    }

    override fun onRestart() {
        super.onRestart()
        viewModel.activityRecreate()
    }

    private fun startMusicService() {
//        mediaPlayerHandler.startMediaService(this, serviceConnection)
        com.manikdev.media3.di
            .startService(this@MainActivity, serviceConnection)
        mediaPlayerHandler.pushPlayerError = { it ->
            pushPlayerError(it)
        }
        mediaPlayerHandler.showToast = { type ->
            viewModel.makeToast(
                when (type) {
                    is ToastType.ExplicitContent -> {
                        runBlocking { ComposeResUtils.getResString(ComposeResUtils.StringType.EXPLICIT_CONTENT_BLOCKED) }
                    }

                    is ToastType.PlayerError -> {
                        runBlocking { ComposeResUtils.getResString(ComposeResUtils.StringType.TIME_OUT_ERROR, type.error) }
                    }

                    is ToastType.SponsorBlockSkip -> {
                        runBlocking { ComposeResUtils.getResString(ComposeResUtils.StringType.SPONSOR_BLOCK_SKIP, type.category) }
                    }
                },
            )
        }
        viewModel.isServiceRunning = true
        shouldUnbind = true
        Logger.d("Service", "Service started")
    }

    private fun checkForUpdate() {
        if (viewModel.shouldCheckForUpdate()) {
            viewModel.checkForUpdate()
        }
    }

    private fun putString(
        key: String,
        value: String,
    ) {
        viewModel.putString(key, value)
    }

    private fun getString(key: String): String? = viewModel.getString(key)

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        viewModel.activityRecreate()
    }

    /**
     * Ask the display for its highest refresh rate (up to 120 Hz).
     *
     * Compose redraws at the display rate, so leaving the window on the default 60 Hz mode
     * makes every scroll and every ripple look like it stutters on a 120 Hz panel. Pinning
     * the fastest supported mode keeps the whole UI on the fast path.
     */
    /**
     * Pins the display to its fastest mode, or releases the pin.
     *
     * `preferredDisplayModeId` is the switch that actually holds a 120Hz panel at 120Hz; the
     * refresh-rate float alone is only a hint on many vendors' builds. Releasing sets both back
     * to 0, which is Android's "no preference" and lets the system go back to adaptive.
     */
    private fun applyHighPerformanceMode(enabled: Boolean) {
        // The renderer reads this to drop its most expensive passes.
        com.manikdev.spotify.nightly.expect.ui.PerformanceSettings.highPerformanceMode = enabled
        // Switching high performance mode OFF used to reset the display mode to the system default
        // right here, which undid enableHighRefreshRate() a few lines above: the panel dropped back
        // to its default mode (60Hz on a 120Hz phone) and every scroll ran at half the display's
        // rate, with nothing in the code looking wrong. The switch drops expensive render passes;
        // it is not supposed to give up the panel's refresh rate.
        if (!enabled) {
            Logger.w("MainActivity", "high performance mode: false")
            return
        }
        runCatching {
            @Suppress("DEPRECATION")
            val current = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else windowManager.defaultDisplay
            val best = current?.supportedModes?.maxByOrNull { it.refreshRate } ?: return
            window.attributes =
                window.attributes.apply {
                    preferredDisplayModeId = best.modeId
                    preferredRefreshRate = best.refreshRate
                }
            Logger.w("MainActivity", "high performance mode: true (${best.refreshRate}Hz)")
        }.onFailure { Logger.w("MainActivity", "high performance mode: ${it.message}") }
    }

    private fun enableHighRefreshRate() {
        runCatching {
            @Suppress("DEPRECATION")
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else windowManager.defaultDisplay
            val best = display?.supportedModes?.maxByOrNull { it.refreshRate } ?: return
            window.attributes =
                window.attributes.apply {
                    preferredDisplayModeId = best.modeId
                    preferredRefreshRate = best.refreshRate
                }
            // The two above are a request the OEM is free to ignore, and ColorOS does: the frame
            // log sat at 53-60fps on a 120Hz panel, which is why every scroll and animation felt
            // heavy. Window.setFrameRate is the call the platform actually honours. It would not
            // resolve against this compileSdk, so it is reached by reflection - guarded, and a
            // no-op on any device that does not have it.
            runCatching {
                val method =
                    window.javaClass.getMethod(
                        "setFrameRate",
                        Float::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                    )
                method.invoke(window, best.refreshRate, 0)
                Logger.w("MainActivity", "setFrameRate: ${best.refreshRate}")
            }
        }.onFailure { Logger.w("MainActivity", "refresh rate: ${it.message}") }
    }

    /**
     * The updater installs APKs from inside the app, which needs the "install unknown apps"
     * permission. Ask for it once at startup so a download never dead-ends on a settings screen
     * the user did not expect.
     */
    private fun requestInstallPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            if (!packageManager.canRequestPackageInstalls()) {
                startActivity(
                    Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        android.net.Uri.parse("package:$packageName"),
                    ),
                )
            }
        }.onFailure { Logger.w("MainActivity", "install permission: ${it.message}") }
    }
}
