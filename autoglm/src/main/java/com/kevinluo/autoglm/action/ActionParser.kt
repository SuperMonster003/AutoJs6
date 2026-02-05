package com.kevinluo.autoglm.action

import com.kevinluo.autoglm.util.Logger

/**
 * Parser for converting model response strings into [AgentAction] objects.
 *
 * Supports parsing:
 * - `do(action="ActionName", ...)` format for action commands
 * - `finish(message="...")` format for task completion
 *
 * The parser handles various action types including Tap, Swipe, Type, Launch,
 * and system key actions. It validates coordinate ranges and provides detailed
 * error messages for parsing failures.
 *
 */
object ActionParser {
    private const val TAG = "ActionParser"

    // Valid coordinate range
    private const val MIN_COORDINATE = 0
    private const val MAX_COORDINATE = 999

    // Regex patterns for parsing
    private val DO_PATTERN = Regex("""^do\((.+)\)$""", RegexOption.DOT_MATCHES_ALL)
    private val FINISH_PATTERN = Regex("""^finish\(message=["'](.*)["']\)$""", RegexOption.DOT_MATCHES_ALL)
    private val ACTION_TYPE_PATTERN = Regex("""action=["']([^"']+)["']""")

    // Coordinate patterns - support both positive integers and detect negative numbers
    private val ELEMENT_PATTERN = Regex("""element=\[(-?\d+),\s*(-?\d+)\]""")
    private val START_PATTERN = Regex("""start=\[(-?\d+),\s*(-?\d+)\]""")
    private val END_PATTERN = Regex("""end=\[(-?\d+),\s*(-?\d+)\]""")
    private val TEXT_PATTERN = Regex("""text=["'](.*)["']""", RegexOption.DOT_MATCHES_ALL)
    private val APP_PATTERN = Regex("""app=["']([^"']+)["']""")
    private val MESSAGE_PATTERN = Regex("""message=["'](.*)["']""", RegexOption.DOT_MATCHES_ALL)
    private val DURATION_PATTERN = Regex("""duration=["']?([^"',\)]+)["']?""")
    private val INSTRUCTION_PATTERN = Regex("""instruction=["'](.*)["']""", RegexOption.DOT_MATCHES_ALL)
    private val DELAY_PATTERN = Regex("""delay=(\d+)""")

