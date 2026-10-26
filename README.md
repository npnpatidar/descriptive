# Paper Practice

An offline Android app for importing question papers, building randomized
practice tests, revealing answers, and exporting papers in four formats.

Built with Kotlin, Jetpack Compose, Room, and no network dependencies.

## Features

- **Import** `.json` papers from device storage using the system file picker.
- **Validate** the schema and point at the exact bad field, for example
  `categories[1].questions[3].answerText is required`.
- **Persist** every imported paper in a local Room database — no internet needed.
- **Configure** a test by selecting one or more categories and entering how many
  questions to take from each, with live validation and a total count.
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

## Screens

1. **Home** — imported papers, import button, delete with confirmation.
2. **Configure test** — category selection, question counts, total, export.
3. **Practice** — randomized questions grouped by category, Show/Hide Answers.
4. **Export** — scope, format, answer inclusion, file name, save location.

## JSON format

```json
{
  "paperName": "Sample Paper",
  "categories": [
    {
      "categoryName": "History",
      "questions": [
        {
          "questionText": "Explain the causes of World War I.",
          "answerText": "The major causes included..."
        }
      ]
    }
  ]
}
```

`paperName`, `categories`, `categoryName`, `questions`, `questionText`, and
`answerText` are required. An answer may be an empty string (useful for
question-only exports). Unknown extra fields are ignored.

A ready-to-import example is included: [`sample-paper.json`](sample-paper.json).

## Build

The project builds with JDK 17, Android SDK 37, and Gradle 9.6.1. On this
aarch64 Alpine host, use the provided toolchain wrapper:

```sh
. /sdcard/repo/apk-build/env.sh
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

The 33 JVM tests cover JSON validation and round-tripping, randomized selection,
answer associations, and the HTML/DOCX/JSON exporters.

## Source layout

```
app/src/main/java/com/paperpractice/
  data/       Room entities, DAO, repository, import models
  domain/     JSON parser, test generator, export content builder
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
