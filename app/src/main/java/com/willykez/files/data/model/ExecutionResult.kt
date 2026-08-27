package com.willykez.files.data.model

enum class ActionStatus { SUCCESS, SKIPPED, FAILED }

data class FileAction(
    val status: ActionStatus,
    val fileName: String,
    val action: String,
    val detail: String,
    /** Populated only for reversible move actions, so the operation can be undone. */
    val undo: UndoableMove? = null
)

/** Records enough information to reverse a single move (not deletes — those are destructive). */
data class UndoableMove(val movedFrom: String, val movedTo: String)

class ExecutionResult(val command: CommandType) {
    private val _actions = mutableListOf<FileAction>()
    val actions: List<FileAction> get() = _actions

    var succeeded = 0; private set
    var skipped = 0; private set
    var failed = 0; private set

    fun add(status: ActionStatus, fileName: String, action: String, detail: String, undo: UndoableMove? = null) {
        _actions += FileAction(status, fileName, action, detail, undo)
        when (status) {
            ActionStatus.SUCCESS -> succeeded++
            ActionStatus.SKIPPED -> skipped++
            ActionStatus.FAILED -> failed++
        }
    }

    val total: Int get() = _actions.size

    val summary: String
        get() {
            if (_actions.isEmpty()) return "No matching files found."
            return buildString {
                append("$succeeded done")
                if (skipped > 0) append(", $skipped skipped")
                if (failed > 0) append(", $failed failed")
            }
        }
}
