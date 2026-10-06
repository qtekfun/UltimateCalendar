// SPDX-FileCopyrightText: 2026 UltimateCalendar contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatecalendar.di

import android.content.Context
import android.content.SharedPreferences
import com.qtekfun.ultimatecalendar.data.auth.AccountSession
import com.qtekfun.ultimatecalendar.data.auth.AndroidKeystoreCipher
import com.qtekfun.ultimatecalendar.data.auth.LoginStorage
import com.qtekfun.ultimatecalendar.data.auth.PreferencesLoginStorage
import com.qtekfun.ultimatecalendar.data.auth.SecretCipher
import com.qtekfun.ultimatecalendar.data.remote.CredentialsProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    /** Credentials are encrypted with a key that lives in Android Keystore. */
    @Binds
    abstract fun secretCipher(cipher: AndroidKeystoreCipher): SecretCipher

    @Binds
    abstract fun loginStorage(storage: PreferencesLoginStorage): LoginStorage

    /** Network clients read the signed-in account's app password from the session. */
    @Binds
    abstract fun credentials(session: AccountSession): CredentialsProvider

    companion object {
        @Provides
        @Named(PreferencesLoginStorage.FILE)
        fun loginPreferences(@ApplicationContext context: Context): SharedPreferences =
            context.getSharedPreferences(PreferencesLoginStorage.FILE, Context.MODE_PRIVATE)
    }
}
