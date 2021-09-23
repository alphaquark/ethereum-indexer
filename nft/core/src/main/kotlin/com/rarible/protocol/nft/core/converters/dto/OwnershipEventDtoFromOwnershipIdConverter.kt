package com.rarible.protocol.nft.core.converters.dto

import com.rarible.protocol.dto.NftOwnershipDeleteEventDto
import com.rarible.protocol.dto.NftOwnershipEventDto
import com.rarible.protocol.nft.core.model.OwnershipId
import org.springframework.stereotype.Component

@Component
object OwnershipEventDtoFromOwnershipIdConverter {
    fun convert(source: OwnershipId, eventId: String): NftOwnershipEventDto {
        return NftOwnershipDeleteEventDto(
            eventId,
            source.decimalStringValue,
            DeletedOwnershipDtoConverter.convert(source)
        )
    }
}
