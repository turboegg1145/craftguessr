# CraftGuessr — implementation status

Target: Minecraft 26.3 (Fabric), PLAN.md v2.0. Runtime: dedicated server + dev client in Docker.

## Done and verified in-game

| Module | State |
| --- | --- |
| AI scan | server-thread sampling, local heuristic pre-filter, 4-view SurfaceView collage, OpenAI/Anthropic/Gemini providers, token ledger. Verified: 400 samples → 66 local pass → 10 AI judged → 4 spots (~11.3k/8.6k tokens). |
| Quad capture | `/geo capture` hides HUD, switches to 90° FOV, grabs 4×512 views at yaw 180/-90/0/90, labels `[View A: NORTH (Yaw 180)]`… and submits a 1024² JPEG. Verified. |
| Map engine | three-level native pyramid (1:1 / 1:4 / 1:16), incremental per-tick rendering with resumable rows, disk cache, client LOD switching, rate limit, map centre from spot centroid. Verified at 1:1 and 1:4 on the Heidelberg world. |
| Nether layers | tiles for roof (y≥100) and interior (y≤110) with a client layer toggle. Verified by rendering both layers of `minecraft:the_nether`. |
| Game loop | rooms, rounds, teleport into arena, movement modes, guesses, classic scoring + streaks, reveal and game over. Verified: 2-round game, 4,699 / 9,647 points. |
| Reveal | map with answer + guess markers, animated dashed guess→spot lines, outcome table, particles beam at the spot (server END_ROD). |
| Safety | damage immunity, hostile mob clearing inside radius+64, `SPAWN_MONSTERS`/`MOB_GRIEFING` off during rounds, food lock, invulnerability, snapshot persistence (`player_snapshots.dat`) and crash/quit restore. Verified: zombie summoned next to an arena player disappeared within one second; disconnecting mid-round and rejoining restored the bridge spawn and cleared the stored snapshot. |
| GUI | lobby, create room, pool album, guess map, reveal, game over, HUD overlay, keybinds G/M/B, F3 coordinate curtain. |
| Commands | `/geo lobby|create|join|leave|start|rooms|pool|scan|capture|probe|tiletest|tokens|ping|config`. |

## Known gaps (PLAN v2 phases 3–4)

- Auto-pilot tour capture (client state machine that teleports, waits for chunk meshes, then runs the quad capture) — only the manual command path exists.
- 3D no-depth reveal beams and 3D billboard name tags (2D map lines + server particle beam exist; `TextDisplay` setters are private in 26.3 and need a mixin/access widener).
- External tile providers (BlueMap/Dynmap/Squaremap URL template) — native tiles only.
- Third-party HUD supression mixin (F3 overlay is hidden during rounds; vanilla HUD is toggled only for capture).
- Nether layer toggle is only wired into the guess screen; reveal screen still uses layer 0.

## Development workflow

```bash
./dev.sh build              # jar
./dev.sh gw test            # unit tests
bash scripts/dev-cycle.sh   # rebuild + restart server and client (~3.5 min)
bash scripts/server-direct.sh   # server only (Gradle stays free)
```

Client runs inside the `client-vnc` compose service (Xvfb :99, llvmpipe, noVNC on :6083) and needs
`-Dcraftguessr.softwareGL=1` (mixin drops SDL's sRGB framebuffer request, which Xvfb cannot satisfy).

Test world: Heidelberg (CartoVoxel free map, Arnis/OSM export) at `run/world`; the old flat world is at
`/tmp/world_old`. Spawn is on the Alte Brücke, ground level y≈-44 — never hardcode world Y.
