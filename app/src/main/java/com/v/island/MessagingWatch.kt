package com.v.island

import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Bundle
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

object MessagingWatch {

    const val TELEGRAM = "org.telegram.messenger"

    private const val MESSAGES_KEPT = 60
    private const val ALLOWED = "messagingAllowed"
    private const val SIMULATED = "debug/simulated"
    // Notification.EXTRA_CONVERSATION_ICON is hidden from the SDK and did not compile, so its key is spelled out.
    private const val CONVERSATION_ICON = "android.conversationIcon"

    private class Message(
        val sender: String,
        val text: String,
        val time: Long,
        val isSelf: Boolean,
        val type: String?
    )

    private class Conversation(val id: String, val packageName: String) {
        var title = ""
        var isGroup = false
        var avatar: Icon? = null
        var avatarUri: Any = JSONObject.NULL
        var hasAvatarUri = false
        val messages = mutableListOf<Message>()
        var unread = 0
        var key: String? = null
        var contentIntent: PendingIntent? = null
        var reply: Notification.Action? = null
        var markRead: Notification.Action? = null
        var updatedAt = 0L
    }

    private val conversations = LinkedHashMap<String, Conversation>()

    fun isMessaging(statusBarNotification: StatusBarNotification): Boolean {
        val notification = statusBarNotification.notification
        return statusBarNotification.packageName == TELEGRAM &&
            notification.category == Notification.CATEGORY_MESSAGE &&
            notification.shortcutId != null &&
            notification.extras.containsKey(Notification.EXTRA_MESSAGES)
    }

    private fun idOf(statusBarNotification: StatusBarNotification) =
        statusBarNotification.packageName + "/" + statusBarNotification.notification.shortcutId

