package io.github.ygaray.voiceactionengine.undo.internal

/** The reading of a clock that has never given one: it stamps a group as "not yet aged" and is never compared. */
// Not a const: a const would be a public static field, which the API-shape test keeps out of the frozen surface.
internal val NO_READING: Long = Long.MIN_VALUE

private const val TOMBSTONES_PER_GROUP = 20L
private const val MIN_TOMBSTONES = 1000L

/**
 * Keeps the journal bounded: a group is dropped when it has been idle for more than [maxAgeMillis], and the least
 * recently active groups are dropped when there are more than [maxGroups]. A group whose undo is running is never
 * dropped, and a dropped group's snapshots are released with it.
 *
 * Dropped keys are remembered in a set bounded by [tombstoneLimit] (the oldest forgotten first), so a late record into
 * a dropped group makes a withheld group instead of a silent part of the command. The set is much larger than
 * [maxGroups] because a held change may be confirmed long after its run, but it is still bounded: past the limit a
 * late record opens a fresh group that is not withheld. Every call is made under the journal's lock.
 */
internal class Retention(private val maxGroups: Int, private val maxAgeMillis: Long) {
    private val tombstones = LinkedHashSet<String>()

    /** How many dropped keys are remembered: 20 per group kept, and at least 1000. */
    val tombstoneLimit: Long = maxOf(maxGroups.toLong() * TOMBSTONES_PER_GROUP, MIN_TOMBSTONES)

    /** Drops what is too old or too many, never [keep] and never a group being undone. Returns the dropped keys. */
    fun sweep(groups: MutableMap<String, Group>, now: Long, keep: String?): List<String> {
        // With no reading yet nothing ages. A group stamped before the first reading starts aging at the first one.
        val aging = now != NO_READING
        if (aging) groups.values.filter { it.lastActive == NO_READING }.forEach { it.lastActive = now }
        val droppable = groups.values.filter { !it.undoing && it.key != keep }
        val gone = droppable.filter { aging && now - it.lastActive > maxAgeMillis }.toMutableList()
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
        while (tombstones.size > tombstoneLimit) tombstones.remove(tombstones.first())
    }
}
