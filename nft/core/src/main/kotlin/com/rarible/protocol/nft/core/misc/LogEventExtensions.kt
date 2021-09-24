package com.rarible.protocol.nft.core.misc

import com.rarible.ethereum.listener.log.domain.LogEvent

val LogEvent.eventId: String
    get() {
        return StringBuilder().run {
            append(transactionHash.hex())
            blockNumber?.let { append("_").append(it) }
            logIndex?.let { append("_").append(it) }
            index.let { append("_").append(it) }
            minorLogIndex.let { append("_").append(it) }
            toString()
        }
    }
