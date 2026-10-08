package com.retally.shared.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.retally.shared.db.ReTallyDatabase
import org.koin.dsl.module

/**
 * iOS side of the shared SQLite database. The [SqlDriver] single is bound here;
 * common code builds [ReTallyDatabase] on top of it. Wired into Koin via
 * `extraModules` in `IosApp.start`.
 */
val iosDatabaseModule = module {
    single<SqlDriver> {
        NativeSqliteDriver(ReTallyDatabase.Schema, "retally.db")
    }
}
