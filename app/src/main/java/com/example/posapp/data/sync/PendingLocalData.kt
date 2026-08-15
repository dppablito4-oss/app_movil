package com.example.posapp.data.sync

/** Resumen completo de información local que todavía no está confirmada en la nube. */
data class PendingLocalData(
    val queuedOperations: Int = 0,
    val products: Int = 0,
    val customers: Int = 0,
    val sales: Int = 0,
    val saleItems: Int = 0,
    val payments: Int = 0,
    val stockMovements: Int = 0,
    val images: Int = 0,
    val businessSettings: Int = 0
) {
    val total: Int
        get() = listOf(
            queuedOperations,
            products,
            customers,
            sales,
            saleItems,
            payments,
            stockMovements,
            images,
            businessSettings
        ).sumOf { it.coerceAtLeast(0) }

    val canSignOutSafely: Boolean get() = total == 0

    fun userSummary(): String = buildList {
        addCount(products, "producto", "productos")
        addCount(customers, "cliente", "clientes")
        addCount(sales, "venta", "ventas")
        addCount(saleItems, "detalle de venta", "detalles de venta")
        addCount(payments, "pago", "pagos")
        addCount(stockMovements, "movimiento de stock", "movimientos de stock")
        addCount(images, "imagen", "imágenes")
        addCount(businessSettings, "configuración", "configuraciones")
        if (isEmpty() && queuedOperations > 0) add("$queuedOperations operaciones en cola")
    }.joinToString(", ")

    private fun MutableList<String>.addCount(count: Int, singular: String, plural: String) {
        if (count > 0) add("$count ${if (count == 1) singular else plural}")
    }
}
