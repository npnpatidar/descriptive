1. Project Overview

Develop an offline Android application that lets users import question papers,
organise and edit them, search their content, generate randomized practice
tests, reveal answers, back up their library, and export papers in multiple
formats. All core functionality must work without a network connection.

2. Functional Requirements

FR-1: Import JSON Files

- Import JSON files from device storage through Android's document picker.
- Validate the JSON structure and required fields before writing anything.
- Display meaningful errors that identify the exact invalid field.
- Store imported papers locally for offline access.
- Preserve imported data between application launches.
- Perform imports transactionally so a failed import cannot leave partial data.
- Limit imports to files under 50 MB.

FR-2: JSON Data Structure

Each paper contains a name and a top-level `categories` array. A category has a
name, an optional `questions` array, and an optional nested `categories` array
using the same shape. Categories may be nested to any practical depth.

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

- `paperName`, top-level `categories`, and `categoryName` are required.
- Each category must contain questions, subcategories, or both.
- Questions require `questionText` and `answerText`; answers may be blank.
- Unknown extra fields are ignored.
- Optional `questionImages` and `answerImages` arrays contain objects with
  `mimeType`, optional `width`/`height`, and base64 `data`.
- Supported imported image types are PNG, JPEG/JPG, GIF, WebP, and BMP.
- Imported images are limited to 1.5 MB each after base64 decoding.
- Category objects with no nested `categories` array remain valid.

FR-3: Paper Library

- Display all papers stored on the device without loading their full contents.
- Allow users to create a paper without importing JSON.
- Allow users to open, rename, reorder, delete, import, back up, and restore.
- Confirm destructive deletion before removing a paper.
- Display category and question counts for each paper.

FR-4: Paper and Category Selection

- Display a selected paper's category tree and available question counts.
- Allow users to select multiple categories, while preventing overlapping
  ancestor/descendant selections.
- Show a category's count across its entire subtree, including all nested
  subcategories.
- Keep the selected categories and configured counts when navigating between the
  configuration, questions, practice, and export screens.

FR-5: Configure Practice Tests

- Allow users to specify the number of questions to practise from each selected
  category.
- Draw requested questions from the whole selected subtree.
- Validate that requested quantities are non-negative integers and do not exceed
  the available total.
- Display the total number of questions before starting.
- Provide Start test and Edit questions actions.

FR-6: Generate Randomized Tests

- Randomly select questions from each selected category according to the
  configured quantity.
- Never include the same question more than once in a test.
- Preserve each question's category path, question text, answer, and images.
- Keep generated tests consistent if questions, categories, or papers are later
  edited, moved, or deleted.

FR-7: Reveal Answers

- Hide answers initially.
- Provide a Show Answers button to reveal every answer, including answer images.
- Provide a Hide Answers action.
- Display each answer directly below its corresponding question.
- Preserve question order when answers are shown or hidden.

FR-8: Export Functionality

Support four export formats:

1. HTML (.html): Interactive, browser-viewable document.
2. DOCX (.docx): Editable Microsoft Word-compatible document.
3. PDF (.pdf): Print-ready document.
4. JSON (.json): Structured data compatible with the import schema.

Export scope:

- Current Test: only the questions in the generated practice test.
- Selected Categories: every question in the selected category subtrees.
- Entire Paper: every category, question, answer, and image in the paper.

Export options:

- Select the scope, format, and filename.
- Choose whether to include answers where applicable.
- Save through Android's document picker or share the rendered file.
- Provide Study, Test, and Answer-key presets.
- Remember the last scope, format, answer settings, and preset per paper.
- Preserve question numbering and question-answer associations.
- Show full slash-separated category paths (for example
  `Science/Physics/Mechanics`) in HTML, DOCX, and PDF.
- Render a heading only for categories that directly contain questions; do not
  create headings for empty ancestor levels.
- Keep question text at the left margin in every rendered format.
- For DOCX and PDF, allow answers below each question or in a numbered answer
  section after all questions.
- Start the end-of-document answer section on a new page.
- Display appropriate success and error messages.

FR-9: HTML Export Requirements

- Display questions with clear formatting and numbering.
- Embed question and answer images so the file is self-contained.
- Hide answers initially when they are included.
- Provide functional Show Answers and Hide Answers controls.
- Work offline with inline CSS and JavaScript only, without external assets,
  network requests, or CDNs.
- Escape all user-authored text and image MIME values.

FR-10: DOCX and PDF Export Requirements

- Format questions and answers for readability.
- Preserve category headings and question numbering.
- Include answers only when requested.
- Embed images in the document.
- Support long questions and answers across pages without losing content.
- Avoid upscaling small images beyond their natural size.
- Omit answer images and answer text from question-only exports.
- Keep DOCX files valid OOXML and PDFs paginated A4 documents.

FR-11: JSON Export and Reimport

