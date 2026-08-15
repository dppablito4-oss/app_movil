package com.example.posapp.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.posapp.data.entities.Cliente
import com.example.posapp.data.entities.DetalleVenta
import com.example.posapp.data.entities.PagoFiado
import com.example.posapp.data.entities.Producto
import com.example.posapp.data.entities.StockMovement
import com.example.posapp.data.entities.Venta
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BusinessIsolationTest {
    private lateinit var database: AppDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun domainQueriesOnlyReturnTheRequestedBusiness() = runBlocking {
        seedBusiness("business-a", "Producto A", "Cliente A")
        seedBusiness("business-b", "Producto B", "Cliente B")

        assertEquals(listOf("Producto A"), database.productoDao().getAll("business-a").first().map { it.nombre })
        assertEquals(listOf("Cliente A"), database.clienteDao().getAll("business-a").first().map { it.nombre })
        assertEquals(1, database.ventaDao().getAllVentas("business-a").size)
        assertEquals(1, database.ventaDao().getAllDetalles("business-a").size)
        assertEquals(1, database.ventaDao().getAllPagos("business-a").size)
        assertEquals(1, database.stockMovementDao().getAllForSync("business-a").size)
    }

    @Test
    fun sameBarcodeIsAllowedInDifferentBusinesses() = runBlocking {
        database.productoDao().insert(product("business-a", "A", "775000000001"))
        database.productoDao().insert(product("business-b", "B", "775000000001"))

        assertEquals("A", database.productoDao().getByBarcode("business-a", "775000000001")?.nombre)
        assertEquals("B", database.productoDao().getByBarcode("business-b", "775000000001")?.nombre)
    }

    private suspend fun seedBusiness(businessId: String, productName: String, clientName: String) {
        val now = System.currentTimeMillis()
        val productId = database.productoDao().insert(product(businessId, productName, null))
        val clientId = database.clienteDao().insert(
            Cliente(
                nombre = clientName,
                telefono = null,
                business_id = businessId,
                sync_id = UUID.randomUUID().toString(),
                created_at = now,
                updated_at = now
            )
        )
        val saleId = database.ventaDao().insertVenta(
            Venta(
                fecha_hora = now,
                total = 10.0,
                tipo_pago = "FIADO",
                clienteId = clientId,
                total_centavos = 1_000,
                business_id = businessId,
                sync_id = UUID.randomUUID().toString(),
                created_at = now,
                updated_at = now
            )
        )
        database.ventaDao().insertDetalle(
            DetalleVenta(
                ventaId = saleId,
                productoId = productId,
                cantidad = 1,
                precio_unitario_historico = 10.0,
                precio_unitario_centavos = 1_000,
                business_id = businessId,
                sync_id = UUID.randomUUID().toString(),
                created_at = now
            )
        )
        database.ventaDao().insertPago(
            PagoFiado(
                ventaId = saleId,
                detalleId = null,
                monto = 2.0,
                monto_centavos = 200,
                fecha_hora = now,
                business_id = businessId,
                sync_id = UUID.randomUUID().toString(),
                created_at = now
            )
        )
        database.stockMovementDao().insert(
            StockMovement(
                productId = productId,
                saleId = saleId,
                type = "SALE",
                quantity_delta = -1,
                sync_id = UUID.randomUUID().toString(),
                business_id = businessId,
                created_at = now
            )
        )
    }

    private fun product(businessId: String, name: String, barcode: String?) = Producto(
        nombre = name,
        precio_costo = 5.0,
        precio_venta = 10.0,
        stock = 5,
        ruta_imagen = null,
        busqueda_normalizada = name.uppercase(),
        sync_id = UUID.randomUUID().toString(),
        precio_costo_centavos = 500,
        precio_venta_centavos = 1_000,
        codigo_barras = barcode,
        business_id = businessId,
        created_at = System.currentTimeMillis(),
        updated_at = System.currentTimeMillis()
    )
}
