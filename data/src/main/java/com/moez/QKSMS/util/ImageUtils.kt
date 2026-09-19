/*
 * Copyright (C) 2017 Moez Bhatti <moez.bhatti@gmail.com>
 *
 * This file is part of QKSMS.
 *
 * QKSMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QKSMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QKSMS.  If not, see <http://www.gnu.org/licenses/>.
 */
package dev.octoshrimpy.quik.util

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import dev.octoshrimpy.quik.model.Attachment
import java.io.ByteArrayOutputStream
import java.io.Closeable

object ImageUtils {

    fun getScaledGif(context: Context, uri: Uri, maxWidth: Int, maxHeight: Int, quality: Int = 90): ByteArray {
        val gif = GlideApp
                .with(context)
                .asGif()
                .load(uri)
                .centerInside()
                .encodeQuality(quality)
                .submit(maxWidth, maxHeight)
                .get()

        val outputStream = ByteArrayOutputStream()
        GifEncoder(context, GlideApp.get(context).bitmapPool).encodeTransformedToStream(gif, outputStream)
        return outputStream.toByteArray()
    }

    fun getScaledImage(context: Context, uri: Uri, maxWidth: Int, maxHeight: Int, quality: Int = 90): ByteArray {
        return GlideApp
            .with(context)
            .`as`(ByteArray::class.java)
            .load(uri)
            .apply(
                RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
            )
            .centerInside()
            .encodeQuality(quality)
            .submit(maxWidth, maxHeight)
            .get()
    }

    /**
     * Decodes an image once and re-encodes it from memory at whatever size and quality the
     * caller asks for.
     *
     * Compression searches for a size that fits under the carrier's limit, so it encodes the
     * same image many times. Going through [getScaledImage] for each attempt re-read and
     * re-decoded the source file every time, and decoding is by far the expensive part. Callers
     * should [open] once per attachment and reuse the result for every attempt.
     *
     * Close it when done so the decoded bitmap can be reclaimed.
     */
    class ScaledImageEncoder private constructor(private val source: Bitmap) : Closeable {

        val width: Int get() = source.width
        val height: Int get() = source.height

        fun encode(width: Int, height: Int, quality: Int = 90): ByteArray {
            val target = if (width == source.width && height == source.height) source
            else Bitmap.createScaledBitmap(source, width, height, true)

            val out = ByteArrayOutputStream()
            target.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (target !== source) target.recycle()
            return out.toByteArray()
        }

        /**
         * Encode at [width]x[height], dropping quality until the result fits [targetBytes].
         * Returns the smallest attempt if nothing fits, so the caller still has something to send.
         */
        fun encodeWithinBytes(width: Int, height: Int, targetBytes: Int): ByteArray {
            var result = encode(width, height, 90)
            if (result.size <= targetBytes) return result
            for (quality in 80 downTo 40 step 10) {
                result = encode(width, height, quality)
                if (result.size <= targetBytes) return result
            }
            return result
        }

        override fun close() = source.recycle()

        companion object {
            /**
             * Returns null when the image cannot be decoded, so callers can fall back rather than
             * dividing by a zero dimension.
             */
            fun open(context: Context, uri: Uri, targetWidth: Int, targetHeight: Int): ScaledImageEncoder? {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

                // Decode no smaller than the largest size we'll be asked for, so re-encoding
                // never has to upscale, but no larger than necessary either.
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, targetWidth, targetHeight)
                }
                // With no carrier cap the decode target is the original size, which on a modern
                // camera can be hundreds of megabytes. Returning null lets the caller fall back
                // to Glide, which streams rather than holding the whole bitmap.
                val bitmap = try {
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, opts)
                    }
                } catch (e: OutOfMemoryError) {
                    null
                } ?: return null

                return ScaledImageEncoder(bitmap)
            }

            private fun sampleSizeFor(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
                var sample = 1
                while (width / (sample * 2) >= targetWidth && height / (sample * 2) >= targetHeight) {
                    sample *= 2
                }
                return sample
            }
        }
    }

    /**
     * Get file size, by first using contentResolver.openAssetFileDescriptor
     * then if it fails, by reading the file size directly from the size column in contentResolver,
     * then if both of those fail, by defaulting to reading the input stream
     */
    fun fetchFileSize(context: Context, attachment: Attachment): Long {
        val resolver = context.contentResolver

        val afdSize = resolver
            .openAssetFileDescriptor(attachment.uri, "r")
            ?.use { it.length }

        if (afdSize != null && afdSize != AssetFileDescriptor.UNKNOWN_LENGTH) {
            return afdSize
        }

        val cursorSize = resolver.query(
            attachment.uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index != -1 && cursor.moveToFirst() && !cursor.isNull(index)) {
                cursor.getLong(index)
            } else {
                null
            }
        }

        if (cursorSize != null && cursorSize > 0) {
            return cursorSize
        }

        val inputStreamBytes = resolver.openInputStream(attachment.uri)?.use { inputStream ->
            val buffer = ByteArray(8192)
            var totalBytes = 0L
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                totalBytes += bytesRead
            }
            totalBytes
        }
        return requireNotNull(inputStreamBytes)
    }

}
