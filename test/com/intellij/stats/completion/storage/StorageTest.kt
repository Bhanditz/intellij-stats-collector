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
        dir.deleteRecursively()
    }

    @Test
    fun test_three_new_files_created() {
        provider.getUniqueFile().createNewFile()
        provider.getUniqueFile().createNewFile()
        provider.getUniqueFile().createNewFile()
        
        val createdFiles = provider.getDataFiles().count()
        
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

        val chunks = filesProvider.getDataFiles()
        assertThat(chunks).hasSize(1)

        val fileLength = chunks.first().length()
        assertThat(fileLength).isLessThan(256 * 1024)
        assertThat(fileLength).isGreaterThan(200 * 1024)
    }
    
    @Test
    fun test_multiple_chunks() {
        writeKb(256)
        writeKb(256)

        val files = filesProvider.getDataFiles()
        assertThat(files).hasSize(2)
        assertThat(files[0].name.substringAfter('_').toInt()).isLessThan(files[1].name.substringAfter('_').toInt())
    }


    @Test
    fun test_delete_old_stuff() {
        writeKb(4096)

        var files = filesProvider.getDataFiles()
        assertThat(files).hasSize(4096 / 250)
        
        val firstBefore = files.map { it.name.substringAfter('_').toInt() }
                .sorted()
                .first()
        
        assertThat(firstBefore).isEqualTo(0)
        
        filesProvider.cleanupOldFiles()
        files = filesProvider.getDataFiles()
        assertThat(files).hasSize(2048 / 250)
        
        val firstAfter = files.map { it.name.substringAfter('_').toInt() }
                .sorted()
                .first()
        
        assertThat(firstAfter).isEqualTo(8)
    }

    private fun writeKb(kb: Int) {
        val bytesToWrite = 1024 * kb
        (0..bytesToWrite).forEach {
            fileLogger.println("")
        }
    }

}