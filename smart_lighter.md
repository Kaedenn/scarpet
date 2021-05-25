# Smart Lighter

This script places torches using an exhaustive brute-force scan.

You can invoke this script via right-clicking the air while holding a torch
named `Auto Lighter` in the main-hand. This torch can be obtained via the
`/smart_lighter give` command or by renaming a torch using an anvil.

For a torch to be placed at a specific position, the block at that position
must satisfy _all_ of the following constraints:

1. The block must be one of the following: `air`, `cave_air`, `void_air`,
`snow`, `fire`, `fern`, `dead_bush`, `crimson_roots`, `warped_roots`,
`nether_sprouts`, `vine`, or `large_fern`
2. The block below must be solid
3. The block below must not be `ice`

## Sub-commands

Invoke the following with `/smart_lighter <command...>`.

### `run`: Invoke the script

The script will scan a region defined by the `config radius` and `config
height` configuration values for a block satisfying the lighting conditions
specified via `config light <cave|surface|both>`. If `config repeat` is set to
`true`, then the region will be scanned _repeatedly_ until no more torches are
placed.

### `save`: Save configuration to file

This command will save the current configuration values to a file. This allows
for a temporary configuration set that will be refreshed on reload or
`/smart_lighter load`.

### `load`: Reload configuration from file

This command will overwrite the current configuration values with the values
saved via a previous `/smart_lighter save` command.

### `give`: Give the player the Auto Lighter item

Right-clicking with this item in the main-hand is equivalent to running
`/smart_lighter run`. By default, the item is a `torch` with the name
`Auto Lighter`. Renaming a torch in an anvil will also work.

### `config`: Display current configuration values

### `config show`: Display current configuration in a condensed form

### `config repeat [true|false]`: Display or change repeat processing behavior

Setting `config repeat true` will cause the script to scan the configured area
repeatedly until no more torches can be placed. The script will delay for
`config delay <int>` ticks between each scan.

### `config radius [<int>]`: Display or change horizontal processing range

This specifies the maximum horizontal distance _from the player_. For example,
setting `config radius 32` will scan an area of 65 by 65 blocks. This radius is
_in addition to_ the block the player is standing on.

### `config height [<int>]`: Display or change vertical processing range

This value can be either positive or negative. Positive values will scan __both
above and below__ the player, while negative values will scan __only below__
the player.

For example, if the player is standing at `y=63`, then `config height -10` will
scan from `y=53` to `y=63`, while `config height 10` will scan from `y=53` to
`y=73`. Note that positive values will scan __twice as many__ blocks as
negative values!

### `config light`: Display current lighting mode

There are three lighting modes: `cave`, `surface`, and `both`. These modes are
explained in further detail below:

### `config light cave`: Place torches only in caves

This mode will place torches where the block light level and sky light level
are __both__ below the minimum level:

> `block_light < min_level && sky_light < min_level`

### `config light surface`: Place torches only on the surface

This mode will place torches where the block light level is __less than__ the
minimum level and the sky light level is __greater than or equal to__ the
minimum level:

> `block_light < min_level && sky_light >= min_level`

### `config light both`: Place torches in both caves and on the surface

This mode will place torches where the block light is __less than__ the minimum
level, __regardless__ of the sky light level:

> `block_light < min_level`

### `config delay [<ticks>]`: Display or change the repeat processing delay

This value is in ticks (0.05 seconds). Setting too low can cause torches to be
placed before Minecraft can update the light levels, resulting in torches being
placed adjacent to each other. The minimum value is 1 tick.

## Other Customization Possibilities

If you wish to customize the `Auto Lighter` item or the `torch` block, you can
modify the script directly. The following variables can be modified:

### `global_item_type`

This sets the item's type without the `minecraft:` prefix. Set this to any
existing item. If using modded items, you need to include that mod's prefix.

### `global_item_name`

This sets the item's name. You can place effectively anything here.

### `global_block`

This sets the block that will be placed, without the `minecraft:` prefix. As
with `global_item_type`, modded blocks will require including the mod's prefix.
You can specify block data just as you would the `/setblock` or `/fill`
commands.

## Optimizations

Scanning the configured region is an expensive operation, and this script does
that repeatedly. In order to minimize the performance hit, the script performs
a few minor optimizations:

1. The current scan halts once a torch is placed. This is to give Minecraft a
moment to recalculate lighting information before the next scan.
2. The script will resume processing at the same y-level as before. This
prevents scanning an already-processed region.
3. The scans terminate upon reaching the highest surface y-level. This prevents
scanning an entire region of just air blocks.

