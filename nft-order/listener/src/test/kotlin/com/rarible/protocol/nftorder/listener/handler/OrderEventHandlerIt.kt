package com.rarible.protocol.nftorder.listener.handler

import com.rarible.core.test.wait.Wait
import com.rarible.protocol.dto.OrderDto
import com.rarible.protocol.dto.OrderUpdateEventDto
import com.rarible.protocol.nftorder.core.service.ItemService
import com.rarible.protocol.nftorder.core.service.OwnershipService
import com.rarible.protocol.nftorder.listener.test.AbstractIntegrationTest
import com.rarible.protocol.nftorder.listener.test.IntegrationTest
import com.rarible.protocol.nftorder.listener.test.data.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@IntegrationTest
class OrderEventHandlerIt : AbstractIntegrationTest() {

    @Autowired
    lateinit var orderEventHandler: OrderEventHandler

    @Autowired
    lateinit var itemService: ItemService

    @Autowired
    lateinit var ownershipService: OwnershipService

    /**
     * The largest test case - received order contains both make/take assetTypes with related items.
     * Both of these items are not present in nft-order Mongo and should be fetched from outside.
     * Ownership, related to make-item should also be fetched outside.
     */
    @Test
    fun `make, take items and ownership fetched and created`() = runWithKafka {
        // Some random data, make-Item linked with Ownership
        val makeItemId = randomItemId()
        val takeItemId = randomItemId()
        val ownershipId = randomOwnershipId(makeItemId)

        val nftMakeItemDto = randomNftItemDto(makeItemId, randomPartDto(ownershipId.owner))
        val nftTakeItemDto = randomNftItemDto(takeItemId)
        val nftOwnership = randomNftOwnershipDto(nftMakeItemDto)

        val bestSell = randomOrderDto(makeItemId)
        val bestBid = randomOrderDto(takeItemId)
        val bestSellByOwnership = randomOrderDto(makeItemId, ownershipId.owner)

        // Since we don't have any data in Mongo, we need to fetch entities and enrichment data via HTTP API
        nftItemControllerApiMock.mockGetNftItemById(makeItemId, nftMakeItemDto)
        nftItemControllerApiMock.mockGetNftItemById(takeItemId, nftTakeItemDto)
        nftOwnershipControllerApiMock.mockGetNftOwnershipById(ownershipId, nftOwnership)

        // Unlockable responses mocks
        lockControllerApiMock.mockIsUnlockable(makeItemId, true)
        lockControllerApiMock.mockIsUnlockable(takeItemId, false)

        // For make-item there is no bestBid Order, and for take-item there is no bestSell Order
        orderControllerApiMock.mockGetSellOrdersByItem(makeItemId, bestSell)
        orderControllerApiMock.mockGetBidOrdersByItem(makeItemId)
        orderControllerApiMock.mockGetSellOrdersByItem(takeItemId)
        orderControllerApiMock.mockGetBidOrdersByItem(takeItemId, bestBid)
        orderControllerApiMock.mockGetSellOrdersByItem(ownershipId, bestSellByOwnership)

        val updatedOrder =
            randomOrderDto(randomAssetErc721(makeItemId), ownershipId.owner, randomAssetErc1155(takeItemId))

        orderEventHandler.handle(createOrderUpdateEvent(updatedOrder))

        // Ensure all three entities are stored in Mongo and has fields equivalent to DTO's fields
        val makeItem = itemService.get(makeItemId)!!
        assertItemAndDtoEquals(makeItem, nftMakeItemDto)
        assertThat(makeItem.unlockable).isTrue()
        assertThat(makeItem.bestSellOrder).isEqualTo(bestSell)
        assertThat(makeItem.bestBidOrder).isNull()

        val takeItem = itemService.get(takeItemId)!!
        assertItemAndDtoEquals(takeItem, nftTakeItemDto)
        assertThat(takeItem.unlockable).isFalse()
        assertThat(takeItem.bestSellOrder).isNull()
        assertThat(takeItem.bestBidOrder).isEqualTo(bestBid)

        val ownership = ownershipService.get(ownershipId)!!
        assertOwnershipAndNftDtoEquals(ownership, nftOwnership)
        assertThat(ownership.bestSellOrder).isEqualTo(bestSellByOwnership)

        Wait.waitAssert {
            assertThat(itemEvents).hasSize(2)
            assertThat(ownershipEvents).hasSize(1)
        }
    }

