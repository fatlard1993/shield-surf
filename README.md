# Shield Surf

A Fabric mod for riding a shield down a hill, the way Link does. Jump, raise your shield while you are in the air, and you land on it and slide. Hold it up to keep riding; lower it to stop.

## Riding

- **Any shield** will do, in either hand: anything that blocks a hit can be ridden.
- **Slopes are the point.** Every block you drop adds speed, so a hillside carries you faster and faster, up to about three times a sprint. Flat ground gives nothing back and bleeds the speed off in a second or two.
- **You cannot ride uphill.** A rise is a wall while you are on the shield; you stop against it and step off.
- **Hop while you ride.** A jump keeps the shield under you, and a jump off a ledge lands with the ledge's worth of speed.
- **You crouch on the shield**, as everyone watching can see.
- **Water ends the ride**, and so does standing still on the shield for half a second.

## Wear

Riding costs the shield a point of durability every half second on hard ground, until it breaks under you. Sand, snow and ice are free: the shield slides on them without scraping. An op can turn the wear off in Pandorical's server settings.

## Advancements

- **Shield Surfer**: ride a shield forty blocks without stopping.
- **Worn Through**: ride a shield until there is nothing left of it.

## Pandorical

Shield Surf runs server-side, and Pandorical is required on the server: it is where the wear setting lives. Players need nothing installed, not even Pandorical. A player on a Pandorical client sees the setting in the mod menu; everyone rides the same.

## Development

Installing, and how the ride is built out of the game's own movement attributes, is in [DEVELOPMENT.md](DEVELOPMENT.md).

## License

MIT, see [LICENSE](LICENSE).
