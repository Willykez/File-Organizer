package com.willykez.files.data.model

/** MOVE relocates (with undo support, same as built-in move commands); COPY duplicates without
 *  removing the source; DELETE removes matched files permanently (no undo). */
enum class CustomActionType { MOVE, COPY, DELETE }

/**
 * A file operation parsed from free-text chat input rather than picked from the built-in command
 * catalog — e.g. "move all .mkv files from Downloads to the SD card Movies folder". Always shown
 * to the user as a concrete, resolved plan (source folder, matched files, destination) for
 * confirmation before anything on disk changes; see MainViewModel.pendingCustomAction.
 */
data class CustomAction(
    val summary: String,
    val actionType: CustomActionType,
    /** Absolute path; null means "search within the current storage scope". */
    val sourceFolderPath: String?,
    val sourceFolderLabel: String?,
    /** Extensions without the dot, lowercase, e.g. {"mkv"}. Empty means "any file". */
    val extensions: Set<String>,
    val nameContains: String?,
    /** Absolute destination path. Not used for DELETE. */
    val destinationPath: String?,
    val destinationLabel: String?
)