    // Batch step parsing patterns (pre-compiled for performance)
    private val BATCH_ACTION_PATTERN = Regex(""""action"\s*:\s*"([^"]+)"""")
    private val BATCH_ELEMENT_PATTERN = Regex(""""element"\s*:\s*\[(-?\d+),\s*(-?\d+)\]""")
    private val BATCH_START_PATTERN = Regex(""""start"\s*:\s*\[(-?\d+),\s*(-?\d+)\]""")
    private val BATCH_END_PATTERN = Regex(""""end"\s*:\s*\[(-?\d+),\s*(-?\d+)\]""")
    private val BATCH_DURATION_PATTERN = Regex(""""duration"\s*:\s*"?([^",}]+)"?""")

    /**
     * Parses a model response string into an [AgentAction].
     *
     * @param response The raw response string from the model
     * @return The parsed [AgentAction]
     * @throws ActionParseException if the response cannot be parsed
     * @throws CoordinateOutOfRangeException if coordinates are outside valid range (0-999)
     */
    fun parse(response: String): AgentAction {
        val trimmed = response.trim()
        Logger.d(TAG, "Parsing response: ${trimmed.take(100)}${if (trimmed.length > 100) "..." else ""}")

        // Check for finish action first
        if (trimmed.startsWith("finish(")) {
            return parseFinishAction(trimmed)
        }

        // Check for do action
        if (trimmed.startsWith("do(")) {
            return parseDoAction(trimmed)
        }

        Logger.w(TAG, "Unknown action format: $trimmed")
        throw ActionParseException("Unknown action format: $trimmed")
    }

    /**
     * Parses a finish action from the response string.
     *
     * @param response The response string starting with "finish("
     * @return The parsed [AgentAction.Finish]
     */
    private fun parseFinishAction(response: String): AgentAction.Finish {
        // Extract message from finish(message="...")
        val messageMatch = FINISH_PATTERN.find(response)
        val message =
            messageMatch?.groupValues?.get(1)
                ?: extractFinishMessage(response)
        Logger.d(TAG, "Parsed finish action with message: ${message.take(50)}")
        return AgentAction.Finish(message)
    }

    /**
     * Fallback extraction for finish message when regex doesn't match.
     *
     * @param response The response string to extract message from
     * @return The extracted message, or empty string if not found
     */
    private fun extractFinishMessage(response: String): String {
        // Handle: finish(message="...") or finish(message='...')
        val startIndex = response.indexOf("message=")
        if (startIndex == -1) {
            return ""
        }
        val afterEquals = response.substring(startIndex + 8)
        val quote = afterEquals.firstOrNull()
        if (quote != '"' && quote != '\'') {
            return afterEquals.substringBefore(")").trim()
        }
        // Find matching closing quote
        val content = afterEquals.drop(1)
        val endIndex = content.lastIndexOf(quote)
        return if (endIndex > 0) content.substring(0, endIndex) else content.substringBefore(")")
    }

    /**
     * Parses a do action from the response string.
     *
     * @param response The response string starting with "do("
     * @return The parsed [AgentAction]
     * @throws ActionParseException if the action type is unknown or parsing fails
     */
    private fun parseDoAction(response: String): AgentAction {
        // Special handling for Type and Type_Name actions due to complex text content
        if (response.contains("""action="Type"""") || response.contains("""action='Type'""") ||
            response.contains("""action="Type_Name"""") || response.contains("""action='Type_Name'""")
        ) {
            return parseTypeAction(response)
        }

        // Extract action type
        val actionTypeMatch =
            ACTION_TYPE_PATTERN.find(response)
                ?: throw ActionParseException("No action type found in: $response")
        val actionType = actionTypeMatch.groupValues[1]

        return when (actionType) {
            "Tap" -> {
                parseTapAction(response)
            }

            "Swipe" -> {
                parseSwipeAction(response)
            }

            "Launch" -> {
                parseLaunchAction(response)
            }

            "List_Apps", "ListApps" -> {
                AgentAction.ListApps
            }

            "Back" -> {
                AgentAction.Back
            }

            "Home" -> {
                AgentAction.Home
            }

            "VolumeUp" -> {
                AgentAction.VolumeUp
            }

            "VolumeDown" -> {
                AgentAction.VolumeDown
            }

            "Power" -> {
                AgentAction.Power
            }

            "Long Press" -> {
                parseLongPressAction(response)
            }

            "Double Tap" -> {
                parseDoubleTapAction(response)
            }

            "Wait" -> {
                parseWaitAction(response)
            }

            "Take_over" -> {
                parseTakeOverAction(response)
            }

            "Interact" -> {
                parseInteractAction(response)
            }

            "Note" -> {
                parseNoteAction(response)
            }

            "Call_API" -> {
                parseCallApiAction(response)
            }

            "Batch" -> {
                parseBatchAction(response)
            }

            else -> {
                Logger.w(TAG, "Unknown action type: $actionType")
                throw ActionParseException("Unknown action type: $actionType")
            }
        }
    }

    /**
     * Parses Type or Type_Name action with special text extraction.
     * Handles escaped quotes within text content.
     *
     * @param response The response string containing Type or Type_Name action
     * @return The parsed [AgentAction.Type] or [AgentAction.TypeName]
     */
    private fun parseTypeAction(response: String): AgentAction {
        val isTypeName = response.contains("Type_Name")

        // Extract text using special handling
        // Format: do(action="Type", text="...")
        val textStartIndex = response.indexOf("text=")
        if (textStartIndex == -1) {
            return if (isTypeName) AgentAction.TypeName("") else AgentAction.Type("")
        }

        val afterText = response.substring(textStartIndex + 5)
        val quote = afterText.firstOrNull()

        val text =
            if (quote == '"' || quote == '\'') {
                // Find the closing quote, handling escaped quotes
                val content = afterText.drop(1)
                val closingIndex = findUnescapedQuote(content, quote)
                if (closingIndex >= 0) {
                    // Unescape the content
                    unescapeText(content.substring(0, closingIndex), quote)
                } else {
                    content.substringBefore(")")
                }
            } else {
                afterText.substringBefore(")").trim()
            }

        return if (isTypeName) AgentAction.TypeName(text) else AgentAction.Type(text)
    }

    /**
     * Finds the index of the first unescaped quote character.
     *
     * @param content The string content to search
     * @param quote The quote character to find
     * @return The index of the first unescaped quote, or -1 if not found
     */
    private fun findUnescapedQuote(content: String, quote: Char): Int {
        var i = 0
        while (i < content.length) {
            val c = content[i]
            if (c == '\\' && i + 1 < content.length) {
                // Skip escaped character
                i += 2
                continue
            }
            if (c == quote) {
                return i
            }
            i++
        }
        return -1
    }

    /**
     * Unescapes text by converting escaped quotes to regular quotes.
     * Handles: `\"` -> `"` and `\'` -> `'`
     *
     * @param text The text to unescape
     * @param quote The quote character that was escaped
     * @return The unescaped text
     */
    private fun unescapeText(text: String, quote: Char): String = text
        .replace("\\$quote", quote.toString())
        .replace("\\\\", "\\")

    /**
     * Parses a Tap action from the response string.
     *
     * @param response The response string containing Tap action
     * @return The parsed [AgentAction.Tap]
     * @throws ActionParseException if element coordinates are not found
     * @throws CoordinateOutOfRangeException if coordinates are outside valid range
     */
    private fun parseTapAction(response: String): AgentAction.Tap {
        val elementMatch =
            ELEMENT_PATTERN.find(response)
                ?: throw ActionParseException("No element coordinates found in Tap action: $response")

        val x = parseCoordinateValue(elementMatch.groupValues[1], "x", response)
        val y = parseCoordinateValue(elementMatch.groupValues[2], "y", response)

        // Validate coordinates
        validateCoordinates(response, "x" to x, "y" to y)

        // Check for optional message (sensitive operation)
        val messageMatch = MESSAGE_PATTERN.find(response)
        val message = messageMatch?.groupValues?.get(1)

        return AgentAction.Tap(x, y, message)
    }

    /**
     * Parses a Swipe action from the response string.
     *
     * @param response The response string containing Swipe action
     * @return The parsed [AgentAction.Swipe]
     * @throws ActionParseException if start or end coordinates are not found
     * @throws CoordinateOutOfRangeException if coordinates are outside valid range
     */
    private fun parseSwipeAction(response: String): AgentAction.Swipe {
        val startMatch =
            START_PATTERN.find(response)
                ?: throw ActionParseException("No start coordinates found in Swipe action: $response")
        val endMatch =
            END_PATTERN.find(response)
                ?: throw ActionParseException("No end coordinates found in Swipe action: $response")

        val startX = parseCoordinateValue(startMatch.groupValues[1], "startX", response)
        val startY = parseCoordinateValue(startMatch.groupValues[2], "startY", response)
        val endX = parseCoordinateValue(endMatch.groupValues[1], "endX", response)
        val endY = parseCoordinateValue(endMatch.groupValues[2], "endY", response)

        // Validate coordinates
        validateCoordinates(
            response,
            "startX" to startX,
            "startY" to startY,
            "endX" to endX,
            "endY" to endY,
        )

        return AgentAction.Swipe(
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY,
        )
    }

    /**
     * Parses a Launch action from the response string.
     *
     * @param response The response string containing Launch action
     * @return The parsed [AgentAction.Launch]
     * @throws ActionParseException if app name is not found
     */
    private fun parseLaunchAction(response: String): AgentAction.Launch {
        val appMatch =
            APP_PATTERN.find(response)
                ?: throw ActionParseException("No app name found in Launch action: $response")
        return AgentAction.Launch(appMatch.groupValues[1])
    }

    /**
     * Parses a Long Press action from the response string.
     *
     * @param response The response string containing Long Press action
     * @return The parsed [AgentAction.LongPress]
     * @throws ActionParseException if element coordinates are not found
     * @throws CoordinateOutOfRangeException if coordinates are outside valid range
     */
    private fun parseLongPressAction(response: String): AgentAction.LongPress {
        val elementMatch =
            ELEMENT_PATTERN.find(response)
                ?: throw ActionParseException("No element coordinates found in Long Press action: $response")

        val x = parseCoordinateValue(elementMatch.groupValues[1], "x", response)
        val y = parseCoordinateValue(elementMatch.groupValues[2], "y", response)

        // Validate coordinates
        validateCoordinates(response, "x" to x, "y" to y)

        // Check for optional duration
        val durationMatch = DURATION_PATTERN.find(response)
        val durationMs = durationMatch?.groupValues?.get(1)?.toIntOrNull() ?: 3000

        return AgentAction.LongPress(x, y, durationMs)
    }

    /**
     * Parses a Double Tap action from the response string.
     *
     * @param response The response string containing Double Tap action
     * @return The parsed [AgentAction.DoubleTap]
     * @throws ActionParseException if element coordinates are not found
     * @throws CoordinateOutOfRangeException if coordinates are outside valid range
     */
    private fun parseDoubleTapAction(response: String): AgentAction.DoubleTap {
        val elementMatch =
            ELEMENT_PATTERN.find(response)
                ?: throw ActionParseException("No element coordinates found in Double Tap action: $response")

        val x = parseCoordinateValue(elementMatch.groupValues[1], "x", response)
        val y = parseCoordinateValue(elementMatch.groupValues[2], "y", response)

        // Validate coordinates
        validateCoordinates(response, "x" to x, "y" to y)

        return AgentAction.DoubleTap(x = x, y = y)
    }

    /**
     * Parses a Wait action from the response string.
     *
     * @param response The response string containing Wait action
     * @return The parsed [AgentAction.Wait]
     */
    private fun parseWaitAction(response: String): AgentAction.Wait {
        val durationMatch = DURATION_PATTERN.find(response)
        val durationStr = durationMatch?.groupValues?.get(1) ?: "1"

        // Handle formats like "2 seconds", "2.5", "2"
        val duration =
            durationStr
                .replace("seconds", "")
                .replace("second", "")
                .trim()
                .toFloatOrNull() ?: 1.0f

        return AgentAction.Wait(duration)
    }

    /**
     * Parses a Take_over action from the response string.
     *
     * @param response The response string containing Take_over action
     * @return The parsed [AgentAction.TakeOver]
     */
    private fun parseTakeOverAction(response: String): AgentAction.TakeOver {
        val messageMatch = MESSAGE_PATTERN.find(response)
        val message = messageMatch?.groupValues?.get(1) ?: "User intervention required"
        return AgentAction.TakeOver(message)
    }

    /**
     * Parses an Interact action from the response string.
     *
     * @param response The response string containing Interact action
     * @return The parsed [AgentAction.Interact]
     */
    private fun parseInteractAction(response: String): AgentAction.Interact {
        // Options parsing could be added if needed
        // For now, return with null options
        return AgentAction.Interact(null)
    }

    /**
     * Parses a Note action from the response string.
     *
     * @param response The response string containing Note action
     * @return The parsed [AgentAction.Note]
     */
    private fun parseNoteAction(response: String): AgentAction.Note {
        val messageMatch = MESSAGE_PATTERN.find(response)
        val message = messageMatch?.groupValues?.get(1) ?: ""
        return AgentAction.Note(message)
    }

    /**
     * Parses a Call_API action from the response string.
     *
     * @param response The response string containing Call_API action
     * @return The parsed [AgentAction.CallApi]
     */
    private fun parseCallApiAction(response: String): AgentAction.CallApi {
        val instructionMatch = INSTRUCTION_PATTERN.find(response)
        val instruction = instructionMatch?.groupValues?.get(1) ?: ""
        return AgentAction.CallApi(instruction)
    }

    /**
     * Parses a Batch action from the response string.
     * Format: `do(action="Batch", steps=[{"action": "Tap", "element": [x,y]}, ...], delay=500)`
     *
     * @param response The response string containing Batch action
     * @return The parsed [AgentAction.Batch]
     * @throws ActionParseException if steps are not found or empty
     */
    private fun parseBatchAction(response: String): AgentAction.Batch {
        // Extract delay (optional, default 500ms)
        val delayMatch = DELAY_PATTERN.find(response)
        val delayMs = delayMatch?.groupValues?.get(1)?.toIntOrNull() ?: 500

        // Extract steps array
        val stepsStartIndex = response.indexOf("steps=[")
        if (stepsStartIndex == -1) {
            throw ActionParseException("No steps found in Batch action: $response")
        }

        // Find the matching closing bracket for steps array
        val stepsContent = extractStepsArray(response, stepsStartIndex + 6)

        // Parse each step
        val steps = parseStepsArray(stepsContent)

        if (steps.isEmpty()) {
            throw ActionParseException("Empty steps in Batch action: $response")
        }

        return AgentAction.Batch(steps, delayMs)
    }

    /**
     * Extracts the steps array content, handling nested brackets.
     *
     * @param response The full response string
     * @param startIndex The index after the opening bracket of steps array
     * @return The content inside the steps array brackets
     */
    private fun extractStepsArray(response: String, startIndex: Int): String {
        var bracketCount = 0
        var inString = false
        var stringChar = ' '
        val result = StringBuilder()

        for (i in startIndex until response.length) {
            val c = response[i]

            // Handle string boundaries
            if ((c == '"' || c == '\'') && (i == 0 || response[i - 1] != '\\')) {
                if (!inString) {
                    inString = true
                    stringChar = c
                } else if (c == stringChar) {
                    inString = false
                }
            }

            if (!inString) {
                when (c) {
                    '[' -> {
                        bracketCount++
                    }

                    ']' -> {
                        bracketCount--
                        if (bracketCount == 0) {
                            return result.toString()
                        }
                    }
                }
            }

            if (bracketCount > 0) {
                result.append(c)
            }
        }

        return result.toString()
    }

    /**
     * Parses the steps array content into a list of [AgentAction]s.
     * Supports both JSON-like format and do() format.
     *
     * @param stepsContent The content inside the steps array
     * @return List of parsed [AgentAction]s
     */
    private fun parseStepsArray(stepsContent: String): List<AgentAction> {
        val steps = mutableListOf<AgentAction>()

        // Try to parse as JSON-like objects: {"action": "Tap", "element": [x,y]}
        val objectPattern = Regex("""\{[^}]+\}""")
        val matches = objectPattern.findAll(stepsContent)

        for (match in matches) {
            val stepStr = match.value
            val action = parseStepObject(stepStr)
            if (action != null) {
                steps.add(action)
            }
        }

        return steps
    }

    /**
     * Parses a single step object from JSON-like format.
     * Format: `{"action": "Tap", "element": [x,y]}`
     *
     * @param stepStr The JSON-like step object string
     * @return The parsed [AgentAction], or null if parsing fails
     */
    private fun parseStepObject(stepStr: String): AgentAction? {
        // Extract action type
        val actionMatch = BATCH_ACTION_PATTERN.find(stepStr) ?: return null
        val actionType = actionMatch.groupValues[1]

        return when (actionType) {
            "Tap" -> {
                val elementMatch = BATCH_ELEMENT_PATTERN.find(stepStr)
                if (elementMatch != null) {
                    val x = parseCoordinateValue(elementMatch.groupValues[1], "x", stepStr)
                    val y = parseCoordinateValue(elementMatch.groupValues[2], "y", stepStr)
                    validateCoordinates(stepStr, "x" to x, "y" to y)
                    AgentAction.Tap(x = x, y = y)
                } else {
                    null
                }
            }

            "Swipe" -> {
                val startMatch = BATCH_START_PATTERN.find(stepStr)
                val endMatch = BATCH_END_PATTERN.find(stepStr)
                if (startMatch != null && endMatch != null) {
                    val startX = parseCoordinateValue(startMatch.groupValues[1], "startX", stepStr)
                    val startY = parseCoordinateValue(startMatch.groupValues[2], "startY", stepStr)
                    val endX = parseCoordinateValue(endMatch.groupValues[1], "endX", stepStr)
                    val endY = parseCoordinateValue(endMatch.groupValues[2], "endY", stepStr)
                    validateCoordinates(
                        stepStr,
                        "startX" to startX,
                        "startY" to startY,
                        "endX" to endX,
                        "endY" to endY,
                    )
                    AgentAction.Swipe(
                        startX = startX,
                        startY = startY,
                        endX = endX,
                        endY = endY,
                    )
                } else {
                    null
                }
            }

            "Long Press" -> {
                val elementMatch = BATCH_ELEMENT_PATTERN.find(stepStr)
                if (elementMatch != null) {
                    val x = parseCoordinateValue(elementMatch.groupValues[1], "x", stepStr)
                    val y = parseCoordinateValue(elementMatch.groupValues[2], "y", stepStr)
                    validateCoordinates(stepStr, "x" to x, "y" to y)
                    AgentAction.LongPress(x = x, y = y)
                } else {
                    null
                }
            }

            "Double Tap" -> {
                val elementMatch = BATCH_ELEMENT_PATTERN.find(stepStr)
                if (elementMatch != null) {
                    val x = parseCoordinateValue(elementMatch.groupValues[1], "x", stepStr)
                    val y = parseCoordinateValue(elementMatch.groupValues[2], "y", stepStr)
                    validateCoordinates(stepStr, "x" to x, "y" to y)
                    AgentAction.DoubleTap(x = x, y = y)
                } else {
                    null
                }
            }

            "Wait" -> {
                val durationMatch = BATCH_DURATION_PATTERN.find(stepStr)
                val durationStr = durationMatch?.groupValues?.get(1) ?: "1"
                val duration =
                    durationStr
                        .replace("seconds", "")
                        .replace("second", "")
                        .trim()
                        .toFloatOrNull() ?: 1.0f
                AgentAction.Wait(duration)
            }

            "Back" -> {
                AgentAction.Back
            }

            "Home" -> {
                AgentAction.Home
            }

            "VolumeUp" -> {
                AgentAction.VolumeUp
            }

            "VolumeDown" -> {
                AgentAction.VolumeDown
            }

            "Power" -> {
                AgentAction.Power
            }

            else -> {
                null
            }
        }
    }

    /**
     * Safely parses a coordinate value string to Int.
     * Handles overflow and invalid number formats.
     *
     * @param value The string value to parse
     * @param name The coordinate name (for error messages)
     * @param originalAction The original action string (for error context)
     * @return The parsed integer value
     * @throws ActionParseException if the value cannot be parsed or overflows
     */
    private fun parseCoordinateValue(value: String, name: String, originalAction: String): Int = try {
        val longValue = value.toLong()
        if (longValue > Int.MAX_VALUE || longValue < Int.MIN_VALUE) {
            throw ActionParseException(
                "Coordinate '$name' value '$value' is too large (overflow) in: $originalAction",
            )
        }
        longValue.toInt()
    } catch (e: NumberFormatException) {
        throw ActionParseException("Invalid coordinate '$name' value '$value' in: $originalAction")
    }

    /**
     * Validates that all coordinates are within the valid range (0-999).
     *
     * @param originalAction The original action string for error context
     * @param coordinates Pairs of coordinate name and value to validate
     * @throws CoordinateOutOfRangeException if any coordinate is out of range
     */
    private fun validateCoordinates(originalAction: String, vararg coordinates: Pair<String, Int>) {
        val invalidCoords =
            coordinates
                .filter { (_, value) -> value < MIN_COORDINATE || value > MAX_COORDINATE }
                .map { (name, value) -> InvalidCoordinate(name, value) }

        if (invalidCoords.isNotEmpty()) {
            throw CoordinateOutOfRangeException(invalidCoords, originalAction)
        }
    }
}

/**
 * Exception thrown when action parsing fails.
 *
 * @param message Description of the parsing failure
 */
class ActionParseException(message: String) : Exception(message)

/**
 * Exception thrown when coordinates are out of valid range (0-999).
 * Contains details about which coordinates are invalid for retry with correction hint.
 *
 * @param invalidCoordinates List of coordinates that are out of range
 * @param originalAction The original action string that contained invalid coordinates
 */
class CoordinateOutOfRangeException(val invalidCoordinates: List<InvalidCoordinate>, val originalAction: String) :
    Exception("Coordinates out of range: ${invalidCoordinates.joinToString { "${it.name}=${it.value}" }}")

/**
 * Represents an invalid coordinate value.
 *
 * @param name The coordinate name (e.g., "x", "y", "startX", "endY")
 * @param value The invalid coordinate value
 */
data class InvalidCoordinate(val name: String, val value: Int)
