package co.nilin.opex.accountant.app.controller

import co.nilin.opex.accountant.core.model.WalletType
import co.nilin.opex.accountant.core.spi.FinancialActionLoader
import co.nilin.opex.accountant.core.spi.WalletProxy
import co.nilin.opex.accountant.ports.walletproxy.data.BooleanResponse
import co.nilin.opex.matching.engine.core.eventh.events.SubmitOrderEvent
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

@RestController
class AccountantController(
    val walletProxy: WalletProxy,
    val financialActionLoader: FinancialActionLoader
) {

    private val logger = LoggerFactory.getLogger(AccountantController::class.java)

    @GetMapping("{uuid}/create_order/{amount}_{currency}/allowed")
    suspend fun canCreateOrder(
        @PathVariable("uuid") uuid: String,
        @PathVariable("currency") currency: String,
        @PathVariable("amount") amount: BigDecimal
    ): BooleanResponse {
        val committedSum = runCatching {
            financialActionLoader.sumUnprocessed(uuid, currency, SubmitOrderEvent::class.simpleName!!)
        }.onFailure { logger.error(it.message) }.getOrElse { BigDecimal.ZERO }

        val totalNeeded = amount.add(committedSum)
        val canFulfil = runCatching { walletProxy.canFulfil(currency, WalletType.MAIN, uuid, totalNeeded) }
            .onFailure { logger.error(it.message) }
            .getOrElse { false }
        return BooleanResponse(canFulfil)
    }
}
