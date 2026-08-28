import { registerPlugin } from '@capacitor/core'
import type { ThermalPrinterPlugin } from './definitions'

const ThermalPrinter = registerPlugin<ThermalPrinterPlugin>('ThermalPrinter', {
  web: () => import('./web').then((m) => new m.ThermalPrinterWeb()),
})

/**
 * Convierte bytes ESC/POS a base64 para `print({ data })`.
 * Seguro para arrays grandes (evita el límite de argumentos de String.fromCharCode).
 */
export function bytesToBase64(bytes: Uint8Array): string {
  let binary = ''
  const CHUNK = 0x8000
  for (let i = 0; i < bytes.length; i += CHUNK) {
    binary += String.fromCharCode(...bytes.subarray(i, i + CHUNK))
  }
  return btoa(binary)
}

export * from './definitions'
export { ThermalPrinter }

// Balanza en vivo (Fase 5, ver docs/BALANZAS_DIGITALES_PLAN.md del repo pos) — plugin
// separado, mismo módulo nativo. Reexportado acá para que un solo import del paquete
// alcance para ambos; el registerPlugin real vive en index-balanza.ts.
export { BalanzaSerial } from './index-balanza'
export type {
  BalanzaSerialPlugin,
  BalanzaSerialDevice,
  BalanzaSerialTarget,
  BalanzaSerialDataEvent,
  BalanzaSerialErrorEvent,
} from './definitions-balanza'
