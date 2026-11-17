package com.paperpractice.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PaperEntity::class,
        CategoryEntity::class,
        QuestionEntity::class,
        QuestionImageEntity::class,
        TagEntity::class,
        QuestionTagEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun paperDao(): PaperDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE papers ADD COLUMN position INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `question_images` (
                        `id` TEXT NOT NULL,
                        `questionId` TEXT NOT NULL,
                        `target` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `width` INTEGER NOT NULL,
                        `height` INTEGER NOT NULL,
                        `position` INTEGER NOT NULL,
                        `data` BLOB NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`questionId`) REFERENCES `questions`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_question_images_questionId` " +
                        "ON `question_images` (`questionId`)"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_categories_paperId_parentId` " +
                        "ON `categories` (`paperId`, `parentId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_questions_categoryId_position` " +
                        "ON `questions` (`categoryId`, `position`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_categories_paperId_position` " +
                        "ON `categories` (`paperId`, `position`)"
                )
            }
        }

        /**
         * Adds the global tag library and its question junction table. Both
         * tables are additive and empty after the migration, so no existing
         * paper, category, question or image data is touched. The SQL mirrors
         * Room's generated schema (NOT NULL columns, composite primary key,
         * unique tag index and cascading foreign keys) so schema validation
         * passes without a destructive fallback.
         *
         * Room models a many-to-many relation with a junction table whose
         * composite primary key is paired with an index on the child column;
         * foreign keys use ON DELETE CASCADE. See
         * https://developer.android.com/training/data-storage/room/relationships/many-to-many#define
         * and https://developer.android.com/reference/androidx/room/ForeignKey
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tags` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `normalizedName` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_normalizedName` " +
                        "ON `tags` (`normalizedName`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `question_tags` (
                        `questionId` TEXT NOT NULL,
                        `tagId` TEXT NOT NULL,
                        PRIMARY KEY(`questionId`, `tagId`),
                        FOREIGN KEY(`questionId`) REFERENCES `questions`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_question_tags_tagId` " +
                        "ON `question_tags` (`tagId`)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "papers.db"
                )
                    .addMigrations(
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6
                    )
                    .build()
                    .also { instance = it }
            }
        }
    }
}
