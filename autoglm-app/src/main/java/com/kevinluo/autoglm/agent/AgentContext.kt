package com.kevinluo.autoglm.agent

import com.kevinluo.autoglm.model.ChatMessage

/**
 * Manages the conversation context for the phone agent.
 * Handles message accumulation and context reset.
 *
 * Note: Images are not stored in the context. They are passed directly to the model
 * at request time to avoid memory accumulation.
 *
 */
class AgentContext(private val systemPrompt: String) {
    private val messages: MutableList<ChatMessage> = mutableListOf()

    init {
        // Initialize with system prompt (Requirement 8.1)
        messages.add(ChatMessage.System(systemPrompt))
    }

    /**
     * Adds a user message to the context.
     *
     * @param text The text content of the user message
     */
    fun addUserMessage(text: String) {
        messages.add(ChatMessage.User(text))
    }

    /**
     * Adds an assistant message to the context (Requirement 8.2).
     *
     * @param content The content of the assistant's response
     */
    fun addAssistantMessage(content: String) {
        messages.add(ChatMessage.Assistant(content))
    }

    /**
     * Returns a copy of all messages in the context.
     *
     * @return List of all chat messages in order
     */
    fun getMessages(): List<ChatMessage> = messages.toList()

    /**
     * Returns the number of messages in the context.
     *
     * @return The message count
     */
    fun getMessageCount(): Int = messages.size

    /**
     * Resets the context for a new task (Requirement 8.4).
     * Clears all messages and re-initializes with the system prompt.
     */
    fun reset() {
        messages.clear()
        messages.add(ChatMessage.System(systemPrompt))
    }

    /**
     * Checks if the context has been initialized (has at least the system prompt).
     *
     * @return true if initialized, false otherwise
     */
    fun isInitialized(): Boolean = messages.isNotEmpty() && messages.first() is ChatMessage.System

    /**
     * Gets the system prompt used to initialize this context.
     *
     * @return The system prompt string
     */
    fun getSystemPrompt(): String = systemPrompt

    /**
     * Checks if the context is empty (only contains system prompt).
     *
     * @return true if only system prompt exists, false otherwise
     */
    fun isEmpty(): Boolean = messages.size == 1 && messages.first() is ChatMessage.System

    /**
     * Gets the last message in the context, if any.
     *
     * @return The last ChatMessage or null if empty
     */
    fun getLastMessage(): ChatMessage? = messages.lastOrNull()

    /**
     * Removes the last user message from the context.
     * Used when a step needs to be retried (e.g., after pause).
     *
     * @return true if a user message was removed, false if no user message found
     */
    fun removeLastUserMessage(): Boolean {
        for (i in messages.indices.reversed()) {
            if (messages[i] is ChatMessage.User) {
                messages.removeAt(i)
                return true
            }
        }
        return false
    }

    /**
     * Removes the last assistant message from the context.
     * Used when a step needs to be retried (e.g., after pause).
     *
     * @return true if an assistant message was removed, false if no assistant message found
     */
    fun removeLastAssistantMessage(): Boolean {
        for (i in messages.indices.reversed()) {
            if (messages[i] is ChatMessage.Assistant) {
                messages.removeAt(i)
                return true
            }
        }
        return false
    }

    /**
     * Counts the number of assistant messages (completed steps).
     *
     * @return The number of assistant messages
     */
    fun getAssistantMessageCount(): Int = messages.count { it is ChatMessage.Assistant }

    /**
     * Counts the number of user messages.
     *
     * @return The number of user messages
     */
    fun getUserMessageCount(): Int = messages.count { it is ChatMessage.User }
}
