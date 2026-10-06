package io.github.ygaray.voiceactionengine.sample.undo

/**
 * One stored item. It is immutable, so a snapshot of it can never change after it was read.
 *
 * @property stamp the value of the store's write counter when this version was written.
 */
data class Item(val id: String, val title: String, val parentId: String?, val stamp: Long)

/**
 * An in-memory stand-in for an app's database, used by the undo proof and the Phase 19 legs.
 *
 * Every write takes a fresh stamp, so every write changes the item's fingerprint. [restoreIf] checks and writes in one
 * synchronized block, like an app's own transaction would.
 */
class ItemStore {
    private val lock = Any()
    private val items = LinkedHashMap<String, Item>()
    private var created = 0
    private var stamp = 0L

    /** Puts an item with a chosen [id] in place, for the start state of a test. */
    fun seed(id: String, title: String, parentId: String? = null): Item = synchronized(lock) {
        put(Item(id, title, parentId, ++stamp))
    }

    /** Creates an item with the next id, `item-1`, `item-2` and so on. */
    fun create(title: String, parentId: String?): Item = synchronized(lock) {
        put(Item("item-${++created}", title, parentId, ++stamp))
    }

    /** Gives the item a new title; null when it does not exist. */
    fun rename(id: String, title: String): Item? = synchronized(lock) {
        items[id]?.let { put(Item(id, title, it.parentId, ++stamp)) }
    }

    /** Removes the item; null when it does not exist. */
    fun delete(id: String): Item? = synchronized(lock) { items.remove(id) }

    /** An unrelated write, used by tests to move an item on between a command and its undo. */
    fun edit(id: String, title: String) {
        rename(id, title)
    }

    /** The item, or null. */
    fun get(id: String): Item? = synchronized(lock) { items[id] }

    /** The item's version as a string, or null when it does not exist. */
    fun fingerprint(id: String): String? = synchronized(lock) { items[id]?.stamp?.toString() }

    /**
     * Puts the item back, atomically. When the live fingerprint differs from [expected] nothing is written and false
     * comes back. A null [snapshot] removes the item; otherwise the snapshot is put back with its original stamp, so
     * the live fingerprint afterwards equals the one from before the command.
     */
    fun restoreIf(id: String, expected: String?, snapshot: Item?): Boolean = synchronized(lock) {
        if (items[id]?.stamp?.toString() != expected) return@synchronized false
        if (snapshot == null) items.remove(id) else items[id] = snapshot
        true
    }

    /** A copy of every item, for whole-store comparisons. */
    fun snapshot(): Map<String, Item> = synchronized(lock) { LinkedHashMap(items) }

    private fun put(item: Item): Item {
        items[item.id] = item
        return item
    }
}