    fun collect(context: Context, statusBarNotification: StatusBarNotification) {
        val notification = statusBarNotification.notification
        val extras = notification.extras
        val id = idOf(statusBarNotification)
        val isAllowed = synchronized(this) {
            val conversation = conversations.getOrPut(id) { Conversation(id, statusBarNotification.packageName) }
            val raw = extras.getParcelableArray(Notification.EXTRA_MESSAGES, Bundle::class.java).orEmpty()
            val incoming = raw.mapNotNull { messageOf(it) }
            incoming.forEach { message ->
                val isKnown = conversation.messages.any {
                    it.time == message.time && it.sender == message.sender && it.text == message.text
                }
                if (isKnown) return@forEach
                conversation.messages.add(message)
                if (!message.isSelf) conversation.unread += 1
            }
            conversation.messages.sortBy { it.time }
            while (conversation.messages.size > MESSAGES_KEPT) conversation.messages.removeAt(0)
            conversation.isGroup = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)
            conversation.title = (
                extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
                    ?: extras.getCharSequence(Notification.EXTRA_TITLE)
                )?.toString().orEmpty()
            val avatar = extras.getParcelable(CONVERSATION_ICON, Icon::class.java)
                ?: raw.lastOrNull { messageOf(it)?.isSelf == false }
                    ?.getParcelable("sender_person", Person::class.java)?.icon
            if (avatar != null) {
                conversation.avatar = avatar
                conversation.hasAvatarUri = false
            }
            conversation.key = statusBarNotification.key
            conversation.contentIntent = notification.contentIntent
            val actions = notification.actions.orEmpty()
            conversation.reply = actions.firstOrNull { !it.remoteInputs.isNullOrEmpty() }
            conversation.markRead = actions.firstOrNull { it.semanticAction == Notification.Action.SEMANTIC_ACTION_MARK_AS_READ }
                ?: actions.firstOrNull { it.remoteInputs.isNullOrEmpty() }
            conversation.updatedAt = statusBarNotification.postTime
            isAllowedLocked(context, id)
        }
        if (isAllowed) publish(context)
    }

    fun gone(context: Context, key: String) {
        val isKnown = synchronized(this) {
            val ended = conversations.values.filter { it.key == key }
            ended.forEach { it.key = null }
            ended.isNotEmpty()
        }
        if (isKnown) publish(context)
    }

    private fun messageOf(bundle: Bundle): Message? {
        val text = bundle.getCharSequence("text")?.toString().orEmpty()
        if (text.isBlank()) return null
        val person = bundle.getParcelable("sender_person", Person::class.java)
        val sender = bundle.getCharSequence("sender")?.toString() ?: person?.name?.toString()
        return Message(
            sender = sender.orEmpty(),
            text = text,
            time = bundle.getLong("time"),
            isSelf = sender == null,
            type = bundle.getString("type")
        )
    }

    private fun allowedOf(context: Context): JSONObject =
        runCatching { JSONObject(Preferences.of(context).getString(ALLOWED, null) ?: "{}") }
            .getOrDefault(JSONObject())

    private fun isAllowedLocked(context: Context, id: String) = id == SIMULATED || allowedOf(context).has(id)

    fun allow(context: Context, id: String, isAllowed: Boolean) {
        val allowed = allowedOf(context)
        synchronized(this) {
            if (isAllowed) allowed.put(id, conversations[id]?.title.orEmpty())
            else allowed.remove(id)
        }
        Preferences.of(context).edit().putString(ALLOWED, allowed.toString()).apply()
        publish(context)
    }

    fun candidates(context: Context): JSONArray {
        val allowed = allowedOf(context)
        return synchronized(this) {
            val seen = conversations.values.filter { it.id != SIMULATED }.sortedByDescending { it.updatedAt }
            JSONArray().apply {
                seen.forEach {
                    put(
                        JSONObject()
                            .put("id", it.id)
                            .put("title", it.title)
                            .put("messages", it.messages.size)
                            .put("isAllowed", allowed.has(it.id))
                    )
                }
                allowed.keys().asSequence().filter { id -> seen.none { it.id == id } }.forEach { id ->
                    put(
                        JSONObject()
                            .put("id", id)
                            .put("title", allowed.optString(id))
                            .put("messages", 0)
                            .put("isAllowed", true)
                    )
                }
            }
        }
    }

    fun publish(context: Context) {
        val allowed = allowedOf(context)
        val payload = synchronized(this) {
            JSONArray().apply {
                conversations.values
                    .filter { it.id == SIMULATED || allowed.has(it.id) }
                    .sortedByDescending { it.updatedAt }
                    .forEach { put(describe(it)) }
            }
        }
        BubbleService.deliverMessaging(payload)
    }

    private fun describe(conversation: Conversation): JSONObject {
        if (!conversation.hasAvatarUri) {
            conversation.avatarUri = IslandNotificationListener.avatar(conversation.avatar)
            conversation.hasAvatarUri = true
        }
        val style = AppStyles.of(conversation.packageName)
        return JSONObject()
            .put("id", conversation.id)
            .put("package", conversation.packageName)
            .put("app", style.key)
            .put("accent", style.accent)
            .put("title", conversation.title)
            .put("isGroup", conversation.isGroup)
            .put("avatarBase64", conversation.avatarUri)
            .put("unread", conversation.unread)
            .put("isLive", conversation.key != null)
            .put("canReply", conversation.reply != null)
            .put("canMarkRead", conversation.markRead != null)
            .put("updatedAt", conversation.updatedAt)
            .put("messages", JSONArray().apply {
                conversation.messages.forEach {
                    put(
                        JSONObject()
                            .put("sender", it.sender)
                            .put("text", it.text)
                            .put("time", it.time)
                            .put("isSelf", it.isSelf)
                            .put("type", it.type ?: JSONObject.NULL)
                    )
                }
            })
    }

    fun simulate(context: Context) {
        synchronized(this) {
            val conversation = conversations.getOrPut(SIMULATED) { Conversation(SIMULATED, TELEGRAM) }
            val now = System.currentTimeMillis()
            conversation.title = "Simulated chat"
            conversation.messages.add(
                Message(
                    sender = "Simulated chat",
                    text = "Message ${conversation.messages.size + 1}, sent at " +
                        android.text.format.DateFormat.format("HH:mm:ss", now),
                    time = now,
                    isSelf = false,
                    type = null
                )
            )
            while (conversation.messages.size > MESSAGES_KEPT) conversation.messages.removeAt(0)
            conversation.unread += 1
            conversation.updatedAt = now
        }
        publish(context)
    }

    fun dump(statusBarNotification: StatusBarNotification) {
        val notification = statusBarNotification.notification
        val extras = notification.extras
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES, Bundle::class.java).orEmpty()
        val people = messages.joinToString(" | ") { message ->
            val person = message.getParcelable("sender_person", Person::class.java)
            "sender=${message.getCharSequence("sender") != null} person=${person != null} " +
                "personIcon=${person?.icon?.type} personKey=${person?.key != null} type=${message.getString("type")}"
        }
        val actions = notification.actions.orEmpty().joinToString(" | ") { action ->
            "${action.title} semantic=${action.semanticAction} inputs=" +
                action.remoteInputs.orEmpty().joinToString(",") { it.resultKey } +
                " generated=${action.allowGeneratedReplies}"
        }
        val wearable = extras.getBundle("android.wearable.EXTENSIONS")
            ?.let { Notification.WearableExtender(notification).actions }
            .orEmpty()
            .joinToString(" | ") { action ->
                "${action.title} inputs=" + action.remoteInputs.orEmpty().joinToString(",") { it.resultKey }
            }
        android.util.Log.d(
            "IslandBubble",
            "telegram extras: shortcut=${notification.shortcutId} " +
                "isGroup=${extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)} " +
                "conversationTitle=${extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE) != null} " +
                "conversationIcon=${extras.getParcelable(CONVERSATION_ICON, Icon::class.java)?.type} " +
                "largeIcon=${notification.getLargeIcon()?.type} " +
                "bubble=${notification.bubbleMetadata != null} " +
                "messages=${messages.size} [$people] actions=[$actions] wearable=[$wearable]"
        )
    }
}
