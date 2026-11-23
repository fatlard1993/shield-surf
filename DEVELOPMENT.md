# Shield Surf - Development Guide

For what the mod is and how it plays, see [README.md](README.md).

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`); connecting clients need nothing, not even Pandorical, though a Pandorical client gets the settings page. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).

## How the ride is built

A player's own client moves them, so a server mod cannot slide a player by pushing them about: every push would be fought by the client's own friction a tick later. Instead the ride is made of three attributes the game syncs from server to client, and the client's own physics does the sliding:

- `friction_modifier` to zero, so every block underfoot slides like a frictionless one
- `step_height` to zero, so a rise is a wall and a slope is only ever ridden down
- `air_drag_modifier` lowered, so a hop or a drop keeps most of its speed

The shield under the rider is an item display riding the player, so the client keeps it at their feet frame by frame. A passenger hangs from the top of its vehicle, and the rider's height differs by viewer: everyone else sees them crouched, while their own client crouches them only when it is the host of the world. So each ride lays two boards, one sent only to the rider and one to everyone else, each pulled down by the height its viewers see. The game will not seat anything on a player, because players are not saved with the world; a mixin lets the board through, and a board is never saved either.

The one thing the server does move is the kick on landing: each block dropped since take-off adds speed along the direction of travel, sent as a single velocity packet. That is what makes a slope faster than the flat.

## Checking a build

`./gradlew build` compiles. `xvfb-run -a ./gradlew runClientGameTest -Ptest.level=full` launches a client off-screen, joins a world, and has the player raise a shield in the air: the test checks the ride begins with the boards under the player (one of them on the player's own client), that standing still ends it, and that lowering the shield ends it, boards and all.
