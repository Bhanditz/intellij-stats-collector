package com.intellij.stats.completion.storage

import com.intellij.stats.completion.AsciiMessageCharStorage
import com.intellij.stats.completion.LogFileManager
import com.intellij.stats.completion.LogFileManagerImpl
import com.intellij.stats.completion.UniqueFilesProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileFilter


class FilesProviderTest {
    
    var root = File(".")
    
    lateinit var provider: UniqueFilesProvider

    @Before
    fun setUp() {
        provider = UniqueFilesProvider("chunk", root)
        removeAllFilesInStatsDataDirectory()
    }

    @After
    fun tearDown() {
        removeAllFilesInStatsDataDirectory()
    }

    private fun removeAllFilesInStatsDataDirectory() {
        val dir = provider.getStatsDataDirectory()
        dir.listFiles(FileFilter { it.isFile }).forEach { it.delete() }
    }

    @Test
    fun test_three_new_files_created() {
        val provider = UniqueFilesProvider("chunk", root)
        
        provider.getUniqueFile().createNewFile()
        provider.getUniqueFile().createNewFile()
        provider.getUniqueFile().createNewFile()

        val directory = provider.getStatsDataDirectory()
        val createdFiles = directory
                .listFiles(FileFilter { it.isFile })
                .filter { it.name.startsWith("chunk") }
                .count()
        
        assertThat(createdFiles).isEqualTo(3)
    }
}


class AsciiMessageStorageTest {
    
    lateinit var storage: AsciiMessageCharStorage
    lateinit var tmpFile: File

    @Before
    fun setUp() {
        storage = AsciiMessageCharStorage()
        tmpFile = File("tmp_test")
        tmpFile.delete()
    }

    @After
    fun tearDown() {
        tmpFile.delete()
    }

    @Test
    fun test_size_with_new_lines() {
        storage.appendLine("text")
        assertThat(storage.sizeWithNewLine("")).isEqualTo(6)
    }
    
    @Test
    fun test_size_is_same_as_file_size() {
        storage.appendLine("text")
        storage.appendLine("text")
        assertThat(storage.size).isEqualTo(10)

        storage.dump(tmpFile)
        assertThat(tmpFile.length()).isEqualTo(10)
    }
    
    
}



class FileLoggerTest {
    
    lateinit var fileLogger: LogFileManager
    lateinit var filesProvider: UniqueFilesProvider

    @Before
    fun setUp() {
        filesProvider = UniqueFilesProvider("chunk", File("."))
        fileLogger = LogFileManagerImpl(filesProvider)
    }

    @After
    fun tearDown() {
        val dir = filesProvider.getStatsDataDirectory()
        dir.deleteRecursively()
    }

    @Test
    fun test_chunk_is_around_256Kb() {
        val bytesToWrite = 1024 * 256
        (0..bytesToWrite).forEach {
            fileLogger.println("")
        }

        val rootDir = filesProvider.getStatsDataDirectory()
        val chunks = rootDir.listFiles(FileFilter { it.isFile }).filter { it.name.startsWith("chunk") }
        assertThat(chunks).hasSize(1)

        val fileLength = chunks.first().length()
        assertThat(fileLength).isLessThan(256 * 1024)
        assertThat(fileLength).isGreaterThan(200 * 1024)
    }
    
    @Test
    fun test_multiple_chunks() {
        val bytesToWrite = 2 * 1024 * 256
        (0..bytesToWrite).forEach {
            fileLogger.println("")
        }

        val rootDir = filesProvider.getStatsDataDirectory()
        val chunks = rootDir.listFiles(FileFilter { it.isFile }).filter { it.name.startsWith("chunk") }
        assertThat(chunks).hasSize(2)
    }
    
}