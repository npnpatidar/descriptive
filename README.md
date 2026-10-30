# Paper Practice

**Paper Practice** is an offline Android app for turning question papers into a
searchable, editable study library. Import a paper, organise it into nested
categories, practise randomly selected questions, and export clean HTML, Word,
PDF, or JSON files.

The app needs no account and requests no internet permission. Papers, images,
and answers are stored locally on your device.

[Download the latest APK](https://github.com/npnpatidar/descriptive/releases/latest) ·
[Report an issue](https://github.com/npnpatidar/descriptive/issues) ·
[Sample paper](app/src/main/assets/sample-paper.json)

## Highlights

- Nested categories with aggregate question counts.
- Randomised practice tests with no repeated questions.
- Full paper, category, and question editing, including moves and reordering.
- Search across every paper, category, question, and answer.
- Multiple question and answer images, plus formatting for maths, chemistry,
  and tables.
- HTML, DOCX, PDF, and re-importable JSON exports.
- One-file backup and restore, with clear choices when a paper already exists.
- Light and dark themes that can follow your device setting or stay fixed.
- A ready-to-use sample paper included in the app.

## Contents

- [At a glance](#at-a-glance)
- [Installation](#installation)
- [Quick start](#quick-start)
- [Using Paper Practice](#using-paper-practice)
  - [Home](#home)
  - [Search](#search)
  - [Configure a test](#configure-a-test)
  - [Questions and categories](#questions-and-categories)
  - [Practice](#practice)
  - [Export and share](#export-and-share)
- [Rich text and formula cheat sheet](#rich-text-and-formula-cheat-sheet)
- [Importing papers and restoring backups](#importing-papers-and-restoring-backups)
- [JSON format](#json-format)
- [Privacy, storage, and permissions](#privacy-storage-and-permissions)
- [Troubleshooting](#troubleshooting)
- [Build from source](#build-from-source)
- [Project layout](#project-layout)
- [License](#license)

## At a glance

| | |
| --- | --- |
| **Platform** | Android 8.0 (API 26) or newer |
| **Works offline** | Yes |
| **Internet permission** | Not requested |
| **Account** | Not required |
| **Data storage** | On-device, app-private Room database |
| **Import** | Paper JSON or a Paper Practice backup |
| **Export** | HTML, DOCX, PDF, JSON |
| **Backup** | One JSON file containing all papers |
| **Source licence** | MIT |

## Installation

Paper Practice is distributed as an APK through GitHub Releases and requires
Android 8.0 (API 26) or newer.

1. Open the [latest release](https://github.com/npnpatidar/descriptive/releases/latest).
2. Download the `.apk` file from the release assets.
3. Open the downloaded file and follow Android's install prompt.
4. If Android blocks the install, allow installs from your browser or file
   manager for that action, then turn the permission off again.

Release APKs are signed, so a newer release can normally be installed over an
existing copy without losing your papers. If Android reports a signature
conflict, the installed copy came from a different build or signing key. Back up
all papers first, uninstall the old copy, install the release, then restore the
backup.

> **Keep a backup before uninstalling the app or clearing its data.** Local
> papers are removed when the app is uninstalled or its storage is cleared.

## Quick start

1. Open the app. On an empty Home screen, choose **Load sample paper**,
   **Import a JSON paper**, or **Create a paper**.
2. Tap a paper card to open **Configure test**.
3. Tick the categories you want and set how many questions to take from each.
4. Tap **Generate test**, then use **Show Answers** and **Hide Answers** while
   you practise.
5. To edit the paper, open a paper and use the edit button in the top bar of the
   Configure test screen.
6. To save or share a copy, use the export button and choose a scope, format,
   and answer option.

The sample paper imports in one tap and opens directly in the Questions screen,
so you can immediately see categories, images, tables, and formulas. If the
sample is already present, the app opens that copy instead of creating a
duplicate.

## Using Paper Practice

### Home

The Home screen lists every paper on the device.

- **Create a paper** with the plus button. Give it a name, then add categories
  and questions.
- **Import JSON** with the floating action button at the bottom.
- **Search everything** with the search button in the top bar.
- **Choose a theme** from the three-dot menu: **System default**, **Light**, or
  **Dark**. The choice is remembered on the device.
- **Back up all papers** or **Restore backup or paper** from the three-dot menu.
- **Load sample paper** from the list header, or from the empty-state buttons.
- Each paper card shows its category count, question count, and when it was
  added.
- Drag the handle on a paper card to reorder papers, or use **Move up** and
  **Move down** in its menu. **Delete paper** asks for confirmation.

Deleting a paper, category, or question cannot be undone, so keep a backup of
anything you want to preserve.

### Search

Search covers all papers on the device, including paper names, category names
and paths, question text, and answers. It does not search inside images.

- Enter at least two characters. Results update as you type.
- Use the **All**, **Questions**, **Categories**, and **Papers** filters to
  narrow the list.
- Open a category or question result to jump straight to it. The app expands the
  matching category path and scrolls the question into view.

### Configure a test

This screen decides what goes into a practice test.

- Tap a category that has subcategories to expand or collapse it, or use the
  arrow beside its name. Tick the checkbox to include the category.
- Set a question count for each selected category. Counts include questions in
  nested subcategories.
- Selecting a category replaces any ancestor or descendant that was already
  selected, so overlapping selections cannot double-count questions.
- Use **Select all** or **Clear** to change the selection quickly.
- The screen shows a running total and explains any validation problem before
  you start.
- **Generate test** creates a random test from the selected categories.
- The top-bar buttons open **Questions** for editing or **Export** for saving a
  copy.

A category with no questions available cannot contribute questions to a test.

### Questions and categories

The Questions screen is the main editor. Categories start collapsed; tap a
category header to expand or collapse it.

**Paper actions** (three-dot menu in the top bar):

- Rename the paper.
- Add a top-level category.
- Enter selection mode to select questions.
- Merge duplicate categories.

**Category actions** (three-dot menu on a category):

- Add a subcategory.
- Rename the category.
- Move the category to another paper.
- Move it under another category in the same paper.
- Merge it into another category.
- Move it up or down among its siblings.
- Delete the category and its entire subtree.

**Question cards** show the formatted question, its answer, and any attached
images. Tap a card to edit it. In the editor you can:

- Enter or change the question and answer.
- Attach multiple images to either the question or the answer.
- Save, delete, or cancel.

The answer is optional; a question can be saved with an empty answer. Question
text is required. Use the question's three-dot menu to edit it, move it to
another category, or reorder it within its category. Drag handles on questions,
categories, and papers also support reordering; the menus provide accessible
**Move up** and **Move down** alternatives.

Formatted rich text is intended for reading in the Questions and Practice
screens. Text selection is disabled in those lists so long content always
measures to its full height; if you need to copy the original markup, open the
question editor and select the text in the question or answer field.

**Selection mode** lets you manage several questions at once:

- Enter it from **Select questions** in the paper menu.
- Tap question cards to select or deselect them.
- Select or clear every question in the paper with the top-bar action.
- Use the bottom bar to move the selected questions or delete them after
  confirmation.

**Merge duplicate categories** combines same-named sibling categories
recursively, keeping their questions and child categories under one copy.

### Practice

The Practice screen shows the generated test, grouped by category.

- Questions are sampled without replacement, so the same question cannot appear
  twice in one test.
- Each selected category contributes the number of questions you requested,
  rather than being drowned out by a larger category.
- **Show Answers** reveals every answer and answer image at once;
  **Hide Answers** hides them again.
- This is a study view, not a graded quiz. The app does not keep scores or track
  correct and incorrect answers.
- Use the export button to export just this test.

### Export and share

Open Export from Configure test or Practice. Choose three things:

1. **What to export** — the current test, the categories selected on the
   configuration screen, or the entire paper.
2. **File format** — HTML, DOCX, PDF, or JSON.
3. **Answer options** — include answers or create a question-only document, and
   for DOCX/PDF choose whether answers appear below each question or in a
   numbered section at the end.

| Format | Best for | What you get |
| --- | --- | --- |
| **HTML** | Reading or sharing in a browser | A self-contained page with offline Show/Hide Answers buttons and inline images. |
| **DOCX** | Editing in Microsoft Word or a compatible editor | An editable OOXML document with embedded images and optional answer placement. |
| **PDF** | Printing or sending a fixed copy | A paginated A4 document with embedded images and copy-friendly text. |
| **JSON** | Moving a paper to another device or editing the data | The import schema, including nested categories, markup, and base64 image data. |

Presets make common jobs quicker:

- **Study** — include answers below each question.
- **Test** — question paper only.
- **Answer key** — include answers in a section at the end (DOCX and PDF).

With **Include answers** switched off, answer text and answer images are left
out of the export. Answers-at-end is available for DOCX and PDF; HTML uses its
interactive toggle instead. The last export settings are remembered separately
for each paper.

Export files are written through Android's document picker, so you can save them
to a folder or cloud provider you already use. You can also share an export
directly from the app. File names are sanitised and the correct extension is
added automatically.

Rendered exports:

- label categories with their full slash-separated path
  (`Science/Physics/Mechanics`);
- keep question text at the left margin;
- emit headings only for categories that directly own questions, so empty
  ancestor levels do not create blank sections;
- embed images in HTML, DOCX, and PDF;
- preserve tables, rich text, and stacked fractions;
- omit answer text and answer images from question-only exports.

JSON exports keep the nested tree and image data, so importing the file again
preserves the paper. A JSON export is a single paper; use **Back up all papers**
when you want one file containing your whole library.

## Rich text and formula cheat sheet

Markup is stored exactly as you type it. It is formatted for display in question
cards, Practice, and exports. The editor itself shows the plain markup, so you
can always see and edit the source.

### Inline styles and maths

| To show | Type |
| --- | --- |
| **Bold** | `**bold**` |
| *Italic* | `*italic*` |
| Underline | `++underline++` |
| Strikethrough | `~~strikethrough~~` |
| Highlight | `==highlight==` |
| Subscript | `H_2O`, `C_6H_12O_6`, `x_{n+1}` |
| Superscript | `x^2`, `x^{n+1}`, `10^-6`, `Fe^3+` |
| Stacked fraction | `\frac{a}{b}` or `\dfrac{a}{b}` |
| Square root | `\sqrt{x}` (shown as `√(x)`) |
| Greek letters and operators | `\alpha`, `\Delta`, `\pi`, `\times`, `\pm`, `\le`, `\ge`, `\infty` |
| Arrows | `->`, `<-`, `<->`, `<=>`, `=>`, `\rightarrow`, `\rightleftharpoons` |
| Chemistry | `H_2SO_4`, `Ca(OH)_2`, `CH_3COOH + NaHCO_3 -> CH_3COONa + H_2O + CO_2` |
| Nested styles | `**bold and *italic***` |

Styles can be combined, for example `**H_2O**` or `==**important**==`. A
backslash escapes a marker, so `\*not italic\*` displays literal asterisks.
Nested braces work inside fractions and roots, including:
`\frac{-b \pm \sqrt{b^2 - 4ac}}{2a}`.

Common LaTeX-style commands such as `\text{...}`, `\mathrm{...}`, `\ce{...}`,
and `\pu{...}` are understood inside formulas. Unsupported markup is displayed
as typed rather than causing the whole card to fail.

### Tables

Put a blank line before and after a table so it stays separate from paragraphs:

```text
| Material | Density |
| :--- | ---: |
| Copper | 8.96 |
| Water | 1.00 |
```

The second row is the alignment/separator row. Use `:---`, `:---:`, and `---:`
to align a column left, centre, or right. Use `\|` for a literal pipe inside a
cell. Tables are kept as tables in the app and in HTML, DOCX, and PDF exports.

### Formatting notes

- Fractions are drawn as stacked fractions in the app and in HTML, DOCX, and
  PDF exports.
- Subscripts and superscripts are displayed with Unicode characters.
- Markup is preserved in JSON exports and backups; re-importing does not flatten
  the formatting.
- If a formula does not render as expected, check the braces and command
  spelling first. The app shows unrecognised text as typed.

## Importing papers and restoring backups

### Import a paper

Use **Import JSON** on Home, or **Restore backup or paper** from the Home menu.
The Android document picker opens; choose a `.json` file.

A paper import accepts one paper object, while Restore also accepts a complete
Paper Practice backup containing multiple papers. Imports run in a transaction,
so a failed import never leaves a half-created paper behind.

Limits and formats:

- The selected file must be **50 MB or smaller**.
- Each image embedded in JSON must decode to **1.5 MB or less**.
- Embedded image types: PNG, JPEG/JPG, GIF, WebP, or BMP.
- An image's optional `width` and `height` must be between 0 and 100000.
- Extremely deep category trees are rejected (more than 64 levels).
- Extra unknown fields are ignored; invalid known fields produce an error naming
  the exact location, such as
  `categories[1].categories[0].questions[3].answerText is required`.

When adding images from the gallery in the editor, source images up to 25 MB are
accepted. The app stores an orientation-corrected copy that is downscaled to at
most 1400 px and recompressed to roughly 1.2 MB, keeping the database and
exports manageable.

### What happens when a paper name already exists

The app asks you to choose:

| Choice | Result |
| --- | --- |
| **Merge** | Keeps the existing paper, adds missing categories and questions, updates a matching answer when the incoming answer is non-blank, and replaces matching images when the incoming copy has them. |
| **Replace** | Removes the existing matching paper first, then imports the new copy. This is destructive. |
| **Keep both** | Imports the new paper as a separate copy. |
| **Cancel** | Leaves everything unchanged. |

Merge matches categories by their path and questions by their text, ignoring
case. It keeps a single copy of matching questions and does not duplicate them.

### Back up all papers

From Home, open the three-dot menu and choose **Back up all papers**. The app
creates one JSON file containing every paper, category, question, answer, and
image. Save it somewhere you can reach later, such as a cloud folder or USB
storage.

To restore, choose **Restore backup or paper** and select the backup. The same
Merge / Replace / Keep both choices apply if any paper names collide. A plain
paper JSON export can also be restored this way.

For moving to a new phone, save a backup first, install Paper Practice on the new
device, then restore the backup there. Do not rely on the old phone remaining
available after an uninstall or factory reset.

### Sample paper

The repository and app include
[`sample-paper.json`](app/src/main/assets/sample-paper.json). It contains:

- 5 top-level sections and 20 categories across up to four levels;
- 30 questions, including categories that have both their own questions and
  subcategories;
- 6 embedded images and 4 tables;
- examples of bold, italic, underline, strikethrough, and highlighted text;
- stacked mathematical fractions, chemical equations, multi-line answers, and
  data-interpretation questions.

Only questions that demonstrate a feature are kept. Use **Load sample paper** on
Home to import and open it.

## JSON format

A paper is a tree of categories. Categories can contain questions, nested
categories, or both. A category that has no children can omit its `categories`
array.

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

### Required fields

| Field | Required | Notes |
| --- | --- | --- |
| `paperName` | Yes | Non-blank; trimmed on import. |
| `categories` | Yes | Top-level array of category objects. |
| `categoryName` | Yes | Non-blank; trimmed on import. |
| `questions` | One of `questions` or `categories` | Array of question objects. |
| `categories` (inside a category) | One of `questions` or `categories` | Nested array of category objects. |
| `questionText` | Yes | Non-blank. |
| `answerText` | Yes | May be an empty string, but the field must be present. |
| `questionImages` | No | Array of image objects. |
| `answerImages` | No | Array of image objects. |

Each image object needs a `mimeType` and base64-encoded `data`. `width` and
`height` are optional hints; the app can infer dimensions from common image
headers.

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

Supported image MIME types are `image/png`, `image/jpeg`, `image/jpg`,
`image/gif`, `image/webp`, and `image/bmp`.

A backup file wraps complete paper objects:

```json
{
  "format": "paper-practice-backup",
  "version": 1,
  "exportedAt": 1760000000000,
  "papers": []
}
```

The `papers` array contains full paper objects; a backup with no papers is
rejected when restored. Backups created by a newer, unsupported backup version
are rejected with an explanation rather than being partially imported.

## Privacy, storage, and permissions

- All papers, categories, questions, answers, and images are stored locally in
  the app's private Room database.
- The app has **no internet permission**, no accounts, no analytics, and no
  advertising or third-party tracking SDKs.
- The app does not have broad storage permissions. Imports use Android's
  document picker, and exports are saved through the system file picker.
- Shared exports use a non-exported `FileProvider` and temporary files in the
  app's cache directory.
- HTML exports contain inline CSS and JavaScript only and work offline. DOCX
  files are generated directly as OOXML. PDFs use Android's platform
  `PdfDocument`. No external document service or network request is involved.
- Android's own system backup and device-transfer features may include app data
  depending on the device's settings. The app enables Android backup support, so
  if you do not want the operating system to back up app data, turn off device
  backup in Android settings.
- Exported papers, backups, and HTML/PDF/DOCX files contain your content and
  images. Treat them like any other document when sharing.

Uninstalling the app or clearing its storage removes the local library. Export a
backup first if you might need the content later.

## Troubleshooting

**The app says the JSON is invalid.**
Read the field path in the message. Common causes are a missing `answerText`, a
blank `questionText`, a category with neither `questions` nor `categories`, or an
image that is too large or not one of the supported types. `answerText` may be
empty, but it must still exist.

**An image in an imported paper is rejected.**
Check that it is PNG, JPEG/JPG, GIF, WebP, or BMP, that it is no larger than
1.5 MB after decoding, and that the base64 `data` is valid. For images added
through the editor, very large source files may also be rejected or recompressed.

**A paper with the same name already exists.**
Choose **Merge** to add new material, **Replace** to swap in the new copy, or
**Keep both** to keep separate copies. Read the confirmation text before choosing
Replace.

**The app will not install over the existing copy.**
The installed app was probably built with a different signing key. Back up your
papers, uninstall the old copy, install the release APK, and restore the backup.

**I reinstalled the app and my papers are gone.**
Paper Practice stores content locally. Restore a backup made with **Back up all
papers**. Reinstalling without a backup cannot recover data that was removed with
the app.

**Search finds nothing.**
Enter at least two characters, check the active filter, and try a word from the
question or answer. Search does not read text inside images.

**A PDF, DOCX, or HTML export will not open.**
Install a viewer that supports that format, or use **Share** and open the file in
another app. The exported file is written through Android's document picker.

**Long questions or formulas look clipped.**
Update to the latest release. Rich-text height fixes for the Questions and
Practice screens are included in v2.1.2. If it still happens, include the
question markup, Android version, and a screenshot in an issue.

**I cannot select text in a question card or on the Practice screen.**
That is intentional: text selection is disabled in those reading lists so long
rich text measures to its full height. Open the question editor and select the
question or answer text there.

**The app cannot read the selected file.**
Reopen the document picker and choose the file again. Import works best with a
local copy of the JSON file; if the file lives in a cloud provider, download it
to the device first.

## Build from source

### Requirements

- JDK 17
- Android SDK platform 37 and build-tools 37.0.0
- Gradle 9.6.1 (the wrapper handles this)
- An Android SDK location configured through `ANDROID_HOME`,
  `ANDROID_SDK_ROOT`, or `local.properties`

The project uses Kotlin, Jetpack Compose, Room, and kotlinx.serialization. Release
builds enable R8 code shrinking and resource shrinking.

### Debug build

From the repository root:

```sh
sh gradlew :app:assembleDebug --no-daemon --console=plain
```

On Windows, use `gradlew.bat` instead of `sh gradlew`.

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Tests, lint, and build

```sh
sh gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug \
  --no-daemon --console=plain
```

The suite contains 74 JVM tests covering strict JSON validation, recursive
categories, sample-paper consistency, randomised selection, answer
associations, rich-text formatting, backups, image metadata and privacy, and
every exporter.

### Release signing

Release builds are signed only when all four signing variables are present:

```sh
export PAPER_PRACTICE_KEYSTORE=/path/to/paper-practice-release.jks
export PAPER_PRACTICE_KEYSTORE_PASSWORD='...'
export PAPER_PRACTICE_KEY_ALIAS=paperpractice
export PAPER_PRACTICE_KEY_PASSWORD='...'
sh gradlew :app:assembleRelease --no-daemon --console=plain
```

The signed APK is written to
`app/build/outputs/apk/release/app-release.apk`. Keep the keystore and passwords
secure: without them you cannot publish updates for an installed release.

### Continuous integration

The Android CI workflow runs unit tests, lint, and a debug build on pushes to
`main` and on pull requests, then uploads the debug APK as a build artifact.
Pushing a `v*` tag runs the release workflow, which builds and signs the release
APK and publishes it on GitHub.

## Project layout

```text
app/src/main/java/com/paperpractice/
  data/       Room entities, DAO, migrations, repository, content models
  domain/     JSON parser, test generator, rich text, content models
  export/     HTML, DOCX, PDF, JSON, and backup writers
  render/     Shared rich-text rendering helpers
  ui/         Compose screens, ViewModel, navigation, theme, utilities
  MainActivity.kt
app/src/main/assets/  Bundled sample-paper.json
app/src/test/         Parser, generator, rich-text, backup, and exporter tests
app/schemas/          Exported Room schemas used by migrations
```

Room database version 4 includes real 2→3 and 3→4 migrations; destructive
migration fallback is not used.

## License

MIT. See [LICENSE](LICENSE).
