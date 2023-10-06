package com.example.bandReader.util

import android.R.attr.height
import android.R.attr.width
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import com.example.bandReader.MainActivity
import java.io.File


object FileUtils{
    fun getRealPath(context: Context, fileUri: Uri): String? {
        return pathFromURI(context, fileUri)
    }

    private fun pathFromURI(context: Context, uri: Uri): String? {
        return when {
            DocumentsContract.isDocumentUri(context, uri) -> {
                // ExternalStorageProvider
                when {
                    uri.isExternalStorageDocument() -> {
                        getExternalDocumentPath(uri)
                    }

                    uri.isDownloadsDocument() -> {
                        context.getDownloadsDocumentPath(uri)
                    }

                    uri.isMediaDocument() -> {
                        context.getMediaDocumentPath(uri)
                    }

                    else -> {
                        null
                    }
                }
            }

            "content".equals(uri.scheme, ignoreCase = true) -> {
                // Return the remote address
                if (uri.isGooglePhotosUri()) {
                    uri.lastPathSegment
                } else {
                    getDataColumn(
                        context,
                        uri,
                        null,
                        null,
                    )
                }
            }

            "file".equals(uri.scheme, ignoreCase = true) -> {
                uri.path
            }

            else -> {
                null
            }
        }
    }

    private fun getExternalDocumentPath(uri: Uri): String {
        val docId = DocumentsContract.getDocumentId(uri)
        val split =
            docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val type = split[0]
        Log.i("TAG","file type: $type"  )

        // This is for checking Main Memory
        return if ("primary".equals(type, ignoreCase = true)) {
            if (split.size > 1) {
                Environment.getExternalStorageDirectory()
                    .toString() + "/" + split[1]
            } else {
                Environment.getExternalStorageDirectory().toString() + "/"
            }
            // This is for checking SD Card
        } else {
            "storage" + "/" + docId.replace(":", "/")
        }
    }

    private fun getFilePath(context: Context, uri: Uri?): String? {
        var cursor: Cursor? = null
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
        )
        try {
            cursor = context.contentResolver.query(
                uri!!,
                projection,
                null,
                null,
                null,
            )
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                return cursor.getString(index)
            }
        } finally {
            cursor?.close()
        }
        return null
    }

    private fun Context.getDownloadsDocumentPath(uri: Uri): String? {
        val fileName = getFilePath(this, uri)
        if (fileName != null) {
            return Environment.getExternalStorageDirectory()
                .toString() + "/Download/" + fileName
        }
        var id = DocumentsContract.getDocumentId(uri)
        if (id.startsWith("raw:")) {
            id = id.replaceFirst("raw:".toRegex(), "")
            val file = File(id)
            if (file.exists()) return id
        }
        val contentUri = ContentUris.withAppendedId(
            Uri.parse("content://downloads/public_downloads"),
            java.lang.Long.valueOf(id),
        )
        return getDataColumn(this, contentUri, null, null)
    }

    private fun Context.getMediaDocumentPath(uri: Uri): String? {
        val docId = DocumentsContract.getDocumentId(uri)
        val split =
            docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val type = split[0]
        var contentUri: Uri? = null
        Log.i("TAG","file type: $type"  )
        when (type) {
            "document"->{
                contentUri = uri
            }
            else-> return null
        }
        val selection = "_id=?"
        val selectionArgs = arrayOf(
            split[1],
        )
        return getDataColumn(this, contentUri, selection, selectionArgs)
    }

    private fun getDataColumn(
        context: Context,
        uri: Uri?,
        selection: String?,
        selectionArgs: Array<String>?,
    ): String? {
        var cursor: Cursor? = null
        val column = "_data"
        val projection = arrayOf(
            column,
        )
        try {
            val wait = 0
            cursor = context.contentResolver.query(
                uri!!,
//                projection,
                null,
                selection,
                selectionArgs,
                null,
            )
            if (cursor != null && cursor.moveToFirst()) {
                val _display_name = cursor.getColumnIndexOrThrow("_display_name")

                return cursor.getString(_display_name)
            }
        } finally {
            cursor?.close()
        }
        return null
    }

    internal fun Uri.isExternalStorageDocument(): Boolean {
        return "com.android.externalstorage.documents" == authority
    }


    internal fun Uri.isDownloadsDocument(): Boolean {
        return "com.android.providers.downloads.documents" == authority
    }


    internal fun Uri.isMediaDocument(): Boolean {
        return "com.android.providers.media.documents" == authority
    }

    internal fun Uri.isGooglePhotosUri(): Boolean {
        return "com.google.android.apps.photos.content" == authority
    }

    fun getBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        val originalBitmap = when {
            Build.VERSION.SDK_INT < 28 -> MediaStore.Images.Media.getBitmap(
                context.contentResolver,
                uri
            )

            else -> {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            }
        }
        val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, 336/3,480/3, true)
        Log.i("TAG","bitmap created size ${scaledBitmap.byteCount / 1024}")
        return scaledBitmap
    }
}