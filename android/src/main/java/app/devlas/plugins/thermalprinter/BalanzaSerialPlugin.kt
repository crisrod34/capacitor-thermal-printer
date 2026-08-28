package app.devlas.plugins.thermalprinter

import android.util.Base64
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

/**
 * Balanza en vivo por USB-serie (Android, Fase 5 — ver docs/BALANZAS_DIGITALES_PLAN.md del
 * repo pos). Plugin separado del de impresora (mismo módulo nativo/repo, distinta clase
 * @CapacitorPlugin) porque son transportes con protocolo y ciclo de vida distintos, aunque
 * compartan el mismo flujo de permiso USB.
 *
 * Los bytes que llegan del puerto se emiten como evento `data` (base64) — parsearlos es
 * trabajo del composable en el POS (balanzaSerial.ts / extraerTramaBalanzaSerial), igual que
 * la variante Electron/Web Serial: este plugin solo transporta bytes, no conoce el formato
 * de trama de ninguna marca de balanza en particular.
 */
@CapacitorPlugin(name = "BalanzaSerial")
class BalanzaSerialPlugin : Plugin() {

    private val transport by lazy { BalanzaSerialTransport(context) }

    @PluginMethod
    fun list(call: PluginCall) {
        val ret = JSObject()
        ret.put("devices", transport.list())
        call.resolve(ret)
    }

    @PluginMethod
    fun requestPermission(call: PluginCall) {
        val vendorId = call.getInt("vendorId")
        val productId = call.getInt("productId")
        transport.requestPermission(
            vendorId, productId,
            onResult = { granted ->
                val ret = JSObject()
                ret.put("granted", granted)
                call.resolve(ret)
            },
            onError = { code, msg -> call.reject(msg, code) },
        )
    }

    @PluginMethod
    fun connect(call: PluginCall) {
        val vendorId = call.getInt("vendorId")
        val productId = call.getInt("productId")
        val baudRate = call.getInt("baudRate") ?: 9600
        try {
            transport.connect(
                vendorId, productId, baudRate,
                onData = { bytes ->
                    val ev = JSObject()
                    ev.put("bytes", Base64.encodeToString(bytes, Base64.NO_WRAP))
                    notifyListeners("data", ev)
                },
                onError = { msg ->
                    val ev = JSObject()
                    ev.put("message", msg)
                    notifyListeners("error", ev)
                },
            )
            call.resolve()
        } catch (e: PrinterException) {
            call.reject(e.message ?: "Error de conexión", e.code)
        } catch (e: Exception) {
            call.reject(e.message ?: "Error de conexión", "connect_failed")
        }
    }

    @PluginMethod
    fun disconnect(call: PluginCall) {
        transport.disconnect()
        call.resolve()
    }
}
