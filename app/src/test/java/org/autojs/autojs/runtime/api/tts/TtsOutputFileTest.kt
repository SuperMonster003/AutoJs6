package org.autojs.autojs.runtime.api.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class TtsOutputFileTest {

    @Test
    fun successfulCommitPublishesOnlyCompletedStagingFile() {
        withTempDirectory { directory ->
            val target = File(directory, "voice.wav")
            val output = prepare(
                target.path,
                TtsFileOptions(overwrite = false),
            )

            assertFalse(target.exists())
            output.stagingFile.writeText("complete")
            output.commit()

            assertEquals("complete", target.readText())
            assertFalse(output.stagingFile.exists())
        }
    }

    @Test
    fun defaultOverwritePreservesOldFileUntilCommit() {
        withTempDirectory { directory ->
            val target = File(directory, "voice.wav").apply { writeText("old") }
            val output = prepare(
                target.path,
                TtsFileOptions(),
            )
            output.stagingFile.writeText("new")

            assertEquals("old", target.readText())
            output.commit()

            assertEquals("new", target.readText())
        }
    }

    @Test
    fun defaultOverwritePublishesANewDestination() {
        withTempDirectory { directory ->
            val target = File(directory, "voice.wav")
            val output = prepare(target.path, TtsFileOptions())
            output.stagingFile.writeText("new")

            output.commit()

            assertEquals("new", target.readText())
            assertFalse(output.stagingFile.exists())
        }
    }

    @Test
    fun discardRemovesPartialFileAndKeepsExistingTarget() {
        withTempDirectory { directory ->
            val target = File(directory, "voice.wav").apply { writeText("old") }
            val output = prepare(
                target.path,
                TtsFileOptions(overwrite = true),
            )
            output.stagingFile.writeText("partial")

            output.discard()

            assertFalse(output.stagingFile.exists())
            assertEquals("old", target.readText())
        }
    }

    @Test
    fun refusesAnEmptyEngineOutput() {
        withTempDirectory { directory ->
            val target = File(directory, "voice.wav")
            val output = prepare(target.path, TtsFileOptions())
            assertTrue(output.stagingFile.createNewFile())

            assertThrows(IllegalArgumentException::class.java, output::commit)

            assertFalse(target.exists())
            assertFalse(output.stagingFile.exists())
        }
    }

    @Test
    fun refusesExistingDestinationWithoutOverwrite() {
        withTempDirectory { directory ->
            val target = File(directory, "voice.wav").apply { writeText("old") }

            assertThrows(IllegalArgumentException::class.java) {
                prepare(target.path, TtsFileOptions(overwrite = false))
            }
            assertEquals("old", target.readText())
        }
    }

    @Test
    fun concurrentDestinationCreationIsLeftIntactWithoutOverwrite() {
        withTempDirectory { directory ->
            val target = File(directory, "voice.wav")
            val output = TtsOutputFile.prepare(
                target.path,
                TtsFileOptions(overwrite = false),
            ) { _, racedTarget, overwrite ->
                assertFalse(overwrite)
                racedTarget.writeText("other-writer")
                throw IOException(
                    "TTS output file already exists: ${racedTarget.absolutePath}",
                )
            }
            output.stagingFile.writeText("synthesized")

            assertThrows(IOException::class.java, output::commit)

            assertEquals("other-writer", target.readText())
            assertFalse(output.stagingFile.exists())
        }
    }

    @Test
    fun successfulPublicationCanDeferStagingCleanupWithoutFailingCommit() {
        withTempDirectory { directory ->
            val target = File(directory, "voice.wav")
            val output = TtsOutputFile.prepare(
                target.path,
                TtsFileOptions(overwrite = false),
            ) { staging, publishedTarget, _ ->
                staging.copyTo(publishedTarget)
                false
            }
            output.stagingFile.writeText("complete")

            assertFalse(output.commit())
            assertEquals("complete", target.readText())
            assertTrue(output.stagingFile.exists())

            output.discard()
            assertFalse(output.stagingFile.exists())
            assertEquals("complete", target.readText())
        }
    }

    @Test
    fun optionallyCreatesParentDirectories() {
        withTempDirectory { directory ->
            val nested = File(directory, "a/b/voice.wav")
            val output = prepare(
                nested.path,
                TtsFileOptions(createDirectories = true),
            )

            assertTrue(requireNotNull(nested.parentFile).isDirectory)
            output.discard()
        }
    }

    @Test
    fun refusesMissingParentWhenDirectoryCreationIsDisabled() {
        withTempDirectory { directory ->
            val target = File(directory, "missing/voice.wav")

            assertThrows(IllegalArgumentException::class.java) {
                prepare(
                    target.path,
                    TtsFileOptions(createDirectories = false),
                )
            }
            assertFalse(requireNotNull(target.parentFile).exists())
        }
    }

    @Test
    fun reservesCanonicalDestinationUntilTransactionFinishes() {
        withTempDirectory { directory ->
            val target = File(directory, "voice.wav")
            val first = prepare(
                File(directory, "./voice.wav").path,
                TtsFileOptions(),
            )

            assertThrows(IllegalArgumentException::class.java) {
                prepare(target.path, TtsFileOptions())
            }

            first.discard()
            prepare(target.path, TtsFileOptions()).discard()
        }
    }

    @Test
    fun failedCommitReleasesDestinationReservation() {
        withTempDirectory { directory ->
            val target = File(directory, "voice.wav")
            val output = prepare(target.path, TtsFileOptions())

            assertThrows(IllegalArgumentException::class.java) {
                output.commit()
            }

            prepare(target.path, TtsFileOptions()).discard()
        }
    }

    private fun prepare(path: String, options: TtsFileOptions): TtsOutputFile =
        TtsOutputFile.prepare(path, options, TEST_FILE_PUBLISHER)

    private fun withTempDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("autojs6-tts-output-test").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }

    private companion object {

        val TEST_FILE_PUBLISHER = TtsFilePublisher { staging, target, overwrite ->
            if (target.exists()) {
                if (!overwrite) {
                    throw java.io.IOException(
                        "TTS output file already exists: ${target.absolutePath}",
                    )
                }
                check(target.delete()) { "Unable to replace test output" }
            }
            check(staging.renameTo(target)) { "Unable to publish test output" }
            true
        }
    }
}
