package com.example.dailylife

import android.content.Context
import java.io.File

/** Native SQLite on Windows has a shorter path limit than Java; use a short, isolated test root. */
internal fun repositoryTestRoot(context: Context): File =
    System.getProperty("dailyLife.testRoot")?.let { File(it).apply { check(isDirectory || mkdirs()) } } ?: context.cacheDir

internal object RepositoryTestSchema {
    fun read(version: Int): String {
        val path="com.example.dailylife.data.local.DailyLifeDatabase/$version.json"
        val input=javaClass.classLoader!!.getResourceAsStream(path)
            ?: androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets.open(path)
        return input.bufferedReader().use {it.readText()}
    }
}
