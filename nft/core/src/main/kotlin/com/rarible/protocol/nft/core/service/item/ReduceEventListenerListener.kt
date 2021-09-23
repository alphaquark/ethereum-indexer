package com.rarible.protocol.nft.core.service.item

import com.rarible.protocol.nft.core.converters.dto.ExtendedItemEventDtoConverter
import com.rarible.protocol.nft.core.converters.dto.OwnershipEventDtoFromOwnershipConverter
import com.rarible.protocol.nft.core.converters.dto.OwnershipEventDtoFromOwnershipIdConverter
import com.rarible.protocol.nft.core.model.*
import com.rarible.protocol.nft.core.producer.ProtocolNftEventPublisher
import kotlinx.coroutines.reactor.mono
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class ReduceEventListenerListener(
    private val publisher: ProtocolNftEventPublisher
) {
    fun onItemChanged(item: Item, eventId: String): Mono<Void> = mono {
        val eventDto = ExtendedItemEventDtoConverter.convert(ExtendedItem(item, ItemMeta.EMPTY), eventId)
        publisher.publishInternalItem(eventDto)
    }.then()

    fun onOwnershipChanged(ownership: Ownership, eventId: String): Mono<Void> = mono {
        val eventDto = OwnershipEventDtoFromOwnershipConverter.convert(ownership, eventId)
        publisher.publish(eventDto)
    }.then()

    fun onOwnershipDeleted(ownershipId: OwnershipId, eventId: String): Mono<Void> = mono {
        val eventDto = OwnershipEventDtoFromOwnershipIdConverter.convert(ownershipId, eventId)
        publisher.publish(eventDto)
    }.then()
}
