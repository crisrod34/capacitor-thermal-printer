import { registerPlugin } from '@capacitor/core'
import type { BalanzaSerialPlugin } from './definitions-balanza'

export const BalanzaSerial = registerPlugin<BalanzaSerialPlugin>('BalanzaSerial', {
  web: () => import('./web-balanza').then((m) => new m.BalanzaSerialWeb()),
})

export * from './definitions-balanza'
