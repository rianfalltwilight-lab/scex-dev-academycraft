# 0.0.27 machine UI fixes

Target: Minecraft 1.21.1, NeoForge 21.1.248, Java 21.0.12, Gradle 9.2.0,
ModDev 2.0.141, Parchment 2024.11.17. Parent is the frozen 0.0.26 source ZIP
SHA-256 `299cc2f6cc7ac4f9243e501342f7db2c63e044a4b77b3fa93de55c0b2ddde7d6`.

## Scope and implementation

- The machine connected-status row receives the node's authoritative `getNodeName()` through
  `RequestNodesPacket` / `NodeListSyncPacket`; node-to-matrix rows use the separate SSID path.
  The stored default is the literal `Unnamed`. `WirelessDisplayName` localizes only that exact
  value at display time, including the developer's current-node row, available lists and
  unfocused node/matrix property fields. Custom names and edit buffers stay literal.
  No persistence, server-side name, protocol or wireless identity changes are introduced.
- Both developer tiers used the 400-pixel developer canvas origin for their 176-pixel wireless
  replacement page. That page bypasses the machine background and does not draw its information
  card. Center its actual 196-pixel sidebar + page composition; use the same origin for input.
  Recompute on opening the network page, rendering, input and resize.
- The skill-tree network button is drawn at logical (8.25, 114.5), derived from the original
  developer widget dimensions. Its old hit box rounded (8, 115), which differs at fractional
  fit scales. Drawing and clicking now round the same coordinates.
- The node icon is white RGB plus alpha. Blending was disabled before drawing it, producing a
  solid white square in actual screenshots. Enable standard alpha blending for that draw.

## API/source verification

Read [NeoForge 1.21.1 screens](https://docs.neoforged.net/docs/1.21.1/gui/screens/) for relative
coordinates, rendering, initialization and mouse event handling. Read the exact generated
`build/moddev/artifacts/neoforge-21.1.248-sources.jar` for:

- `Screen.init`, `resize`, `repositionElements`, and `AbstractContainerScreen.init` / GUI origins.
  Minecraft supplies logical GUI coordinates; resize rebuilds the widgets. Recompute layout
  from those dimensions, never from framebuffer pixels.
- `Component.translatable`, `getString`, `TranslatableContents.decompose`: translation resolves
  with the current Language instance. Resolution happens in client GUI drawing, not in NBT or
  packet encoding. No cached translated name is persisted.
- `GuiGraphics.blit` / `innerBlit` and `RenderSystem.enableBlend`, `defaultBlendFunc`, `disableBlend`:
  the texture-only blit does not enable blending. These render operations run on the render thread.
- `ClientTickEvent.Post`, `Screenshot.grab`: client-only event, render-thread capture followed by
  asynchronous file writing. The test waits for the callback AND the file. Server fixture changes
  and assertions are queued with `MinecraftServer.execute`, as in the parent client gate.

Selected exact sources are retained in the delivery's `evidence/api`. Existing same-version
server interaction, menu, quick-play and normal shutdown practices are reused from 0.0.26.

## Validation contract

`gradlew.bat --offline --console=plain test build auditTestJar` compiles the release and separate
test-only probe. `MachineUiClientGate` does not enter the release JAR. It tests both developer
tiers at GUI scales 2, 3, 4 in a 1600x1000 Vicerach desktop client: real item placement and block
interaction, edge-of-button clicks, registered wireless menus, exact centering/bounds, real node
connection, server name/NBT preservation, sidebar return and the reopened skill tree. It also
changes the connected node's name while the wireless page stays open and waits for the refreshed
status row. Default and custom names are both covered. Screenshots require manual visual review.

The first visual iteration exposed an over-reserved invisible information card; the next exposed
the pre-existing opaque node icon. Those intermediate automatic PASS results are diagnostic only.
Final results, tested/final artifact identity, screenshots and cleanup are recorded in
`DELIVERY-0.0.27.md` and the delivery evidence. No full-pack, multiplayer, new GameTestServer run,
resource-pack compatibility, performance or production acceptance is claimed for this UI change.
