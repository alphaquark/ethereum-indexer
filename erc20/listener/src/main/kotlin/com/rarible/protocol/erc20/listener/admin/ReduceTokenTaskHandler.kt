package com.rarible.protocol.erc20.listener.admin

import com.rarible.contracts.erc20.TransferEvent
import com.rarible.core.task.Task
import com.rarible.core.task.TaskHandler
import com.rarible.core.task.TaskRepository
import com.rarible.core.task.TaskStatus
import com.rarible.ethereum.listener.log.ReindexTopicTaskHandler
import com.rarible.protocol.erc20.core.model.BalanceId
import com.rarible.protocol.erc20.core.model.ReduceTokenTaskParams
import com.rarible.protocol.erc20.core.model.Wallet
import com.rarible.protocol.erc20.core.repository.Erc20TransferHistoryRepository
import com.rarible.protocol.erc20.listener.service.balance.Erc20BalanceReduceService
import io.daonomic.rpc.domain.Word
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import scalether.domain.Address

@Component
@ExperimentalCoroutinesApi
class ReduceTokenTaskHandler(
    private val taskRepository: TaskRepository,
    private val reduceService: Erc20BalanceReduceService,
    private val erc20TransferHistoryRepository: Erc20TransferHistoryRepository
) : TaskHandler<Wallet> {

    override val type: String
        get() = ReduceTokenTaskParams.ADMIN_REDUCE_TOKEN

    override suspend fun isAbleToRun(param: String): Boolean {
        return verifyAllCompleted(
            TransferEvent.id()
        )
    }

    override fun runLongTask(from: Wallet?, param: String): Flow<Wallet> {
        val token = ReduceTokenTaskParams.fromParamString(param).token
        val fromWallet = from ?: Wallet(token = token, owner = Address.ZERO())

        return erc20TransferHistoryRepository
            .findOwnerLogEvents(token = token, owner = null, from = fromWallet)
            .windowUntilChanged { BalanceId(token = it.history.token, owner = it.history.owner) }
            .concatMap { flow ->
                flow.switchOnFirst { element, balanceFlow ->
                    val balanceId = element.get()?.let { BalanceId(token = it.history.token, owner = it.history.owner) }
                    if (balanceId != null) {
                        balanceFlow
                            .thenMany(reduceService.update(key = balanceId, minMark = Long.MIN_VALUE))
                            .then(Mono.just(Wallet(token = balanceId.token, owner = balanceId.owner)))
                    } else {
                        Mono.empty<Wallet>()
                    }
                }
            }
            .asFlow()
    }

    private suspend fun verifyAllCompleted(vararg topics: Word): Boolean {
        for (topic in topics) {
            val task = findTask(topic)
            if (task?.lastStatus != TaskStatus.COMPLETED) {
                return false
            }
        }
        return true
    }

    private suspend fun findTask(topic: Word): Task? {
        return taskRepository.findByTypeAndParam(ReindexTopicTaskHandler.TOPIC, topic.toString()).awaitFirstOrNull()
    }
}
