package ru.jeland.os.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import ru.jeland.os.core.MediaArtwork
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/** Local OEM artwork only. No HTTP, file paths, network permission or unbounded full-size decoding. */
object MediaArtworkLoader {
    const val MAX_BYTES=2*1024*1024
    fun encoded(bytes:ByteArray?):MediaArtwork? {
        if(bytes==null||bytes.isEmpty()||bytes.size>MAX_BYTES)return null
        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        if(bounds.outWidth !in 1..16384||bounds.outHeight !in 1..16384)return null
        var scale=1
        while(bounds.outWidth/scale>512||bounds.outHeight/scale>512)scale*=2
        val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply {inSampleSize=scale})?:return null
        return try {bitmap(bitmap)}finally {bitmap.recycle()}
    }
    fun bitmap(bitmap:Bitmap?):MediaArtwork? {
        if(bitmap==null||bitmap.isRecycled||bitmap.width !in 1..16384||bitmap.height !in 1..16384)return null
        val ratio=(512f/maxOf(bitmap.width,bitmap.height)).coerceAtMost(1f)
        val small=if(ratio<1)Bitmap.createScaledBitmap(bitmap,(bitmap.width*ratio).toInt().coerceAtLeast(1),(bitmap.height*ratio).toInt().coerceAtLeast(1),true)else bitmap
        return try {
            val bytes=ByteArrayOutputStream().use {small.compress(Bitmap.CompressFormat.JPEG,88,it);it.toByteArray()}
            MediaArtwork(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)},bytes)
        }finally {if(small!==bitmap)small.recycle()}
    }
    fun local(context:Context,raw:String?):MediaArtwork? {
        val uri=raw?.takeIf {it.length<=2048}?.let(Uri::parse)?:return null
        // Known OEM/media provider authorities, never arbitrary content providers or file://.
        if(uri.scheme!="content"||uri.authority !in setOf("media","com.desaysv.vehicle.carplayapp","com.desaysv.svmusicapp","com.desaysv.svaudioapp"))return null
        return runCatching {context.contentResolver.openInputStream(uri)?.use {stream->
            val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(out.size()<=MAX_BYTES){val n=stream.read(buffer,0,minOf(buffer.size,MAX_BYTES+1-out.size()));if(n<0)break;out.write(buffer,0,n)}
            encoded(out.toByteArray())
        }}.getOrNull()
    }
}
