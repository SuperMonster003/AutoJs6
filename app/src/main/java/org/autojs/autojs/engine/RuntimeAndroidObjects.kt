package org.autojs.autojs.engine

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Paint
import android.net.Uri
import org.autojs.autojs.external.fileprovider.AppFileProvider
import org.autojs.autojs.theme.ThemeColorManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Real Android values stay in their execution; JSON snapshots can be used by native View attributes. */
internal class RuntimeAndroidObjects(private val context:Context,private val cwd:String):AutoCloseable {
    private val objects=linkedMapOf<String,Any>()
    private var closed=false
    @Synchronized fun invoke(method:String,args:JSONArray):Any? {
        check(!closed) { "Android object execution is closed." }
        return when(method) {
            "createPaint" -> {
                val options=args.optJSONObject(0)?:JSONObject();val paint=Paint()
                paint.color=options.optInt("color",android.graphics.Color.BLACK)
                paint.isAntiAlias=options.optBoolean("antiAlias",true)
                paint.strokeWidth=finite(options,"strokeWidth",0f)
                paint.textSize=finite(options,"textSize",16f)
                paint.style=Paint.Style.valueOf(options.optString("style","fill").uppercase())
                val id=UUID.randomUUID().toString();objects[id]=paint;paintSnapshot(id,paint)
            }
            "setPaintColor" -> { paint(args.getString(0)).color=args.getInt(1);null }
            "getPaintInfo" -> paintSnapshot(args.getString(0),paint(args.getString(0)))
            "measureText" -> paint(args.getString(0)).measureText(args.getString(1)).toDouble()
            "createColorStateList" -> {
                val descriptor=args.getJSONObject(0);val value=colorStateList(descriptor);val id=UUID.randomUUID().toString();objects[id]=value
                JSONObject(descriptor.toString()).put("id",id).put("__autojs6AndroidObject", "colorStateList").put("defaultColor",value.defaultColor).put("stateful",value.isStateful)
            }
            "getColorForState" -> {
                val value=objects[args.getString(0)] as? ColorStateList?:error("ColorStateList is closed or belongs to another execution.")
                val states=args.getJSONArray(1);value.getColorForState(IntArray(states.length()){stateCode(states.get(it))},args.optInt(2,value.defaultColor))
            }
            "close" -> objects.remove(args.getString(0))!=null
            "getThemeColor" -> ThemeColorManager.currentThemeColor.let { JSONObject().put("colorPrimary",it.colorPrimary).put("colorPrimaryDark",it.colorPrimaryDark).put("colorAccent",it.colorAccent) }
            "getUriForFile" -> {
                val raw=args.getString(0);val file=if(raw.startsWith("file:"))File(Uri.parse(raw).path?:error("Invalid file URI"))else File(raw)
                val resolved=(if(file.isAbsolute)file else File(cwd,file.path)).canonicalFile;require(resolved.exists()) { "File does not exist: $resolved" }
                uriSnapshot(AppFileProvider.getUriForFile(context,resolved))
            }
            "parseUri" -> uriSnapshot(Uri.parse(args.getString(0)))
            else -> throw IllegalArgumentException("Unknown Android helper: $method")
        }
    }
    private fun paint(id:String)=objects[id] as? Paint?:error("Paint is closed or belongs to another execution.")
    private fun paintSnapshot(id:String,paint:Paint)=JSONObject().put("id",id).put("__autojs6AndroidObject","paint").put("color",paint.color)
        .put("antiAlias",paint.isAntiAlias).put("strokeWidth",paint.strokeWidth.toDouble()).put("textSize",paint.textSize.toDouble()).put("style",paint.style.name.lowercase())
    @Synchronized fun activeCount():Int=objects.size
    @Synchronized override fun close(){closed=true;objects.clear()}
    companion object {
        val methods=setOf("createPaint","setPaintColor","getPaintInfo","measureText","createColorStateList","getColorForState","close","getThemeColor","getUriForFile","parseUri")
        private fun finite(value:JSONObject,key:String,default:Float):Float=if(!value.has(key))default else value.getDouble(key).also { require(it.isFinite()&&it>=0&&it<=Float.MAX_VALUE) { "$key must be finite and non-negative." } }.toFloat()
        fun colorStateList(value:JSONObject):ColorStateList {
            val colors=value.getJSONArray("colors");val states=value.getJSONArray("states")
            require(colors.length()>0 && colors.length()==states.length()) { "Color state and color arrays must have equal non-zero lengths." }
            return ColorStateList(Array(states.length()){index->states.getJSONArray(index).let { row->IntArray(row.length()){stateCode(row.get(it))} }},IntArray(colors.length()){colors.getInt(it)})
        }
        private fun stateCode(value:Any):Int {
            if(value is Number)return value.toInt().also { require(it!=0 && it.toDouble()==value.toDouble()) }
            val raw=value.toString();val name=raw.removePrefix("!").removePrefix("state_")
            val code=when(name){"enabled"->android.R.attr.state_enabled;"pressed"->android.R.attr.state_pressed;"focused"->android.R.attr.state_focused
                "checked"->android.R.attr.state_checked;"selected"->android.R.attr.state_selected;"activated"->android.R.attr.state_activated
                "window_focused"->android.R.attr.state_window_focused;else->throw IllegalArgumentException("Unknown Android state: $raw")}
            return if(raw.startsWith("!"))-code else code
        }
        fun uriSnapshot(uri:Uri)=JSONObject().put("schema","autojs6-node-app-uri-descriptor-v1").put("value",uri.toString())
            .put("scheme",uri.scheme?:JSONObject.NULL).put("authority",uri.authority?:JSONObject.NULL).put("host",uri.host?:JSONObject.NULL)
            .put("port",uri.port).put("path",uri.path?:JSONObject.NULL).put("query",uri.query?:JSONObject.NULL).put("fragment",uri.fragment?:JSONObject.NULL)
        @JvmStatic fun intentSnapshot(intent:Intent):JSONObject=JSONObject().put("action",intent.action?:JSONObject.NULL).put("data",intent.dataString?:JSONObject.NULL)
            .put("type",intent.type?:JSONObject.NULL).put("packageName",intent.`package`?:JSONObject.NULL).put("flags",intent.flags)
            .put("categories",JSONArray(intent.categories?.toList().orEmpty())).put("extras",JSONObject().apply {
                intent.extras?.let { bundle -> for(key in bundle.keySet()) { @Suppress("DEPRECATION") val value=bundle.get(key);put(key,JSONObject.wrap(value)) } }
            })
    }
}
