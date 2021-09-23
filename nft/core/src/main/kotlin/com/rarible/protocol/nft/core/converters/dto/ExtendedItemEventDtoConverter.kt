package com.rarible.protocol.nft.core.converters.dto

import com.rarible.protocol.dto.NftItemDeleteEventDto
import com.rarible.protocol.dto.NftItemEventDto
import com.rarible.protocol.dto.NftItemUpdateEventDto
import com.rarible.protocol.nft.core.model.ExtendedItem

object ExtendedItemEventDtoConverter {
    fun convert(source: ExtendedItem, eventId: String): NftItemEventDto {

        return if (source.item.deleted) {
            val itemId = source.item.id

            NftItemDeleteEventDto(
                eventId,
                itemId.decimalStringValue,
                DeletedItemDtoConverter.convert(itemId)
            )
        } else {
            NftItemUpdateEventDto(
                eventId,
                source.item.id.decimalStringValue,
                ExtendedItemDtoConverter.convert(source)
            )
        }
    }
}
