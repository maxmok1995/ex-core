package co.nilin.opex.api.ports.binance.dao

import co.nilin.opex.api.ports.binance.model.StopOrderModel
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Repository
interface StopOrderRepository : ReactiveCrudRepository<StopOrderModel, Long> {
    fun findAllByUuidAndStatus(uuid: String, status: String): Flux<StopOrderModel>
    fun findAllByStatus(status: String): Flux<StopOrderModel>
    fun findByOuid(ouid: String): Mono<StopOrderModel>
}
