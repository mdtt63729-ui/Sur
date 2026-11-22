package com.manikdev.spotify.nightly.di

import com.manikdev.spotify.nightly.viewModel.AlbumViewModel
import com.manikdev.spotify.nightly.utils.VersionManager
import com.manikdev.spotify.nightly.viewModel.AnalyticsViewModel
import com.manikdev.spotify.nightly.viewModel.ListenTogetherSettingsViewModel
import com.manikdev.spotify.nightly.viewModel.ListenTogetherViewModel
import com.manikdev.spotify.nightly.viewModel.ArtistViewModel
import com.manikdev.spotify.nightly.viewModel.BrowseViewModel
import com.manikdev.spotify.nightly.viewModel.BrowserLoginViewModel
import com.manikdev.spotify.nightly.viewModel.CommentsViewModel
import com.manikdev.spotify.nightly.viewModel.SoundCloudPlayerViewModel
import com.manikdev.spotify.nightly.viewModel.HomeViewModel
import com.manikdev.spotify.nightly.viewModel.ImportViewModel
import com.manikdev.spotify.nightly.viewModel.LibraryDynamicPlaylistViewModel
import com.manikdev.spotify.nightly.viewModel.LibrarySidebarViewModel
import com.manikdev.spotify.nightly.viewModel.LibraryViewModel
import com.manikdev.spotify.nightly.viewModel.LocalPlaylistViewModel
import com.manikdev.spotify.nightly.viewModel.LibrarySyncHostViewModel
import com.manikdev.spotify.nightly.viewModel.LibrarySyncPhoneViewModel
import com.manikdev.spotify.nightly.viewModel.LogInViewModel
import com.manikdev.spotify.nightly.viewModel.LoginSyncHostViewModel
import com.manikdev.spotify.nightly.viewModel.LoginSyncSenderViewModel
import com.manikdev.spotify.nightly.viewModel.MoodViewModel
import com.manikdev.spotify.nightly.viewModel.MoreAlbumsViewModel
import com.manikdev.spotify.nightly.viewModel.NotificationViewModel
import com.manikdev.spotify.nightly.viewModel.NowPlayingBottomSheetViewModel
import com.manikdev.spotify.nightly.viewModel.PlaylistViewModel
import com.manikdev.spotify.nightly.viewModel.PodcastViewModel
import com.manikdev.spotify.nightly.viewModel.SearchViewModel
import com.manikdev.spotify.nightly.viewModel.AutoEqViewModel
import com.manikdev.spotify.nightly.viewModel.SettingsViewModel
import com.manikdev.spotify.nightly.viewModel.SharedViewModel
import com.manikdev.spotify.nightly.viewModel.SongSelectionViewModel
import com.manikdev.spotify.nightly.viewModel.TasteViewModel
import com.manikdev.spotify.nightly.viewModel.WrappedViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val viewModelModule =
    module {
        single {
            SharedViewModel(
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        single {
            SearchViewModel(
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            SongSelectionViewModel(
                get(),
                get(),
            )
        }
        // Only one of these resolves on a given platform: Desktop binds the host repository, Android the
        // sender. Koin resolves dependencies when a ViewModel is created, not when it is declared.
        viewModel { LoginSyncHostViewModel(get()) }
        viewModel { LoginSyncSenderViewModel(get(), get()) }
        viewModel { LibrarySyncHostViewModel(get()) }
        viewModel { LibrarySyncPhoneViewModel(get()) }
        viewModel { BrowserLoginViewModel(get()) }
        viewModel { CommentsViewModel(get(), get()) }
        viewModel { SoundCloudPlayerViewModel(get()) }
        viewModel {
            NowPlayingBottomSheetViewModel(
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            LibraryViewModel(
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            LibraryDynamicPlaylistViewModel(
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            ImportViewModel(
                get(),
            )
        }
        viewModel {
            AlbumViewModel(
                get(),
                get(),
            )
        }
        viewModel {
            HomeViewModel(
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            AutoEqViewModel(
                get(),
                get(),
            )
        }
        viewModel {
            SettingsViewModel(
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            ArtistViewModel(
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            PlaylistViewModel(
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            LogInViewModel(
                get(),
                get(),
            )
        }
        viewModel {
            PodcastViewModel(
                get(),
            )
        }
        viewModel {
            MoreAlbumsViewModel(
                get(),
            )
        }
        viewModel {
            BrowseViewModel(
                get(),
            )
        }
        viewModel {
            LibrarySidebarViewModel(
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            LocalPlaylistViewModel(
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            NotificationViewModel(
                get(),
            )
        }
        viewModel {
            MoodViewModel(
                get(),
                get(),
            )
        }
        viewModel {
            AnalyticsViewModel(
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            WrappedViewModel(
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            TasteViewModel(
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel {
            ListenTogetherSettingsViewModel(get())
        }
        viewModel {
            ListenTogetherViewModel(
                repository = get(),
                dataStore = get(),
                bridge = get(),
            )
        }

    }
