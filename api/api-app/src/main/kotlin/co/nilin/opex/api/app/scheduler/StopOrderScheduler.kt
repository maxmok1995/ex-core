package co.nilin.opex.api.app.scheduler

import co.nilin.opex.api.core.inout.MatchConstraint
import co.nilin.opex.api.core.inout.MatchingOrderType
import co.nilin.opex.api.core.inout.OrderDirection
import co.nilin.opex.api.core.spi.MarketDataProxy
import co.nilin.opex.api.core.spi.MatchingGatewayProxy
import co.nilin.opex.api.ports.binance.dao.StopOrderRepository
import co.nilin.opex.common.utils.Interval
import kotlinx.coroutines.reactive.collect
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class StopOrderScheduler(
    private val stopOrderRepository: StopOrderRepository,
    private val marketDataProxy: MarketDataProxy,
    private val matchingGatewayProxy: MatchingGatewayProxy
) {
    private val logger = LoggerFactory.getLogger(StopOrderScheduler::class.java)

    @Scheduled(fixedDelay = 10000)
    fun checkStopOrders() {
        runBlocking {
            try {
                stopOrderRepository.findAllByStatus("WAITING").collect { order ->
                    try {
                        val prices = marketDataProxy.lastPrice(order.symbol)
                        val lastPrice = prices.firstOrNull()?.price?.let { BigDecimal(it) } ?: return@collect

                        val triggered = when (order.side) {
                            "BUY" -> lastPrice >= order.stopPrice
                            "SELL" -> lastPrice <= order.stopPrice
                            else -> false
                        }

                        if (triggered) {
                            logger.info("[StopOrder] Triggering ${order.ouid} symbol=${order.symbol} side=${order.side} stopPrice=${order.stopPrice} lastPrice=$lastPrice")
                            matchingGatewayProxy.createNewOrder(
                                uuid = order.uuid,
                                pair = order.symbol,
                                price = order.price,
                                quantity = order.quantity,
                                direction = if (order.side == "BUY") OrderDirection.BID else OrderDirection.ASK,
                                matchConstraint = when (order.timeInForce) {
                                    "IOC" -> MatchConstraint.IOC
                                    "FOK" -> MatchConstraint.FOK
                                    else -> MatchConstraint.GTC
                                },
                                orderType = MatchingOrderType.LIMIT_ORDER,
                                userLevel = "*",
                                token = null
                            )
                            order.status = "TRIGGERED"
                            stopOrderRepository.save(order).subscribe()
                        }
                    } catch (e: Exception) {
                        logger.error("[StopOrder] Error processing ${order.ouid}: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                logger.error("[StopOrder] Scheduler error: ${e.message}")
            }
        }
    }
}
