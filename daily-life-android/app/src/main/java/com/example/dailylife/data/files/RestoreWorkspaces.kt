package com.example.dailylife.data.files

import java.io.File
import java.nio.file.Files
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID

/** One backup controller owns its previews, including a result lost during coroutine cancellation. */
class RestoreWorkspaceOwner(private val cache: File) {
    private val folders = mutableSetOf<File>()
    @Synchronized fun create(prefix: String): File = RestoreWorkspaces.create(cache, prefix).also { folders += it }
    @Synchronized fun discard(folder: File) {
        if (RestoreWorkspaces.discard(cache, folder)) folders -= folder
    }
    @Synchronized fun close() { folders.toList().forEach(::discard) }
}

/** Owns only UUID-named restore folders directly under the app cache. Never follows symlinks. */
object RestoreWorkspaces {
    private val active = mutableSetOf<String>()
    private val names = Regex("(?:restore-|plan-restore-|ledger-restore-|tools-restore-)[0-9a-f-]{36}")
    private const val MAX_AGE = 24 * 60 * 60 * 1000L

    @Synchronized fun create(cache: File, prefix: String): File {
        require(prefix in setOf("restore-", "plan-restore-", "ledger-restore-", "tools-restore-"))
        cleanup(cache)
        return File(cache, prefix + UUID.randomUUID()).apply {
            check(mkdirs()) { "无法创建恢复预览目录，请检查可用空间" }
            active += canonicalPath
        }
    }

    @Synchronized fun discard(cache: File, folder: File): Boolean {
        require(folder.name.matches(names) && folder.canonicalFile.parentFile == cache.canonicalFile) {
            "恢复缓存路径不属于此应用"
        }
        active -= folder.canonicalPath
        if (!folder.exists()) return true
        return runCatching {
            Files.walkFileTree(folder.toPath(), object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.delete(file)
                    return FileVisitResult.CONTINUE
                }
                override fun postVisitDirectory(dir: Path, exc: java.io.IOException?): FileVisitResult {
                    if (exc != null) throw exc
                    Files.delete(dir)
                    return FileVisitResult.CONTINUE
                }
            })
            true
        }.getOrDefault(false)
    }

    @Synchronized fun cleanup(cache: File, now: Long = System.currentTimeMillis()) {
        cache.listFiles()?.filter { it.name.matches(names) && now - it.lastModified() >= MAX_AGE }
            ?.forEach { folder ->
                if (folder.canonicalPath !in active) runCatching { discard(cache, folder) }
            }
    }
}
