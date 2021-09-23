package com.rarible.protocol.nft.core.converters.dto

import com.rarible.protocol.dto.NftOwnershipEventDto
import com.rarible.protocol.dto.NftOwnershipUpdateEventDto
import com.rarible.protocol.nft.core.model.Ownership

object OwnershipEventDtoFromOwnershipConverter {
    fun convert(source: Ownership, eventId: String): NftOwnershipEventDto {
        return NftOwnershipUpdateEventDto(
            eventId,
            source.id.decimalStringValue,
            OwnershipDtoConverter.convert(source)
        )
    }
}
