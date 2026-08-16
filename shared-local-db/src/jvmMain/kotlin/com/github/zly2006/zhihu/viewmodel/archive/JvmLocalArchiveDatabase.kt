/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.zly2006.zhihu.viewmodel.archive

import androidx.room.Room
import java.io.File

fun desktopLocalArchiveDatabaseFile(): File =
    File(File(System.getProperty("user.home"), ".zhihu-plus"), "local-archive.db")

private val desktopLocalArchiveDatabase by lazy {
    getLocalArchiveDatabase(desktopLocalArchiveDatabaseFile().also { it.parentFile?.mkdirs() })
}

actual fun getLocalArchiveDatabase(): LocalArchiveDatabase = desktopLocalArchiveDatabase

fun getLocalArchiveDatabase(databaseFile: File): LocalArchiveDatabase =
    buildLocalArchiveDatabase(
        Room.databaseBuilder<LocalArchiveDatabase>(
            name = databaseFile.absolutePath,
        ),
    )
