package co.nilin.opex.api.ports.binance.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Table("stop_orders")
data class StopOrderModel(
    @Id val id: Long? = null,
    val uuid: String,
    val ouid: String,
    val symbol: String,
    val side: String,
    val quantity: BigDecimal,
    val price: BigDecimal,
    val stopPrice: BigDecimal,
    val timeInForce: String = "GTC",
    var status: String = "WAITING",
    val createDate: LocalDateTime = LocalDateTime.now()
)
