package com.khawar.studia.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns

/** A save file the user picked with the system file picker, e.g. in Documents. */
class DocumentRemote(private val context: Context, val uri: Uri) : Remote {
    override val label: String = "your save file"

    override val fileName: String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

    override val location: String = run {
        val name = fileName ?: "studia.json"
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
        when {
            // Phone storage ids look like "primary:Documents/studia.json"
            uri.authority == "com.android.externalstorage.documents" && docId != null ->
                docId.substringAfter(':').ifEmpty { name }
            uri.authority == "com.android.providers.downloads.documents" -> "Download/$name"
            else -> name
        }
    }

    override fun read(): String? = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }

    override fun write(text: String) {
        val resolver = context.contentResolver
        // "wt" truncates; some providers only accept "w".
        val out = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: resolver.openOutputStream(uri, "w")
            ?: error("Can’t open $uri for writing")
        out.use { it.write(text.toByteArray()) }
    }

    companion object {
        /** Starting folder for the "folder on this phone" option. */
        val PHONE_DOCUMENTS: Uri =
            DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents")
    }
}
