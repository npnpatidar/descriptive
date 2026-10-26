# Paper Practice

An offline Android app for importing question papers, building randomized
practice tests, revealing answers, and exporting papers in four formats.

Built with Kotlin, Jetpack Compose, Room, and no network dependencies.

## Features

- **Import** `.json` papers from device storage using the system file picker.
- **Validate** the schema and point at the exact bad field, for example
  `categories[1].categories[0].questions[3].answerText is required`.
- **Persist** every imported paper in a local Room database — no internet needed.
- **Create papers from scratch**, with full editing for papers, categories, and
  questions:
  - add, rename, and delete papers;
  - add, rename, and delete categories;
  - add nested subcategories to any depth;
  - add, edit, and delete questions, with optional answers.
- **Configure** a test by selecting one or more categories and entering how many
  questions to take from each, with live validation and a total count. A
  category's count includes every question in its nested subcategories.
- **Generate** a randomized test without repeating a question.
- **Reveal answers** all at once with Show Answers, and hide them again.
- **Export** the current test, the selected categories, or the whole paper as:
  - **HTML** — self-contained interactive document with offline Show/Hide
    Answers buttons.
  - **DOCX** — editable WordprocessingML document.
  - **PDF** — paginated A4 document with page breaks for long content.
  - **JSON** — valid import schema, ready to re-import.
- Choose whether to include answers and pick the file name and save location in
  Android's document picker.
- Rendered exports label categories with their full slash-separated path
  (`Science/Physics/Mechanics`) and keep questions at the left margin. Headings
  are emitted only where a category directly owns questions, so empty ancestor
  levels do not create blank sections. JSON exports keep the nested tree and
  round-trip through import.

## Screens

1. **Home** — imported papers, import/create buttons, delete with confirmation.
2. **Configure test** — category selection, question counts, total, export, and
   a shortcut to edit questions.
3. **Questions** — view, add, edit, and delete questions; add, rename, and delete
   categories and nested subcategories; rename the paper. Categories are
   collapsible and start collapsed.
4. **Practice** — randomized questions grouped by category, Show/Hide Answers.
5. **Export** — scope, format, answer inclusion, file name, save location.

## JSON format

Categories can be nested recursively. A category may contain questions,
subcategories, or both. Existing flat files with no `categories` array continue
to work.

```json
{
  "paperName": "Sample Paper",
  "categories": [
    {
      "categoryName": "Science",
      "questions": [
        {
          "questionText": "State Newton's second law of motion.",
          "answerText": "F = ma."
        }
      ],
      "categories": [
        {
          "categoryName": "Physics",
          "questions": [
            {
              "questionText": "What is inertia?",
              "answerText": "An object's resistance to a change in motion."
            }
          ]
        }
      ]
    }
  ]
}
```

`paperName`, top-level `categories`, and `categoryName` are required. Each
category must contain a `questions` array, a nested `categories` array, or both.
An answer may be an empty string (useful for question-only exports). Unknown
extra fields are ignored.

A ready-to-import example is included: [`sample-paper.json`](sample-paper.json).
It has five top-level sections, 23 categories nested up to four levels, and 53
questions, including categories that hold both their own questions and
subcategories.

## Build

The project builds with JDK 17, Android SDK 37, and Gradle 9.6.1. On this
aarch64 Alpine host, use the provided toolchain wrapper:

```sh
. /opt/mcq-build-env.sh
cd /sdcard/repo/descriptive
sh gradlew :app:assembleDebug $MCQ_AAPT2_OVERRIDE --no-daemon --console=plain
```

The debug APK is written to:

```
app/build/outputs/apk/debug/app-debug.apk
```

A copy of the latest build is also kept at
`/sdcard/Download/paper-practice-debug.apk` for easy installation.

Run the unit tests and lint with:

```sh
sh gradlew :app:testDebugUnitTest :app:lintDebug $MCQ_AAPT2_OVERRIDE --no-daemon
```

The 43 JVM tests cover JSON validation (including nested categories),
round-tripping, randomized selection, answer associations, and the
HTML/DOCX/PDF/JSON exporters.

## Source layout

```
app/src/main/java/com/paperpractice/
  data/       Room entities, DAO, repository, paper/category tree models
  domain/     JSON parser, test generator, import/export content
  export/     HTML, DOCX, PDF and JSON writers
  ui/         Compose screens, ViewModel, navigation, theme
app/src/test/ Parser, generator and exporter tests
```

## Notes

- Exports go through Android's Storage Access Framework, so the app does not
  need broad storage permissions.
- HTML exports contain inline CSS and JavaScript only; they work from a
  Downloads folder with no data connection.
- DOCX files are generated directly as OOXML, and PDFs use the platform
  `PdfDocument`, so no external document service is involved.
