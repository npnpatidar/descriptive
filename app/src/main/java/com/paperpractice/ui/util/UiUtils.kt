package com.paperpractice.ui.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import com.paperpractice.export.CreateDocumentRequest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object CreateDocumentContract : ActivityResultContract<CreateDocumentRequest, Uri?>() {

    override fun createIntent(context: Context, input: CreateDocumentRequest): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = input.mimeType
            putExtra(Intent.EXTRA_TITLE, input.fileName)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (resultCode == Activity.RESULT_OK) intent?.data else null
    }
}

fun formatImportedAt(timestamp: Long): String {
    val formatter = DateTimeFormatter
        .ofPattern("d MMM yyyy, HH:mm", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
    return formatter.format(Instant.ofEpochMilli(timestamp))
}

fun questionCountLabel(count: Int): String =
    if (count == 1) "1 question" else "$count questions"

fun categoryCountLabel(count: Int): String =
    if (count == 1) "1 category" else "$count categories"