- Export valid JSON using the application's defined schema.
- Keep the nested category tree and leaf category names so the file can be
  imported without changing the paper's structure.
- Preserve paper names, categories, questions, answers, and images.
- Export selected categories as a valid paper structure.
- Export the current test as a valid JSON paper containing only the selected
  questions in their original order.
- Strip answers and answer images when answers are excluded.
- Ensure exported JSON files can be reimported without data loss.

FR-12: Authoring and Content Organisation

- Allow users to create, rename, and delete papers.
- Allow users to add, rename, move, reorder, merge, and delete categories at any
  depth.
- Allow moving a category subtree to another parent or to another paper.
- Allow users to add, edit, move, reorder, bulk-delete, and reorder questions.
- Allow merging two categories, combining their questions and children.
- Provide a command to merge all same-named sibling categories recursively.
- Deleting a category deletes its subcategories and their questions after
  confirmation.
- Keep local data and any generated test consistent after every edit.
- Provide accessible Move up / Move down alternatives in item menus in addition
  to drag reordering.

FR-13: Global Search

- Search across every paper, category name, full category path, question text,
  and answer text.
- Require at least two characters and debounce input.
- Provide All, Papers, Categories, and Questions filters.
- Open a paper result on its configuration screen.
- Open a category or question result on the Questions screen, expand its
  ancestors, and scroll to the match.
- Work in memory and skip image bytes for performance.
- Avoid showing stale results when the query changes quickly.

FR-14: Backup and Restore

- Back up every paper, category, question, answer, and image into one JSON file.
- Use a versioned wrapper containing `format`, `version`, `exportedAt`, and
  `papers`.
- Restore backups transactionally.
- Reject newer backup versions with a clear message.
- Accept a plain paper JSON file through the restore/import flow.
- When imported paper names already exist, offer Merge, Replace, or Keep both.
- Merge adds missing content, updates answers when present, and does not
  duplicate matching questions or categories.
- Replace removes the existing matching paper before inserting the imported one.
- Keep both creates a new copy.

FR-15: Images and Formula Formatting

- Allow multiple images on both questions and answers.
- Correct gallery image orientation, downscale stored images to at most 1400 px,
  and recompress them to keep database rows comfortably small.
- Display image previews without decoding full-size bitmaps for thumbnails.
- Embed images in rendered exports and include them in JSON backups.
- Format common formula and chemistry notation for display and export without
  changing the stored source text.
- Support at least subscripts, superscripts, common fractions, square roots,
  Greek letters, arrows, equilibrium arrows, and comparison symbols.

3. Non-Functional Requirements

- Offline support: Core features must work without internet access.
- Privacy: Request no internet permission and use no analytics, accounts, or
  cloud storage.
- Data persistence: Papers remain available after restarting the app.
- Data safety: Use Room migrations rather than destructive fallback.
- Performance: Keep the interface responsive with large question banks and
  large image sets; perform parsing, backup generation, and rendering off the
  main thread.
- Reliability: Invalid files and export failures must not crash the application.
- Usability: Provide intuitive navigation, accessible controls, clear validation
  messages, and explicit destructive-action confirmations.
- Portability: Share only through a non-exported FileProvider with temporary URI
  permissions.

4. Main Screens

1. Home: List papers; search; create, import, reorder, delete, back up, and
   restore.
2. Search: Search every paper with result-kind filters.
3. Test Configuration: Select categories, configure question counts, validate
   totals, start practice, export, or edit questions.
4. Questions: Collapsible nested category tree, question editing, image editing,
   selection bulk actions, category moves/merges, and paper options.
5. Practice: Display a randomized test grouped by category with Show/Hide
   Answers.
6. Export: Select scope, format, preset, answer placement, filename, save, or
   share.

5. Data and Storage

- Room stores papers, categories, questions, and question images locally.
- Categories use a self-referential `parentId` and sibling `position`.
- Questions and images use persisted sibling positions for ordering.
- Database version 4 migrates from versions 2 and 3 without deleting user data.
- Exports and shares are written to app cache or a user-selected document URI.
- In-app backups are independent of Android's system backup.

6. Acceptance Criteria

The application is complete when users can:

- Import, retain, and reimport valid JSON papers.
- Create papers and manage categories, nested subcategories, questions, images,
  and ordering.
- Search all content and jump to a category or question result.
- Select categories and configure valid question quantities.
- Generate randomized tests without duplicate selections.
- Reveal and hide all answers on demand.
- Export tests, selected categories, and entire papers in HTML, DOCX, PDF, and
  JSON, with images and correct category paths.
- Open HTML exports offline and reveal answers interactively.
- Place DOCX/PDF answers below questions or in a numbered section on a new page.
- Back up and restore the whole library with predictable conflict handling.
- Handle invalid input and export failures gracefully without data loss.
