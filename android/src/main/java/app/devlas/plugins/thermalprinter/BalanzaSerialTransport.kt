package app.devlas.plugins.thermalprinter

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager

/**
 * Transporte USB-serie (RS232 vía adaptador FTDI/CH340/CP210x/PL2303) para balanza en vivo.
 *
 * A diferencia de UsbTransport.kt (impresora, bulkTransfer crudo — funciona porque una
 * "impresora USB" es una clase de dispositivo simple donde los bytes que se mandan/reciben
 * SON el stream ESC/POS sin más), un adaptador USB-serie necesita hablar el protocolo propio
 * de cada chip (baudios, framing) para configurar el puerto — de ahí `usb-serial-for-android`
 * en vez de reimplementar cuatro protocolos de chip distintos a mano.
 *
 * Sin validar contra hardware real (ver docs/BALANZAS_DIGITALES_PLAN.md del repo pos, Fase 5)
 * — el permiso USB y el manejo de hotplug SÍ son el mismo código ya probado con impresoras
 * reales (mismo patrón que UsbTransport.requestPermission), lo nuevo es solo la apertura del
 * puerto serie y el loop de lectura.
 */
class BalanzaSerialTransport(private val context: Context) {

    companion object {
        private const val ACTION_USB_PERMISSION = "app.devlas.plugins.thermalprinter.BALANZA_USB_PERMISSION"
    }

    private val usbManager: UsbManager?
        get() = context.getSystemService(Context.USB_SERVICE) as? UsbManager

    private var port: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null

    fun list(): JSArray {
        val result = JSArray()
        val manager = usbManager ?: return result
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        for (driver in drivers) {
            val device = driver.device
            val obj = JSObject()
            obj.put("vendorId", device.vendorId)
            obj.put("productId", device.productId)
            obj.put("name", device.productName ?: device.deviceName)
            obj.put("hasPermission", manager.hasPermission(device))
            result.put(obj)
        }
        return result
    }

    /** Mismo flujo de permiso ya validado con impresoras reales (ver UsbTransport.kt) —
     *  PendingIntent mutable, intent explícito, receiver no exportado (Android 12/14+). */
    fun requestPermission(
        vendorId: Int?,
        productId: Int?,
        onResult: (granted: Boolean) -> Unit,
        onError: (code: String, msg: String) -> Unit,
    ) {
        val manager = usbManager ?: return onError("unavailable", "USB no disponible en este dispositivo")
        val driver = findDriver(manager, vendorId, productId)
            ?: return onError("not_found", "No se encontró un adaptador USB-serie conectado")
        val device = driver.device
        if (manager.hasPermission(device)) return onResult(true)

        val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, flags)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, received: Intent) {
                if (received.action != ACTION_USB_PERMISSION) return
                try { context.unregisterReceiver(this) } catch (_: Exception) { /* ya removido */ }
                onResult(received.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(ACTION_USB_PERMISSION), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        manager.requestPermission(device, pendingIntent)
    }

    /** Abre el puerto y arranca el loop de lectura async — `onData` se llama en background,
     *  el caller (BalanzaSerialPlugin) es responsable de despachar a notifyListeners. */
    fun connect(
        vendorId: Int?,
        productId: Int?,
        baudRate: Int,
        onData: (bytes: ByteArray) -> Unit,
        onError: (msg: String) -> Unit,
    ) {
        val manager = usbManager ?: throw PrinterException("unavailable", "USB no disponible en este dispositivo")
        val driver = findDriver(manager, vendorId, productId)
            ?: throw PrinterException("not_found", "No se encontró un adaptador USB-serie conectado")
        val device = driver.device
        if (!manager.hasPermission(device)) {
            throw PrinterException("permission_denied", "Sin permiso USB — llama a requestPermission() primero")
        }
        val connection = manager.openDevice(device)
            ?: throw PrinterException("connect_failed", "No se pudo abrir el dispositivo USB")
        val p = driver.ports.firstOrNull()
            ?: throw PrinterException("not_found", "El adaptador no expone ningún puerto serie")

        p.open(connection)
        try {
            p.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        } catch (e: Exception) {
            try { p.close() } catch (_: Exception) { /* best effort */ }
            throw PrinterException("connect_failed", "No se pudo configurar el puerto: ${e.message}")
        }
        port = p

        // SerialInputOutputManager administra su propio hilo (start()/stop()) — no implementa
        // Runnable, a diferencia de lo que sugiere su nombre; confirmado contra el código
        // fuente real de la librería (github.com/mik3y/usb-serial-for-android, tag 3.11.0).
        val manager2 = SerialInputOutputManager(p, object : SerialInputOutputManager.Listener {
            override fun onNewData(data: ByteArray) = onData(data)
            override fun onRunError(e: Exception) = onError(e.message ?: "Error de lectura del puerto serie")
        })
        ioManager = manager2
        manager2.start()
    }

    fun disconnect() {
        ioManager?.stop()
        ioManager = null
        try { port?.close() } catch (_: Exception) { /* best effort */ }
        port = null
    }

    private fun findDriver(manager: UsbManager, vendorId: Int?, productId: Int?): UsbSerialDriver? {
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        if (vendorId != null && productId != null) {
            return drivers.firstOrNull { it.device.vendorId == vendorId && it.device.productId == productId }
        }
        return drivers.firstOrNull()
    }
}
