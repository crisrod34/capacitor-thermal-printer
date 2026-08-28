import { WebPlugin } from '@capacitor/core'
import type { BalanzaSerialPlugin, BalanzaSerialDevice, BalanzaSerialTarget } from './definitions-balanza'

/**
 * Stub web: este plugin es Android-nativo. En navegador usa Web Serial directo
 * (navigator.serial) — no lo envolvemos acá, mismo criterio que ThermalPrinterWeb.
 */
export class BalanzaSerialWeb extends WebPlugin implements BalanzaSerialPlugin {
  async list(): Promise<{ devices: BalanzaSerialDevice[] }> {
    throw this.unimplemented('Solo Android. En navegador usa Web Serial.')
  }

  async requestPermission(_options: BalanzaSerialTarget): Promise<{ granted: boolean }> {
    throw this.unimplemented('Solo Android. En navegador usa Web Serial.')
  }

  async connect(_options: BalanzaSerialTarget & { baudRate?: number }): Promise<void> {
    throw this.unimplemented('Solo Android. En navegador usa Web Serial.')
  }

  async disconnect(): Promise<void> {
    throw this.unimplemented('Solo Android. En navegador usa Web Serial.')
  }
}
