package org.autojs.autojs.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.autojs.autojs.core.image.ColorFinder
import org.autojs.autojs.core.opencv.OpenCVHelper
import org.autojs.autojs.runtime.api.ImageFeatureMatching
import org.autojs.autojs.runtime.api.ImageSimilarity
import org.autojs.autojs.runtime.api.PngQuantBridge
import org.json.JSONArray
import org.json.JSONObject
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.features2d.DescriptorMatcher
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*
import org.autojs.autojs.core.opencv.Mat as OwnedMat

/** Native algorithms shared by Node and Bun. Called on the provider's serialized image lane. */
internal class NodeAdvancedImages(
    private val context: Context,
    private val registry: NodeBridgeResourceRegistry,
    private val checkRoom: () -> Unit,
) {
    data class NativeImage(val bitmap: Bitmap, val mat: Mat)
    data class Features(val descriptor: ImageFeatureMatching.FeatureMatchingDescriptor, val region: Rect)
    private val matrices = ConcurrentHashMap<String, Mat>()
    private val features = ConcurrentHashMap<String, Features>()
    private val resources = ConcurrentHashMap<String, NodeBridgeResourceHandle>()
    private val quantizations = ConcurrentHashMap.newKeySet<PngQuantBridge.CancellationToken>()
    val handleCount get() = resources.size
    private val pixels = NodeImageOperations(context)

    fun init() { OpenCVHelper.initIfNeeded(context) { } }
    fun cancelPending() { quantizations.forEach { it.cancel() } }

    fun <T> withMat(bitmap: Bitmap, retained: Mat? = null, action: (OwnedMat) -> T): T {
        init()
        val mat = if (retained == null) OwnedMat() else OwnedMat.from(retained)
        try { if (retained == null) Utils.bitmapToMat(bitmap, mat, true); return action(mat) } finally { mat.release() }
    }

    fun matFromBitmap(bitmap: Bitmap, channels: Int): Mat {
        init()
        val mat = OwnedMat()
        try {
            Utils.bitmapToMat(bitmap, mat, true)
            when(channels) { 1 -> Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2GRAY); 3 -> Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2RGB) }
            return mat
        } catch(error: Throwable) { mat.release(); throw error }
    }

    fun image(mat: Mat): NativeImage {
        try {
            require(!mat.empty() && mat.depth() == CvType.CV_8U && mat.channels() in setOf(1,3,4)) { "An image requires a non-empty 8-bit Mat with 1, 3 or 4 channels." }
            val bitmap = Bitmap.createBitmap(mat.cols(),mat.rows(),Bitmap.Config.ARGB_8888)
            try { Utils.matToBitmap(mat,bitmap,true); return NativeImage(bitmap,mat) }
            catch(error: Throwable) { bitmap.recycle(); throw error }
        } catch(error: Throwable) { mat.release(); throw error }
    }

    private fun room() = checkRoom()
    fun imread(path: String): JSONObject {
        init(); room()
        val mat=Imgcodecs.imread(path)
        try {
            require(!mat.empty()) { "Image file could not be decoded as a Mat." }
            val id="mat-${UUID.randomUUID()}"
            val resource=registry.register(NodeBridgeResourceType.IMAGE_HANDLE,id,mapOf("kind" to "mat")) { matrices.remove(id)?.release(); resources.remove(id) }
            matrices[id]=mat;resources[id]=resource
            return JSONObject().put("id",id).put("__autojs6MatHandle",id).put("width",mat.cols()).put("height",mat.rows()).put("channels",mat.channels()).put("depth",mat.depth())
        } catch(error: Throwable) { mat.release(); throw error }
    }

    fun matToImage(value: JSONObject): NativeImage {
        val id=value.optString("__autojs6MatHandle",value.optString("id"))
        val mat=matrices[id]?:throw IllegalArgumentException("An active Mat handle from this execution is required.")
        // imread uses BGR(A), while public image pixels and Bitmap-backed Mats use RGB(A).
        val copy=mat.clone()
        try { when(copy.channels()) { 3 -> Imgproc.cvtColor(copy,copy,Imgproc.COLOR_BGR2RGB);4 -> Imgproc.cvtColor(copy,copy,Imgproc.COLOR_BGRA2RGBA) } }
        catch(error: Throwable) { copy.release();throw error }
        return image(copy)
    }

    fun recycle(id: String): Boolean = resources[id]?.close("recycle") ?: false
    fun feature(value: Any?): Features? = (value as? JSONObject)?.let { features[it.optString("__autojs6ImageFeaturesHandle",it.optString("id"))] }

    fun detect(bitmap: Bitmap, retained: Mat?, options: JSONObject): Features = withMat(bitmap,retained) { source ->
        val region=pixels.region(bitmap,options)
        val requested=number(options,"scale",0.0)
        val scale=if(requested>0) requested else min(1.0,min(sqrt(1_000_000.0/(region.width.toDouble()*region.height)),1600.0/max(region.width,region.height)))
        require(scale>0&&scale<=8) { "Feature scale must be in (0, 8]." }
        val method=when(options.optString("method","SIFT").uppercase()) { "SIFT" -> ImageFeatureMatching.FEATURE_MATCHING_METHOD_SIFT;"ORB" -> ImageFeatureMatching.FEATURE_MATCHING_METHOD_ORB;else -> throw IllegalArgumentException("Feature method must be SIFT or ORB.") }
        val maxFeatures=integer(options,"maxFeatures",0);require(maxFeatures>=0) { "maxFeatures must be non-negative." }
        val crop=OwnedMat(source,region)
        try { Features(ImageFeatureMatching.createFeatureMatchingDescriptor(crop,ImageFeatureMatching.DetectionOptions(method,scale.toFloat(),-1,maxFeatures)),region) }
        finally { crop.release() }
    }

    fun registerFeatures(value: Features): JSONObject {
        try { room() } catch(error: Throwable) {value.descriptor.release();throw error}
        val id="features-${UUID.randomUUID()}"
        val resource=registry.register(NodeBridgeResourceType.IMAGE_HANDLE,id,mapOf("kind" to "features")) { features.remove(id)?.descriptor?.release();resources.remove(id) }
        features[id]=value;resources[id]=resource
        return JSONObject().put("id",id).put("__autojs6ImageFeaturesHandle",id).put("count",value.descriptor.keyPointCount)
            .put("method",if(value.descriptor.method==ImageFeatureMatching.FEATURE_MATCHING_METHOD_ORB)"ORB" else "SIFT")
            .put("scale",value.descriptor.scale.toDouble()).put("region",rect(value.region))
    }

    fun match(scene: Features, target: Features, options: JSONObject, saveDrawing: ((Bitmap) -> Unit)?): JSONObject? {
        val binary=target.descriptor.descriptors.depth()==CvType.CV_8U
        val ratio=number(options,"threshold",if(binary)0.8 else 0.7);require(ratio>0&&ratio<=1) { "Feature threshold must be in (0, 1]." }
        val ransac=number(options,"ransacThreshold",3.0);require(ransac>0) { "ransacThreshold must be positive." }
        val inliers=integer(options,"minInliers",4);require(inliers>=4) { "minInliers must be at least 4." }
        val matcher=options.opt("matcher")?.takeUnless{it==JSONObject.NULL}?.let{constant(DescriptorMatcher::class.java,it)}?:-1
        val result=ImageFeatureMatching.featureMatching(scene.descriptor,target.descriptor,ImageFeatureMatching.MatchingOptions(matcher,ratio.toFloat(),ransac,inliers,saveDrawing!=null))
        try {
            if(saveDrawing!=null) result.matches?.let { drawing ->
                val converted=drawing.clone()
                if(converted.channels()==3) Imgproc.cvtColor(converted,converted,Imgproc.COLOR_BGR2RGB)
                val rendered=image(converted);try{saveDrawing(rendered.bitmap)}finally{rendered.bitmap.recycle();rendered.mat.release()}
            }
            val quad=result.quad?:return null
            fun position(p: Point)=point(Point(p.x/scene.descriptor.scale+scene.region.x,p.y/scene.descriptor.scale+scene.region.y))
            return JSONObject().put("__autojs6ObjectFrame",true).put("topLeft",position(quad[0])).put("topRight",position(quad[1]))
                .put("bottomLeft",position(quad[2])).put("bottomRight",position(quad[3])).put("matches",result.matchCount).put("inliers",result.inlierCount)
                .put("points",JSONArray().apply { result.points.forEach { put(position(it)) } })
        } finally { result.release() }
    }

    fun filter(method: String, bitmap: Bitmap, retained: Mat?, options: JSONObject): NativeImage = withMat(bitmap,retained) { source ->
        val output=OwnedMat();val scratch=OwnedMat()
        try {
            when(method) {
                "inRange","interval" -> {
                    val low: Scalar; val high: Scalar
                    if(method=="inRange") {low=color(options.get("lower"));high=color(options.get("upper"))}
                    else {val threshold=number(options,"threshold").coerceIn(0.0,255.0);low=color(options.get("color"),-threshold);high=color(options.get("color"),threshold)}
                    Core.inRange(source,low,high,output)
                }
                "adaptiveThreshold" -> {
                    gray(source,scratch)
                    val block=integer(options,"blockSize",3);require(block>1&&block%2==1) { "Adaptive blockSize must be odd and greater than 1." }
                    val adaptive=constant(Imgproc::class.java,options.opt("adaptiveMethod")?:"MEAN_C","ADAPTIVE_THRESH_")
                    val type=constant(Imgproc::class.java,options.opt("type")?:"BINARY","THRESH_")
                    Imgproc.adaptiveThreshold(scratch,output,number(options,"maxValue",255.0),adaptive,type,block,number(options,"C",0.0))
                }
                "bilateralFilter" -> {
                    if(source.channels()==4)Imgproc.cvtColor(source,scratch,Imgproc.COLOR_RGBA2RGB) else source.copyTo(scratch)
                    Imgproc.bilateralFilter(scratch,output,integer(options,"d",0),number(options,"sigmaColor",40.0),number(options,"sigmaSpace",20.0),
                        constant(Core::class.java,options.opt("borderType")?:"DEFAULT","BORDER_"))
                    if(source.channels()==4) {
                        val alpha=OwnedMat();try{Core.extractChannel(source,alpha,3);Imgproc.cvtColor(output,output,Imgproc.COLOR_RGB2RGBA);Core.insertChannel(alpha,output,3)}finally{alpha.release()}
                    }
                }
                "cvtColor" -> Imgproc.cvtColor(source,output,constant(Imgproc::class.java,options.get("code"),"COLOR_"),integer(options,"dstCn",0))
                else -> throw IllegalArgumentException("Unknown advanced image filter: $method")
            }
        } catch(error: Throwable) { output.release();throw error } finally { scratch.release() }
        image(output)
    }

    fun circles(bitmap: Bitmap, retained: Mat?, options: JSONObject): JSONArray = withMat(bitmap,retained) { source ->
        val region=pixels.region(bitmap,options);val crop=OwnedMat(source,region);val gray=OwnedMat();val found=OwnedMat()
        try {
            gray(crop,gray)
            Imgproc.HoughCircles(gray,found,Imgproc.HOUGH_GRADIENT,number(options,"dp",1.0),number(options,"minDst",bitmap.height/8.0),
                number(options,"param1",100.0),number(options,"param2",100.0),integer(options,"minRadius",0),integer(options,"maxRadius",0))
            require(found.total()<=NodeImageOperations.MAX_JSON_POINTS) { "Circle result exceeds the JSON point limit." }
            JSONArray().apply{for(y in 0 until found.rows())for(x in 0 until found.cols()){val c=found[y,x];put(JSONObject().put("x",c[0]+region.x).put("y",c[1]+region.y).put("radius",c[2]))}}
        }finally{crop.release();gray.release();found.release()}
    }

    fun isGrayscale(bitmap: Bitmap): Boolean {
        val row=IntArray(bitmap.width)
        for(y in 0 until bitmap.height){bitmap.getPixels(row,0,bitmap.width,0,y,bitmap.width,1);if(row.any{Color.red(it)!=Color.green(it)||Color.red(it)!=Color.blue(it)})return false}
        return true
    }

    fun mean(bitmap: Bitmap, options: JSONObject): Int {
        val r=pixels.region(bitmap,options);val row=IntArray(r.width);var red=0L;var green=0L;var blue=0L;var alpha=0L
        for(y in r.y until r.y+r.height){bitmap.getPixels(row,0,r.width,r.x,y,r.width,1);for(c in row){red+=Color.red(c);green+=Color.green(c);blue+=Color.blue(c);alpha+=Color.alpha(c)}}
        val n=r.width.toLong()*r.height
        return Color.argb((alpha.toDouble()/n).roundToInt(),(red.toDouble()/n).roundToInt(),(green.toDouble()/n).roundToInt(),(blue.toDouble()/n).roundToInt())
    }

    fun count(bitmap: Bitmap, value: Any, options: JSONObject): Int = withMat(bitmap) { source ->
        ColorFinder.countPointsByColor(source,pixels.parseColor(value),integer(options,"threshold",4).also{require(it in 0..255)},pixels.region(bitmap,options))
    }

    fun findPoints(bitmap: Bitmap,value: Any,offsets: JSONArray,options: JSONObject): JSONArray = withMat(bitmap) { source ->
        val points=IntArray(offsets.length()*3)
        for(i in 0 until offsets.length()) {
            val a=offsets.optJSONArray(i);val o=offsets.optJSONObject(i)
            points[i*3]=pixels.integer(a?.opt(0)?:o?.opt("x"),"offset x")
            points[i*3+1]=pixels.integer(a?.opt(1)?:o?.opt("y"),"offset y")
            points[i*3+2]=pixels.parseColor(a?.get(2)?:requireNotNull(o).get("color"))
        }
        JSONArray().apply{ColorFinder.findPointsByColors(bitmap,source,pixels.parseColor(value),integer(options,"threshold",4).also{require(it in 0..255)},pixels.region(bitmap,options),points,NodeImageOperations.MAX_JSON_POINTS).forEach{put(point(it))}}
    }

    fun detectMultiple(bitmap: Bitmap,value: Any,offsets: JSONArray,x: Int,y: Int,options: JSONObject): Boolean {
        if(!pixels.detectsColor(bitmap,value,x,y,options))return false
        for(i in 0 until offsets.length()) {
            val a=offsets.optJSONArray(i);val o=offsets.optJSONObject(i)
            val px=x.toLong()+pixels.integer(a?.opt(0)?:o?.opt("x"),"offset x");val py=y.toLong()+pixels.integer(a?.opt(1)?:o?.opt("y"),"offset y")
            if(px !in 0L until bitmap.width.toLong()||py !in 0L until bitmap.height.toLong())return false
            if(!pixels.detectsColor(bitmap,a?.get(2)?:requireNotNull(o).get("color"),px.toInt(),py.toInt(),options))return false
        }
        return true
    }

    fun similarity(method: String,a: Bitmap,am: Mat?,b: Bitmap,bm: Mat?,options: JSONObject): Any = withMat(a,am) { first -> withMat(b,bm) { second ->
        val metric=if(method=="getSimilarity")options.optString("metric",options.optString("type","mssim")).lowercase().let{if(it=="pnsr")"psnr" else it} else method
        val value: Any=when(metric){
            "psnr"->ImageSimilarity.psnr(first,second);"ssim"->ImageSimilarity.ssim(first,second);"mssim"->ImageSimilarity.mssim(first,second)
            "hist"->ImageSimilarity.hist(first,second);"mse"->ImageSimilarity.mse(first,second);"ncc"->ImageSimilarity.ncc(first,second)
            "isEqual"->ImageSimilarity.isEqual(first,second);else->throw IllegalArgumentException("Unknown image similarity metric: $metric")
        }
        if(value is Double&&!value.isFinite())value.toString() else value
    }}

    fun quantize(bitmap: Bitmap,options: JSONObject): PngQuantBridge.Result {
        val cancellation=PngQuantBridge.CancellationToken();quantizations.add(cancellation)
        try{return PngQuantBridge.quantizeWithResult(bitmap,quantizationOptions(options),cancellation)}finally{quantizations.remove(cancellation)}
    }

    fun quantizeToFile(bitmap: Bitmap,file: java.io.File,options: JSONObject): PngQuantBridge.OutputResult {
        val parent=file.absoluteFile.parentFile?:throw IllegalArgumentException("Quantization output needs a parent directory.")
        if(!parent.exists())check(parent.mkdirs()){ "Could not create the quantization output directory." }
        val temporary=java.io.File.createTempFile("autojs6-quant-",".png",parent)
        val cancellation=PngQuantBridge.CancellationToken();quantizations.add(cancellation)
        try {
            val result=java.io.FileOutputStream(temporary).use { output ->
                PngQuantBridge.quantizeToFileDescriptor(bitmap,output.fd,quantizationOptions(options),cancellation)
            }
            if(cancellation.isCancelled||Thread.currentThread().isInterrupted)throw java.util.concurrent.CancellationException("PNG quantization was cancelled.")
            check(temporary.renameTo(file)){ "Could not publish the quantized image." }
            return result
        }finally{quantizations.remove(cancellation);temporary.delete()}
    }

    fun quantizationOptions(options: JSONObject,defaultQuality: Int=100): PngQuantBridge.Options {
        fun long(name:String,default:Long)=number(options,name,default.toDouble()).also{require(it%1==0.0&&it>0&&it<=Long.MAX_VALUE.toDouble())}.toLong()
        return PngQuantBridge.Options(integer(options,"maxColors",256),integer(options,"speed",8),integer(options,"minQuality",0),
            integer(options,"maxQuality",integer(options,"quality",defaultQuality)),number(options,"ditheringLevel",0.0).toFloat(),integer(options,"posterizeBits",0),options.optBoolean("preserveAlpha",true),
            long("maxPixels",PngQuantBridge.Options.DEFAULT_MAX_PIXELS),long("maxMemoryBytes",PngQuantBridge.Options.DEFAULT_MAX_MEMORY_BYTES))
    }

    fun encode(bitmap: Bitmap,format: String,quality: Int): ByteArray {
        require(quality in 0..100) { "Image quality must be in 0..100." }
        val type=when(format.lowercase()){"png"->Bitmap.CompressFormat.PNG;"jpg","jpeg"->Bitmap.CompressFormat.JPEG;"webp"->Bitmap.CompressFormat.WEBP;else->throw IllegalArgumentException("Image format must be png, jpeg or webp.")}
        return ByteArrayOutputStream().use { stream -> check(bitmap.compress(type,quality,stream)){"Image encoding failed."};stream.toByteArray() }
    }

    private fun gray(source: Mat,output: Mat) {when(source.channels()){1->source.copyTo(output);3->Imgproc.cvtColor(source,output,Imgproc.COLOR_RGB2GRAY);4->Imgproc.cvtColor(source,output,Imgproc.COLOR_RGBA2GRAY);else->throw IllegalArgumentException("Unsupported image channel count.")}}
    private fun color(value:Any,offset:Double=0.0):Scalar {val c=pixels.parseColor(value);return Scalar(Color.red(c)+offset,Color.green(c)+offset,Color.blue(c)+offset,Color.alpha(c).toDouble())}
    private fun number(options:JSONObject,key:String,default:Double?=null):Double {val raw=options.opt(key);val value=if(raw==null||raw==JSONObject.NULL)default else(raw as? Number)?.toDouble();require(value!=null&&value.isFinite()){ "$key must be finite." };return value}
    private fun integer(options:JSONObject,key:String,default:Int)=pixels.integer(options.opt(key)?.takeUnless{it==JSONObject.NULL}?:default,key)
    private fun constant(type:Class<*>,value:Any,prefix:String=""):Int = if(value is Number)pixels.integer(value,"OpenCV constant") else {
        require(value is String){"OpenCV constant must be a name or number."};val name=value.trim().uppercase().replace('-','_').let{if(it.startsWith(prefix))it else prefix+it}
        try{type.getField(name).getInt(null)}catch(_:ReflectiveOperationException){throw IllegalArgumentException("Unknown OpenCV constant: $name")}
    }
    companion object {
        fun point(p:Point)=JSONObject().put("x",p.x).put("y",p.y)
        fun rect(r:Rect)=JSONObject().put("x",r.x).put("y",r.y).put("width",r.width).put("height",r.height)
    }
}
