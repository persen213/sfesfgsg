# Time Bubble – a Re:AcademyCraft 1.5.0 add-on (Minecraft 1.20.1, Forge 47.x)

One ability, **Time Bubble**, in its own category, **Temporal**. A separate mod: it does not change Re:AcademyCraft.

## What it does

Aim anywhere and press the skill key.

1. **Formation (0.5 s):** a small blue-white sphere forms above your hand with rotating violet rings.
2. **Active (5 s):** a transparent dome expands to **8 blocks** radius. Hostile players and mobs inside are **heavily slowed** (15% speed). Projectiles are **dramatically slowed** (5% speed). You and your allies move normally.
3. **Collapse:** after 5 s, or when you press the key again, the dome contracts into a bright geometric point and vanishes. All affected entities are restored to normal movement.

Safety and balance: you and your allies are never slowed; only targets the server's PvP / team / protection rules let you attack are affected (checked through Re:AcademyCraft's safety service). Bosses are slowed much less. **No blocks are changed. No server ticks are frozen.** Death, logout or a dimension change end it cleanly with no visual artifacts.
Cost: **35 CP** (level-2 players have about 4000 CP by default), cooldown **30 s**.
Every number is in `config/timebubble-common.toml` (created on first launch).

## Visuals – zero particles

Drawn entirely as custom 3D geometry on the client:
- Formation: small sphere at hand, violet rotating geometric rings
- Active: transparent spherical dome (8 blocks) with glass-like distortion, large concentric rings rotating on its surface
- Entities inside: faint blue outlines
- Collapse: dome shrinks to a bright implosion point with expanding shockwave rings
- Screen edges: subtle blue vignette and time-distortion lines when inside

The source contains no particle API at all (checked by search).

## Honest status

| Stage | State |
|---|---|
| Code written | Yes (server ability, network, client rendering, screen effect, config) |
| Syntax / API check | Done in a sandbox: parses cleanly, Re:AcademyCraft API types resolve. Minecraft/Forge classes were NOT available there |
| Compiled with Forge | **Not yet** – the GitHub build below is the first real compile |
| Tested in Minecraft | **No** |

If the GitHub build fails, copy the red error text and send it back; most likely a one-line fix to a vanilla name.
Known unknowns that only a real test can settle: how the visuals look and perform; whether a one-skill level-2 category can be levelled up and learned smoothly in the Ability Developer; and whether the cost and slow strength feel right.

## Build it on GitHub (no Java needed)

1. Sign in at github.com. Click **+** (top right) → **New repository**. Name it `timebubble-addon`, choose **Private**, click **Create repository**.
2. Unzip `timebubble-addon.zip` on your PC. In the new repository click **uploading an existing file** (or **Add file → Upload files**) and drag in everything INSIDE the `timebubble-addon` folder (the `src`, `libs`, `gradle` folders and the files next to them). Click **Commit changes**.
3. Click the **Actions** tab. If it asks you to choose a workflow ("Get started with GitHub Actions"), click **set up a workflow yourself**, select everything in the editor, delete it, and paste the contents of `.github/workflows/build.yml` from the zip. Click **Commit changes…** twice. (Drag-and-drop often skips the hidden `.github` folder, which is why this step may be needed.)
4. A run starts by itself (or press **Run workflow**). The first build takes 10–15 minutes. When it shows a green tick, open the run, scroll to **Artifacts**, download **timebubble-mod**, and unzip it: the `.jar` inside is the mod (`timebubble-forge-1.20.1-0.1.0.jar`).
5. If the run is red, open it, click **build**, copy the red error text and send it back.

## Try it (use a separate test instance, not your live server)

1. Make a new Minecraft 1.20.1 instance with Forge 47.4.10 or newer.
2. Put two jars in its `mods` folder: the normal **Re:AcademyCraft 1.5.0** jar from CurseForge, and `timebubble-forge-1.20.1-0.1.0.jar`.
3. Start a creative world. Take the **Temporal Factor** from the Ingredients creative tab and use the Ability Developer machine (see the mod's tutorial) to get the category, learn **Time Bubble**, and bind it in the skill preset editor.
4. Aim at an area with mobs around and press the key. Press it again during the 5 s to collapse early.
   On a server, install the same jars on the server **and every client**.

## Files

* `TimeBubble.java` – the ability (temporal field, slowing, restoration); `TimeBubbleMod.java` – registration; `Cfg.java` – all balance numbers
* `net/` – the small network messages; `client/` – the 3D rendering (`FxRenderer`, `Geo`, `BhRenderTypes`), screen effects
* `.github/workflows/build.yml` – the GitHub build