package com.willykez.files.domain

import com.willykez.files.data.model.FileMetadata

/**
 * Identifies folders that must be treated as a single unit — never reorganized file-by-file —
 * because their internal layout is load-bearing: an extracted source repo (the build breaks if
 * `build.gradle` and `src/` end up in different folders), an unzipped firmware/ROM dump (flashing
 * tools expect an exact `system/`, `vendor/`, `META-INF/` layout), etc.
 *
 * This exists because of a real data-loss report: running "Organize by Type" against a device
 * that had an extracted Android project and an unpacked stock ROM scattered every `.kt`/`.xml`/
 * `.so`/`.apk` file into `Organized/<category>` by extension, destroying both. Bulk commands now
 * skip anything under a detected (or user-marked) protected root by default.
 */
object ProtectionRules {

    /** A marker file's presence directly inside a folder means "this folder is a project root". */
    private val PROJECT_MARKERS = setOf(
        "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
        "package.json", "pom.xml", "Cargo.toml", "requirements.txt", "setup.py",
        "AndroidManifest.xml", "CMakeLists.txt", "Makefile", ".git", ".project", "Podfile"
    )

    /**
     * A folder containing 2+ of these as direct subfolders looks like a firmware/ROM dump root —
     * these don't have one canonical marker file the way source repos do.
     */
    private val FIRMWARE_SIBLING_MARKERS = setOf("system", "vendor", "product", "META-INF", "boot.img", "recovery.img")
    private const val FIRMWARE_MARKER_THRESHOLD = 2

    /**
     * Computes the set of protected root paths from a scanned file list. A root is protected in
     * its entirety — every file and subfolder beneath it — once detected.
     */
    fun detectProtectedRoots(metadata: List<FileMetadata>): Set<String> {
        val roots = mutableSetOf<String>()

        // Project markers: the marker's own parent folder is the root.
        for (meta in metadata) {
            if (meta.name in PROJECT_MARKERS || meta.name.startsWith(".git")) {
                if (meta.parentPath.isNotBlank()) roots += meta.parentPath
            }
        }

        // Firmware dumps: walk every ancestor directory segment of each file — not just its
        // immediate parent — since a real ROM dump almost always nests files several levels below
        // a marker folder (e.g. "vendor/etc/init.rc", "META-INF/com/google/android/update-binary"),
        // so that marker name would never show up as anyone's *immediate* parent-of-parent. Also
        // check the file's own name, since boot.img/recovery.img are markers that are files
        // themselves, sitting directly in the dump root rather than being folder names. A root
        // qualifies once at least FIRMWARE_MARKER_THRESHOLD distinct marker names are found
        // (at any depth) as its descendants.
        val markerHitsByRoot = HashMap<String, MutableSet<String>>()
        fun recordHit(root: String, marker: String) {
            if (root.isBlank()) return
            markerHitsByRoot.getOrPut(root) { mutableSetOf() }.add(marker)
        }

        for (meta in metadata) {
            if (meta.name in FIRMWARE_SIBLING_MARKERS) {
                recordHit(meta.parentPath, meta.name)
            }
            var pathSoFar = ""
            for (segment in meta.parentPath.split('/')) {
                if (segment.isEmpty()) continue
                pathSoFar += "/$segment"
                if (segment in FIRMWARE_SIBLING_MARKERS) {
                    recordHit(pathSoFar.substringBeforeLast('/'), segment)
                }
            }
        }

        for ((root, hits) in markerHitsByRoot) {
            if (hits.size >= FIRMWARE_MARKER_THRESHOLD) roots += root
        }

        return roots
    }

    fun isProtected(absolutePath: String, protectedRoots: Set<String>): Boolean =
        protectedRoots.any { root -> absolutePath == root || absolutePath.startsWith("$root/") }

    /** True if [scopeRoot] is inside (or equal to) a protected root — i.e. the user explicitly
     *  navigated there via the folder picker, which counts as informed consent to override the
     *  automatic safety net for that one run. */
    fun isExplicitlyScopedInto(scopeRoot: String, protectedRoots: Set<String>): Boolean =
        protectedRoots.any { root -> scopeRoot == root || scopeRoot.startsWith("$root/") || root.startsWith("$scopeRoot/") }
}
