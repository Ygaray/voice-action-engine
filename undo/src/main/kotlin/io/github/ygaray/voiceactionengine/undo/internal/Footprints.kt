package io.github.ygaray.voiceactionengine.undo.internal

import io.github.ygaray.voiceactionengine.undo.EntityKey
import java.util.IdentityHashMap

/**
 * A set of actions that are tied together by the entities they share, and every entity they declared. Actions in
 * different components cannot affect each other, so an undo may carry on with one when another fails.
 */
internal class Cluster(val entries: List<Entry>, val keys: Set<EntityKey>)

/**
 * The entryKeys of a set of actions, grouped into components. An action's footprint is every entity it captured,
 * created or declared as touched. Two actions are in one component when their entryKeys overlap, directly or through
 * a chain of other actions. An action with no entity is a component of its own.
 */
internal class Footprints(entries: List<Entry>) {
    private val parent = HashMap<EntityKey, EntityKey>()
    private val entryKeys = IdentityHashMap<Entry, Set<EntityKey>>()
    private val membership = IdentityHashMap<Entry, Int>()

    /** The components, in the order their first action appears in the list given. */
    val components: List<Cluster>

    init {
        for (entry in entries) {
            val footprint = entry.footprint
            entryKeys[entry] = footprint
            footprint.forEach { find(it) }
            footprint.zipWithNext().forEach { (a, b) -> union(a, b) }
        }
        val byRoot = LinkedHashMap<Any, MutableList<Entry>>()
        for (entry in entries) {
            val root: Any = entryKeys.getValue(entry).firstOrNull()?.let { find(it) } ?: entry
            byRoot.getOrPut(root) { ArrayList() }.add(entry)
        }
        components = byRoot.values.mapIndexed { index, members ->
            members.forEach { membership[it] = index }
            Cluster(members, members.flatMap { entryKeys.getValue(it) }.toSet())
        }
    }

    /** The position in [components] of the component that holds [entry]. */
    fun componentOf(entry: Entry): Int = checkNotNull(membership[entry]) { "the action is not in this footprint set" }

    /** The actions that share an entity with no other action given. Actions with no entity are isolated. */
    fun isolatedEntries(): List<Entry> = components.filter { it.entries.size == 1 }.flatMap { it.entries }

    /** True when no other action given shares an entity with [entry]. An action with no entity is isolated. */
    fun isolated(entry: Entry): Boolean = components[componentOf(entry)].entries.size == 1

    private fun find(key: EntityKey): EntityKey {
        var root = key
        var next = parent.getOrPut(root) { root }
        while (next != root) {
            root = next
            next = parent.getOrPut(root) { root }
        }
        var walk = key
        while (walk != root) {
            val following = parent.getValue(walk)
            parent[walk] = root
            walk = following
        }
        return root
    }

    private fun union(a: EntityKey, b: EntityKey) {
        val rootA = find(a)
        val rootB = find(b)
        if (rootA != rootB) parent[rootB] = rootA
    }
}
