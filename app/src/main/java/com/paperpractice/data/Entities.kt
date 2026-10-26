package com.paperpractice.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "papers")
data class PaperEntity(
    @PrimaryKey val id: String,
    val name: String,
    val importedAt: Long
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
    indices = [Index("paperId"), Index("parentId")]
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
    indices = [Index("categoryId")]
)
data class QuestionEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val questionText: String,
    val answerText: String,
    val position: Int
)
