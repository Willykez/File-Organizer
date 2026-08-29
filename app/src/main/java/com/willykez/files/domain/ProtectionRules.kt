package com.willykez.files.domain

import com.willykez.files.data.model.FileMetadata

object ProtectionRules {

    private val PROJECT_MARKERS = setOf(
        "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
        "package.json", "pom.xml", "Cargo.toml", "requirements.txt", "setup.py",
        "AndroidManifest.xml", "CMakeLists.txt", "Makefile", ".git", ".project", "Podfile"
    )

    private val FIRMWARE_SIBLING_MARKERS = setOf("system", "vendor", "product", "META-INF", "boot.img", "recovery.img")
    private const val FIRMWARE_MARKER_THRESHOLD = 2

    fun detectProtectedRoots(metadata: List<FileMetadata>): Set<String> {
        val roots = mutableSetOf<String>()

        for (meta in metadata) {
            if (meta.name in PROJECT_MARKERS || meta.name.startsWith(".git")) {
                if (meta.parentPath.isNotBlank()) roots += meta.parentPath
            }
        }

        val firmwareHitsByRoot = mutableMapOf<String, MutableSet<String>>()

        for (meta in metadata) {
            if (meta.name in FIRMWARE_SIBLING_MARKERS && meta.parentPath.isNotBlank()) {
                firmwareHitsByRoot.getOrPut(meta.parentPath) { mutableSetOf() }.add(meta.name)
            }

            val parts = meta.parentPath.split('/').filter { it.isNotEmpty() }
            for (i in parts.indices) {
                val candidateMarker = parts[i]
                if (candidateMarker in FIRMWARE_SIBLING_MARKERS) {
                    val rootPath = if (meta.parentPath.startsWith("/")) {
                        "/" + parts.take(i).joinToString("/")
                    } else {
                        parts.take(i).joinToString("/")
                    }
                    if (rootPath.isNotBlank()) {
                        firmwareHitsByRoot.getOrPut(rootPath) { mutableSetOf() }.add(candidateMarker)
                    }
                }
            }
        }

        for ((candidateRoot, matchedMarkers) in firmwareHitsByRoot) {
            if (matchedMarkers.size >= FIRMWARE_MARKER_THRESHOLD) {
                roots += candidateRoot
            }
        }

        return roots
    }

    fun isProtected(absolutePath: String, protectedRoots: Set<String>): Boolean =
        protectedRoots.any { root -> absolutePath == root || absolutePath.startsWith("$root/") }

    fun isExplicitlyScopedInto(scopeRoot: String, protectedRoots: Set<String>): Boolean =
        protectedRoots.any { root -> scopeRoot == root || scopeRoot.startsWith("$root/") || root.startsWith("$scopeRoot/") }
}
