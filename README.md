# Snowmen

Build your own snowman. Roll a snowball through the snow until it is big enough, stack three, and decorate it.

- **Pack a snowball:** right-click snow with an empty hand.
- **Roll it:** hit it. It rolls one block the way you face, picks up the snow it rolls over and grows. Rolling leaves a trail of bare ground.
- **Carry it:** right-click a snowball to lift it over your head, right-click the ground to put it down. Snowballs up to size 10 are light enough to carry.
- **Stack it:** carry a smaller snowball to a bigger one and right-click. Three make a snowman, and it turns to face you.
- **Decorate it:** right-click the snowman with
  - coal or charcoal: eyes first, then up to three buttons
  - a carrot: the nose
  - sticks: up to two arms
  - any carpet: a scarf in that colour
  - anything else: a hat. Blocks like a carved pumpkin go over the head, items like a bucket sit on top.
- **Take things off:** sneak and right-click with an empty hand. Decorations come off one at a time, then the top snowball.
- **Knock it over:** hit a snowman twice quickly. You get the snow and every decoration back.
- **Melting:** snowmen slowly melt where a snow golem would, like deserts and the Nether.

Snowmen are made of vanilla display entities, so the mod adds no blocks, items or textures, and only the server does the work. Requires [Mycel](https://www.curseforge.com/minecraft/mc-mods/mycel). Fabric, Forge and NeoForge, Minecraft 26.1 to 26.3.

## Settings

| Setting | Default | What it does |
|---|---|---|
| Melting | On | Snowmen slowly shrink and finally fall apart where a snow golem would melt |
| Max ball size | 16 | How big a snowball can grow while rolling |
| Snow trail | On | Rolling picks up the snow and leaves bare ground behind |

## Build

```
./gradlew build            # Minecraft 26.3
./gradlew build -Pmc=26.2
./gradlew build -Pmc=26.1
```

MIT licensed. Part of [MyCelium](https://mycelium-gg.org).
