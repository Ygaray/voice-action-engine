package io.github.ygaray.voiceactionengine.undo.internal

/**
 * Keeps the journal bounded: a group is dropped when it has been idle for more than [maxAgeMillis], and the least
 * recently active groups are dropped when there are more than [maxGroups]. A group whose undo is running is never
 * dropped, and a dropped group's snapshots are released with it.
 *
 * Dropped keys are remembered in a small set (at most [maxGroups] of them, the oldest forgotten first), so a late
 * record into a dropped group makes a withheld group instead of a silent part of the command. Every call is made under
 * the journal's lock.
 */
internal class Retention(private val maxGroups: Int, private val maxAgeMillis: Long) {
    private val tombstones = LinkedHashSet<String>()

    /** Drops what is too old or too many, never [keep] and never a group being undone. Returns the dropped keys. */
    fun sweep(groups: MutableMap<String, Group>, now: Long, keep: String?): List<String> {
        val droppable = groups.values.filter { !it.undoing && it.key != keep }
        val gone = droppable.filter { now - it.lastActive > maxAgeMillis }.toMutableList()
        val surplus = groups.size - gone.size - maxGroups
        if (surplus > 0) {
            droppable.filter { it !in gone }.sortedBy { it.activity }.take(surplus).forEach { gone.add(it) }
        }
        for (group in gone) {
            groups.remove(group.key)
            group.dropSnapshots()
            remember(group.key)
        }
        return gone.map { it.key }
    }

    /** True, once, when [key] was dropped and has not been recreated since. */
    fun wasDropped(key: String): Boolean = tombstones.remove(key)

    private fun remember(key: String) {
        tombstones.add(key)
        while (tombstones.size > maxGroups) tombstones.remove(tombstones.first())
    }
}
