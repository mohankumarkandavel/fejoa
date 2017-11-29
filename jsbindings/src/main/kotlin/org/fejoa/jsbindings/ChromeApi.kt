package org.fejoa.jsbindings

import org.w3c.dom.Window
import kotlin.js.Json


external class OnTabRemovedEvent {
    fun addListener(callback: (tabId: Int, removeInfo: dynamic) -> Unit)
}

external class OnTabCreatedEvent {
    fun addListener(callback: (tab: Tab) -> Unit)
}

external class OnTabUpdatedEvent {
    fun addListener(callback: (tabId: Int, changeInfo: dynamic, tab: Tab) -> Unit)
}


external class OnTabReplacedEvent {
    fun addListener(callback: (addedTabId: Int, removedTabId: Int) -> Unit)
}

external class Tabs {
    fun get(tabId: Int, callback: (tab: Tab) -> Unit)
    fun getSelected(windowId: Int?, callBack: (tab: Tab) -> Unit)
    fun query(queryInfo: Json, callback: (result: Array<Tab>) -> Unit)
    fun sendMessage(tabId: Int, message: Json, options: Any? = definedExternally,
                    responseCallback: ((response: Json?) -> Unit)? = definedExternally)

    val onCreated: OnTabCreatedEvent
    val onUpdated: OnTabUpdatedEvent
    val onRemoved: OnTabRemovedEvent
    val onReplaced: OnTabReplacedEvent
}

external class Tab {
    val id: Int?
    val url: String?
}

external class Extension {
    fun getBackgroundPage(): Window
}

external class MessageSender {
    val tab: Tab?
    val frameId: Int?
    val id: String?
    val url: String?
    val tlsChannelId: String?
}

external class OnMessageEvent {
    fun addListener(callback: (message: Json?, sender: MessageSender, sendResponse: (arg: Json?) -> Unit) -> Unit)
}

external class Runtime {
    val onMessage: OnMessageEvent
    val lastError: String?
}

/* please, don't implement this interface! */
external class TemplateType {
    companion object
}
inline val TemplateType.Companion.BASIC: TemplateType get() = "basic".asDynamic().unsafeCast<TemplateType>()
inline val TemplateType.Companion.IMAGE: TemplateType get() = "image".asDynamic().unsafeCast<TemplateType>()
inline val TemplateType.Companion.LIST: TemplateType get() = "list".asDynamic().unsafeCast<TemplateType>()
inline val TemplateType.Companion.PROGRESS: TemplateType get() = "progress".asDynamic().unsafeCast<TemplateType>()


external class OnButtonClickEvent {
    fun addListener(callback: (notificationId: String, buttonIndex: Int) -> Unit)
}

external class Notification {
    fun create(notificationId: String, options: Json, callback: ((notificationId: String) -> Unit)? = definedExternally)
    fun clear(notificationId: String, callback: ((wasCleared: Boolean) -> Unit)? = definedExternally)
    val onButtonClicked: OnButtonClickEvent
}

external class StorageArea {
    fun get(keys: String, callback: (items: Json) -> Unit)
    fun set(items: Json, callback: (() -> Unit)? = definedExternally)
    fun remove(key: String, callback: (() -> Unit)? = definedExternally)
    fun clear(callback: (() -> Unit)? = definedExternally)
}

external class Storage {
    val local: StorageArea
    val sync: StorageArea
}

external object chrome {
    val tabs: Tabs
    val extension: Extension
    val runtime: Runtime
    val notifications: Notification
    val storage: Storage
}
