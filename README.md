# Paper Practice

An offline Android app for importing, organising, practising, and exporting
question papers. Built with Kotlin, Jetpack Compose, Room, and
kotlinx.serialization. The app requests no internet permission and stores all
content on the device.

## Highlights

- Nested categories to any depth, with aggregate question counts.
- Randomised practice tests with no repeated questions.
- Full paper, category, and question editing, including moves and reordering.
- Search across every paper, category, question, and answer.
- Multiple question/answer images and an offline formula/chemistry formatter.
- HTML, DOCX, PDF, and re-importable JSON exports.
- One-file backup and restore, with safe choices when names already exist.

## Features

### Import and backup

- Import a paper JSON file through Android's document picker.
- Validation points at the exact bad field, for example
  `categories[1].categories[0].questions[3].answerText is required`.
- When a paper with the same name already exists, choose **Merge**, **Replace**,
  or **Keep both**.
- **Merge** adds new categories/questions, updates answers when the incoming
  answer is present, and keeps a single copy of matching questions.
- **Back up all papers** to one JSON file and restore it later. A plain paper
  export can also be opened through Restore/Import.
- Imported and edited content is stored locally in Room and survives restarts.
- Imports are transactional, so a failed import never leaves half a paper.

### Organise content

- Create papers from scratch and rename or delete them.
- Add, rename, move, merge, reorder, and delete categories.
- Category menus support subcategories, moving inside the paper, moving to
  another paper, merging into another category, and deleting the whole subtree.
- Add, edit, move, reorder, select, and bulk-delete questions.
- **Merge duplicate categories** merges same-named siblings recursively and
  combines their questions and children.
- Category counts include all questions in nested subcategories.
- Categories on the Questions screen start collapsed. Selecting a category in a
  test includes its entire subtree.
- Reorder handles include accessible Move up / Move down alternatives in the
  item menus.

### Search

- Search all papers, category names and paths, question text, and answers.
- Results are filtered to papers, categories, or questions with one tap.
- Opening a category or question result expands its category path and scrolls
  directly to the match.
- Search is debounced, skips image data, and works completely offline.

### Practice

- Select one or more categories and choose how many questions to take from each.
- Sampling is without replacement, so a question cannot appear twice.
- See live validation and a total question count before starting.
- Show or hide every answer at once, including answer images.

### Export

Export the current test, selected categories, or the entire paper as:

- **HTML** — self-contained interactive page with offline Show/Hide Answers
  buttons.
- **DOCX** — editable OOXML document with embedded images.
- **PDF** — paginated A4 document with embedded images and copy-friendly text.
- **JSON** — the same import schema, ready to re-import.

For DOCX and PDF, answers can appear below each question or in a numbered
answer section beginning on a new page. Presets provide quick Study, Test, and
Answer-key configurations. The last scope/format/answer settings are remembered
per paper. Render the file into the app cache and share it, or save it through
Android's document picker.

Rendered exports:

- label categories with their full slash-separated path
  (`Science/Physics/Mechanics`);
- keep question text at the left margin;
- emit headings only for categories that directly own questions, so empty
  ancestor levels do not create blank sections;
- embed images in HTML, DOCX, and PDF;
- omit answer images and answer text from question-only exports.

JSON exports keep the nested tree and image data, so a round trip preserves the
paper.

### Images and formulas

- Attach multiple images to both questions and answers by picking from the
  device gallery.
- Gallery images are orientation-corrected, downscaled to at most 1400 px, and
  recompressed to keep database rows small.
- Imported images are limited to 1.5 MB each and to PNG, JPEG/JPG, GIF, WebP, or
  BMP content.
- Formula and chemistry shortcuts are formatted for display and export without
  changing the stored text: `H_2O`, `C_6H_12O_6`, `x^2`, `x^{n+1}`,
  `\frac{a}{b}`, `\sqrt{x}`, `->`, `<=>`, `\alpha`, `\Delta`.
- Inline styles use `**bold**`, `*italic*`, `++underline++`,
  `~~strikethrough~~`, and `==highlight==`. Styles can be nested, for example
  `**bold and *italic***`.
- A blank line before and after a table keeps it separate from paragraphs:

  ```text
  | Material | Density |
  | :--- | ---: |
  | Copper | 8.96 |
  | Water | 1.00 |
  ```

  The second row is the alignment/separator row. `:---`, `:---:` and `---:`
  align a column left, centre and right. Use `\|` for a literal pipe inside a
  cell.
