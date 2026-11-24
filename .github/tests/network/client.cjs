// Keep the protocol client separate from the server processes so console and player actions are distinct.
const { createBot } = require('mineflayer')
const readline = require('node:readline')
const [requestedVersion, schemaVersion] = process.argv.slice(4)
const bot = createBot({
  host: '127.0.0.1', port: Number(process.argv[2]), username: process.argv[3] || 'ReviewMember',
  auth: 'offline', version: schemaVersion || '1.21.11', hideErrors: false
})
if (requestedVersion) {
  const assert = require('node:assert/strict')
  const { versionsByMinecraftVersion } = require('minecraft-data')
  const { createDeserializer } = require('minecraft-protocol')
  const requested = versionsByMinecraftVersion.pc[requestedVersion]
  // Adjacent game releases can share a wire protocol. Inspect the actual outbound login handshake.
  assert.equal(requested.releaseType, 'release')
  assert.equal(bot.protocolVersion, requested.version)
  const decoder = createDeserializer({ version: schemaVersion, isServer: true })
  decoder.once('data', ({ data }) => {
    assert.equal(data.name, 'set_protocol')
    assert.equal(data.params.nextState, 2)
    assert.equal(data.params.protocolVersion, requested.version)
    console.log('LOGIN_HANDSHAKE', requestedVersion, data.params.protocolVersion)
  })
  bot._client.serializer.once('data', bytes => decoder.end(bytes))
}
bot.on('spawn', () => console.log('CLIENT_READY'))
bot.on('messagestr', text => console.log('CHAT', text))
bot.on('kicked', reason => console.error('KICK', reason))
bot.on('error', error => console.error(error))
bot.on('end', () => process.exit(0))
bot.on('windowOpen', window => {
  const items = window.slots.slice(0, window.inventoryStart).flatMap((item, slot) =>
    item ? [slot + '=' + item.name] : [])
  console.log('MENU', items.join(' '))
})
readline.createInterface({ input: process.stdin }).on('line', line => {
  if (!line.startsWith(':click ')) return bot.chat(line)
  const [, slot, material] = line.split(' ')
  const window = bot.currentWindow
  if (!window || window.slots[Number(slot)]?.name !== material) {
    console.error('Unexpected menu item at slot', slot, material)
    process.exitCode = 1
    return bot.quit()
  }
  bot.clickWindow(Number(slot), 0, 0).catch(error => console.error('Menu click failed', error))
})
