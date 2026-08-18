package com.example.posapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.posapp.data.dao.ClienteDao
import com.example.posapp.data.dao.JobDao
import com.example.posapp.data.dao.ProductoDao
import com.example.posapp.data.dao.QrTokenDao
import com.example.posapp.data.dao.SyncDao
import com.example.posapp.data.dao.StockMovementDao
import com.example.posapp.data.dao.VentaDao
import com.example.posapp.data.entities.BusinessSettings
import com.example.posapp.data.entities.Cliente
import com.example.posapp.data.entities.DetalleVenta
import com.example.posapp.data.entities.Job
import com.example.posapp.data.entities.JobItem
import com.example.posapp.data.entities.Producto
import com.example.posapp.data.entities.PagoFiado
import com.example.posapp.data.entities.QrBatch
import com.example.posapp.data.entities.QrToken
import com.example.posapp.data.entities.SyncMetadata
import com.example.posapp.data.entities.SyncQueueItem
import com.example.posapp.data.entities.StockMovement
import com.example.posapp.data.entities.Venta

@Database(
    entities = [
        Producto::class,
        Cliente::class,
        Venta::class,
        DetalleVenta::class,
        PagoFiado::class,
        SyncQueueItem::class,
        SyncMetadata::class,
        BusinessSettings::class,
        StockMovement::class,
        Job::class,
        JobItem::class,
        QrToken::class,
        QrBatch::class
    ],
    version = 13,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productoDao(): ProductoDao
    abstract fun clienteDao(): ClienteDao
    abstract fun ventaDao(): VentaDao
    abstract fun syncDao(): SyncDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun jobDao(): JobDao
    abstract fun qrTokenDao(): QrTokenDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pos_database"
                ).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13
                ).build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cliente ADD COLUMN nota TEXT NOT NULL DEFAULT ''")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pago_fiado (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        ventaId INTEGER NOT NULL,
                        detalleId INTEGER NOT NULL,
                        monto REAL NOT NULL,
                        fecha_hora INTEGER NOT NULL,
                        FOREIGN KEY(ventaId) REFERENCES venta(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(detalleId) REFERENCES detalle_venta(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pago_fiado_ventaId ON pago_fiado(ventaId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pago_fiado_detalleId ON pago_fiado(detalleId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_venta_clienteId ON venta(clienteId)")
            }
        }

        /**
         * Conserva los IDs existentes de Room y añade un UUID por fila para la nube.
         * No se borra ni se recrea la base de datos del negocio.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE producto ADD COLUMN sync_id TEXT")
                db.execSQL("ALTER TABLE cliente ADD COLUMN sync_id TEXT")
                db.execSQL("ALTER TABLE venta ADD COLUMN sync_id TEXT")
                db.execSQL("ALTER TABLE detalle_venta ADD COLUMN sync_id TEXT")
                db.execSQL("ALTER TABLE pago_fiado ADD COLUMN sync_id TEXT")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_producto_sync_id ON producto(sync_id)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_cliente_sync_id ON cliente(sync_id)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_venta_sync_id ON venta(sync_id)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_detalle_venta_sync_id ON detalle_venta(sync_id)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pago_fiado_sync_id ON pago_fiado(sync_id)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE venta ADD COLUMN nube_sincronizada INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE detalle_venta ADD COLUMN nube_sincronizada INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE pago_fiado ADD COLUMN nube_sincronizada INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Fase 1 offline-first: añade dinero exacto, pertenencia al negocio,
         * metadatos de sincronización y una cola persistente. Las columnas REAL
         * antiguas permanecen temporalmente para restaurar respaldos anteriores.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addProductSyncColumns(db)
                addCustomerSyncColumns(db)
                addSaleSyncColumns(db)
                addSaleItemSyncColumns(db)
                addCreditPaymentSyncColumns(db)
                createSyncTables(db)
                migrateLegacyMoney(db)
                populateMissingSyncIds(db)
            }

            private fun addProductSyncColumns(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE producto ADD COLUMN precio_costo_centavos INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE producto ADD COLUMN precio_venta_centavos INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE producto ADD COLUMN codigo_barras TEXT")
                db.execSQL("ALTER TABLE producto ADD COLUMN stock_minimo INTEGER NOT NULL DEFAULT 5")
                db.execSQL("ALTER TABLE producto ADD COLUMN business_id TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE producto ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE producto ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE producto ADD COLUMN deleted_at INTEGER")
                db.execSQL("ALTER TABLE producto ADD COLUMN remote_updated_at INTEGER")
                db.execSQL("ALTER TABLE producto ADD COLUMN sync_status TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_producto_business_id_updated_at ON producto(business_id, updated_at)")
            }

            private fun addCustomerSyncColumns(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cliente ADD COLUMN deuda_total_centavos INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE cliente ADD COLUMN business_id TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE cliente ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE cliente ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE cliente ADD COLUMN deleted_at INTEGER")
                db.execSQL("ALTER TABLE cliente ADD COLUMN remote_updated_at INTEGER")
                db.execSQL("ALTER TABLE cliente ADD COLUMN sync_status TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cliente_business_id_updated_at ON cliente(business_id, updated_at)")
            }

            private fun addSaleSyncColumns(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE venta ADD COLUMN total_centavos INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE venta ADD COLUMN business_id TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE venta ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE venta ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE venta ADD COLUMN remote_updated_at INTEGER")
                db.execSQL("ALTER TABLE venta ADD COLUMN sync_status TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_venta_business_id_fecha_hora ON venta(business_id, fecha_hora)")
            }

            private fun addSaleItemSyncColumns(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE detalle_venta ADD COLUMN precio_unitario_centavos INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE detalle_venta ADD COLUMN costo_unitario_centavos INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE detalle_venta ADD COLUMN business_id TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE detalle_venta ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE detalle_venta ADD COLUMN remote_updated_at INTEGER")
                db.execSQL("ALTER TABLE detalle_venta ADD COLUMN sync_status TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_detalle_venta_business_id_created_at ON detalle_venta(business_id, created_at)")
            }

            private fun addCreditPaymentSyncColumns(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE pago_fiado_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        ventaId INTEGER NOT NULL,
                        detalleId INTEGER,
                        monto REAL NOT NULL,
                        fecha_hora INTEGER NOT NULL,
                        sync_id TEXT,
                        nube_sincronizada INTEGER NOT NULL,
                        monto_centavos INTEGER NOT NULL DEFAULT 0,
                        metodo_pago TEXT NOT NULL DEFAULT 'EFECTIVO',
                        nota TEXT NOT NULL DEFAULT '',
                        business_id TEXT NOT NULL DEFAULT '',
                        created_at INTEGER NOT NULL DEFAULT 0,
                        remote_updated_at INTEGER,
                        sync_status TEXT NOT NULL DEFAULT 'PENDING',
                        FOREIGN KEY(ventaId) REFERENCES venta(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(detalleId) REFERENCES detalle_venta(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO pago_fiado_new (
                        id, ventaId, detalleId, monto, fecha_hora, sync_id,
                        nube_sincronizada, monto_centavos, created_at
                    )
                    SELECT id, ventaId, detalleId, monto, fecha_hora, sync_id,
                           nube_sincronizada, CAST(ROUND(monto * 100.0) AS INTEGER), fecha_hora
                    FROM pago_fiado
                """.trimIndent())
                db.execSQL("DROP TABLE pago_fiado")
                db.execSQL("ALTER TABLE pago_fiado_new RENAME TO pago_fiado")
                db.execSQL("CREATE INDEX index_pago_fiado_ventaId ON pago_fiado(ventaId)")
                db.execSQL("CREATE INDEX index_pago_fiado_detalleId ON pago_fiado(detalleId)")
                db.execSQL("CREATE UNIQUE INDEX index_pago_fiado_sync_id ON pago_fiado(sync_id)")
                db.execSQL("CREATE INDEX index_pago_fiado_business_id_created_at ON pago_fiado(business_id, created_at)")
            }

            private fun createSyncTables(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sync_queue (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        operation_id TEXT NOT NULL,
                        business_id TEXT NOT NULL,
                        entity_type TEXT NOT NULL,
                        entity_sync_id TEXT NOT NULL,
                        operation TEXT NOT NULL,
                        payload_json TEXT NOT NULL,
                        attempt_count INTEGER NOT NULL DEFAULT 0,
                        next_attempt_at INTEGER NOT NULL DEFAULT 0,
                        last_error TEXT,
                        created_at INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sync_queue_operation_id ON sync_queue(operation_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_queue_business_id_next_attempt_at ON sync_queue(business_id, next_attempt_at)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_queue_entity_type_entity_sync_id ON sync_queue(entity_type, entity_sync_id)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sync_metadata (
                        business_id TEXT NOT NULL,
                        entity_type TEXT NOT NULL,
                        last_pulled_at INTEGER NOT NULL DEFAULT 0,
                        last_success_at INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(business_id, entity_type)
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS business_settings (
                        business_id TEXT NOT NULL PRIMARY KEY,
                        currency TEXT NOT NULL DEFAULT 'PEN',
                        daily_goal_cents INTEGER NOT NULL DEFAULT 50000,
                        low_stock_enabled INTEGER NOT NULL DEFAULT 1,
                        receipt_message TEXT NOT NULL DEFAULT '',
                        updated_at INTEGER NOT NULL DEFAULT 0,
                        sync_status TEXT NOT NULL DEFAULT 'PENDING'
                    )
                """.trimIndent())
            }

            private fun migrateLegacyMoney(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE producto SET precio_costo_centavos = CAST(ROUND(precio_costo * 100.0) AS INTEGER), precio_venta_centavos = CAST(ROUND(precio_venta * 100.0) AS INTEGER)")
                db.execSQL("UPDATE cliente SET deuda_total_centavos = CAST(ROUND(IFNULL(deuda_total, 0) * 100.0) AS INTEGER)")
                db.execSQL("UPDATE venta SET total_centavos = CAST(ROUND(total * 100.0) AS INTEGER)")
                db.execSQL("UPDATE detalle_venta SET precio_unitario_centavos = CAST(ROUND(precio_unitario_historico * 100.0) AS INTEGER)")
            }

            private fun populateMissingSyncIds(db: SupportSQLiteDatabase) {
                val uuidSql = "lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)),2) || '-' || substr('89ab',abs(random()) % 4 + 1,1) || substr(hex(randomblob(2)),2) || '-' || hex(randomblob(6)))"
                listOf("producto", "cliente", "venta", "detalle_venta", "pago_fiado").forEach { table ->
                    db.execSQL("UPDATE $table SET sync_id = $uuidSql WHERE sync_id IS NULL OR sync_id = ''")
                }
            }
        }

        /** Separa la ruta privada remota de la ruta de imagen/cache del telefono. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE producto ADD COLUMN storage_path TEXT")
            }
        }

        /** Marca imagenes locales existentes para subirlas sin perder el archivo offline. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE producto ADD COLUMN image_sync_status TEXT NOT NULL DEFAULT 'SYNCED'")
                db.execSQL("UPDATE producto SET image_sync_status = 'PENDING' WHERE ruta_imagen IS NOT NULL AND ruta_imagen != '' AND storage_path IS NULL")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS stock_movement (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        productId INTEGER NOT NULL,
                        saleId INTEGER,
                        type TEXT NOT NULL,
                        quantity_delta INTEGER NOT NULL,
                        notes TEXT NOT NULL,
                        sync_id TEXT NOT NULL,
                        business_id TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        remote_created_at INTEGER,
                        sync_status TEXT NOT NULL DEFAULT 'PENDING',
                        FOREIGN KEY(productId) REFERENCES producto(id) ON UPDATE NO ACTION ON DELETE NO ACTION,
                        FOREIGN KEY(saleId) REFERENCES venta(id) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_movement_productId ON stock_movement(productId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_movement_saleId ON stock_movement(saleId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_stock_movement_sync_id ON stock_movement(sync_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_movement_business_id_created_at ON stock_movement(business_id, created_at)")
            }
        }

        /** Aísla también la identidad comercial del código de barras por negocio. */
        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE producto SET codigo_barras = NULL WHERE codigo_barras IS NOT NULL AND trim(codigo_barras) = ''")
                db.execSQL("""
                    UPDATE producto
                    SET codigo_barras = NULL,
                        updated_at = CAST(strftime('%s','now') AS INTEGER) * 1000,
                        sync_status = 'PENDING'
                    WHERE codigo_barras IS NOT NULL
                      AND id NOT IN (
                          SELECT MIN(id)
                          FROM producto
                          WHERE codigo_barras IS NOT NULL
                          GROUP BY business_id, codigo_barras
                      )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_producto_business_id_codigo_barras ON producto(business_id, codigo_barras)")
            }
        }

        /**
         * Sustituye el cursor milisegundo por el par exacto (timestamp remoto, UUID).
         * Se reinicia el punto de lectura para hacer un pull completo y seguro: una
         * repeticion es preferible a omitir filas que compartian timestamp.
         */
        internal val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sync_metadata_new (
                        business_id TEXT NOT NULL,
                        entity_type TEXT NOT NULL,
                        last_server_timestamp TEXT NOT NULL DEFAULT '1970-01-01T00:00:00Z',
                        last_remote_id TEXT NOT NULL DEFAULT '',
                        last_success_at INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(business_id, entity_type)
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO sync_metadata_new (
                        business_id, entity_type, last_server_timestamp,
                        last_remote_id, last_success_at
                    )
                    SELECT business_id, entity_type, '1970-01-01T00:00:00Z', '', last_success_at
                    FROM sync_metadata
                """.trimIndent())
                db.execSQL("DROP TABLE sync_metadata")
                db.execSQL("ALTER TABLE sync_metadata_new RENAME TO sync_metadata")
            }
        }

        /** Conserva lineas historicas aunque el producto ya no exista localmente. */
        internal val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS detalle_venta_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        ventaId INTEGER NOT NULL,
                        productoId INTEGER,
                        product_sync_id_snapshot TEXT,
                        product_name_snapshot TEXT NOT NULL DEFAULT 'Producto',
                        cantidad INTEGER NOT NULL,
                        precio_unitario_historico REAL NOT NULL,
                        sync_id TEXT,
                        nube_sincronizada INTEGER NOT NULL,
                        precio_unitario_centavos INTEGER NOT NULL DEFAULT 0,
                        costo_unitario_centavos INTEGER NOT NULL DEFAULT 0,
                        business_id TEXT NOT NULL DEFAULT '',
                        created_at INTEGER NOT NULL DEFAULT 0,
                        remote_updated_at INTEGER,
                        sync_status TEXT NOT NULL DEFAULT 'PENDING',
                        FOREIGN KEY(ventaId) REFERENCES venta(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(productoId) REFERENCES producto(id) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO detalle_venta_new (
                        id, ventaId, productoId, product_sync_id_snapshot,
                        product_name_snapshot, cantidad, precio_unitario_historico,
                        sync_id, nube_sincronizada, precio_unitario_centavos,
                        costo_unitario_centavos, business_id, created_at,
                        remote_updated_at, sync_status
                    )
                    SELECT detail.id, detail.ventaId, detail.productoId,
                           product.sync_id,
                           COALESCE(NULLIF(trim(product.nombre), ''), 'Producto'),
                           detail.cantidad, detail.precio_unitario_historico,
                           detail.sync_id, detail.nube_sincronizada,
                           detail.precio_unitario_centavos,
                           detail.costo_unitario_centavos, detail.business_id,
                           detail.created_at, detail.remote_updated_at,
                           detail.sync_status
                    FROM detalle_venta detail
                    LEFT JOIN producto product ON product.id = detail.productoId
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pago_fiado_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        ventaId INTEGER NOT NULL,
                        detalleId INTEGER,
                        monto REAL NOT NULL,
                        fecha_hora INTEGER NOT NULL,
                        sync_id TEXT,
                        nube_sincronizada INTEGER NOT NULL,
                        monto_centavos INTEGER NOT NULL DEFAULT 0,
                        metodo_pago TEXT NOT NULL DEFAULT 'EFECTIVO',
                        nota TEXT NOT NULL DEFAULT '',
                        business_id TEXT NOT NULL DEFAULT '',
                        created_at INTEGER NOT NULL DEFAULT 0,
                        remote_updated_at INTEGER,
                        sync_status TEXT NOT NULL DEFAULT 'PENDING',
                        FOREIGN KEY(ventaId) REFERENCES venta(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(detalleId) REFERENCES detalle_venta_new(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO pago_fiado_new (
                        id, ventaId, detalleId, monto, fecha_hora, sync_id,
                        nube_sincronizada, monto_centavos, metodo_pago, nota,
                        business_id, created_at, remote_updated_at, sync_status
                    )
                    SELECT id, ventaId, detalleId, monto, fecha_hora, sync_id,
                           nube_sincronizada, monto_centavos, metodo_pago, nota,
                           business_id, created_at, remote_updated_at, sync_status
                    FROM pago_fiado
                """.trimIndent())
                db.execSQL("DROP TABLE pago_fiado")
                db.execSQL("DROP TABLE detalle_venta")
                db.execSQL("ALTER TABLE detalle_venta_new RENAME TO detalle_venta")
                db.execSQL("ALTER TABLE pago_fiado_new RENAME TO pago_fiado")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_detalle_venta_ventaId ON detalle_venta(ventaId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_detalle_venta_productoId ON detalle_venta(productoId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_detalle_venta_sync_id ON detalle_venta(sync_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_detalle_venta_business_id_created_at ON detalle_venta(business_id, created_at)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pago_fiado_ventaId ON pago_fiado(ventaId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pago_fiado_detalleId ON pago_fiado(detalleId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pago_fiado_sync_id ON pago_fiado(sync_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pago_fiado_business_id_created_at ON pago_fiado(business_id, created_at)")
            }
        }

        /** Convierte las fotos en una cola recuperable independiente del producto. */
        internal val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    UPDATE producto
                    SET image_sync_status = CASE
                        WHEN ruta_imagen IS NULL OR trim(ruta_imagen) = '' THEN
                            CASE WHEN storage_path IS NULL OR trim(storage_path) = '' THEN 'NONE' ELSE 'DOWNLOAD_PENDING' END
                        WHEN storage_path IS NULL OR trim(storage_path) = '' THEN 'LOCAL_PENDING'
                        WHEN image_sync_status = 'ERROR' THEN 'ERROR_RETRYABLE'
                        ELSE 'SYNCED'
                    END
                """.trimIndent())
            }
        }

        /** Integra soporte de Trabajos, Servicios y Códigos QR Físicos. */
        internal val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE venta ADD COLUMN job_id TEXT")
                db.execSQL("ALTER TABLE detalle_venta ADD COLUMN job_item_id TEXT")
                db.execSQL("ALTER TABLE detalle_venta ADD COLUMN item_type TEXT NOT NULL DEFAULT 'PRODUCT'")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS job (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        business_id TEXT NOT NULL DEFAULT '',
                        sync_id TEXT,
                        clienteId INTEGER,
                        customer_sync_id TEXT,
                        customer_name_snapshot TEXT NOT NULL,
                        customer_phone_snapshot TEXT,
                        description TEXT,
                        status TEXT NOT NULL DEFAULT 'received',
                        total_cents INTEGER NOT NULL DEFAULT 0,
                        notes TEXT NOT NULL DEFAULT '',
                        ventaId INTEGER,
                        sale_id TEXT,
                        ready_at INTEGER,
                        delivered_at INTEGER,
                        created_by TEXT,
                        created_at INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL DEFAULT 0,
                        deleted_at INTEGER,
                        remote_updated_at INTEGER,
                        sync_status TEXT NOT NULL DEFAULT 'PENDING',
                        FOREIGN KEY(clienteId) REFERENCES cliente(id) ON UPDATE NO ACTION ON DELETE SET NULL,
                        FOREIGN KEY(ventaId) REFERENCES venta(id) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_job_clienteId ON job(clienteId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_job_ventaId ON job(ventaId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_job_sync_id ON job(sync_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_job_business_id_status_created_at ON job(business_id, status, created_at)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_job_business_id_updated_at ON job(business_id, updated_at)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS job_item (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        business_id TEXT NOT NULL DEFAULT '',
                        sync_id TEXT,
                        jobId INTEGER NOT NULL,
                        job_sync_id TEXT,
                        service_type TEXT NOT NULL DEFAULT 'GENERAL',
                        description TEXT NOT NULL,
                        paper_size TEXT,
                        color_mode TEXT,
                        side_mode TEXT,
                        quantity INTEGER NOT NULL DEFAULT 1,
                        pages INTEGER NOT NULL DEFAULT 1,
                        copies INTEGER NOT NULL DEFAULT 1,
                        unit_price_cents INTEGER NOT NULL DEFAULT 0,
                        subtotal_cents INTEGER NOT NULL DEFAULT 0,
                        notes TEXT NOT NULL DEFAULT '',
                        created_at INTEGER NOT NULL DEFAULT 0,
                        remote_updated_at INTEGER,
                        sync_status TEXT NOT NULL DEFAULT 'PENDING',
                        FOREIGN KEY(jobId) REFERENCES job(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_job_item_jobId ON job_item(jobId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_job_item_sync_id ON job_item(sync_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_job_item_business_id_created_at ON job_item(business_id, created_at)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS qr_token (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        business_id TEXT NOT NULL DEFAULT '',
                        sync_id TEXT,
                        token TEXT NOT NULL,
                        status TEXT NOT NULL DEFAULT 'unused',
                        jobId INTEGER,
                        job_sync_id TEXT,
                        batch_id TEXT,
                        assigned_at INTEGER,
                        released_at INTEGER,
                        created_at INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL DEFAULT 0,
                        remote_updated_at INTEGER,
                        sync_status TEXT NOT NULL DEFAULT 'PENDING',
                        FOREIGN KEY(jobId) REFERENCES job(id) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_qr_token_token ON qr_token(token)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_qr_token_sync_id ON qr_token(sync_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_qr_token_jobId ON qr_token(jobId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_qr_token_business_id_status ON qr_token(business_id, status)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS qr_batch (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        business_id TEXT NOT NULL DEFAULT '',
                        sync_id TEXT,
                        quantity INTEGER NOT NULL DEFAULT 0,
                        created_by TEXT,
                        created_at INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_qr_batch_sync_id ON qr_batch(sync_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_qr_batch_business_id_created_at ON qr_batch(business_id, created_at)")
            }
        }
    }
}
