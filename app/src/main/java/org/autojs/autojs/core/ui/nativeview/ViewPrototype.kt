package org.autojs.autojs.core.ui.nativeview

import android.annotation.SuppressLint
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.CompoundButton
import org.autojs.autojs.core.eventloop.EventEmitter
import org.autojs.autojs.core.ui.BaseEvent
import org.autojs.autojs.core.ui.attribute.ViewAttributes
import org.autojs.autojs.core.ui.nativeview.NativeView.LongClickEvent
import org.autojs.autojs.core.ui.widget.JsListView
import org.autojs.autojs.runtime.ScriptRuntime
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class ViewPrototype(
    private val view: View,
    private val viewAttributes: ViewAttributes,
    private val scope: Scriptable,
    runtime: ScriptRuntime,
) {
    var widget: Any? = null

    val maxListeners
        get() = mEventEmitter.maxListeners

    private val mEventEmitter = runtime.events.emitter()
    private val mRegisteredEvents = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    fun attr(name: String): Any = viewAttributes[name]?.get() ?: Undefined.SCRIPTABLE_UNDEFINED

    fun attr(name: String, value: Any?) {
        viewAttributes[name]?.set(org.mozilla.javascript.ScriptRuntime.toString(value))
    }

    fun attrReset(name: String) = attr(name, attr(name))

    fun click() {
        view.performClick()
    }

    fun longClick() {
        view.performLongClick()
    }

    fun click(listener: BaseFunction) {
        on("click", listener)
    }

    fun longClick(listener: BaseFunction) {
        on("long_click", listener)
    }

    fun once(eventName: String, listener: BaseFunction): EventEmitter {
        registerEventIfNeeded(eventName)
        return mEventEmitter.once(eventName, listener)
    }

    fun on(eventName: String, listener: BaseFunction): EventEmitter {
        registerEventIfNeeded(eventName)
        return mEventEmitter.on(eventName, listener)
    }

    fun addListener(eventName: String, listener: BaseFunction): EventEmitter {
        registerEventIfNeeded(eventName)
        return mEventEmitter.addListener(eventName, listener)
    }

    private fun registerEventIfNeeded(eventName: String) {
        if (!mRegisteredEvents.contains(eventName)) {
            when (Looper.getMainLooper() == Looper.myLooper()) {
                true -> eventName.takeIf { registerEvent(it) }?.let { mRegisteredEvents.add(it) }
                else -> view.post { eventName.takeIf { registerEvent(it) }?.let { mRegisteredEvents.add(it) } }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun registerEvent(eventName: String): Boolean = when (eventName) {
        "touch", "touch_down", "touch_up", "touch_move" -> run {
            view.setOnTouchListener { v: View, event: MotionEvent ->
                val e = BaseEvent(scope, event, event.javaClass)
                // Log.d(LOG_TAG, "this = " + NativeView.this + ", emitter = " + mEventEmitter + ", view = " + mView);
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> emit("touch_down", e, v)
                    MotionEvent.ACTION_UP -> emit("touch_up", e, v)
                    MotionEvent.ACTION_MOVE -> emit("touch_move", e, v)
                }
                emit("touch", e, v)
                e.isConsumed
            }
            true
        }
        "click" -> run {
            view.setOnClickListener { v: View -> emit("click", v) }
            true
        }
        "long_click" -> run {
            view.setOnLongClickListener { v: View ->
                val e = BaseEvent(scope, LongClickEvent(v))
                emit("long_click", e, v)
                e.isConsumed
            }
            true
        }
        "key", "key_up", "key_down" -> run {
            view.setOnKeyListener { v: View, keyCode: Int, event: KeyEvent ->
                val e = BaseEvent(scope, event, event.javaClass)
                if (event.action == MotionEvent.ACTION_DOWN) {
                    emit("key_down", keyCode, e, v)
                } else if (event.action == MotionEvent.ACTION_UP) {
                    emit("key_up", keyCode, e, v)
                }
                emit("key", keyCode, e, v)
                e.isConsumed
            }
            true
        }
        "scroll_change" -> run {
            view.setOnScrollChangeListener { v: View, scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int ->
                val e = BaseEvent(scope, NativeView.ScrollEvent(scrollX, scrollY, oldScrollX, oldScrollY))
                emit("scroll_change", e, v)
            }
            true
        }
        "check" -> run {
            if (view is CompoundButton) {
                view.setOnCheckedChangeListener { buttonView: CompoundButton, isChecked: Boolean -> emit("check", isChecked, buttonView) }
                return@run true
            }
            if (view is JsListView) {
                view.setOnItemTouchListener(object : JsListView.OnItemTouchListener {
                    override fun onItemClick(listView: JsListView, itemView: View, item: Any, pos: Int) {
                        emit("item_click", item, pos, itemView, listView)
                    }

                    override fun onItemLongClick(listView: JsListView, itemView: View, item: Any, pos: Int): Boolean {
                        val e = BaseEvent(scope, LongClickEvent(itemView))
                        emit("item_long_click", e, item, pos, itemView, listView)
                        return e.isConsumed
                    }
                })
                return@run true
            }
            false
        }
        "item_click", "item_long_click" -> run {
            if (view is JsListView) {
                view.setOnItemTouchListener(object : JsListView.OnItemTouchListener {
                    override fun onItemClick(listView: JsListView, itemView: View, item: Any, pos: Int) {
                        emit("item_click", item, pos, itemView, listView)
                    }

                    override fun onItemLongClick(listView: JsListView, itemView: View, item: Any, pos: Int): Boolean {
                        val e = BaseEvent(scope, LongClickEvent(itemView))
                        emit("item_long_click", e, item, pos, itemView, listView)
                        return e.isConsumed
                    }
                })
                return@run true
            }
            false
        }
        else -> false
    }

    fun emit(eventName: String, vararg args: Any): Boolean = mEventEmitter.emit(eventName, *args)

    fun eventNames(): Array<String> = mEventEmitter.eventNames()

    fun listenerCount(eventName: String): Int = mEventEmitter.listenerCount(eventName)

    fun listeners(eventName: String): Array<Any> = mEventEmitter.listeners(eventName)

    fun prependListener(eventName: String, listener: BaseFunction): EventEmitter = mEventEmitter.prependListener(eventName, listener)

    fun prependOnceListener(eventName: String, listener: BaseFunction): EventEmitter = mEventEmitter.prependOnceListener(eventName, listener)

    fun removeAllListeners(): EventEmitter = mEventEmitter.removeAllListeners()

    fun removeAllListeners(eventName: String): EventEmitter = mEventEmitter.removeAllListeners(eventName)

    fun removeListener(eventName: String, listener: BaseFunction): EventEmitter = mEventEmitter.removeListener(eventName, listener)

    fun setMaxListeners(n: Int): EventEmitter = mEventEmitter.setMaxListeners(n)

    companion object {

        @JvmStatic
        fun defaultMaxListeners(): Int = EventEmitter.defaultMaxListeners()

    }

}