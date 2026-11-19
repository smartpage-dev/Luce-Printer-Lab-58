# Command probes and next-step decisions

## No hidden protocol sweep

Each send is a single operator-confirmed job. The tester never guesses UART baud rates, RFCOMM channel numbers, BLE write UUIDs, power settings or vendor firmware commands. It does not establish that the hardware is safe under every command parser; stop immediately when output is unexpected.

| Test | Bytes / mechanism | Evidence sought |
|---|---|---|
| A | Printable ASCII plus LF only | Basic application-controlled stream/text output |
| B | ESC @ then ASCII | Volatile initialization behavior and text output |
| C/D/J | GS ( k model 2 / module size / M correction / store / print | Native short, long or custom test URL QR |
| E | GS v 0; 160 x 72 dot low-density grid | Raster support without first sending a dense full ticket |
| F/G/K | GS v 0; integer-module QR with >=4-module margins | Raster QR as an alternative to native QR |
| H | ESC * mode 33 in 24-dot bands, ESC 3 spacing then ESC 2 | Separate column-image compatibility hypothesis |
| I | Android-rendered small Chinese/English bitmap via GS v 0 | Avoids trusting printer character-code pages |
| Status | 10 04 01, one wait, raw reply only | Presence/absence of documented-style status response |

ESC @ initializes volatile state and may clear pending data. Do not test while another application owns the printer. Raster methods are not automatically tried after native failure. Neither image command is claimed to work on this specific firmware.

## Decision tree

- No paired device: use Android system settings; grant permissions; confirm the printer is charged and not connected to another app/phone.
- SDP shows standard SPP: try secure SPP, still without assuming printing compatibility.
- SDP empty/timeout: inconclusive; explicitly try standard SPP once. Never claim that a missing result proves BLE-only hardware.
- Secure connection fails: check other app connections and power first. Optional insecure RFCOMM is a manually acknowledged diagnostic for fake data only.
- SPP still fails: collect the SDP report. A bounded BLE scan and NO-WRITES GATT inspection can expose service UUIDs. Stop there: this release cannot print over BLE and must not guess the meaning of a writable characteristic.
- Connection works but A prints nothing: confirm paper orientation/power and recording. Try B separately. The displayed 115200 serial rate is not an Android RFCOMM setting to sweep.
- A/B works and native QR does not: test E raster grid. If supported, test F. These establish separate capabilities.
- GS v 0 prints garbage: STOP. Photograph the result. Close connection. Let buffered output stop, then deliberately power-cycle the printer before trying another protocol. ESC * H is a separate experiment, not an automatic fallback.
- Native or raster QR prints: use another phone to decode full data; do not infer success from appearance. The example.org URL is not a real guest page and need not resolve.
- Short QR works; long fails: compare the exact test ID, module size, paper width and bitmap truncation. Do not shrink below safe integer modules just to fit.
- BLE listing succeeds but SPP does not: send report for protocol research, with vendor SDK if obtainable. A GATT property does not explain framing or application commands.
- Status has no reply: lack of status support is possible. It is not proof of offline/paper failure. The tool closes the socket before another command after the timeout.

## First acceptance round

Do A once, B once, C once. Then E and F separately. Record each output immediately. Once a method reliably scans, perform 10 manually numbered trials (no automatic loop). Restart/reconnect, leave idle, and test again. Only afterward combine the lab with ongoing camera transfer and cloud upload. The app must remain foreground for this trial, so simultaneous third-party-app foreground operation is not validated here.

Record phone model/Android version, firmware label, transport mode, test method, requested dot width, chunk size/delay, elapsed behavior, paper output, QR decode text match, and failure recovery. The app exports most technical parameters; attach paper photos for physical evidence.

## Stop / recovery semantics

STOP closes local sockets. It cannot retract bytes already buffered by the printer and cannot guarantee an immediate physical stop. Partly transmitted image commands may leave the printer parser expecting more data. Do not automatically resend after restart. Every fresh press creates a new test ID and may print another ticket.

The journal stores an intent before writes. Sending becomes sent_unconfirmed only after the output stream returns successfully. Errors are outcome_unknown, including a write that threw before the caller learned how many bytes were accepted. Restarted sending records become interrupted_unknown. Operator observations remain separate. This is an audit trail, not a production print queue or a driver acknowledgement.

## Applying successful results later

Choose a transport and command profile from actual evidence. Add a bounded production ticket adapter and persistent ticket jobs to LensDrop in a separate release. Keep guest QR identity stable, one printer per photographer, and the ticket queue independent of photo-upload and photo-print queues. Do not copy this foreground diagnostic connection/lifecycle behavior into an always-running production agent without redesign and tests.
