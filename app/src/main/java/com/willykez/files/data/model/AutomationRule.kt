package com.willykez.files.data.model

import kotlinx.serialization.Serializable

/**
 * A user-defined background automation: run [commandNames] on a schedule, scoped to either a
 * specific folder the user picked or a coarser internal/SD-card scope.
 *
 * Unlike a manually-run command, there's no one present to review results or grant an explicit
 * override when a rule's scope happens to touch a protected (source-code/firmware) folder — so
 * automated runs always enforce protection in full, with no override, regardless of how the rule
 * was scoped. See AutomationRuleWorker.
 */
@Serializable
data class AutomationRule(
    val id: String,
    val name: String,
    val commandNames: List<String>,
    /** Null means "use [storageScope]" instead of a specific folder. */
    val folderPath: String? = null,
    val folderLabel: String? = null,
    /** One of StorageScope's names — only used when [folderPath] is null. */
    val storageScope: String = "ALL",
    val intervalHours: Int = 24,
    val enabled: Boolean = true
)
