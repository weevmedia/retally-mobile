package com.retally.shared.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.retally.shared.db.ReTallyDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Android side of the shared SQLite database. The [SqlDriver] single is bound
 * here (it needs the app [android.content.Context]); common code builds
 * [ReTallyDatabase] on top of it. Wired into Koin via `extraModules` in
 * `ReTallyApplication`.
 */
val androidDatabaseModule = module {
    single<SqlDriver> {
        AndroidSqliteDriver(ReTallyDatabase.Schema, androidContext(), "retally.db")
    }
}
