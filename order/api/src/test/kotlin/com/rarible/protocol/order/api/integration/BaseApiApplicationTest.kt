package com.rarible.protocol.order.api.integration

import com.rarible.core.test.containers.MongodbTestContainer
import com.rarible.core.test.containers.OpenEthereumTestContainer
import com.rarible.ethereum.domain.Blockchain

abstract class BaseApiApplicationTest {
    init {
        System.setProperty(
            "rarible.core.ethereum.url", openEthereumTest.ethereumUrl().toString()
        )
        System.setProperty(
            "rarible.core.ethereum.web-socket-url", openEthereumTest.ethereumWebSocketUrl().toString()
        )
        System.setProperty(
            "common.blockchain", Blockchain.ETHEREUM.name.toLowerCase()
        )
        System.setProperty(
            "spring.data.mongodb.uri", mongoTest.connectionString()
        )
        System.setProperty(
            "spring.data.mongodb.database", "protocol"
        )
    }
    companion object {
        val openEthereumTest = OpenEthereumTestContainer()
        val mongoTest = MongodbTestContainer()
    }
}
