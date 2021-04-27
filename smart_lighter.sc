// Scarpet app to light caves and surfaces against mob spawning.
// Inspired by the auto_lighter example app.
// Author: Kaedenn A. D. N.
// Creation Date: 23 April 2021

// Right click the air while holding a torch named "Auto Lighter" to toggle.
// Responds to the following commands:
//	/auto_lighter
//	/auto_lighter config
//	/auto_lighter config repeat
//	/auto_lighter config repeat true
//	/auto_lighter config repeat false
//	/auto_lighter config radius
//	/auto_lighter config radius <int>
//	/auto_lighter config height
//	/auto_lighter config height <int>
//	/auto_lighter config sky
//	/auto_lighter config sky skip
//	/auto_lighter config sky include
//	/auto_lighter config delay
//	/auto_lighter config delay <int>
// Note that a positive height will scan twice as many blocks as a negative
// height, due to scanning both above and below the player.

// To stop the script while it's repeating, invoke one of the following:
//   /smart_lighter config repeat false
//   /script in smart_lighter run global_enable_repeat = false
//   /script unload smart_lighter

// To light up a specific area, invoke the __light_area function directly.
// Replace x1, y1, z1, x2, y2, and z2 with the values you want to use. Note
// that y2 should be less than or equal to y1.
//   /script in smart_lighter run global_p1 = [x1, y1, z1];
//   /script in smart_lighter run global_p2 = [x2, y2, z2];
// Next, run the following command as many times as torches you want to place:
//   /script in smart_lighter run bpos = __light_area(global_p1, global_p2); print(bpos); if (bpos != null, global_p2:1 = bpos:1);
// Do not invoke this command while it's still running.

global_enable_repeat = false;
global_is_running = false;

global_radius = 32;         // Process 32 blocks around the player
global_height = 8;          // Process 8 blocks above and below the player
global_sky_skip = false;	  // Include blocks that have sky access
global_delay = 2;           // Repeat processing every 2 ticks
global_min_level = 8;       // Light levels below this are torched

__config() -> {
  'stay_loaded' -> 'true',
  'commands' -> {
    '' -> ['help'],
    'config' -> ['config', null, null],
		'config repeat' -> ['config', 'repeat', null],
		'config repeat true' -> ['config', 'repeat', true],
		'config repeat false' -> ['config', 'repeat', false],
		'config radius' -> ['config', 'radius', null],
		'config radius <radius>' -> _(val) -> config('radius', val),
		'config height' -> ['config', 'height', null],
		'config height <int>' -> _(val) -> config('height', val),
		'config sky' -> ['config', 'sky', null],
    'config sky skip' -> ['config', 'sky', true],
    'config sky include' -> ['config', 'sky', false],
		'config delay' -> ['config', 'delay', null],
		'config delay <ticks>' -> _(val) -> config('delay', val),
		'config min' -> ['config', 'min', null],
		'config min <level>' -> _(val) -> config('min', val)
  },
	'arguments' -> {
    'ticks' -> {
      'type' -> 'int',
      'min' -> 1,
      'max' -> 100,
      'suggest' -> [1, 2, 5, 10, 20]
    },
		'level' -> {
			'type' -> 'int',
			'min' -> 1,
			'max' -> 15,
			'suggest' -> [1, 8, 15]
		},
		'radius' -> {
			'type' -> 'int',
			'min' -> 1,
			'suggest' -> [1, 16, 32, 64, 128]
		}
	}
};

// Convenience function to simplify readability
__format(f, s) -> format(f + ' ' + s);

// Convenience function to format a help line
__format_line(string) -> (
	self = format('gb /smart_lighter');
	f_cmd(s) -> __format('eb', s);
	f_arg(s) -> __format('bi', s);
	f_desc(s) -> __format('i', s);
	[cmd_s, desc_s] = split(' - ', string);
	args = [];
	while (cmd_s ~ '<[a-z]+>', length(cmd_s),
		args += cmd_s ~ '<[a-z]+>';
		cmd_s = replace_first(cmd_s, ' <[a-z]+>');
	);
	cmd = if (cmd_s, ' ' + f_cmd(cmd_s), '');
	for (args,
		cmd += ' ' + f_arg(_);
	);
	return(self + cmd + ' -> ' + f_desc(desc_s));
);

__on_player_uses_item(pl, item, hand) -> (
	if (hand != 'mainhand', return());
	[itype, icount, inbt] = item;
	if (itype == 'torch' && escape_nbt(inbt:'display':'Name') ~ '"Auto Lighter"',
		if (global_is_running,
			print('Smart Lighter is currently running, please wait a moment...')
		,
			task('light_area');
		);
	);
);

__get_volume() -> (
	dy = if (global_height < 0, -global_height, global_height * 2);
	return(global_radius * global_radius * dy);
);

