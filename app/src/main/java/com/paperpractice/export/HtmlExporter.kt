package com.paperpractice.export

import com.paperpractice.domain.PaperContent

/**
 * Self-contained interactive HTML: inline CSS and JavaScript, no network
 * requests, no web fonts, and no CDN. It works from a download folder offline.
 */
object HtmlExporter {

    fun build(content: PaperContent, includeAnswers: Boolean): String {
        val answerCount = content.questionCount
        val totalLabel = if (answerCount == 1) "1 question" else "$answerCount questions"
        val categoryLabel = if (content.categories.size == 1) {
            "1 category"
        } else {
            "${content.categories.size} categories"
        }

        return buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html lang=\"en\">")
            appendLine("<head>")
            appendLine("<meta charset=\"utf-8\">")
            appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            appendLine("<title>${escape(content.paperName)}</title>")
            appendLine("<style>")
            appendLine(CSS.trimIndent())
            appendLine("</style>")
            appendLine("</head>")
            appendLine("<body>")
            appendLine("<header class=\"paper-header\">")
            appendLine("<p class=\"eyebrow\">Practice paper</p>")
            appendLine("<h1>${escape(content.paperName)}</h1>")
            appendLine("<p class=\"meta\">$categoryLabel &middot; $totalLabel</p>")
            if (includeAnswers) {
                appendLine(
                    "<div class=\"toolbar\"><button type=\"button\" id=\"toggleAnswers\" " +
                        "aria-controls=\"question-list\">Show Answers</button></div>"
                )
            }
            appendLine("</header>")
            appendLine("<main id=\"question-list\">")

            var number = 1
            content.categories.forEach { category ->
                appendLine("<section class=\"category\">")
                appendLine("<h2>${escape(category.categoryName)}</h2>")
                category.questions.forEach { question ->
                    appendLine("<article class=\"question\">")
                    appendLine("<div class=\"question-heading\">")
                    appendLine("<span class=\"number\">$number</span>")
                    appendLine("<div class=\"question-text\">${escape(question.questionText)}</div>")
                    appendLine("</div>")
                    if (includeAnswers) {
                        appendLine("<div class=\"answer\" hidden>")
                        appendLine("<span class=\"answer-label\">Answer</span>")
                        val answer = if (question.answerText.isEmpty()) {
                            "<span class=\"empty-answer\">(No answer provided)</span>"
                        } else {
                            escape(question.answerText)
                        }
                        appendLine("<div class=\"answer-text\">$answer</div>")
                        appendLine("</div>")
                    }
                    appendLine("</article>")
                    number++
                }
                appendLine("</section>")
            }

            appendLine("</main>")
            appendLine("<footer>Generated offline by Paper Practice.</footer>")
            if (includeAnswers) {
                appendLine("<script>")
                appendLine(SCRIPT.trimIndent())
                appendLine("</script>")
            }
            appendLine("</body>")
            appendLine("</html>")
        }
    }

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { character ->
            when (character) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(character)
            }
        }
    }

    private val CSS = """
        :root {
          color-scheme: light dark;
          --bg: #f4f2eb;
          --paper: #fffdf8;
          --ink: #1e2433;
          --muted: #646b7c;
          --accent: #3d4d87;
          --line: #ddd8ca;
          --answer-bg: #eef1fa;
        }
        @media (prefers-color-scheme: dark) {
          :root {
            --bg: #14161d;
            --paper: #1d2029;
            --ink: #e9ebf1;
            --muted: #aeb4c3;
            --accent: #aebcf5;
            --line: #353a48;
            --answer-bg: #252a39;
          }
        }
        * { box-sizing: border-box; }
        body {
          margin: 0;
          padding: 0 1rem 3rem;
          background: var(--bg);
          color: var(--ink);
          font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Arial, sans-serif;
          line-height: 1.55;
        }
        .paper-header, main, footer { max-width: 52rem; margin-left: auto; margin-right: auto; }
        .paper-header { padding: 2.5rem 0 1rem; }
        .eyebrow {
          margin: 0;
          text-transform: uppercase;
          letter-spacing: .12em;
          font-size: .75rem;
          font-weight: 700;
          color: var(--muted);
        }
        h1 { margin: .35rem 0 .5rem; font-size: clamp(1.7rem, 4vw, 2.6rem); line-height: 1.15; }
        .meta { margin: 0; color: var(--muted); }
        .toolbar { margin-top: 1.25rem; }
        button {
          border: 0;
          border-radius: .65rem;
          padding: .7rem 1.1rem;
          background: var(--accent);
          color: #fff;
          font: inherit;
          font-weight: 700;
          cursor: pointer;
        }
        button:hover { filter: brightness(1.08); }
        button:focus-visible { outline: 3px solid var(--accent); outline-offset: 3px; }
        @media (prefers-color-scheme: dark) { button { color: #14161d; } }
        .category { margin: 2rem 0 0; }
        .category h2 {
          margin: 0 0 .85rem;
          padding-bottom: .45rem;
          border-bottom: 2px solid var(--line);
          font-size: 1.25rem;
        }
        .question {
          margin: 0 0 .9rem;
          padding: 1rem 1.1rem;
          background: var(--paper);
          border: 1px solid var(--line);
          border-radius: .8rem;
          break-inside: avoid-page;
        }
        .question-heading { display: flex; gap: .85rem; align-items: flex-start; }
        .number {
          flex: 0 0 auto;
          min-width: 2rem;
          height: 2rem;
          display: inline-flex;
          align-items: center;
          justify-content: center;
          border-radius: 50%;
          background: var(--accent);
          color: #fff;
          font-weight: 700;
          font-size: .9rem;
        }
        @media (prefers-color-scheme: dark) { .number { color: #14161d; } }
        .question-text, .answer-text { white-space: pre-wrap; overflow-wrap: anywhere; }
        .question-text { padding-top: .2rem; }
        .answer {
          margin: 1rem 0 0 2.85rem;
          padding: .8rem .9rem;
          border-left: 3px solid var(--accent);
          border-radius: .3rem;
          background: var(--answer-bg);
        }
        .answer-label {
          display: block;
          margin-bottom: .25rem;
          color: var(--muted);
          font-size: .75rem;
          font-weight: 800;
          letter-spacing: .08em;
          text-transform: uppercase;
        }
        .empty-answer { color: var(--muted); font-style: italic; }
        [hidden] { display: none !important; }
        footer { margin-top: 3rem; color: var(--muted); font-size: .8rem; }
        @media print {
          :root { --bg: #fff; --paper: #fff; --ink: #000; --answer-bg: #f4f4f4; }
          body { padding: 0; }
          .toolbar { display: none; }
          .question { break-inside: avoid; }
        }
    """

    private val SCRIPT = """
        (function () {
          var button = document.getElementById('toggleAnswers');
          var answers = Array.prototype.slice.call(document.querySelectorAll('.answer'));
          if (!button || !answers.length) return;
          var visible = false;
          button.addEventListener('click', function () {
            visible = !visible;
            answers.forEach(function (answer) { answer.hidden = !visible; });
            button.textContent = visible ? 'Hide Answers' : 'Show Answers';
            button.setAttribute('aria-pressed', visible ? 'true' : 'false');
          });
          button.setAttribute('aria-pressed', 'false');
        })();
    """
}
