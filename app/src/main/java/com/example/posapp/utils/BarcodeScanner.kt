package com.example.posapp.utils

import android.app.Activity
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

object BarcodeScanBus {
    private val channel = Channel<String>(Channel.BUFFERED)
    val scans = channel.receiveAsFlow()

    fun publish(value: String) {
        channel.trySend(value.trim())
    }
}

object BarcodeDraftStore {
    private var pending: String? = null

    fun set(value: String) {
        pending = value
    }

    fun take(): String = pending.orEmpty().also { pending = null }
}

class SpaceSaleBarcodeScanner(activity: Activity) {
    private val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E,
            Barcode.FORMAT_CODE_128
        )
        .enableAutoZoom()
        .build()
    private val scanner = GmsBarcodeScanning.getClient(activity, options)

    fun start(onResult: (String) -> Unit, onError: (String) -> Unit) {
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val value = barcode.rawValue?.trim().orEmpty()
                if (value.isBlank()) onError("No pudimos leer el codigo") else onResult(value)
            }
            .addOnFailureListener { onError("No se pudo abrir el escaner") }
    }
}
