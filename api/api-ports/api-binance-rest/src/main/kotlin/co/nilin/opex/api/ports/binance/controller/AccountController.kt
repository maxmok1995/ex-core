package co.nilin.opex.api.ports.binance.controller

import co.nilin.opex.api.core.inout.*
import co.nilin.opex.api.core.spi.MarketUserDataProxy
import co.nilin.opex.api.core.spi.MatchingGatewayProxy
import co.nilin.opex.api.core.spi.SymbolMapper
import co.nilin.opex.api.core.spi.WalletProxy
import co.nilin.opex.api.ports.binance.dao.StopOrderRepository
import co.nilin.opex.api.ports.binance.data.*
import co.nilin.opex.api.ports.binance.model.StopOrderModel
import co.nilin.opex.api.ports.binance.util.*
import co.nilin.opex.common.OpexError
import io.swagger.annotations.ApiParam
import io.swagger.annotations.ApiResponse
import io.swagger.annotations.Example
import io.swagger.annotations.ExampleProperty
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.collect
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.CurrentSecurityContext
import org.springframework.security.core.context.SecurityContext
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.security.Principal
import java.time.ZoneId
import java.util.*

@RestController
class AccountController(
    val queryHandler: MarketUserDataProxy,
    val matchingGatewayProxy: MatchingGatewayProxy,
    val walletProxy: WalletProxy,
    val symbolMapper: SymbolMapper,
    val stopOrderRepository: StopOrderRepository
) {

    @PostMapping(
        "/v3/order",
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ApiResponse(
        message = "OK", code = 200,
        examples = Example(ExampleProperty(value = "{ \"symbol\": \"btc_usdt\" }", mediaType = "application/json"))
    )
    suspend fun createNewOrder(
        @RequestParam symbol: String,
        @RequestParam side: OrderSide,
        @RequestParam type: OrderType,
        @RequestParam(required = false) timeInForce: TimeInForce?,
        @RequestParam(required = false) quantity: BigDecimal?,
        @RequestParam(required = false) quoteOrderQty: BigDecimal?,
        @RequestParam(required = false) price: BigDecimal?,
        @RequestParam(required = false) newClientOrderId: String?,
        @ApiParam(value = "Used with STOP_LOSS, STOP_LOSS_LIMIT, TAKE_PROFIT, and TAKE_PROFIT_LIMIT orders.")
        @RequestParam(required = false) stopPrice: BigDecimal?,
        @RequestParam(required = false) icebergQty: BigDecimal?,
        @RequestParam(required = false) newOrderRespType: OrderResponseType?,
        @ApiParam(value = "The value cannot be greater than 60000")
        @RequestParam(required = false) recvWindow: Long?,
        @RequestParam timestamp: Long,
        @CurrentSecurityContext securityContext: SecurityContext
    ): NewOrderResponse {
        val internalSymbol = symbolMapper.toInternalSymbol(symbol) ?: throw OpexError.SymbolNotFound.exception()
        validateNewOrderParams(type, price, quantity, timeInForce, stopPrice, quoteOrderQty)

        if (type == OrderType.STOP_LOSS_LIMIT) {
            val ouid = UUID.randomUUID().toString()
            val stopOrder = StopOrderModel(
                uuid = securityContext.jwtAuthentication().name,
                ouid = ouid,
                symbol = internalSymbol,
                side = side.name,
                quantity = quantity!!,
                price = price!!,
                stopPrice = stopPrice!!,
                timeInForce = timeInForce?.name ?: "GTC",
                status = "WAITING"
            )
            stopOrderRepository.save(stopOrder).awaitFirst()
            return NewOrderResponse(symbol, -1, -1, null, Date(), null, null, null, null, null, null, null, null, null)
        }

        matchingGatewayProxy.createNewOrder(
            securityContext.jwtAuthentication().name,
            internalSymbol,
            price ?: BigDecimal.ZERO,
            quantity ?: BigDecimal.ZERO,
            side.asOrderDirection(),
            timeInForce?.asMatchConstraint(),
            type.asMatchingOrderType(),
            "*",
            securityContext.jwtAuthentication().tokenValue()
        )
        return NewOrderResponse(symbol, -1, -1, null, Date(), null, null, null, null, null, null, null, null, null)
    }

    @DeleteMapping(
        "/v3/order",
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    suspend fun cancelOrder(
        principal: Principal,
        @RequestParam symbol: String,
        @RequestParam(required = false) orderId: Long?,
        @RequestParam(required = false) origClientOrderId: String?,
        @RequestParam(required = false) newClientOrderId: String?,
        @ApiParam(value = "The value cannot be greater than 60000")
        @RequestParam(required = false) recvWindow: Long?,
        @RequestParam timestamp: Long,
        @CurrentSecurityContext securityContext: SecurityContext
    ): CancelOrderResponse {
        val localSymbol = symbolMapper.toInternalSymbol(symbol) ?: throw OpexError.SymbolNotFound.exception()
        if (orderId == null && origClientOrderId == null)
            throw OpexError.BadRequest.exception("'orderId' or 'origClientOrderId' must be sent")

        val order = queryHandler.queryOrder(principal, localSymbol, orderId, origClientOrderId)
            ?: throw OpexError.OrderNotFound.exception()

        val response = CancelOrderResponse(
            symbol, origClientOrderId, orderId, -1, null,
            order.price, order.quantity, order.executedQuantity, order.accumulativeQuoteQty,
            OrderStatus.CANCELED, order.constraint.asTimeInForce(), order.type.asOrderType(), order.direction.asOrderSide()
        )

        if (order.status == OrderStatus.CANCELED) return response
        if (order.status.equalsAny(OrderStatus.REJECTED, OrderStatus.EXPIRED, OrderStatus.FILLED))
            throw OpexError.CancelOrderNotAllowed.exception()

        matchingGatewayProxy.cancelOrder(
            order.ouid, principal.name, order.orderId ?: 0, localSymbol,
            securityContext.jwtAuthentication().tokenValue()
        )
        return response
    }

    @DeleteMapping(
        "/v3/stopOrder",
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    suspend fun cancelStopOrder(
        @RequestParam stopOrderId: String,
        @RequestParam timestamp: Long,
        @CurrentSecurityContext securityContext: SecurityContext
    ): Map<String, String> {
        val uuid = securityContext.jwtAuthentication().name
        val order = stopOrderRepository.findByOuid(stopOrderId).awaitFirstOrNull()
            ?: throw OpexError.OrderNotFound.exception()
        if (order.uuid != uuid) throw OpexError.Forbidden.exception()
        if (order.status != "WAITING") throw OpexError.CancelOrderNotAllowed.exception()
        order.status = "CANCELED"
        stopOrderRepository.save(order).awaitFirst()
        return mapOf("ouid" to stopOrderId, "status" to "CANCELED")
    }

    @GetMapping(
        "/v3/openStopOrders",
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    suspend fun getOpenStopOrders(
        @RequestParam timestamp: Long,
        @CurrentSecurityContext securityContext: SecurityContext
    ): List<Map<String, Any?>> {
        val uuid = securityContext.jwtAuthentication().name
        val result = mutableListOf<Map<String, Any?>>()
        stopOrderRepository.findAllByUuidAndStatus(uuid, "WAITING").collect { order ->
            val externalSymbol = symbolMapper.fromInternalSymbol(order.symbol) ?: order.symbol
            result.add(mapOf(
                "ouid" to order.ouid,
                "symbol" to externalSymbol,
                "side" to order.side,
                "quantity" to order.quantity,
                "price" to order.price,
                "stopPrice" to order.stopPrice,
                "timeInForce" to order.timeInForce,
                "status" to order.status,
                "createDate" to order.createDate.toString()
            ))
        }
        return result
    }

    @GetMapping(
        "/v3/order",
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    suspend fun queryOrder(
        principal: Principal,
        @RequestParam symbol: String,
        @RequestParam(required = false) orderId: Long?,
        @RequestParam(required = false) origClientOrderId: String?,
        @ApiParam(value = "The value cannot be greater than 60000")
        @RequestParam(required = false) recvWindow: Long?,
        @RequestParam timestamp: Long
    ): QueryOrderResponse {
        val internalSymbol = symbolMapper.toInternalSymbol(symbol) ?: throw OpexError.SymbolNotFound.exception()
        return queryHandler.queryOrder(principal, internalSymbol, orderId, origClientOrderId)
            ?.asQueryOrderResponse()
            ?.apply { this.symbol = symbol }
            ?: throw OpexError.OrderNotFound.exception()
    }

    @GetMapping(
        "/v3/openOrders",
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    suspend fun fetchOpenOrders(
        principal: Principal,
        @RequestParam(required = false) symbol: String?,
        @ApiParam(value = "The value cannot be greater than 60000")
        @RequestParam(required = false) recvWindow: Long?,
        @RequestParam timestamp: Long,
        @RequestParam(required = false) limit: Int?
    ): List<QueryOrderResponse> {
        val internalSymbol = symbolMapper.toInternalSymbol(symbol) ?: throw OpexError.SymbolNotFound.exception()
        return queryHandler.openOrders(principal, internalSymbol, limit).map {
            it.asQueryOrderResponse().apply { symbol?.let { s -> this.symbol = s } }
        }
    }

    @GetMapping(
        "/v3/allOrders",
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    suspend fun fetchAllOrders(
        principal: Principal,
        @RequestParam(required = false) symbol: String?,
        @RequestParam(required = false) startTime: Date?,
        @RequestParam(required = false) endTime: Date?,
        @ApiParam(value = "Default 500; max 1000.")
        @RequestParam(required = false) limit: Int?,
        @ApiParam(value = "The value cannot be greater than 60000")
        @RequestParam(required = false) recvWindow: Long?,
        @RequestParam timestamp: Long
    ): List<QueryOrderResponse> {
        val internalSymbol = symbolMapper.toInternalSymbol(symbol) ?: throw OpexError.SymbolNotFound.exception()
        return queryHandler.allOrders(principal, internalSymbol, startTime, endTime, limit).map {
            it.asQueryOrderResponse().apply { symbol?.let { s -> this.symbol = s } }
        }
    }

    @GetMapping(
        "/v3/myTrades",
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    suspend fun fetchAllTrades(
        principal: Principal,
        @RequestParam symbol: String?,
        @RequestParam(required = false) startTime: Date?,
        @RequestParam(required = false) endTime: Date?,
        @ApiParam(value = "TradeId to fetch from. Default gets most recent trades.")
        @RequestParam(required = false) fromId: Long?,
        @ApiParam(value = "Default 500; max 1000.")
        @RequestParam(required = false) limit: Int?,
        @ApiParam(value = "The value cannot be greater than 60000")
        @RequestParam(required = false) recvWindow: Long?,
        @RequestParam timestamp: Long
    ): List<TradeResponse> {
        val internalSymbol = symbolMapper.toInternalSymbol(symbol) ?: throw OpexError.SymbolNotFound.exception()
        return queryHandler.allTrades(principal, internalSymbol, fromId, startTime, endTime, limit).map {
            TradeResponse(
                symbol ?: "", it.id, it.orderId, -1, it.price, it.quantity, it.quoteQuantity,
                it.commission, it.commissionAsset, it.time, it.isBuyer, it.isMaker, it.isBestMatch
            )
        }
    }

    @GetMapping(
        "/v3/account",
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    suspend fun accountInfo(
        @CurrentSecurityContext securityContext: SecurityContext,
        @ApiParam(value = "The value cannot be greater than 60000")
        @RequestParam(required = false) recvWindow: Long?,
        @RequestParam timestamp: Long
    ): AccountInfoResponse {
        val auth = securityContext.jwtAuthentication()
        val wallets = walletProxy.getWallets(auth.name, auth.tokenValue())
        val limits = walletProxy.getOwnerLimits(auth.name, auth.tokenValue())
        val accountType = "SPOT"
        return AccountInfoResponse(
            0, 0, 0, 0, limits.canTrade, limits.canWithdraw, limits.canDeposit,
            Date().time, accountType,
            wallets.map { BalanceResponse(it.asset, it.balance, it.locked, it.withdraw) },
            listOf(accountType)
        )
    }

    private fun validateNewOrderParams(
        type: OrderType, price: BigDecimal?, quantity: BigDecimal?,
        timeInForce: TimeInForce?, stopPrice: BigDecimal?, quoteOrderQty: BigDecimal?
    ) {
        when (type) {
            OrderType.LIMIT -> {
                checkDecimal(price, "price"); checkDecimal(quantity, "quantity"); checkNull(timeInForce, "timeInForce")
            }
            OrderType.MARKET -> {
                if (quantity == null) checkDecimal(quoteOrderQty, "quoteOrderQty") else checkDecimal(quantity, "quantity")
            }
            OrderType.STOP_LOSS -> {
                checkDecimal(quantity, "quantity"); checkDecimal(stopPrice, "stopPrice")
            }
            OrderType.STOP_LOSS_LIMIT -> {
                checkDecimal(price, "price"); checkDecimal(quantity, "quantity")
                checkDecimal(stopPrice, "stopPrice"); checkNull(timeInForce, "timeInForce")
            }
            OrderType.TAKE_PROFIT -> {
                checkDecimal(quantity, "quantity"); checkDecimal(stopPrice, "stopPrice")
            }
            OrderType.TAKE_PROFIT_LIMIT -> {
                checkDecimal(price, "price"); checkDecimal(quantity, "quantity")
                checkDecimal(stopPrice, "stopPrice"); checkNull(timeInForce, "timeInForce")
            }
            OrderType.LIMIT_MAKER -> {
                checkDecimal(price, "price"); checkDecimal(quantity, "quantity")
            }
        }
    }

    private fun checkDecimal(decimal: BigDecimal?, paramName: String) {
        if (decimal == null || decimal <= BigDecimal.ZERO)
            throw OpexError.InvalidRequestParam.exception("Parameter '$paramName' is either missing or invalid")
    }

    private fun checkNull(obj: Any?, paramName: String) {
        if (obj == null)
            throw OpexError.InvalidRequestParam.exception("Parameter '$paramName' is either missing or invalid")
    }

    private fun Order.asQueryOrderResponse() = QueryOrderResponse(
        symbol, ouid, orderId ?: 0, -1, "", price, quantity, executedQuantity, accumulativeQuoteQty,
        status, constraint.asTimeInForce(), type.asOrderType(), direction.asOrderSide(), null, null,
        Date.from(createDate.atZone(ZoneId.systemDefault()).toInstant()),
        Date.from(updateDate.atZone(ZoneId.systemDefault()).toInstant()),
        status.isWorking(), quoteQuantity
    )
}
