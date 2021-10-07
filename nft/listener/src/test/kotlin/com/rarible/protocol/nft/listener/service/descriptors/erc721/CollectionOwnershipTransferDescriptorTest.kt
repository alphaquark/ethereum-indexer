package com.rarible.protocol.nft.listener.service.descriptors.erc721

import com.rarible.core.application.ApplicationEnvironmentInfo
import com.rarible.core.kafka.RaribleKafkaConsumer
import com.rarible.core.kafka.json.JsonDeserializer
import com.rarible.core.test.wait.Wait
import com.rarible.protocol.contracts.erc721.rarible.ERC721Rarible
import com.rarible.protocol.dto.NftCollectionDto
import com.rarible.protocol.dto.NftCollectionEventDto
import com.rarible.protocol.dto.NftCollectionEventTopicProvider
import com.rarible.protocol.dto.NftCollectionUpdateEventDto
import com.rarible.protocol.nft.core.model.CollectionOwnershipTransferred
import com.rarible.protocol.nft.core.model.CreateCollection
import com.rarible.protocol.nft.listener.integration.AbstractIntegrationTest
import com.rarible.protocol.nft.listener.integration.IntegrationTest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.reactive.awaitFirst
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import scalether.domain.Address
import java.util.*

@IntegrationTest
@FlowPreview
class CollectionOwnershipTransferDescriptorTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var application: ApplicationEnvironmentInfo

    private lateinit var collectionEventConsumer: RaribleKafkaConsumer<NftCollectionEventDto>

    private val collectionEvents = Collections.synchronizedList(arrayListOf<NftCollectionEventDto>())
    private lateinit var consumingJobs: List<Job>

    @BeforeEach
    fun setUpEventConsumers() {
        collectionEventConsumer = RaribleKafkaConsumer(
            clientId = "test-consumer-collection-event",
            consumerGroup = "test-group-collection-event",
            valueDeserializerClass = JsonDeserializer::class.java,
            valueClass = NftCollectionEventDto::class.java,
            defaultTopic = NftCollectionEventTopicProvider.getTopic(
                application.name,
                nftIndexerProperties.blockchain.value
            ),
            bootstrapServers = nftIndexerProperties.kafkaReplicaSet,
            offsetResetStrategy = OffsetResetStrategy.EARLIEST
        )
        consumingJobs = listOf(
            GlobalScope.launch {
                collectionEventConsumer.receive().collect {
                    collectionEvents += it.value
                }
            }
        )
    }

    @AfterEach
    fun stopConsumers() = runBlocking {
        consumingJobs.forEach { it.cancelAndJoin() }
    }

    @Test
    fun `on minting the ownership is transferred from the zero address`() = runBlocking<Unit> {
        val (creatorAddress, creatorSender) = newSender()

        val token = ERC721Rarible.deployAndWait(creatorSender, poller).awaitFirst()
        val timestamp = token.__ERC721Rarible_init("Test", "TEST", "BASE", "URI")
            .execute().verifySuccess().getTimestamp()

        Wait.waitAssert {
            val histories = nftHistoryRepository.findAllByCollection(token.address()).collectList().awaitFirst()
            assertThat(histories).hasSize(2)
            // Ownership of the contract is set first, then "CreateERC721Rarible" event is emitted.
            val (ownershipLog, createLog) = histories
            assertEquals(
                CollectionOwnershipTransferred(token.address(), timestamp, Address.ZERO(), creatorAddress),
                ownershipLog.data as CollectionOwnershipTransferred
            )
            assertThat(createLog.data).isInstanceOf(CreateCollection::class.java)
        }

        Wait.waitAssert {
            assertThat(collectionEvents).anyMatch {
                it is NftCollectionUpdateEventDto && it.collection == NftCollectionDto(
                    id = token.address(),
                    type = NftCollectionDto.Type.ERC721,
                    owner = creatorAddress,
                    name = "Test",
                    symbol = "TEST",
                    features = it.collection.features,
                    supportsLazyMint = it.collection.supportsLazyMint,
                    minters = listOf(creatorAddress)
                )
            }
        }
    }

    @Test
    fun `ownership transferred`() = runBlocking<Unit> {
        val (creatorAddress, creatorSender) = newSender()

        val token = ERC721Rarible.deployAndWait(creatorSender, poller).awaitFirst()
        token.__ERC721Rarible_init("Test", "TEST", "BASE", "URI").execute().verifySuccess()

        val (newOwnerAddress, _) = newSender()
        val timestamp = token.transferOwnership(newOwnerAddress).withSender(creatorSender).execute()
            .verifySuccess().getTimestamp()

        Wait.waitAssert {
            val histories = nftHistoryRepository.findAllByCollection(token.address()).collectList().awaitFirst()
            assertThat(histories).hasSize(3)
            val data = histories.last().data
            assertThat(data).isInstanceOf(CollectionOwnershipTransferred::class.java)
            assertEquals(
                CollectionOwnershipTransferred(token.address(), timestamp, creatorAddress, newOwnerAddress),
                data as CollectionOwnershipTransferred
            )
        }
        Wait.waitAssert {
            assertThat(collectionEvents).anyMatch {
                it is NftCollectionUpdateEventDto && it.collection == NftCollectionDto(
                    id = token.address(),
                    type = NftCollectionDto.Type.ERC721,
                    owner = creatorAddress,
                    name = "Test",
                    symbol = "TEST",
                    features = it.collection.features,
                    supportsLazyMint = it.collection.supportsLazyMint,
                    minters = listOf(creatorAddress)
                )
            }
        }
    }
}