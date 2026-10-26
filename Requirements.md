1. Project Overview

Develop an Android application that allows users to import question papers, select categories, generate randomized practice tests, reveal answers, and export questions in multiple formats.

2. Functional Requirements

FR-1: Import JSON Files

- Import JSON files from device storage.
- Validate the JSON structure and required fields.
- Display meaningful errors for invalid files.
- Store imported papers locally for offline access.
- Preserve imported data between application launches.

FR-2: JSON Data Structure

Each paper contains a name and multiple categories. Categories may contain
questions with corresponding answers and may also contain nested subcategories
to any depth.

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

A category may also include an optional nested `categories` array using the same
shape. Existing flat papers with only `questions` remain valid.

FR-3: Paper and Category Selection

- Display all imported papers.
- Allow users to select a paper.
- Display its categories and available question counts.
- Allow users to select multiple categories.

FR-4: Configure Practice Tests

- Allow users to specify the number of questions to practice from each selected category.
- Include all questions in a category's nested subcategories when calculating
  its available count and drawing the requested questions.
- Validate that requested quantities are non-negative integers and do not exceed available questions.
- Display the total number of questions that will appear in the test.

FR-5: Generate Randomized Tests

- Provide a Test button to generate a practice test.
- Randomly select questions from each category according to the configured quantities, including questions from nested subcategories.
- Avoid duplicate questions within a test.
- Display all selected questions at the top of the test screen.
- Preserve each question's category and corresponding answer.

FR-6: Reveal Answers

- Hide answers initially.
- Provide a Show Answers button to reveal all answers.
- Display each answer directly below its corresponding question.
- Preserve question ordering when answers are revealed.

FR-7: Export Functionality

Support four export formats:

1. HTML (.html): Interactive, browser-viewable document.
2. DOCX (.docx): Editable Microsoft Word-compatible document.
3. PDF (.pdf): Print-ready document.
4. JSON (.json): Structured data compatible with the application's import functionality.

Export Scope

Users can export:

- Current Test: All randomly selected questions.
- Selected Categories: All questions belonging to chosen categories.
- Entire Paper: Every category, question, and answer in the selected paper.

Export Options

- Select the export scope and file format.
- Choose whether to include answers, wherever applicable.
- Specify the filename and save location.
- Preserve question numbering, category headings, and question-answer associations.
- Display appropriate success or error messages.

FR-8: HTML Export Requirements

- Display questions with clear formatting and numbering.
- When answers are included, hide them initially.
- Provide a functional Show Answers button to reveal all answers.
- Optionally provide a Hide Answers button.
- Ensure interactivity works offline without external dependencies.

FR-9: DOCX and PDF Export Requirements

- Format questions and answers for readability.
- Preserve category headings and question numbering.
- Include answers when requested.
- Support long questions and answers across pages without losing content.

FR-10: JSON Export and Reimport

- Export valid JSON using the application's defined schema.
- Preserve paper names, categories, questions, and answers.
- Export selected categories as a valid paper structure.
- Export the current test as a valid JSON paper containing the selected questions.
- Ensure exported JSON files can be reimported without data loss.

FR-11: Authoring and Editing

- Allow users to create a new paper without importing JSON.
- Allow users to add, rename, and delete categories and nested subcategories.
- Allow users to add, edit, and delete questions and answers.
- Deleting a category also deletes its subcategories and their questions, with
  confirmation before destructive actions.
- Keep the app's local data and any generated test consistent after edits.

3. Non-Functional Requirements

- Offline support: Core features must work without internet access.
- Data persistence: Imported papers remain available after restarting the app.
- Performance: The interface remains responsive with large question banks.
- Reliability: Invalid files and export failures must not crash the application.
- Usability: Provide intuitive navigation and clear validation messages.

4. Main Screens

1. Home Screen: List imported papers and provide import and create options.
2. Test Configuration Screen: Select categories and configure question counts.
3. Questions Screen: View, add, edit, and delete questions, categories, and
   nested subcategories.
4. Practice Screen: Display randomized questions and reveal answers.
5. Export Screen: Select export scope, format, answer inclusion, and destination.

5. Acceptance Criteria

The application is complete when users can:

- Import and retain valid JSON papers.
- Create papers and manage categories, nested subcategories, and questions.
- Select categories and configure question quantities.
- Generate randomized tests without duplicate selections.
- Reveal all answers on demand.
- Export tests, selected categories, and entire papers in HTML, DOCX, PDF, and JSON.
- Open HTML exports offline and reveal answers interactively.
- Reimport exported JSON files successfully.
- Handle invalid input and export errors gracefully.
                                      }
                            ]
              }
        ]
  }
