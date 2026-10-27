package com.paperpractice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paperpractice.domain.SessionQuestion
import com.paperpractice.domain.TestSession
import com.paperpractice.ui.util.categoryCountLabel
import com.paperpractice.ui.util.questionCountLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onExport: () -> Unit
) {
    val session = viewModel.session
    var showAnswers by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Practice test") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onExport, enabled = session != null) {
                        Icon(
                            Icons.Outlined.FileDownload,
                            contentDescription = "Export questions"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (session == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(28.dp)
                ) {
                    Text(
                        "No practice test yet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Go back and choose categories and question counts to generate one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onBack) { Text("Configure a test") }
                }
            }
        } else {
            PracticeContent(
                session = session,
                showAnswers = showAnswers,
                onToggleAnswers = { showAnswers = !showAnswers },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }
}

@Composable
private fun PracticeContent(
    session: TestSession,
    showAnswers: Boolean,
    onToggleAnswers: () -> Unit,
    modifier: Modifier = Modifier
) {
    val groups = remember(session) {
        session.questions
            .groupBy { it.categoryId }
            .values
            .toList()
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 10.dp,
            bottom = 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SessionHeader(session)
        }

        item {
            FilledTonalButton(
                onClick = onToggleAnswers,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    if (showAnswers) Icons.Outlined.VisibilityOff else {
                        Icons.Outlined.Visibility
                    },
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(if (showAnswers) "Hide Answers" else "Show Answers")
            }
        }

        groups.forEach { questions ->
            val categoryName = questions.first().categoryPath
                .joinToString(" / ")
                .ifBlank { questions.first().categoryName }
            item(key = "category-${questions.first().categoryId}") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = categoryName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (questions.size == 1) "1 question" else {
                            "${questions.size} questions"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(questions, key = { it.questionId }) { question ->
                QuestionCard(question = question, showAnswer = showAnswers)
            }
        }
    }
}

@Composable
private fun SessionHeader(session: TestSession) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                "Randomised practice test",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                session.paperName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${questionCountLabel(session.questions.size)}  \u00b7  " +
                    categoryCountLabel(session.categoryCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun QuestionCard(question: SessionQuestion, showAnswer: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${question.number}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                RichTextContent(
                    text = question.questionText,
                    style = MaterialTheme.typography.bodyLarge,
                    // Selectable TextViews can keep a stale height in lazy
                    // lists, clipping the end of long questions.
                    selectable = false
                )
                AttachedImages(
                    images = question.questionImages,
                    modifier = Modifier.padding(top = 10.dp),
                    description = "Question image"
                )

                if (showAnswer) {
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 14.dp, bottom = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Text(
                        "Answer",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            RichTextContent(
                                text = question.answerText.ifBlank {
                                    "(No answer provided)"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                selectable = false
                            )
                            AttachedImages(
                                images = question.answerImages,
                                modifier = Modifier.padding(top = 8.dp),
                                description = "Answer image"
                            )
                        }
                    }
                }
            }
        }
    }
}
