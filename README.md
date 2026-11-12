# Snowman

Build your own snowman. Roll a snowball through the snow until it is big enough, stack three, and decorate it.

- **Pack a snowball:** right-click snow with an empty hand.
- **Roll it:** walk into it to push it ahead of you, or hit it to kick it away. It rolls smoothly in any direction, slows down on its own, slides along walls, rolls down drops but never uphill. Every block it rolls over snow adds one size, with no size limit, and it leaves a trail of bare ground as wide as the ball. Bigger balls are heavier, so a kick sends them less far.
- **Pick it up:** right-click a snowball with an empty hand. It becomes a Rolled Snowball item that keeps its size and looks bigger the bigger it is, in the inventory and in your hand. Right-click the ground to put it down again. In creative, every size up to 16 (and a few bigger ones) is in Tools & Utilities and in the search.
- **Stack it:** hold a Rolled Snowball and right-click another snowball (or the ground right at it). It goes on top if it isn't bigger than the top ball. Three make a snowman, and it turns to face you; you can keep stacking up to five (a setting) for a taller one. The face, scarf and hat go on the top ball, arms and buttons on the one below.
- **Turn it:** stand next to a finished snowman and press **R** (sneak + R turns the other way). The key can be changed in Controls.
- **Decorate it:** right-click the snowman with
  - coal or charcoal: eyes first, then up to three buttons
  - a carrot: the nose
  - sticks: up to two arms
  - a **Scarf**: a knitted scarf with a hanging end. Craft it from wool and dye it any colour, like leather armor.
  - a **Top Hat**: crafted from black wool and red dye. A snowman wearing one never melts.
  - anything else: a hat. Blocks like a carved pumpkin go over the head, items like a bucket sit on top.
- **Take things off:** sneak and right-click with an empty hand. Decorations come off one at a time, then the top snowball.
- **Knock it over:** hit a snowman twice quickly. You get the snow and every decoration back.
- **Melting:** snowmen slowly melt where a snow golem would, like deserts and the Nether, unless they wear a Top Hat.

### Scarf recipe

```
WWW      W = any wool
  W      Dye it by crafting it with any dye.
```

### Top Hat recipe

```
 B       B = black wool
 R       R = red dye
BBB
```

Every action has its own snowy sound (with subtitles), built from vanilla sound files. Snowmen are made of vanilla display entities. The mod adds three items, the Rolled Snowball, the Top Hat and the Scarf, so it is needed on both the server and the client. Requires [Mycel](https://www.curseforge.com/minecraft/mc-mods/mycel): without it the game still starts, Snowman stays off and tells you in chat that Mycel is missing. Fabric, Forge and NeoForge, Minecraft 26.1 to 26.3.

## Settings

| Setting | Default | What it does |
|---|---|---|
| Melting | On | Snowmen slowly shrink and finally fall apart where a snow golem would melt |
| Ball size limit | 0 (no limit) | How big a snowball can grow while rolling |
| Max stack height | 5 | How many snowballs can be stacked (3 to 32) |
| Snow trail | On | Rolling picks up the snow and leaves bare ground behind |
| Rotate hint | On | Shows "Press R to turn the snowman" above the hotbar next to a finished snowman |

## Build

```
./gradlew build            # Minecraft 26.3
./gradlew build -Pmc=26.2
./gradlew build -Pmc=26.1
```

MIT licensed. Part of [MyCelium](https://mycelium-gg.org).