    @Test
    fun `make item and ownership updated`() = runWithKafka {
        // Starting with item/ownership already saved in Mongo, both of them have no enrich data
        val item = randomItem(randomPart())
        val makeItemId = item.id
        val ownership = randomOwnership(item)
        val ownershipId = ownership.id
        ownershipService.save(ownership)
        itemService.save(item)

        // If ownership and item already stored in Mongo, no HTTP calls should be executed,
        // except fetching bestSell Order for ownership and item
        val bestSell = randomOrderDto(makeItemId)
        val bestSellByOwnership = randomOrderDto(makeItemId, ownershipId.owner)
        orderControllerApiMock.mockGetSellOrdersByItem(makeItemId, bestSell)
        orderControllerApiMock.mockGetSellOrdersByItem(ownershipId, bestSellByOwnership)

        val updatedOrder = randomOrderDto(makeItemId, ownershipId.owner)
        orderEventHandler.handle(createOrderUpdateEvent(updatedOrder))

        // Ensure existing entities updated with enrich data
        val updatedMakeItem = itemService.get(makeItemId)!!
        assertThat(updatedMakeItem.bestSellOrder).isEqualTo(bestSell)

        val updateOwnership = ownershipService.get(ownershipId)!!
        assertThat(updateOwnership.bestSellOrder).isEqualTo(bestSellByOwnership)
        Wait.waitAssert {
            assertThat(itemEvents).hasSize(1)
            assertThat(ownershipEvents).hasSize(1)
        }
    }

    @Test
    fun `take item updated`() = runWithKafka {
        // Starting with item already saved in Mongo without enrich data
        val item = randomItem(randomPart())
        val takeItemId = item.id
        itemService.save(item)

        // If item already stored in Mongo, no HTTP calls should be executed,
        // except fetching bestBid Order for item
        val bestBid = randomOrderDto(takeItemId)
        orderControllerApiMock.mockGetBidOrdersByItem(takeItemId, bestBid)

        val updatedOrder = randomOrderDto(randomAssetErc20(), randomAddress(), randomAssetErc1155(takeItemId))
        orderEventHandler.handle(createOrderUpdateEvent(updatedOrder))

        // Ensure existing entity updated with enrich data
        val updatedMakeItem = itemService.get(takeItemId)!!
        assertThat(updatedMakeItem.bestBidOrder).isEqualTo(bestBid)
        Wait.waitAssert {
            assertThat(itemEvents).hasSize(1)
        }
    }

    @Test
    fun `make item and ownership deleted, no enrich data`() = runWithKafka {
        // Starting with item/ownership already saved in Mongo, both of them have bestSellOrder
        val item = randomItem(randomPart()).copy(bestSellOrder = randomOrderDto())
        val ownership = randomOwnership(item).copy(bestSellOrder = randomOrderDto(item.id))
        ownershipService.save(ownership)
        itemService.save(item)

        // HTTP API returns NULL bestSellOrder
        orderControllerApiMock.mockGetSellOrdersByItem(item.id)
        orderControllerApiMock.mockGetSellOrdersByItem(ownership.id)

        val updatedOrder = randomOrderDto(item.id, ownership.id.owner)
        orderEventHandler.handle(createOrderUpdateEvent(updatedOrder))

        // Both entities should be deleted
        assertThat(itemService.get(item.id)).isNull()
        assertThat(ownershipService.get(ownership.id)).isNull()
        Wait.waitAssert {
            assertThat(itemEvents).hasSize(1)
            assertThat(ownershipEvents).hasSize(1)
        }
    }

    @Test
    fun `take item NOT deleted if some enrich data remains`() = runWithKafka {
        // Unlockable set to TRUE, so even we don't have bestBidOrder, we have to keep this entity in Mongo
        val item = randomItem(randomPart()).copy(unlockable = true, bestBidOrder = randomOrderDto())
        itemService.save(item)

        // HTTP API returns NULL bestSellOrder
        orderControllerApiMock.mockGetBidOrdersByItem(item.id)

        val updatedOrder = randomOrderDto(randomAssetErc20(), randomAddress(), randomAssetErc1155(item.id))
        orderEventHandler.handle(createOrderUpdateEvent(updatedOrder))

        // Unlockable is still TRUE, bestBid Order reset
        val update = itemService.get(item.id)!!
        assertThat(update.bestBidOrder).isNull()
        assertThat(update.unlockable).isTrue()
        Wait.waitAssert {
            assertThat(itemEvents).hasSize(1)
        }
    }

    fun createOrderUpdateEvent(order: OrderDto): OrderUpdateEventDto {
        return OrderUpdateEventDto(
            randomString(),
            order.hash.hex(),
            order
        )
    }

}