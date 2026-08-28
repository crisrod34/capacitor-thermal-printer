/**
 * @devlas/capacitor-thermal-printer — Balanza en vivo (USB-serie, Android).
 *
 * Plugin separado del de impresora (mismo módulo nativo, otra clase @CapacitorPlugin) —
 * transportes con protocolo y ciclo de vida distintos, aunque compartan el flujo de
 * permiso USB. El plugin solo transporta bytes crudos del puerto serie: no conoce el
 * formato de trama de ninguna marca de balanza en particular — eso lo decodifica el POS
 * (balanzaSerial.ts / extraerTramaBalanzaSerial), igual que las variantes Electron/Web Serial.
 */

export interface BalanzaSerialDevice {
  vendorId: number
  productId: number
  name?: string
  hasPermission?: boolean
}

export interface BalanzaSerialTarget {
  vendorId?: number
  productId?: number
}

/** Bytes crudos leídos del puerto — parsearlos es trabajo del composable en el POS. */
export interface BalanzaSerialDataEvent {
  /** Base64 de los bytes recibidos en este chunk. */
  bytes: string
}

export interface BalanzaSerialErrorEvent {
  message: string
}

export interface BalanzaSerialPlugin {
  list(): Promise<{ devices: BalanzaSerialDevice[] }>
  requestPermission(options: BalanzaSerialTarget): Promise<{ granted: boolean }>
  connect(options: BalanzaSerialTarget & { baudRate?: number }): Promise<void>
  disconnect(): Promise<void>
  addListener(
    eventName: 'data',
    listener: (event: BalanzaSerialDataEvent) => void,
  ): Promise<import('@capacitor/core').PluginListenerHandle>
  addListener(
    eventName: 'error',
    listener: (event: BalanzaSerialErrorEvent) => void,
  ): Promise<import('@capacitor/core').PluginListenerHandle>
}
