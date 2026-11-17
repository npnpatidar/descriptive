package com.paperpractice.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "papers")
data class PaperEntity(
    @PrimaryKey val id: String,
    val name: String,
    val importedAt: Long,
    val position: Int = 0
)

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = PaperEntity::class,
            parentColumns = ["id"],
            childColumns = ["paperId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("paperId"),
        Index("parentId"),
        Index("paperId", "parentId"),
        Index("paperId", "position")
    ]
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val paperId: String,
    val name: String,
    val position: Int,
    val parentId: String? = null
)

@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("categoryId"), Index("categoryId", "position")]
)
data class QuestionEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val questionText: String,
    val answerText: String,
    val position: Int
)

@Entity(
    tableName = "question_images",
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("questionId")]
)
data class QuestionImageEntity(
    @PrimaryKey val id: String,
    val questionId: String,
    val target: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val position: Int,
    val data: ByteArray
)

/** A global, user-owned tag. Identity is [normalizedName], not the display name. */
@Entity(
    tableName = "tags",
    indices = [Index(value = ["normalizedName"], unique = true)]
)
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String
)

/**
 * Junction between questions and tags. Both foreign keys cascade so deleting a
 * question or a tag removes its links; the additional tagId index backs the
 * "questions carrying a tag" and "count links for a tag" lookups. Room does not
 * support `WITHOUT ROWID` for junction tables at this version, so a composite
 * primary key is used instead.
 */
@Entity(
    tableName = "question_tags",
    primaryKeys = ["questionId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tagId")]
)
data class QuestionTagEntity(
    val questionId: String,
    val tagId: String
)