help() -> (
	print(__format_line(' - This menu'));
	print(__format_line('config - Display current configuration'));
	print(__format_line('config repeat - Display current repeat status'));
	print(__format_line('config repeat true - Enable automatic repeat'));
	print(__format_line('config repeat false - Disable automatic repeat'));
	print(__format_line('config radius - Display current radius'));
	print(__format_line('config radius <int> - Set radius value'));
	print(__format_line('config height - Display current height configuration'));
	print(__format_line('config height <int> - Set max height difference. Set to a negative value to place torches only below the player'));
	print(__format_line('config sky - Display current skylight configuration'));
	print(__format_line('config sky skip - Skip blocks with adequate skylight'));
	print(__format_line('config sky include - Include blocks with adequate skylight'));
	print(__format_line('config delay - Display current delay (in ticks) between lighting attempts'));
	print(__format_line('config delay <ticks> - Set delay (in ticks) between lighting attempts'));
	print(__format_line('config min - Display current minimum allowable light level'));
	print(__format_line('config min <int> - Change minimum allowable light level'));
);

config(option, value) -> (
	__plural(n, s) -> (__format('eb', n) + ' ' + s + if(n == 1, '', 's'));
	leader = format('gb /smart_lighter') + ' ' + format('eb config ' + option) + ' -> ';
	if (option == null, (
		print('Current smart_lighter configuration:');
		print('Current volume: ' + __format('eb', __get_volume()) + ' blocks');
		config('repeat', null);
		config('radius', null);
		config('height', null);
		config('sky', null);
		config('delay', null);
		config('min', null);
	), option == 'repeat', (
		if (value != null, global_enable_repeat = value);
		print(leader + 'Automatic repeat is '
			+ if(global_enable_repeat, format('eb enabled'), format('eb disabled')));
	), option == 'radius', (
		if (value != null, global_radius = value);
		print(leader + 'Processing radius is set to '
			+ __plural(global_radius, 'block'));
	), option == 'height', (
		if (value != null, global_height = value);
		dy = abs(global_height);
		rel = if (global_height < 0, 'below (negative)', 'above and below (positive)');
		print(leader + 'Scanning ' + __plural(dy, 'block') + ' '
			+ __format('eb', rel) + ' the player');
	), option == 'sky', (
		if (value != null, global_sky_skip = value);
		print(leader
			+ __format('eb', if(global_sky_skip, 'Skipping', 'Including'))
			+ ' blocks with adequate sky access');
	), option == 'delay', (
		if (value != null, global_delay = value);
		print(leader + 'Repeat processing will occur every '
			+ __plural(global_delay, 'tick'));
	), option == 'min', (
		if (value != null, global_min_level = value);
		print(leader + 'Minimum light level set to '
			+ __format('eb', global_min_level));
	), (
		// Should be unreachable unless there's a bug in the code
		print('Unknown configuration term ' + __format('eb', option) + '; see '
			+ format('gb /smart_lighter') + ' for a list');
	));
);

// True if a torch can be placed at the given position
__can_have_torch(lpos) -> (
	blk = block(lpos);
	blk_down = block(pos_offset(lpos, 'down'));
	return(solid(blk_down)
		&& blk_down != 'ice'
		&& (air(blk) || blk == 'snow' || blk == 'fire' || blk == 'fern'
			|| blk == 'dead_bush' || blk == 'crimson_roots' || blk == 'warped_roots'
			|| blk == 'nether_sprouts' || blk == 'vine'));
);

// Place one torch at a dark location between p1 and p2
__light_area(p1, p2) -> (
  volume(p1, p2,
		light_level = if (global_sky_skip, light(_), block_light(_));
		if (light_level < global_min_level && __can_have_torch(_),
      set(_, block('torch'));
			return(_); // To avoid placing torches before the area is updated
    )
  );
	return(null);
);

// Place one torch in the configured area around the player
light_area_once() -> (
	pl = map(pos(player()), floor(_));
	p1 = pl - [
		global_radius,
		abs(global_height),
		global_radius];
	p2 = pl + [
		global_radius,
		if (global_height < 0, 0, abs(global_height)),
		global_radius];
	return(__light_area(p1, p2));
);

// Light the configured area around the player. Repeats every global_delay
// ticks if global_enable_repeat is true.
light_area() -> (
	if (global_is_running, (
		run('tellraw @s {"text":"light_area() is still running, please wait a moment"}')
	), (
		global_is_running = true;
		blk = light_area_once();
		if (blk != null, (
			run(str('tellraw @s {"text":"Set the block %s at %s to a torch"}',
				blk, str(pos(blk))));
			if (global_enable_repeat,
				schedule(global_delay, 'light_area'));
		), (
			run('tellraw @s {"text":"No blocks found that needed to be lit"}');
		));
		global_is_running = false;
	));
);

