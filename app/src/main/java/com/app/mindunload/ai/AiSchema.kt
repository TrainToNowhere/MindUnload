package com.app.mindunload.ai

import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonPropertyDescription

/**
 * Hand-written JSON schema for [ParsedCommand]. The SDK variant
 * `outputConfig(Class)` generates the schema via reflection and calls
 * `Method.getAnnotatedReturnType()` — that API does not exist on Android/ART
 * (NoSuchMethodError). Must stay in sync with the classes below.
 */
val PARSED_COMMAND_SCHEMA_JSON = """
{
  "type": "object",
  "description": "The structured result of parsing a user command into planner entries, link suggestions, and change commands against existing entries.",
  "additionalProperties": false,
  "required": ["items", "links", "commands"],
  "properties": {
    "items": {
      "type": "array",
      "description": "All new entries extracted from the user input. One input may produce several entries.",
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": ["type", "title", "notes", "category", "priority", "dueAt", "listName", "quantity", "tags", "research", "recurrence"],
        "properties": {
          "type": {
            "type": "string",
            "enum": ["task", "idea", "goal", "appointment", "shopping_item", "note"],
            "description": "Entry type. Use 'note' for durable personal knowledge/facts to remember (e.g. tire pressure, wifi password) rather than an actionable item."
          },
          "title": {
            "type": "string",
            "description": "Short, clean title in the language of the user input."
          },
          "notes": {
            "type": ["string", "null"],
            "description": "Optional additional details from the input that don't fit the title."
          },
          "category": {
            "type": ["string", "null"],
            "description": "A short free-form category, e.g. 'Haushalt', 'Arbeit', 'Gesundheit'. In the language of the user input."
          },
          "priority": {
            "type": "string",
            "enum": ["high", "medium", "low", "none"],
            "description": "Priority. Use 'none' unless the input implies urgency or importance."
          },
          "dueAt": {
            "type": ["string", "null"],
            "description": "Due date/time as ISO-8601 local date-time, e.g. 2026-07-07T10:00:00. Resolve relative expressions ('morgen', 'Dienstag') against the current date given in the prompt. Null if none."
          },
          "listName": {
            "type": ["string", "null"],
            "description": "For shopping_item only: the shopping list name, e.g. 'Einkauf'. Reuse an existing list name when it fits."
          },
          "quantity": {
            "type": ["string", "null"],
            "description": "For shopping_item only: quantity as text, e.g. '2', '500 g'. Null if not given."
          },
          "tags": {
            "type": "array",
            "items": { "type": "string" },
            "description": "2-5 short topic keywords for linking, lowercase, in the language of the user input."
          },
          "research": {
            "type": "boolean",
            "description": "True only when web research would clearly add value for this entry (e.g. an idea or goal needing options, prices, background). False for routine todos, appointments, shopping items and simple notes."
          },
          "recurrence": {
            "type": ["string", "null"],
            "description": "Recurrence rule when the input asks for repetition, null otherwise. One of: 'daily', 'weekly' (same weekday as dueAt), 'monthly' (same day of month as dueAt), 'monthly_weekday' (same nth weekday of the month as dueAt, e.g. every 2nd Friday), 'yearly' — each optionally with ':n' for every n-th interval, e.g. 'weekly:2' for every two weeks. Requires dueAt; dueAt must be the FIRST occurrence."
          }
        }
      }
    },
    "links": {
      "type": "array",
      "description": "Suggested links between entries (new ones and existing backlog entries) that belong to the same or related topics.",
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": ["from", "to", "relation", "reason"],
        "properties": {
          "from": {
            "type": "string",
            "description": "Reference to the source entry: 'new:<index>' for an entry from items (0-based), or 'existing:<id>' for an existing backlog entry."
          },
          "to": {
            "type": "string",
            "description": "Reference to the target entry, same format as 'from'."
          },
          "relation": {
            "type": "string",
            "enum": ["related", "same_topic", "depends_on", "part_of"],
            "description": "Relation type."
          },
          "reason": {
            "type": ["string", "null"],
            "description": "One short sentence explaining why these entries are connected, in the language of the user input."
          }
        }
      }
    },
    "commands": {
      "type": "array",
      "description": "Change commands the user issued against EXISTING backlog entries (rename, edit content, complete, delete, change priority/due date/category, link/unlink two existing entries). Only include a command when the target entry can be resolved unambiguously via the search tools; otherwise omit it.",
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": ["targetRef", "action", "newTitle", "newNotes", "newCategory", "priority", "dueAt", "recurrence", "linkTargetRef", "relation", "reason"],
        "properties": {
          "targetRef": {
            "type": "string",
            "description": "Reference to the existing entry the command applies to: 'existing:<id>'."
          },
          "action": {
            "type": "string",
            "enum": ["rename", "set_notes", "append_notes", "set_category", "complete", "uncomplete", "delete", "set_priority", "set_due", "set_recurrence", "link", "unlink"],
            "description": "The change to apply."
          },
          "newTitle": {
            "type": ["string", "null"],
            "description": "For action 'rename': the new title. Null otherwise."
          },
          "newNotes": {
            "type": ["string", "null"],
            "description": "For action 'set_notes': the complete new body text, replacing the old one — carry over any still-valid parts of the existing text yourself. For 'append_notes': only the text to add as a new paragraph. Null otherwise."
          },
          "newCategory": {
            "type": ["string", "null"],
            "description": "For action 'set_category': the new category, in the user's language. Reuse an existing category when it fits. Null otherwise."
          },
          "priority": {
            "type": ["string", "null"],
            "description": "For action 'set_priority': the new priority — one of 'high', 'medium', 'low', 'none'. Null otherwise."
          },
          "dueAt": {
            "type": ["string", "null"],
            "description": "For action 'set_due': the new due date/time as ISO-8601 local date-time. Also set it alongside 'set_recurrence' when the series has a new start date. Null otherwise."
          },
          "recurrence": {
            "type": ["string", "null"],
            "description": "For action 'set_recurrence': the recurrence rule — 'daily', 'weekly', 'monthly', 'monthly_weekday' (same nth weekday of month), 'yearly', each optionally ':n' (e.g. 'weekly:4' = every four weeks). Use the literal string 'none' to remove a recurrence. Null for other actions."
          },
          "linkTargetRef": {
            "type": ["string", "null"],
            "description": "For actions 'link'/'unlink': reference to the other existing entry, same format as targetRef ('existing:<id>') or 'new:<index>' if it refers to a newly created entry from this same input. Null otherwise."
          },
          "relation": {
            "type": ["string", "null"],
            "description": "For action 'link': the relation type — one of 'related', 'same_topic', 'depends_on', 'part_of'. Null otherwise."
          },
          "reason": {
            "type": ["string", "null"],
            "description": "One short sentence in the user's language describing what was changed, suitable to show the user as confirmation, e.g. 'Priorität von \"Steuerunterlagen\" auf hoch gesetzt.'"
          }
        }
      }
    }
  }
}
""".trimIndent()

