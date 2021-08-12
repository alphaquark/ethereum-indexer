package com.rarible.protocol.nftorder.listener.handler

import com.rarible.core.kafka.KafkaMessage
import com.rarible.core.test.wait.Wait
import com.rarible.protocol.dto.*
import com.rarible.protocol.nftorder.core.model.OwnershipId
import com.rarible.protocol.nftorder.core.service.OwnershipService
import com.rarible.protocol.nftorder.listener.test.AbstractIntegrationTest
import com.rarible.protocol.nftorder.listener.test.IntegrationTest
import com.rarible.protocol.nftorder.listener.test.data.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@IntegrationTest
internal class OwnershipEventHandlerIt : AbstractIntegrationTest() {

    @Autowired
    private lateinit var ownershipEventHandler: OwnershipEventHandler

    @Autowired
    private lateinit var ownershipService: OwnershipService

    @Test
    fun `update event - ownership fetched and stored`() = runWithKafka {
        val itemId = randomItemId()
        val ownershipId = randomOwnershipId(itemId)
        val bestSell = randomOrderDto(itemId)

        orderControllerApiMock.mockGetSellOrdersByItem(ownershipId, bestSell)

        val ownershipDto = randomNftOwnershipDto(ownershipId)
        ownershipEventHandler.handle(createOwnershipUpdateEvent(ownershipDto))

        val created = ownershipService.get(ownershipId)!!

        assertOwnershipAndNftDtoEquals(created, ownershipDto)
        assertThat(created.bestSellOrder).isEqualTo(bestSell)
        Wait.waitAssert {
            assertThat(ownershipEvents).hasSize(1)
            assertUpdateOwnershipEvent(ownershipId, ownershipEvents!![0])
        }
    }

    @Test
    fun `update event - existing ownership updated`() = runWithKafka {
        val itemId = randomItemId()
        val ownership = ownershipService.save(randomOwnership(itemId))
        val bestSell = randomOrderDto(itemId)

        // Despite we already have stored enrichment data, we refreshing it on update
        orderControllerApiMock.mockGetSellOrdersByItem(ownership.id, bestSell)

        val ownershipDto = randomNftOwnershipDto(ownership.id)
        ownershipEventHandler.handle(createOwnershipUpdateEvent(ownershipDto))

        val updated = ownershipService.get(ownership.id)!!

        // Entity should be completely replaced by update data
        assertOwnershipAndNftDtoEquals(updated, ownershipDto)
        assertThat(updated.bestSellOrder).isEqualTo(bestSell)
        Wait.waitAssert {
            assertThat(ownershipEvents).hasSize(1)
            assertUpdateOwnershipEvent(ownership.id, ownershipEvents!![0])
        }
    }

    @Test
    fun `update event - existing ownership deleted, no enrich data`() = runWithKafka {
        val itemId = randomItemId()
        val ownership = ownershipService.save(randomOwnership(itemId))
        assertThat(ownershipService.get(ownership.id)).isNotNull()

        // No enrichment data fetched
        orderControllerApiMock.mockGetSellOrdersByItem(itemId)

        val ownershipDto = randomNftOwnershipDto(ownership.id)
        ownershipEventHandler.handle(createOwnershipUpdateEvent(ownershipDto))

        // Entity removed due to absence of enrichment data
        assertThat(ownershipService.get(ownership.id)).isNull()
        Wait.waitAssert {
            assertThat(ownershipEvents).hasSize(1)
            assertUpdateOwnershipEvent(ownership.id, ownershipEvents!![0])
        }
    }

    @Test
    fun `update event - update skipped, no enrich data`() = runWithKafka<Unit> {
        val itemId = randomItemId()
        val ownershipId = randomOwnershipId(itemId)

        orderControllerApiMock.mockGetSellOrdersByItem(itemId)

        val ownershipDto = randomNftOwnershipDto(ownershipId)
        ownershipEventHandler.handle(createOwnershipUpdateEvent(ownershipDto))

        assertThat(ownershipService.get(ownershipId)).isNull()
        Wait.waitAssert {
            assertThat(ownershipEvents).hasSize(1)
            assertUpdateOwnershipEvent(ownershipId, ownershipEvents!![0])
        }
    }

    @Test
    fun `delete event - existing ownership deleted`() = runWithKafka {
        val ownership = ownershipService.save(randomOwnership())
        assertThat(ownershipService.get(ownership.id)).isNotNull()

        // No enrichment data fetched
        ownershipEventHandler.handle(createOwnershipDeleteEvent(ownership.id))

        // Entity not created due to absence of enrichment data
        assertThat(ownershipService.get(ownership.id)).isNull()
        Wait.waitAssert {
            assertThat(ownershipEvents).hasSize(1)
            assertDeleteOwnershipEvent(ownership.id, ownershipEvents!![0])
        }
    }

    @Test
    fun `delete event - ownership doesn't exist`() = runWithKafka {
        val ownershipId = randomOwnershipId()

        ownershipEventHandler.handle(createOwnershipDeleteEvent(ownershipId))

        assertThat(ownershipService.get(ownershipId)).isNull()
        Wait.waitAssert {
            assertThat(ownershipEvents).hasSize(1)
            assertDeleteOwnershipEvent(ownershipId, ownershipEvents!![0])
        }
    }

    private fun assertDeleteOwnershipEvent(ownershipId: OwnershipId, message: KafkaMessage<NftOrderOwnershipEventDto>) {
        val event = message.value
        assertThat(event is NftOrderOwnershipEventDto)
        assertThat(event.ownershipId).isEqualTo(ownershipId.decimalStringValue)
    }

    private fun assertUpdateOwnershipEvent(ownershipId: OwnershipId, message: KafkaMessage<NftOrderOwnershipEventDto>) {
        val event = message.value
        assertThat(event is NftOrderOwnershipUpdateEventDto)
        assertThat(event.ownershipId).isEqualTo(ownershipId.decimalStringValue)
    }

    private fun createOwnershipUpdateEvent(nftOwnership: NftOwnershipDto): NftOwnershipUpdateEventDto {
        return NftOwnershipUpdateEventDto(
            randomString(),
            nftOwnership.id,
            nftOwnership
        )
    }

    private fun createOwnershipDeleteEvent(ownershipId: OwnershipId): NftOwnershipDeleteEventDto {
        return NftOwnershipDeleteEventDto(
            randomString(),
            ownershipId.stringValue,
            NftDeletedOwnershipDto(
                ownershipId.stringValue,
                ownershipId.token,
                ownershipId.tokenId.value,
                ownershipId.owner
            )
        )
    }
}