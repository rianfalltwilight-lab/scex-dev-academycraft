# Matrix SSID candidate 0.0.23

Baseline: frozen AcademyCraft 0.0.22-scex-wireless, SHA256 3E88B7BC409B15E356AA219B0146A9DB46F9DBCE033CBC825781D49B879B69DA.

The reported name is the matrix own SSID. The old GUI submitted only the focused field: edit SSID, focus password, press Enter sent only the password while retaining the unsaved SSID text. A generic successful-save chat message then looked like both fields were saved. This source-confirmed path is fixed; the reporter exact interaction sequence remains unconfirmed.

The matrix now submits all explicitly edited fields together, leaves untouched fields absent, and waits for an action-scoped server receipt. Rejected or unconfirmed requests remain visibly unsaved; Escape cancels drafts. Initialization uses the same public receipt. Live block updates refresh an unedited SSID. No plaintext password appears in a receipt. Other machines retain their existing bounded discovery refresh; matrix SSID and individual node name remain distinct.

Protocol: academy-1.21.1-payload-v16-data-v4. Client and server must upgrade together; do not mix with v15. No save schema change. Roll back the paired jars together; previously saved SSIDs/passwords remain data, not reversed by a jar downgrade.

Verification: 343 existing JUnit tests and 286 required GameTests passed on Java 21.0.12 / Gradle 9.2.0 / NeoForge 21.1.248. New server test covers combined/name-only/password-only mutation, discovery source, BE/network NBT and invalid input. Actual socket/GUI receipt delivery is not proven by mock-player GameTest. No actual world restart, multiplayer SSID concurrency or complete-pack test was run for this candidate. The previous 0.0.22 UI geometry/render/resource evidence remains applicable to unchanged layout/assets; it does not verify the new input behavior.

Real GUI probe: -Dacademy.matrixSsidGate=true in a new isolated client game directory. It creates MatrixSsidGate, opens production menus, invokes real field clicks/typing/Enter, checks server state, checks node network-list data, reopens the matrix and writes matrix-ssid-result.json plus three screenshots. Success additionally requires inspecting PNGs and checking normal exit/save. This probe is prepared and compiled, NOT RUN: Vicerach has existing desktop Java clients; profile concurrent_clients=1. No fallback local client was started. Do not call this a completed player release.

Performance boundary: existing wireless tick/discovery algorithms, ranges and refresh budgets unchanged. New menu reads are O(1), text bounded to 32/64 characters; one small receipt per submitted action. No measured performance comparison is claimed.

Documentation erratum inherited from 0.0.22: the 0.0.21 2.5x value concerns 电流回冲 generation efficiency/output, not damage. Older frozen historical release text is not a new gameplay claim.

Production directories were not modified. Candidate requires GUI validation before any release decision by SCEX-长期维护.