@JsonClassDescription("The structured result of parsing a user command into planner entries, link suggestions, and change commands.")
class ParsedCommand {
    @JsonPropertyDescription("All new entries extracted from the user input. One input may produce several entries.")
    var items: List<ParsedItem> = emptyList()

    @JsonPropertyDescription("Suggested links between entries (new ones and existing backlog entries) that belong to the same or related topics.")
    var links: List<LinkSuggestion> = emptyList()

    @JsonPropertyDescription("Change commands against existing backlog entries.")
    var commands: List<ParsedAction> = emptyList()
}

class ParsedAction {
    @JsonPropertyDescription("Reference to the existing entry the command applies to: 'existing:<id>'.")
    var targetRef: String = ""

    @JsonPropertyDescription("The change to apply. One of: rename, set_notes, append_notes, set_category, complete, uncomplete, delete, set_priority, set_due, set_recurrence, link, unlink")
    var action: String = ""

    @JsonPropertyDescription("For action 'rename': the new title.")
    var newTitle: String? = null

    @JsonPropertyDescription("For action 'set_notes': the complete new body text. For 'append_notes': only the text to add.")
    var newNotes: String? = null

    @JsonPropertyDescription("For action 'set_category': the new category, in the user's language.")
    var newCategory: String? = null

    @JsonPropertyDescription("For action 'set_priority': the new priority. One of: high, medium, low, none")
    var priority: String? = null

    @JsonPropertyDescription("For action 'set_due': the new due date/time as ISO-8601 local date-time.")
    var dueAt: String? = null

    @JsonPropertyDescription("For action 'set_recurrence': the recurrence rule, or 'none' to remove it. Null otherwise.")
    var recurrence: String? = null

    @JsonPropertyDescription("For actions 'link'/'unlink': reference to the other entry, 'existing:<id>' or 'new:<index>'.")
    var linkTargetRef: String? = null

