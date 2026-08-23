package org.autojs.autojs.core.plugin.python

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PythonHostCapabilityDispatcherTest {
    @Test
    fun dispatchesEnabledCapabilitiesWithTypedJsonResults() {
        val actions = RecordingActions()
        actions.fileTexts["seed.txt"] = "seed"
        val dispatcher = dispatcher(actions)

        assertNull(success(dispatcher.dispatch(request(1, PythonHostCapabilities.TOAST_SHOW, "text" to "hello"))))
        assertNull(success(dispatcher.dispatch(request(2, PythonHostCapabilities.CLIP_SET, "text" to "copy"))))
        assertEquals("copy", success(dispatcher.dispatch(request(3, PythonHostCapabilities.CLIP_GET))))
        assertEquals(
            true,
            success(dispatcher.dispatch(request(4, PythonHostCapabilities.APP_LAUNCH, "package" to "org.example.app"))),
        )
        assertEquals(
            true,
            success(dispatcher.dispatch(request(5, PythonHostCapabilities.APP_LAUNCH_APP, "name" to "Example"))),
        )
        assertEquals(
            true,
            success(dispatcher.dispatch(request(6, PythonHostCapabilities.APP_OPEN_URL, "url" to "https://example.org"))),
        )
        assertEquals(
            DEVICE_INFO.toJson(),
            success(dispatcher.dispatch(request(7, PythonHostCapabilities.DEVICE_INFO))),
        )
        assertNull(success(dispatcher.dispatch(request(8, PythonHostCapabilities.CONSOLE_LOG, "text" to "log"))))
        assertNull(success(dispatcher.dispatch(request(9, PythonHostCapabilities.CONSOLE_WARN, "text" to "warn"))))
        assertNull(success(dispatcher.dispatch(request(10, PythonHostCapabilities.CONSOLE_ERROR, "text" to "error"))))
        assertNull(success(dispatcher.dispatch(request(11, PythonHostCapabilities.NOTICE_SHOW, "text" to "notice"))))
        assertEquals(
            "seed",
            success(dispatcher.dispatch(request(12, PythonHostCapabilities.FILES_READ_TEXT, "path" to "seed.txt"))),
        )
        assertNull(
            success(
                dispatcher.dispatch(
                    request(
                        13,
                        PythonHostCapabilities.FILES_WRITE_TEXT,
                        "path" to "written.txt",
                        "text" to "written",
                    ),
                ),
            ),
        )
        assertEquals(
            COLOR_MATCH.toJson(),
            success(
                dispatcher.dispatch(
                    request(
                        38,
                        PythonHostCapabilities.IMAGES_FIND_COLOR,
                        "color" to 0x123456,
                        "threshold" to 2,
                        "region" to mapOf(
                            "x" to 10,
                            "y" to 20,
                            "width" to 100,
                            "height" to 200,
                        ),
                    ),
                ),
            ),
        )
        assertEquals(
            true,
            success(dispatcher.dispatch(request(14, PythonHostCapabilities.FILES_EXISTS, "path" to "written.txt"))),
        )
        assertEquals(
            true,
            success(dispatcher.dispatch(request(15, PythonHostCapabilities.FILES_IS_FILE, "path" to "written.txt"))),
        )
        assertEquals(
            true,
            success(dispatcher.dispatch(request(16, PythonHostCapabilities.FILES_IS_DIR, "path" to "."))),
        )
        assertEquals(
            listOf("seed.txt", "written.txt"),
            success(dispatcher.dispatch(request(17, PythonHostCapabilities.FILES_LIST, "path" to "."))),
        )
        assertNull(
            success(
                dispatcher.dispatch(
                    request(
                        18,
                        PythonHostCapabilities.DIALOGS_ALERT,
                        "title" to "Alert",
                        "text" to "alert text",
                    ),
                ),
            ),
        )
        assertEquals(
            true,
            success(
                dispatcher.dispatch(
                    request(
                        19,
                        PythonHostCapabilities.DIALOGS_CONFIRM,
                        "title" to "Confirm",
                        "text" to "confirm text",
                    ),
                ),
            ),
        )
        assertEquals(
            "typed value",
            success(
                dispatcher.dispatch(
                    request(
                        20,
                        PythonHostCapabilities.DIALOGS_PROMPT,
                        "title" to "Prompt",
                        "text" to "prompt text",
                        "default" to "seed",
                    ),
                ),
            ),
        )
        assertEquals(
            1,
            success(
                dispatcher.dispatch(
                    request(
                        21,
                        PythonHostCapabilities.DIALOGS_SELECT,
                        "title" to "Select",
                        "items" to listOf("first", "second"),
                    ),
                ),
            ),
        )
        assertEquals(
            ENGINE_INFO.toJson(),
            success(dispatcher.dispatch(request(22, PythonHostCapabilities.ENGINES_CURRENT))),
        )
        assertEquals(
            ENGINE_LAUNCH.toJson(),
            success(
                dispatcher.dispatch(
                    request(23, PythonHostCapabilities.ENGINES_RUN, "path" to "child.js"),
                ),
            ),
        )
        assertNull(success(dispatcher.dispatch(request(24, PythonHostCapabilities.ENGINES_STOP_SELF))))
        assertEquals(
            true,
            success(
                dispatcher.dispatch(
                    request(25, PythonHostCapabilities.AUTOMATOR_CLICK, "x" to 120, "y" to 240),
                ),
            ),
        )
        assertEquals(
            true,
            success(
                dispatcher.dispatch(
                    request(26, PythonHostCapabilities.AUTOMATOR_LONG_CLICK, "x" to 121, "y" to 241),
                ),
            ),
        )
        assertEquals(
            true,
            success(
                dispatcher.dispatch(
                    request(
                        27,
                        PythonHostCapabilities.AUTOMATOR_PRESS,
                        "x" to 122,
                        "y" to 242,
                        "durationMillis" to 75,
                    ),
                ),
            ),
        )
        assertEquals(
            true,
            success(
                dispatcher.dispatch(
                    request(
                        28,
                        PythonHostCapabilities.AUTOMATOR_SWIPE,
                        "x1" to 10,
                        "y1" to 20,
                        "x2" to 110,
                        "y2" to 220,
                        "durationMillis" to 300,
                    ),
                ),
            ),
        )
        assertEquals(true, success(dispatcher.dispatch(request(29, PythonHostCapabilities.AUTOMATOR_BACK))))
        assertEquals(true, success(dispatcher.dispatch(request(30, PythonHostCapabilities.AUTOMATOR_HOME))))
        assertEquals(
            SELECTOR_SNAPSHOT.toJson(),
            success(
                dispatcher.dispatch(
                    request(
                        31,
                        PythonHostCapabilities.SELECTOR_SNAPSHOT,
                        "maxNodes" to 64,
                        "maxDepth" to 16,
                    ),
                ),
            ),
        )
        assertEquals(
            SELECTOR_NODE.toJson(),
            success(
                dispatcher.dispatch(
                    request(
                        32,
                        PythonHostCapabilities.SELECTOR_FIND,
                        "query" to linkedMapOf(
                            "textContains" to "smoke",
                            "clickable" to true,
                        ),
                        "maxNodes" to 512,
                        "maxDepth" to 32,
                    ),
                ),
            ),
        )
        assertEquals(
            true,
            success(
                dispatcher.dispatch(
                    request(33, PythonHostCapabilities.SELECTOR_CLICK, "nodeId" to SELECTOR_NODE.id),
                ),
            ),
        )
        assertEquals(
            true,
            success(
                dispatcher.dispatch(
                    request(
                        34,
                        PythonHostCapabilities.SELECTOR_SET_TEXT,
                        "nodeId" to SELECTOR_NODE.id,
                        "text" to "updated",
                    ),
                ),
            ),
        )
        assertEquals(
            SCREEN_IMAGE.toJson(),
            success(
                dispatcher.dispatch(
                    request(
                        35,
                        PythonHostCapabilities.IMAGES_CAPTURE_SCREEN,
                        "format" to "png",
                        "quality" to 100,
                    ),
                ),
            ),
        )
        assertEquals(
            SCREEN_CHUNK.toJson(),
            success(
                dispatcher.dispatch(
                    request(
                        36,
                        PythonHostCapabilities.IMAGES_READ_CHUNK,
                        "imageId" to SCREEN_IMAGE.id,
                        "offset" to 0,
                    ),
                ),
            ),
        )
        assertNull(
            success(
                dispatcher.dispatch(
                    request(
                        37,
                        PythonHostCapabilities.IMAGES_RELEASE,
                        "imageId" to SCREEN_IMAGE.id,
                    ),
                ),
            ),
        )

        assertEquals(listOf("hello"), actions.toasts)
        assertEquals("copy", actions.clipboard)
        assertEquals(listOf("org.example.app"), actions.packages)
        assertEquals(listOf("Example"), actions.apps)
        assertEquals(listOf("https://example.org"), actions.urls)
        assertEquals(
            listOf(
                PythonHostConsoleLevel.LOG to "log",
                PythonHostConsoleLevel.WARN to "warn",
                PythonHostConsoleLevel.ERROR to "error",
            ),
            actions.console,
        )
        assertEquals(listOf("notice"), actions.notices)
        assertEquals(mapOf("seed.txt" to "seed", "written.txt" to "written"), actions.fileTexts)
        assertEquals(listOf("Alert" to "alert text"), actions.alerts)
        assertEquals(listOf("Confirm" to "confirm text"), actions.confirmations)
        assertEquals(listOf(Triple("Prompt", "prompt text", "seed")), actions.prompts)
        assertEquals(listOf("Select" to listOf("first", "second")), actions.selections)
        assertEquals(listOf("child.js"), actions.scriptPaths)
        assertEquals(1, actions.stopSelfRequests)
        assertEquals(listOf(120 to 240), actions.clicks)
        assertEquals(listOf(121 to 241), actions.longClicks)
        assertEquals(listOf(Triple(122, 242, 75)), actions.presses)
        assertEquals(listOf(listOf(10, 20, 110, 220, 300)), actions.swipes)
        assertEquals(1, actions.backRequests)
        assertEquals(1, actions.homeRequests)
        assertEquals(listOf(64 to 16), actions.selectorSnapshots)
        assertEquals(
            listOf(
                Triple(
                    PythonHostSelectorQuery(textContains = "smoke", clickable = true),
                    512,
                    32,
                ),
            ),
            actions.selectorFinds,
        )
        assertEquals(listOf(SELECTOR_NODE.id), actions.nodeClicks)
        assertEquals(listOf(SELECTOR_NODE.id to "updated"), actions.nodeTexts)
        assertEquals(listOf("png" to 100), actions.screenCaptures)
        assertEquals(listOf(SCREEN_IMAGE.id to 0), actions.screenChunkReads)
        assertEquals(listOf(SCREEN_IMAGE.id), actions.screenReleases)
        assertEquals(
            listOf(
                Triple(
                    0x123456,
                    2,
                    PythonHostImageRegion(10, 20, 100, 200),
                ),
            ),
            actions.screenColorSearches,
        )
    }

    @Test
    fun validatesEngineArgumentsAndMapsBoundedLaunchFailures() {
        val actions = RecordingActions()
        val dispatcher = dispatcher(actions)

        assertEquals(
            "INVALID_ARGUMENT",
            failureCode(
                dispatcher.dispatch(
                    request(1, PythonHostCapabilities.ENGINES_CURRENT, "path" to "child.js"),
                ),
            ),
        )
        assertEquals(
            "INVALID_ARGUMENT",
            failureCode(
                dispatcher.dispatch(
                    request(2, PythonHostCapabilities.ENGINES_STOP_SELF, "path" to "child.js"),
                ),
            ),
        )
        assertEquals(
            "INVALID_ARGUMENT",
            failureCode(
                dispatcher.dispatch(
                    request(3, PythonHostCapabilities.ENGINES_RUN, "path" to "../outside.js"),
                ),
            ),
        )
        assertEquals(0, actions.stopSelfRequests)
        assertEquals(emptyList<String>(), actions.scriptPaths)

        actions.engineFailure = PythonHostCapabilityNestedPythonException()
        assertEquals(
            "NESTED_PYTHON_NOT_ALLOWED",
            failureCode(
                dispatcher.dispatch(
                    request(4, PythonHostCapabilities.ENGINES_RUN, "path" to "child.py"),
                ),
            ),
        )
        actions.engineFailure = PythonHostCapabilityEngineLaunchLimitException()
        assertEquals(
            "ENGINE_LAUNCH_LIMIT_EXCEEDED",
            failureCode(
                dispatcher.dispatch(
                    request(5, PythonHostCapabilities.ENGINES_RUN, "path" to "child.js"),
                ),
            ),
        )
    }

    @Test
    fun validatesAutomatorArgumentsAndMapsMissingAccessibility() {
        val actions = RecordingActions()
        val dispatcher = dispatcher(actions)

        val invalidRequests = listOf(
            request(1, PythonHostCapabilities.AUTOMATOR_CLICK, "x" to -1, "y" to 0),
            request(
                2,
                PythonHostCapabilities.AUTOMATOR_LONG_CLICK,
                "x" to PythonHostAutomatorPolicy.MAX_COORDINATE + 1L,
                "y" to 0,
            ),
            request(
                3,
                PythonHostCapabilities.AUTOMATOR_PRESS,
                "x" to 0,
                "y" to 0,
                "durationMillis" to 0,
            ),
            request(
                4,
                PythonHostCapabilities.AUTOMATOR_SWIPE,
                "x1" to 0,
                "y1" to 0,
                "x2" to 1,
                "y2" to 1,
                "durationMillis" to PythonHostAutomatorPolicy.MAX_DURATION_MILLIS + 1L,
            ),
            request(5, PythonHostCapabilities.AUTOMATOR_BACK, "unexpected" to true),
            request(6, PythonHostCapabilities.AUTOMATOR_CLICK, "x" to true, "y" to 0),
        )
        invalidRequests.forEach { encoded ->
            assertEquals("INVALID_ARGUMENT", failureCode(dispatcher.dispatch(encoded)))
        }
        assertTrue(actions.clicks.isEmpty())
        assertTrue(actions.longClicks.isEmpty())
        assertTrue(actions.presses.isEmpty())
        assertTrue(actions.swipes.isEmpty())
        assertEquals(0, actions.backRequests)

        actions.automatorFailure = PythonHostCapabilityAccessibilityUnavailableException()
        assertEquals(
            "ACCESSIBILITY_UNAVAILABLE",
            failureCode(dispatcher.dispatch(request(7, PythonHostCapabilities.AUTOMATOR_HOME))),
        )
        assertEquals(0, actions.homeRequests)
    }

    @Test
    fun validatesSelectorBoundsQueriesAndStableFailures() {
        val actions = RecordingActions()
        val dispatcher = dispatcher(actions)
        val invalidRequests = listOf(
            request(
                1,
                PythonHostCapabilities.SELECTOR_SNAPSHOT,
                "maxNodes" to 0,
                "maxDepth" to 16,
            ),
            request(
                2,
                PythonHostCapabilities.SELECTOR_SNAPSHOT,
                "maxNodes" to PythonHostSelectorPolicy.MAX_SNAPSHOT_NODES,
                "maxDepth" to PythonHostSelectorPolicy.MAX_DEPTH + 1L,
            ),
            request(
                3,
                PythonHostCapabilities.SELECTOR_FIND,
                "query" to emptyMap<String, Any>(),
                "maxNodes" to 1,
                "maxDepth" to 0,
            ),
            request(
                4,
                PythonHostCapabilities.SELECTOR_FIND,
                "query" to mapOf("unsupported" to "value"),
                "maxNodes" to 1,
                "maxDepth" to 0,
            ),
            request(
                5,
                PythonHostCapabilities.SELECTOR_FIND,
                "query" to mapOf("text" to ""),
                "maxNodes" to 1,
                "maxDepth" to 0,
            ),
            request(
                6,
                PythonHostCapabilities.SELECTOR_FIND,
                "query" to mapOf("clickable" to "true"),
                "maxNodes" to 1,
                "maxDepth" to 0,
            ),
            request(
                7,
                PythonHostCapabilities.SELECTOR_FIND,
                "query" to mapOf("text" to "target"),
                "maxNodes" to PythonHostSelectorPolicy.MAX_FIND_NODES + 1L,
                "maxDepth" to 0,
            ),
            request(8, PythonHostCapabilities.SELECTOR_CLICK, "nodeId" to "raw-handle"),
            request(
                9,
                PythonHostCapabilities.SELECTOR_SET_TEXT,
                "nodeId" to SELECTOR_NODE.id,
                "text" to "x".repeat(PythonHostSelectorPolicy.MAX_SET_TEXT_BYTES + 1),
            ),
        )
        invalidRequests.forEach { encoded ->
            assertEquals("INVALID_ARGUMENT", failureCode(dispatcher.dispatch(encoded)))
        }
        assertTrue(actions.selectorSnapshots.isEmpty())
        assertTrue(actions.selectorFinds.isEmpty())
        assertTrue(actions.nodeClicks.isEmpty())
        assertTrue(actions.nodeTexts.isEmpty())

        actions.selectorFailure = PythonHostCapabilityAccessibilityUnavailableException()
        assertEquals(
            "ACCESSIBILITY_UNAVAILABLE",
            failureCode(
                dispatcher.dispatch(
                    request(
                        10,
                        PythonHostCapabilities.SELECTOR_SNAPSHOT,
                        "maxNodes" to 1,
                        "maxDepth" to 0,
                    ),
                ),
            ),
        )
        actions.selectorFailure = PythonHostCapabilityStaleNodeException()
        assertEquals(
            "STALE_NODE",
            failureCode(
                dispatcher.dispatch(
                    request(11, PythonHostCapabilities.SELECTOR_CLICK, "nodeId" to SELECTOR_NODE.id),
                ),
            ),
        )
        actions.selectorFailure = PythonHostCapabilitySelectorScanLimitException()
        assertEquals(
            "SELECTOR_SCAN_LIMIT_EXCEEDED",
            failureCode(
                dispatcher.dispatch(
                    request(
                        12,
                        PythonHostCapabilities.SELECTOR_FIND,
                        "query" to mapOf("text" to "missing"),
                        "maxNodes" to 1,
                        "maxDepth" to 0,
                    ),
                ),
            ),
        )
    }

    @Test
    fun validatesImageArgumentsAndMapsStableCaptureFailures() {
        val actions = RecordingActions()
        val dispatcher = dispatcher(actions)
        val invalidRequests = listOf(
            request(
                1,
                PythonHostCapabilities.IMAGES_CAPTURE_SCREEN,
                "format" to "jpg",
                "quality" to 100,
            ),
            request(
                2,
                PythonHostCapabilities.IMAGES_CAPTURE_SCREEN,
                "format" to "png",
                "quality" to 0,
            ),
            request(
                3,
                PythonHostCapabilities.IMAGES_CAPTURE_SCREEN,
                "format" to "jpeg",
                "quality" to 101,
            ),
            request(
                4,
                PythonHostCapabilities.IMAGES_READ_CHUNK,
                "imageId" to "raw-handle",
                "offset" to 0,
            ),
            request(
                5,
                PythonHostCapabilities.IMAGES_READ_CHUNK,
                "imageId" to SCREEN_IMAGE.id,
                "offset" to -1,
            ),
            request(
                6,
                PythonHostCapabilities.IMAGES_READ_CHUNK,
                "imageId" to SCREEN_IMAGE.id,
                "offset" to PythonHostImagePolicy.MAX_IMAGE_BYTES,
            ),
            request(
                7,
                PythonHostCapabilities.IMAGES_RELEASE,
                "imageId" to "image-0",
            ),
            request(
                8,
                PythonHostCapabilities.IMAGES_FIND_COLOR,
                "color" to -1,
                "threshold" to 0,
                "region" to null,
            ),
            request(
                9,
                PythonHostCapabilities.IMAGES_FIND_COLOR,
                "color" to PythonHostImagePolicy.MAX_COLOR + 1,
                "threshold" to 0,
                "region" to null,
            ),
            request(
                10,
                PythonHostCapabilities.IMAGES_FIND_COLOR,
                "color" to 0,
                "threshold" to -1,
                "region" to null,
            ),
            request(
                11,
                PythonHostCapabilities.IMAGES_FIND_COLOR,
                "color" to 0,
                "threshold" to PythonHostImagePolicy.MAX_COLOR_THRESHOLD + 1,
                "region" to null,
            ),
            request(
                12,
                PythonHostCapabilities.IMAGES_FIND_COLOR,
                "color" to 0,
                "threshold" to 0,
                "region" to "0,0,1,1",
            ),
            request(
                13,
                PythonHostCapabilities.IMAGES_FIND_COLOR,
                "color" to 0,
                "threshold" to 0,
                "region" to mapOf("x" to 0, "y" to 0, "width" to 1),
            ),
            request(
                14,
                PythonHostCapabilities.IMAGES_FIND_COLOR,
                "color" to 0,
                "threshold" to 0,
                "region" to mapOf(
                    "x" to 0,
                    "y" to 0,
                    "width" to 1,
                    "height" to 1,
                    "extra" to 1,
                ),
            ),
            request(
                15,
                PythonHostCapabilities.IMAGES_FIND_COLOR,
                "color" to 0,
                "threshold" to 0,
                "region" to mapOf("x" to 0, "y" to 0, "width" to 0, "height" to 1),
            ),
            request(
                16,
                PythonHostCapabilities.IMAGES_FIND_COLOR,
                "color" to 0,
                "threshold" to 0,
                "region" to mapOf(
                    "x" to PythonHostImagePolicy.MAX_DIMENSION - 1,
                    "y" to 0,
                    "width" to 2,
                    "height" to 1,
                ),
            ),
        )
        invalidRequests.forEach { encoded ->
            assertEquals("INVALID_ARGUMENT", failureCode(dispatcher.dispatch(encoded)))
        }
        assertTrue(actions.screenCaptures.isEmpty())
        assertTrue(actions.screenChunkReads.isEmpty())
        assertTrue(actions.screenReleases.isEmpty())
        assertTrue(actions.screenColorSearches.isEmpty())

        assertEquals(
            COLOR_MATCH.toJson(),
            success(
                dispatcher.dispatch(
                    request(
                        17,
                        PythonHostCapabilities.IMAGES_FIND_COLOR,
                        "color" to 0x123456,
                        "threshold" to 0,
                        "region" to null,
                    ),
                ),
            ),
        )
        assertEquals(listOf(Triple(0x123456, 0, null)), actions.screenColorSearches)

        val failures = listOf(
            PythonHostCapabilityAccessibilityUnavailableException() to "ACCESSIBILITY_UNAVAILABLE",
            PythonHostCapabilityScreenCaptureUnavailableException() to "SCREEN_CAPTURE_UNAVAILABLE",
            PythonHostCapabilityScreenCaptureFailedException() to "SCREEN_CAPTURE_FAILED",
            PythonHostCapabilityResultLimitException() to "RESULT_LIMIT_EXCEEDED",
        )
        failures.forEachIndexed { index, (failure, expectedCode) ->
            actions.imageFailure = failure
            assertEquals(
                expectedCode,
                failureCode(
                    dispatcher.dispatch(
                        request(
                            (index + 20).toLong(),
                            PythonHostCapabilities.IMAGES_CAPTURE_SCREEN,
                            "format" to "png",
                            "quality" to 100,
                        ),
                    ),
                ),
            )
            assertEquals(
                expectedCode,
                failureCode(
                    dispatcher.dispatch(
                        request(
                            (index + 30).toLong(),
                            PythonHostCapabilities.IMAGES_FIND_COLOR,
                            "color" to 0x123456,
                            "threshold" to 0,
                            "region" to null,
                        ),
                    ),
                ),
            )
        }
        actions.imageFailure = PythonHostCapabilityStaleImageException()
        assertEquals(
            "STALE_IMAGE",
            failureCode(
                dispatcher.dispatch(
                    request(
                        40,
                        PythonHostCapabilities.IMAGES_READ_CHUNK,
                        "imageId" to SCREEN_IMAGE.id,
                        "offset" to 0,
                    ),
                ),
            ),
        )
    }

    @Test
    fun bindsExecutionIdRejectsReplayAndRejectsEveryCallAfterClose() {
        val actions = RecordingActions()
        val dispatcher = dispatcher(actions)
        val request = request(1, PythonHostCapabilities.CLIP_GET)
        assertEquals("", success(dispatcher.dispatch(request)))
        assertEquals("REPLAYED_CALL", failureCode(dispatcher.dispatch(request)))

        assertTrue(dispatcher.close())
        assertFalse(dispatcher.close())
        assertEquals(1, actions.closeRequests)
        assertEquals(
            "BROKER_CLOSED",
            failureCode(dispatcher.dispatch(request(2, PythonHostCapabilities.CLIP_GET))),
        )
        assertThrows(IllegalArgumentException::class.java) {
            dispatcher.dispatch(request(3, PythonHostCapabilities.CLIP_GET, executionId = "other"))
        }
    }

    @Test
    fun enforcesGrantArgumentAndLifetimeQuotasWithoutInvokingActions() {
        val actions = RecordingActions()
        val denied = PythonHostCapabilityDispatcher(
            executionId = EXECUTION_ID,
            grantedCapabilities = setOf(PythonHostCapabilities.CLIP_GET),
            actions = actions,
        )
        assertEquals(
            "CAPABILITY_UNAVAILABLE",
            failureCode(denied.dispatch(request(1, PythonHostCapabilities.TOAST_SHOW, "text" to "no"))),
        )
        assertTrue(actions.toasts.isEmpty())

        val invalid = dispatcher(actions)
        assertEquals(
            "INVALID_ARGUMENT",
            failureCode(invalid.dispatch(request(1, PythonHostCapabilities.APP_LAUNCH, "package" to "bad package"))),
        )
        assertEquals(
            "INVALID_ARGUMENT",
            failureCode(invalid.dispatch(request(2, PythonHostCapabilities.NOTICE_SHOW, "text" to ""))),
        )
        assertEquals(
            "INVALID_ARGUMENT",
            failureCode(
                invalid.dispatch(
                    request(3, PythonHostCapabilities.FILES_READ_TEXT, "path" to "../outside.txt"),
                ),
            ),
        )

        actions.denyNotice = true
        assertEquals(
            "PERMISSION_DENIED",
            failureCode(invalid.dispatch(request(4, PythonHostCapabilities.NOTICE_SHOW, "text" to "denied"))),
        )
        assertTrue(actions.notices.isEmpty())

        val bounded = PythonHostCapabilityDispatcher(
            executionId = EXECUTION_ID,
            grantedCapabilities = PythonHostCapabilities.ENABLED,
            actions = actions,
            maxCalls = 1,
        )
        assertEquals("", success(bounded.dispatch(request(1, PythonHostCapabilities.CLIP_GET))))
        assertEquals(
            "CALL_LIMIT_EXCEEDED",
            failureCode(bounded.dispatch(request(2, PythonHostCapabilities.CLIP_GET))),
        )
    }

    @Test
    fun mapsBoundedFileFailuresToStableCodes() {
        val actions = RecordingActions()
        val dispatcher = dispatcher(actions)
        val failures = listOf(
            PythonHostCapabilityPathNotFoundException() to "PATH_NOT_FOUND",
            PythonHostCapabilityPathTypeException() to "PATH_TYPE_MISMATCH",
            PythonHostCapabilityFileEncodingException(IllegalArgumentException()) to "FILE_ENCODING_ERROR",
            PythonHostCapabilityResultLimitException() to "RESULT_LIMIT_EXCEEDED",
            SecurityException() to "PERMISSION_DENIED",
        )

        failures.forEachIndexed { index, (failure, expectedCode) ->
            actions.fileFailure = failure
            assertEquals(
                expectedCode,
                failureCode(
                    dispatcher.dispatch(
                        request(
                            (index + 1).toLong(),
                            PythonHostCapabilities.FILES_READ_TEXT,
                            "path" to "seed.txt",
                        ),
                    ),
                ),
            )
        }
        assertTrue(actions.fileTexts.isEmpty())
    }

    @Test
    fun boundsDialogArgumentsAndMapsMissingForegroundAuthority() {
        val actions = RecordingActions()
        val dispatcher = dispatcher(actions)
        assertEquals(
            "INVALID_ARGUMENT",
            failureCode(
                dispatcher.dispatch(
                    request(
                        1,
                        PythonHostCapabilities.DIALOGS_ALERT,
                        "title" to "",
                        "text" to "text",
                    ),
                ),
            ),
        )
        assertEquals(
            "INVALID_ARGUMENT",
            failureCode(
                dispatcher.dispatch(
                    request(
                        2,
                        PythonHostCapabilities.DIALOGS_SELECT,
                        "title" to "Select",
                        "items" to emptyList<String>(),
                    ),
                ),
            ),
        )
        assertEquals(
            "INVALID_ARGUMENT",
            failureCode(
                dispatcher.dispatch(
                    request(
                        3,
                        PythonHostCapabilities.DIALOGS_SELECT,
                        "title" to "Select",
                        "items" to List(PythonHostDialogPolicy.MAX_ITEMS + 1) { "item" },
                    ),
                ),
            ),
        )
        assertTrue(actions.alerts.isEmpty())
        assertTrue(actions.selections.isEmpty())

        actions.dialogFailure = PythonHostCapabilityInteractiveNotAllowedException()
        assertEquals(
            "INTERACTIVE_NOT_ALLOWED",
            failureCode(
                dispatcher.dispatch(
                    request(
                        4,
                        PythonHostCapabilities.DIALOGS_CONFIRM,
                        "title" to "Confirm",
                        "text" to "foreground only",
                    ),
                ),
            ),
        )
    }

    @Test
    fun rejectsMalformedOrOversizedJsonBeforeDispatch() {
        val dispatcher = dispatcher(RecordingActions())
        assertThrows(IllegalArgumentException::class.java) {
            dispatcher.dispatch("{}".toByteArray())
        }
        assertThrows(IllegalArgumentException::class.java) {
            dispatcher.dispatch(byteArrayOf(0x80.toByte()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            dispatcher.dispatch(ByteArray(64 * 1024 + 1))
        }
    }

    private fun dispatcher(actions: RecordingActions) = PythonHostCapabilityDispatcher(
        executionId = EXECUTION_ID,
        grantedCapabilities = PythonHostCapabilities.ENABLED,
        actions = actions,
    )

    private fun request(
        callId: Long,
        capability: String,
        vararg arguments: Pair<String, Any?>,
        executionId: String = EXECUTION_ID,
    ): ByteArray = JsonObject().apply {
        addProperty("version", 1)
        addProperty("executionId", executionId)
        addProperty("callId", callId)
        addProperty("capability", capability)
        add("arguments", JsonObject().apply {
            arguments.forEach { (name, value) -> addTestValue(name, value) }
        })
    }.toString().toByteArray()

    private fun JsonObject.addTestValue(name: String, value: Any?) {
        when (value) {
            null -> add(name, JsonNull.INSTANCE)
            is Map<*, *> -> add(
                name,
                JsonObject().apply {
                    value.forEach { (nestedName, nestedValue) ->
                        addTestValue(nestedName as String, nestedValue)
                    }
                },
            )
            is Iterable<*> -> add(
                name,
                com.google.gson.JsonArray().apply {
                    value.forEach { entry -> add(requireNotNull(entry).toString()) }
                },
            )
            is Boolean -> addProperty(name, value)
            is Number -> addProperty(name, value)
            else -> addProperty(name, value.toString())
        }
    }

    private fun success(encoded: ByteArray): Any? {
        val response = JsonParser.parseString(String(encoded)).asJsonObject
        assertTrue(response.get("ok").asBoolean)
        return when (val value = response.get("value")) {
            null, JsonNull.INSTANCE -> null
            else -> when {
                value.isJsonObject -> value.asJsonObject
                value.isJsonArray -> value.asJsonArray.map { it.asString }
                value.asJsonPrimitive.isBoolean -> value.asBoolean
                value.asJsonPrimitive.isNumber -> value.asInt
                value.asJsonPrimitive.isString -> value.asString
                else -> error("Unexpected test response type")
            }
        }
    }

    private fun failureCode(encoded: ByteArray): String {
        val response = JsonParser.parseString(String(encoded)).asJsonObject
        assertFalse(response.get("ok").asBoolean)
        return response.getAsJsonObject("error").get("code").asString
    }

    private class RecordingActions : PythonHostCapabilityActions {
        val toasts = mutableListOf<String>()
        var clipboard = ""
        val packages = mutableListOf<String>()
        val apps = mutableListOf<String>()
        val urls = mutableListOf<String>()
        val console = mutableListOf<Pair<PythonHostConsoleLevel, String>>()
        val notices = mutableListOf<String>()
        val fileTexts = linkedMapOf<String, String>()
        val alerts = mutableListOf<Pair<String, String>>()
        val confirmations = mutableListOf<Pair<String, String>>()
        val prompts = mutableListOf<Triple<String, String, String>>()
        val selections = mutableListOf<Pair<String, List<String>>>()
        val scriptPaths = mutableListOf<String>()
        val clicks = mutableListOf<Pair<Int, Int>>()
        val longClicks = mutableListOf<Pair<Int, Int>>()
        val presses = mutableListOf<Triple<Int, Int, Int>>()
        val swipes = mutableListOf<List<Int>>()
        val selectorSnapshots = mutableListOf<Pair<Int, Int>>()
        val selectorFinds = mutableListOf<Triple<PythonHostSelectorQuery, Int, Int>>()
        val nodeClicks = mutableListOf<String>()
        val nodeTexts = mutableListOf<Pair<String, String>>()
        val screenCaptures = mutableListOf<Pair<String, Int>>()
        val screenColorSearches = mutableListOf<Triple<Int, Int, PythonHostImageRegion?>>()
        val screenChunkReads = mutableListOf<Pair<String, Int>>()
        val screenReleases = mutableListOf<String>()
        var stopSelfRequests = 0
        var backRequests = 0
        var homeRequests = 0
        var closeRequests = 0
        var denyNotice = false
        var fileFailure: RuntimeException? = null
        var dialogFailure: RuntimeException? = null
        var engineFailure: RuntimeException? = null
        var automatorFailure: RuntimeException? = null
        var selectorFailure: RuntimeException? = null
        var imageFailure: RuntimeException? = null

        override fun showToast(text: String) {
            toasts += text
        }

        override fun getClipboardText(): String = clipboard

        override fun setClipboardText(text: String) {
            clipboard = text
        }

        override fun launchPackage(packageName: String): Boolean {
            packages += packageName
            return true
        }

        override fun launchApp(label: String): Boolean {
            apps += label
            return true
        }

        override fun openUrl(url: String): Boolean {
            urls += url
            return true
        }

        override fun getDeviceInfo(): PythonHostDeviceInfo = DEVICE_INFO

        override fun writeConsole(level: PythonHostConsoleLevel, text: String) {
            console += level to text
        }

        override fun postNotice(text: String) {
            if (denyNotice) throw PythonHostCapabilityPermissionDeniedException()
            notices += text
        }

        override fun readFileText(path: String): String {
            fileFailure?.let { throw it }
            return fileTexts[path].orEmpty()
        }

        override fun writeFileText(path: String, text: String) {
            fileFailure?.let { throw it }
            fileTexts[path] = text
        }

        override fun fileExists(path: String): Boolean {
            fileFailure?.let { throw it }
            return path == "." || path in fileTexts
        }

        override fun isFile(path: String): Boolean {
            fileFailure?.let { throw it }
            return path in fileTexts
        }

        override fun isDirectory(path: String): Boolean {
            fileFailure?.let { throw it }
            return path == "."
        }

        override fun listDirectory(path: String): List<String> {
            fileFailure?.let { throw it }
            return fileTexts.keys.sorted()
        }

        override fun showDialogAlert(title: String, text: String) {
            dialogFailure?.let { throw it }
            alerts += title to text
        }

        override fun showDialogConfirm(title: String, text: String): Boolean {
            dialogFailure?.let { throw it }
            confirmations += title to text
            return true
        }

        override fun showDialogPrompt(title: String, text: String, defaultValue: String): String? {
            dialogFailure?.let { throw it }
            prompts += Triple(title, text, defaultValue)
            return "typed value"
        }

        override fun showDialogSelect(title: String, items: List<String>): Int? {
            dialogFailure?.let { throw it }
            selections += title to items
            return 1
        }

        override fun currentEngine(): PythonHostEngineInfo = ENGINE_INFO

        override fun requestStopSelf() {
            engineFailure?.let { throw it }
            stopSelfRequests += 1
        }

        override fun runScript(path: String): PythonHostEngineLaunch {
            engineFailure?.let { throw it }
            scriptPaths += path
            return ENGINE_LAUNCH.copy(path = path)
        }

        override fun click(x: Int, y: Int): Boolean {
            automatorFailure?.let { throw it }
            clicks += x to y
            return true
        }

        override fun longClick(x: Int, y: Int): Boolean {
            automatorFailure?.let { throw it }
            longClicks += x to y
            return true
        }

        override fun press(x: Int, y: Int, durationMillis: Int): Boolean {
            automatorFailure?.let { throw it }
            presses += Triple(x, y, durationMillis)
            return true
        }

        override fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMillis: Int): Boolean {
            automatorFailure?.let { throw it }
            swipes += listOf(x1, y1, x2, y2, durationMillis)
            return true
        }

        override fun back(): Boolean {
            automatorFailure?.let { throw it }
            backRequests += 1
            return true
        }

        override fun home(): Boolean {
            automatorFailure?.let { throw it }
            homeRequests += 1
            return true
        }

        override fun selectorSnapshot(maxNodes: Int, maxDepth: Int): PythonHostSelectorSnapshot {
            selectorFailure?.let { throw it }
            selectorSnapshots += maxNodes to maxDepth
            return SELECTOR_SNAPSHOT
        }

        override fun selectorFind(
            query: PythonHostSelectorQuery,
            maxNodes: Int,
            maxDepth: Int,
        ): PythonHostSelectorNode? {
            selectorFailure?.let { throw it }
            selectorFinds += Triple(query, maxNodes, maxDepth)
            return SELECTOR_NODE
        }

        override fun clickNode(nodeId: String): Boolean {
            selectorFailure?.let { throw it }
            nodeClicks += nodeId
            return true
        }

        override fun setNodeText(nodeId: String, text: String): Boolean {
            selectorFailure?.let { throw it }
            nodeTexts += nodeId to text
            return true
        }

        override fun captureScreen(format: String, quality: Int): PythonHostScreenImageDescriptor {
            imageFailure?.let { throw it }
            screenCaptures += format to quality
            return SCREEN_IMAGE
        }

        override fun findScreenColor(
            color: Int,
            threshold: Int,
            region: PythonHostImageRegion?,
        ): PythonHostColorMatch {
            imageFailure?.let { throw it }
            screenColorSearches += Triple(color, threshold, region)
            return COLOR_MATCH
        }

        override fun readScreenImageChunk(imageId: String, offset: Int): PythonHostScreenImageChunk {
            imageFailure?.let { throw it }
            screenChunkReads += imageId to offset
            return SCREEN_CHUNK
        }

        override fun releaseScreenImage(imageId: String) {
            imageFailure?.let { throw it }
            screenReleases += imageId
        }

        override fun close() {
            closeRequests += 1
        }
    }

    private companion object {
        const val EXECUTION_ID = "12345678-1234-5678-9abc-def012345678"
        val DEVICE_INFO = PythonHostDeviceInfo(
            batteryPercent = 88.5,
            charging = true,
            screenOn = true,
            brightness = 127,
            musicVolume = 7,
            musicMaxVolume = 15,
            notificationVolume = 4,
            notificationMaxVolume = 7,
            alarmVolume = 5,
            alarmMaxVolume = 7,
        )
        val ENGINE_INFO = PythonHostEngineInfo(
            id = 17,
            engineName = "python",
            sourceName = "main",
            entryPoint = "main.py",
            project = true,
            startedAtMillis = 123456789L,
        )
        val ENGINE_LAUNCH = PythonHostEngineLaunch(
            id = 18,
            engineName = "org.autojs.autojs.script.JavaScriptSource.Engine",
            sourceName = "child",
            path = "child.js",
        )
        val SELECTOR_NODE = PythonHostSelectorNode(
            id = "node-1-1",
            parentId = null,
            depth = 2,
            childCount = 0,
            text = "AutoJs6 selector smoke",
            description = "Selector smoke description",
            resourceId = "org.autojs.autojs6:id/selector_smoke",
            className = "android.widget.Button",
            packageName = "org.autojs.autojs6",
            bounds = PythonHostSelectorBounds(1, 2, 101, 202),
            clickable = true,
            editable = false,
            enabled = true,
            focused = false,
            selected = false,
            checkable = false,
            checked = false,
            scrollable = false,
            password = false,
            visibleToUser = true,
            truncatedFields = emptyList(),
        )
        val SELECTOR_SNAPSHOT = PythonHostSelectorSnapshot(
            generation = 1,
            truncated = false,
            nodes = listOf(SELECTOR_NODE),
        )
        val SCREEN_IMAGE = PythonHostScreenImageDescriptor(
            id = "image-1",
            format = "png",
            width = 1080,
            height = 2400,
            byteLength = 3,
            sha256 = "0".repeat(64),
        )
        val SCREEN_CHUNK = PythonHostScreenImageChunk(
            imageId = SCREEN_IMAGE.id,
            offset = 0,
            data = "AAEC",
            nextOffset = 3,
            eof = true,
        )
        val COLOR_MATCH = PythonHostColorMatch.found(17, 29)
    }
}
