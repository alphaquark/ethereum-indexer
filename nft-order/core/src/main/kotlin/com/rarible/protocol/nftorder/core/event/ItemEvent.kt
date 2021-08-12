package com.rarible.protocol.nftorder.core.event

import com.rarible.protocol.nftorder.core.model.Item
import com.rarible.protocol.nftorder.core.model.ItemId
import java.util.*

sealed class ItemEvent(
    val type: ItemEventType
) {
    val id: String = UUID.randomUUID().toString()
}

data class ItemEventUpdate(
    val item: Item
) : ItemEvent(ItemEventType.UPDATE)


data class ItemEventDelete(
    val itemId: ItemId
) : ItemEvent(ItemEventType.DELETE)

enum class ItemEventType {
    UPDATE, DELETE
}