- Fractions are stacked in the app and in HTML, DOCX and PDF exports. Braces can
  contain nested math, for example
  `\frac{-b \pm \sqrt{b^2 - 4ac}}{2a}`.

## Screens

1. **Home** — papers, global search, create, import, backup/restore, reorder,
   and delete with confirmation.
2. **Search** — results across all papers with kind filters.
3. **Configure test** — category selection, per-category counts, validation,
   total, practice start, export, and a shortcut to edit questions.
4. **Questions** — collapsible category tree, question editing, selection mode,
   bulk moves/deletes, image editing, and category management.
5. **Practice** — randomised questions grouped by category with Show/Hide
   Answers.
6. **Export** — scope, format, presets, answer placement, file name, save, and
   share.

## JSON format

Categories can be nested recursively. A category may contain questions,
subcategories, or both. Category objects that do not need children can omit the
nested `categories` array.

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
An answer may be blank. Unknown extra fields are ignored. Paper and category
names are trimmed on import.

Each question may also contain `questionImages` and/or `answerImages`:

```json
{
  "questionText": "Label the diagram.",
  "answerText": "See the annotated image.",
  "questionImages": [
    {
      "mimeType": "image/png",
      "width": 1200,
      "height": 800,
      "data": "<base64-encoded image bytes>"
    }
  ]
}
```

A backup file wraps complete paper objects:

```json
{
  "format": "paper-practice-backup",
  "version": 1,
  "exportedAt": 1760000000000,
  "papers": []
}
```

A ready-to-import example is bundled with the app and included in the repository:
[`sample-paper.json`](app/src/main/assets/sample-paper.json). It has five
top-level sections, 20 categories nested up to four levels, and 30 questions,
including categories that hold both their own questions and subcategories. Only
questions that demonstrate a feature are kept. The sample includes embedded
diagram and table images, bordered text tables, bold/italic/underline/
strikethrough/highlighted text, stacked mathematical fractions, chemical
equations, multi-line answers, and data-interpretation questions. Use **Load
sample paper** on the Home screen to import it in one tap and open it.

## Build

The project builds with JDK 17, Android SDK 37, and Gradle 9.6.1. Release builds
enable R8 code shrinking and resource shrinking to keep the APK small.

```sh
cd descriptive
sh gradlew :app:assembleDebug --no-daemon --console=plain
```

The debug APK is written to:

```
app/build/outputs/apk/debug/app-debug.apk
```

Run the tests, lint, and a debug build:

```sh
sh gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug \
  --no-daemon --console=plain
```

### Release signing

Release builds are signed when all four signing variables are present:

```sh
export PAPER_PRACTICE_KEYSTORE=/path/to/paper-practice-release.jks
export PAPER_PRACTICE_KEYSTORE_PASSWORD='...'
export PAPER_PRACTICE_KEY_ALIAS=paperpractice
export PAPER_PRACTICE_KEY_PASSWORD='...'
sh gradlew :app:assembleRelease --no-daemon --console=plain
```

The signed APK is written to `app/build/outputs/apk/release/app-release.apk`.
Keep the keystore and passwords backed up securely; without them you cannot
publish updates for an installed release.

The 74 JVM tests cover strict JSON validation, recursive categories, sample
paper consistency, randomized selection, answer associations, rich-text
formatting, backups, image metadata and privacy, and every exporter. The Android
CI workflow runs the same checks on pushes and pull requests. Pushing a `v*` tag
runs the release workflow, which builds a signed APK and publishes it on GitHub.

## Source layout

```
app/src/main/java/com/paperpractice/
  data/       Room entities, DAO, migrations, repository, content models
  domain/     JSON parser, test generator, rich text, content models
  export/     HTML, DOCX, PDF, JSON, and backup writers
  ui/         Compose screens, ViewModel, navigation, theme, utilities
app/src/test/ Parser, generator, rich-text, backup, and exporter tests
app/schemas/  Exported Room schemas used by migrations
```

Room database version 4 includes real 2→3 and 3→4 migrations; destructive
migration fallback is not used.

## Notes

- Exports use Android's Storage Access Framework, so the app does not need broad
  storage permissions.
- Shared files are exposed through a non-exported `FileProvider` and live under
  the app's cache directory.
- HTML exports contain inline CSS and JavaScript only and work offline.
- DOCX files are generated directly as OOXML, and PDFs use the platform
  `PdfDocument`; no external document service or network request is involved.
- The app has no internet permission, analytics, accounts, or cloud storage.
