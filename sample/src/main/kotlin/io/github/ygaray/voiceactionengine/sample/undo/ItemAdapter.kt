package io.github.ygaray.voiceactionengine.sample.undo

import io.github.ygaray.voiceactionengine.undo.EntityAdapter

/** The entity adapter for [ItemStore]: reads, fingerprints and restores items under the entity type `item`. */
class ItemAdapter(private val store: ItemStore) : EntityAdapter {
    override val entityType: String = "item"

    override suspend fun read(id: String): Any? = store.get(id)

    override suspend fun fingerprint(id: String): String? = store.fingerprint(id)

    override suspend fun restoreIf(id: String, expectedFingerprint: String?, snapshot: Any?): Boolean {
        require(snapshot == null || snapshot is Item) { "an item adapter restores items only" }
        return store.restoreIf(id, expectedFingerprint, snapshot as Item?)
    }
}
