package org.happyzion.api.menu.application

import org.happyzion.api.menu.domain.MenuItem

object PublicVideoMenuPathSupport {
    fun buildPlaylistPath(menu: MenuItem, itemsById: Map<Long, MenuItem>): String {
        val segments = collectSegments(menu, itemsById)
        return "/${segments.joinToString("/")}"
    }

    fun matchesPlaylistPath(menu: MenuItem, itemsById: Map<Long, MenuItem>, path: String): Boolean {
        val normalizedPath = normalizeLookupPath(path)
        return normalizedPath == buildPlaylistPath(menu, itemsById)
    }

    private fun collectSegments(menu: MenuItem, itemsById: Map<Long, MenuItem>): List<String> {
        val segments = mutableListOf<String>()
        var current: MenuItem? = menu

        while (current != null) {
            segments += current.slug
            current = current.parentId?.let(itemsById::get)
        }

        return segments.asReversed()
    }

    private fun normalizeLookupPath(path: String): String {
        val trimmed = path.substringBefore('?').substringBefore('#').trim()
        if (trimmed.isBlank()) {
            return "/"
        }
        return if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    }
}