    @JsonPropertyDescription("For action 'link': the relation type. One of: related, same_topic, depends_on, part_of")
    var relation: String? = null

    @JsonPropertyDescription("Short user-facing confirmation sentence describing the change, in the user's language.")
    var reason: String? = null
}

class ParsedItem {
    @JsonPropertyDescription("Entry type. Exactly one of: task, idea, goal, appointment, shopping_item")
    var type: String = "task"

    @JsonPropertyDescription("Short, clean title in the language of the user input.")
    var title: String = ""

    @JsonPropertyDescription("Optional additional details from the input that don't fit the title.")
    var notes: String? = null

    @JsonPropertyDescription("A short free-form category, e.g. 'Haushalt', 'Arbeit', 'Gesundheit'. In the language of the user input.")
    var category: String? = null

    @JsonPropertyDescription("Priority. Exactly one of: high, medium, low, none. Use 'none' unless the input implies urgency or importance.")
    var priority: String = "none"

    @JsonPropertyDescription("Due date/time as ISO-8601 local date-time, e.g. 2026-07-07T10:00:00. Resolve relative expressions ('morgen', 'Dienstag') against the current date given in the prompt. Null if none.")
    var dueAt: String? = null

    @JsonPropertyDescription("For shopping_item only: the shopping list name, e.g. 'Einkauf'. Reuse an existing list name when it fits.")
    var listName: String? = null

    @JsonPropertyDescription("For shopping_item only: quantity as text, e.g. '2', '500 g'. Null if not given.")
    var quantity: String? = null

    @JsonPropertyDescription("2-5 short topic keywords for linking, lowercase, in the language of the user input.")
    var tags: List<String> = emptyList()

    @JsonPropertyDescription("True only when web research would clearly add value for this entry.")
    var research: Boolean = false

    @JsonPropertyDescription("Recurrence rule ('daily', 'weekly', 'monthly', 'monthly_weekday', 'yearly', optionally ':n') or null.")
    var recurrence: String? = null
}

class LinkSuggestion {
    @JsonPropertyDescription("Reference to the source entry: 'new:<index>' for an entry from items (0-based), or 'existing:<id>' for an existing backlog entry.")
    var from: String = ""

    @JsonPropertyDescription("Reference to the target entry, same format as 'from'.")
    var to: String = ""

    @JsonPropertyDescription("Relation type. Exactly one of: related, same_topic, depends_on, part_of")
    var relation: String = "related"

    @JsonPropertyDescription("One short sentence explaining why these entries are connected, in the language of the user input.")
    var reason: String? = null
}

/**
 * Hand-written schema for the weekly cleanup suggestions — same Android
 * pitfall as above, must stay in sync with the classes below.
 */
val CLEANUP_SCHEMA_JSON = """
{
  "type": "object",
  "description": "Weekly cleanup suggestions for the user's backlog.",
  "additionalProperties": false,
  "required": ["suggestions"],
  "properties": {
    "suggestions": {
      "type": "array",
      "description": "Cleanup suggestions. Empty array when the backlog is tidy.",
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": ["action", "itemIds", "description"],
        "properties": {
          "action": {
            "type": "string",
            "enum": ["delete", "complete", "none"],
            "description": "What applying the suggestion does to all itemIds: 'delete' removes them, 'complete' marks them done, 'none' is a pure hint without an action."
          },
          "itemIds": {
            "type": "array",
            "items": { "type": "integer" },
            "description": "The ids of the affected entries. For duplicate merges: only the redundant entries to delete, never the one to keep."
          },
          "description": {
            "type": "string",
            "description": "One short sentence in the user's language explaining the suggestion, naming the entries by title, e.g. 'Doppelt: \"Milch\" ist zweimal auf der Einkaufsliste — einen Eintrag entfernen.'"
          }
        }
      }
    }
  }
}
""".trimIndent()

class CleanupResult {
    @JsonPropertyDescription("Cleanup suggestions. Empty array when the backlog is tidy.")
    var suggestions: List<CleanupSuggestion> = emptyList()
}

class CleanupSuggestion {
    @JsonPropertyDescription("What applying the suggestion does: 'delete', 'complete' or 'none' (pure hint).")
    var action: String = ""

    @JsonPropertyDescription("Ids of the affected entries.")
    var itemIds: List<Long> = emptyList()

    @JsonPropertyDescription("One short sentence in the user's language explaining the suggestion.")
    var description: String = ""
}
