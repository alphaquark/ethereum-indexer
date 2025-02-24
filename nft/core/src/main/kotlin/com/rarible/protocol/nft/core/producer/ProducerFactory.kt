package com.rarible.protocol.nft.core.producer

import com.rarible.core.kafka.RaribleKafkaProducer
import com.rarible.core.kafka.json.JsonSerializer
import com.rarible.ethereum.domain.Blockchain
import com.rarible.protocol.dto.*
import com.rarible.protocol.nft.core.service.item.meta.InternalItemHandler

class ProducerFactory(
    private val kafkaReplicaSet: String,
    private val blockchain: Blockchain,
    private val environment: String
) {
    private val clientId = "$environment.${blockchain.value}.protocol-nft-events-importer"

    fun createCollectionEventsProducer(): RaribleKafkaProducer<NftCollectionEventDto> {
        return RaribleKafkaProducer(
            clientId = "$clientId.collection",
            valueSerializerClass = JsonSerializer::class.java,
            valueClass = NftCollectionEventDto::class.java,
            defaultTopic = NftCollectionEventTopicProvider.getTopic(environment, blockchain.value),
            bootstrapServers = kafkaReplicaSet
        )
    }

    fun createItemEventsProducer(): RaribleKafkaProducer<NftItemEventDto> {
        return RaribleKafkaProducer(
            clientId = "$clientId.item",
            valueSerializerClass = JsonSerializer::class.java,
            valueClass = NftItemEventDto::class.java,
            defaultTopic = NftItemEventTopicProvider.getTopic(environment, blockchain.value),
            bootstrapServers = kafkaReplicaSet
        )
    }

    fun createInternalItemEventsProducer(): RaribleKafkaProducer<NftItemEventDto> {
        return RaribleKafkaProducer(
            clientId = "$clientId.item.internal",
            valueSerializerClass = JsonSerializer::class.java,
            valueClass = NftItemEventDto::class.java,
            defaultTopic = InternalItemHandler.getInternalTopic(environment, blockchain.value),
            bootstrapServers = kafkaReplicaSet
        )
    }

    fun createOwnershipEventsProducer(): RaribleKafkaProducer<NftOwnershipEventDto> {
        return RaribleKafkaProducer(
            clientId = "$clientId.ownership",
            valueSerializerClass = JsonSerializer::class.java,
            valueClass = NftOwnershipEventDto::class.java,
            defaultTopic = NftOwnershipEventTopicProvider.getTopic(environment, blockchain.value),
            bootstrapServers = kafkaReplicaSet
        )
    }

    fun createItemActivityProducer(): RaribleKafkaProducer<ActivityDto> {
        return RaribleKafkaProducer(
            clientId = "$clientId.item-activity",
            valueSerializerClass = JsonSerializer::class.java,
            valueClass = ActivityDto::class.java,
            defaultTopic = ActivityTopicProvider.getTopic(environment, blockchain.value),
            bootstrapServers = kafkaReplicaSet
        )
    }
}
