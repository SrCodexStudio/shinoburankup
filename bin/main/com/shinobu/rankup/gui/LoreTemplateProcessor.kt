package com.shinobu.rankup.gui

/**
 * Processes lore templates by replacing placeholders with actual values.
 *
 * Supports two types of placeholders:
 * - Single-line: {cost}, {status}, etc. - replaced inline within the line
 * - Multi-line: {description}, {requirements}, {rewards} - when alone on a line,
 *   expand to 0+ lines. If empty, the line is removed entirely.
 *   If part of a larger line, values are joined with commas.
 */
object LoreTemplateProcessor {

    /**
     * Process a lore template, replacing all placeholders.
     *
     * @param template The template lines from YAML config
     * @param placeholders Single-line placeholder replacements (e.g., "{cost}" to "$5,000")
     * @param multiLinePlaceholders Multi-line expansions (e.g., "{description}" to list of lines)
     * @return Processed lore lines ready for ItemBuilder
     */
    fun processTemplate(
        template: List<String>,
        placeholders: Map<String, String>,
        multiLinePlaceholders: Map<String, List<String>>
    ): List<String> {
        val result = mutableListOf<String>()

        for (line in template) {
            val trimmed = line.trim()

            // Check if the entire line is exactly a multi-line placeholder
            val multiLineKey = multiLinePlaceholders.keys.find { trimmed == it }
            if (multiLineKey != null) {
                val expansion = multiLinePlaceholders[multiLineKey] ?: emptyList()
                if (expansion.isNotEmpty()) {
                    result.addAll(expansion)
                }
                // If empty, line is skipped entirely (not added)
                continue
            }

            // Apply single-line replacements
            var processed = line
            for ((key, value) in placeholders) {
                processed = processed.replace(key, value)
            }

            // Handle multi-line placeholders appearing inline (comma-join)
            for ((key, values) in multiLinePlaceholders) {
                if (processed.contains(key)) {
                    processed = processed.replace(key, values.joinToString(", "))
                }
            }

            result.add(processed)
        }

        return result
    }
}
