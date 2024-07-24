/*
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.conversation.v2

import android.annotation.TargetApi
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.StyleSpan
import android.view.View
import com.annimon.stream.Stream
import com.google.android.mms.pdu_alt.CharacterSets
import com.google.android.mms.pdu_alt.EncodedStringValue
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.security.SecureRandom
import java.util.Arrays
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import network.loki.messenger.R
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.components.ComposeText

object Util {
    private val TAG: String = Log.tag(Util::class.java)

    private val BUILD_LIFESPAN = TimeUnit.DAYS.toMillis(90)

    fun <T> asList(vararg elements: T): List<T> {
        val result = mutableListOf<T>() // LinkedList()
        Collections.addAll(result, *elements)
        return result
    }

    fun join(list: Array<String?>, delimiter: String?): String {
        return join(listOf(*list), delimiter)
    }

    fun <T> join(list: Collection<T>, delimiter: String?): String {
        val result = StringBuilder()
        var i = 0

        for (item in list) {
            result.append(item)
            if (++i < list.size) result.append(delimiter)
        }

        return result.toString()
    }

    fun join(list: LongArray, delimeter: String?): String {
        val boxed: MutableList<Long> = ArrayList(list.size)

        for (i in list.indices) {
            boxed.add(list[i])
        }

        return join(boxed, delimeter)
    }

    @SafeVarargs
    fun <E> join(vararg lists: List<E>): List<E> {
        val totalSize = Stream.of(*lists).reduce(0) { sum: Int, list: List<E> -> sum + list.size }
        val joined: MutableList<E> = ArrayList(totalSize)

        for (list in lists) {
            joined.addAll(list)
        }

        return joined
    }

    fun join(list: List<Long>, delimeter: String?): String {
        val sb = StringBuilder()

        for (j in list.indices) {
            if (j != 0) sb.append(delimeter)
            sb.append(list[j])
        }

        return sb.toString()
    }

    fun rightPad(value: String, length: Int): String {
        if (value.length >= length) {
            return value
        }

        val out = StringBuilder(value)
        while (out.length < length) {
            out.append(" ")
        }

        return out.toString()
    }

    fun isEmpty(value: Array<EncodedStringValue?>?): Boolean {
        return value == null || value.size == 0
    }

    fun isEmpty(value: ComposeText?): Boolean {
        return value == null || value.text == null || TextUtils.isEmpty(value.textTrimmed)
    }

    fun isEmpty(collection: Collection<*>?): Boolean {
        return collection == null || collection.isEmpty()
    }

    fun isEmpty(charSequence: CharSequence?): Boolean {
        return charSequence == null || charSequence.length == 0
    }

    fun hasItems(collection: Collection<*>?): Boolean {
        return collection != null && !collection.isEmpty()
    }

    fun <K, V> getOrDefault(map: Map<K, V>, key: K, defaultValue: V): V? {
        return if (map.containsKey(key)) map[key] else defaultValue
    }

    fun getFirstNonEmpty(vararg values: String?): String {
        for (value in values) {
            if (!value.isNullOrEmpty()) { return value }
        }
        return ""
    }

    fun emptyIfNull(value: String?): String {
        return value ?: ""
    }

    fun emptyIfNull(value: CharSequence?): CharSequence {
        return value ?: ""
    }

    fun getBoldedString(value: String?): CharSequence {
        val spanned = SpannableString(value)
        spanned.setSpan(
            StyleSpan(Typeface.BOLD), 0,
            spanned.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        return spanned
    }

    fun toIsoString(bytes: ByteArray?): String {
        try {
            return String(bytes!!, charset(CharacterSets.MIMENAME_ISO_8859_1))
        } catch (e: UnsupportedEncodingException) {
            throw AssertionError("ISO_8859_1 must be supported!")
        }
    }

    fun toIsoBytes(isoString: String): ByteArray {
        try {
            return isoString.toByteArray(charset(CharacterSets.MIMENAME_ISO_8859_1))
        } catch (e: UnsupportedEncodingException) {
            throw AssertionError("ISO_8859_1 must be supported!")
        }
    }

    fun toUtf8Bytes(utf8String: String): ByteArray {
        try {
            return utf8String.toByteArray(charset(CharacterSets.MIMENAME_UTF_8))
        } catch (e: UnsupportedEncodingException) {
            throw AssertionError("UTF_8 must be supported!")
        }
    }

    fun wait(lock: Any, timeout: Long) {
        try {
            (lock as Object).wait(timeout)
        } catch (ie: InterruptedException) {
            throw AssertionError(ie)
        }
    }

    fun split(source: String, delimiter: String): List<String> {
        val results = mutableListOf<String>()

        if (TextUtils.isEmpty(source)) {
            return results
        }

        val elements =
            source.split(delimiter.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        Collections.addAll(results, *elements)

        return results
    }

    fun split(input: ByteArray?, firstLength: Int, secondLength: Int): Array<ByteArray?> {
        val parts = arrayOfNulls<ByteArray>(2)

        parts[0] = ByteArray(firstLength)
        System.arraycopy(input, 0, parts[0], 0, firstLength)

        parts[1] = ByteArray(secondLength)
        System.arraycopy(input, firstLength, parts[1], 0, secondLength)

        return parts
    }

    fun combine(vararg elements: ByteArray?): ByteArray {
        try {
            val baos = ByteArrayOutputStream()

            for (element in elements) {
                baos.write(element)
            }

            return baos.toByteArray()
        } catch (e: IOException) {
            throw AssertionError(e)
        }
    }

    fun trim(input: ByteArray?, length: Int): ByteArray {
        val result = ByteArray(length)
        System.arraycopy(input, 0, result, 0, result.size)

        return result
    }

    fun getSecretBytes(size: Int): ByteArray {
        return getSecretBytes(SecureRandom(), size)
    }

    fun getSecretBytes(secureRandom: SecureRandom, size: Int): ByteArray {
        val secret = ByteArray(size)
        secureRandom.nextBytes(secret)
        return secret
    }

    fun <T> getRandomElement(elements: Array<T>): T {
        return elements[SecureRandom().nextInt(elements.size)]
    }

    fun <T> getRandomElement(elements: List<T>): T {
        return elements[SecureRandom().nextInt(elements.size)]
    }

    fun equals(a: Any?, b: Any?): Boolean {
        return a === b || (a != null && a == b)
    }

    fun hashCode(vararg objects: Any?): Int {
        return objects.contentHashCode()
    }

    fun uri(uri: String?): Uri? {
        return if (uri == null) null
        else Uri.parse(uri)
    }

    @TargetApi(VERSION_CODES.KITKAT)
    fun isLowMemory(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        return (VERSION.SDK_INT >= VERSION_CODES.KITKAT && activityManager.isLowRamDevice) ||
                activityManager.largeMemoryClass <= 64
    }

    fun clamp(value: Int, min: Int, max: Int): Int {
        return min(max(value.toDouble(), min.toDouble()), max.toDouble()).toInt()
    }

    fun clamp(value: Long, min: Long, max: Long): Long {
        return min(max(value.toDouble(), min.toDouble()), max.toDouble()).toLong()
    }

    fun clamp(value: Float, min: Float, max: Float): Float {
        return min(max(value.toDouble(), min.toDouble()), max.toDouble()).toFloat()
    }

    /**
     * Returns half of the difference between the given length, and the length when scaled by the
     * given scale.
     */
    fun halfOffsetFromScale(length: Int, scale: Float): Float {
        val scaledLength = length * scale
        return (length - scaledLength) / 2
    }

    fun readTextFromClipboard(context: Context): String? {
        run {
            val clipboardManager =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            return if (clipboardManager.hasPrimaryClip() && clipboardManager.primaryClip!!.itemCount > 0) {
                clipboardManager.primaryClip!!.getItemAt(0).text.toString()
            } else {
                null
            }
        }
    }

    fun writeTextToClipboard(context: Context, text: String) {
        writeTextToClipboard(context, context.getString(R.string.app_name), text)
    }

    fun writeTextToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

    fun toIntExact(value: Long): Int {
        if (value.toInt().toLong() != value) {
            throw ArithmeticException("integer overflow")
        }
        return value.toInt()
    }

    fun isEquals(first: Long?, second: Long): Boolean {
        return first != null && first == second
    }

    @SafeVarargs
    fun <T> concatenatedList(vararg items: Collection<T>): List<T> {
        val concat: MutableList<T> = ArrayList(
            Stream.of(*items).reduce(0) { sum: Int, list: Collection<T> -> sum + list.size })

        for (list in items) {
            concat.addAll(list)
        }

        return concat
    }

    fun isLong(value: String): Boolean {
        try {
            value.toLong()
            return true
        } catch (e: NumberFormatException) {
            return false
        }
    }

    fun parseInt(integer: String, defaultValue: Int): Int {
        return try {
            integer.toInt()
        } catch (e: NumberFormatException) {
            defaultValue
        }
    }

    // Method to determine if we're currently in a left-to-right or right-to-left language like Arabic
    fun usingRightToLeftLanguage(context: Context): Boolean {
        val config = context.resources.configuration
        return config.layoutDirection == View.LAYOUT_DIRECTION_RTL
    }

    // Method to determine if we're currently in a left-to-right or right-to-left language like Arabic
    fun usingLeftToRightLanguage(context: Context): Boolean {
        val config = context.resources.configuration
        return config.layoutDirection == View.LAYOUT_DIRECTION_LTR
    }
}