// Scarpet app to light caves and surfaces against mob spawning.
// Inspired by the auto_lighter example app.
// Author: Kaedenn A. D. N.
// Creation Date: 23 April 2021

// Right click the air while holding a torch named "Auto Lighter" to toggle.
// Responds to the following commands:
//	/smart_lighter
//	/smart_lighter run
//	/smart_lighter save
//	/smart_lighter load
//	/smart_lighter give
//	/smart_lighter config
//	/smart_lighter config show
//	/smart_lighter config repeat
//	/smart_lighter config repeat true
//	/smart_lighter config repeat false
//	/smart_lighter config radius
//	/smart_lighter config radius <int>
//	/smart_lighter config height
//	/smart_lighter config height <int>
//	/smart_lighter config light
//	/smart_lighter config light cave
//	/smart_lighter config light surface
//	/smart_lighter config light both
//	/smart_lighter config delay
//	/smart_lighter config delay <int>

// Note that a positive height will scan twice as many blocks as a negative
// height, due to scanning both above and below the player.

// To stop the script while it's repeating, invoke one of the following:
//	 /smart_lighter config repeat false
//	 /script in smart_lighter run global_enable_repeat = false
//	 /script unload smart_lighter

// TODO:
// * Run in a thread: use sleep() instead of schedule() for looping
// * Provide named configuration sets

global_is_running = false;
global_curr_y = null;
global_max_y = null;

// Invoke when the player right-clicks with this item in their mainhand:
global_item_type = 'torch';
global_item_name = 'Auto Lighter';

// This block will be placed where torches are needed (can include block data):
global_block = 'torch';

// This player will receive the messages
global_target = '@s';

global_enable_repeat = false; // Automatic repeat starts disabled
global_radius = 32;           // Process 32 blocks around the player
global_height = 8;            // Process 8 blocks above and below the player
global_light_mode = 'both';   // Place torches in both caves and on the surface
global_delay = 2;             // Repeat processing every 2 ticks
global_min_level = 8;         // Light levels below this are torched

global_debug = false;

__prefix() -> format('eb /' + system_info():'app_name') + ' ';

__config() -> {
	'stay_loaded' -> 'true',
	'commands' -> {
		'' -> ['help'],
		'help' -> ['help'],
		'run' -> ['light_area'],
		'give' -> ['give_lighter'],
		'save' -> ['save_config'],
		'load' -> ['load_config'],
		'config' -> ['config', null, null],
		'config show' -> ['config', 'show_raw', null],
		'config repeat' -> ['config', 'repeat', null],
		'config repeat true' -> ['config', 'repeat', true],
		'config repeat false' -> ['config', 'repeat', false],
		'config radius' -> ['config', 'radius', null],
		'config radius <radius>' -> _(val) -> config('radius', val),
		'config height' -> ['config', 'height', null],
		'config height <int>' -> _(val) -> config('height', val),
		'config light' -> ['config', 'light', null],
		'config light cave' -> ['config', 'light', 'cave'],
		'config light surface' -> ['config', 'light', 'surface'],
		'config light both' -> ['config', 'light', 'both'],
		'config delay' -> ['config', 'delay', null],
		'config delay <ticks>' -> _(val) -> config('delay', val),
		'config min' -> ['config', 'min', null],
		'config min <level>' -> _(val) -> config('min', val)
	},
	'arguments' -> {
		'ticks' -> {
			'type' -> 'int',
			'min' -> 1,
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

__on_start() -> __load_config();

__debug(msg) -> (
	if (global_debug,
		print(format('eb DEBUG: ') + msg);
	);
);

__save_config() -> (
	write_file(system_info():'app_name', 'shared_json', {
		'repeat' -> global_enable_repeat,
		'radius' -> global_radius,
		'height' -> global_height,
		'light_mode' -> global_light_mode,
		'delay' -> global_delay,
		'min_level' -> global_min_level,
	});
);

__load_config() -> (
	data = read_file(system_info():'app_name', 'shared_json');
	if (data != null,
		if (data:'repeat' != null, global_enable_repeat = data:'repeat');
		if (data:'radius' != null, global_radius = data:'radius');
		if (data:'height' != null, global_height = data:'height');
		if (data:'light_mode' != null, global_light_mode = data:'light_mode');
		if (data:'delay' != null, global_delay = data:'delay');
		if (data:'min_level' != null, global_min_level = data:'min_level');
		return(true);
	);
	return(false);
);

__format(f, s) -> format(f + ' ' + s);
__join(words) -> reduce(words, _a + if (_a != '', ' ', '') + _, '');
__vjoin(...words) -> __join(words);
__plural(n, s) -> __format('lb', n) + ' ' + s + if (n == 1, '', 's');

__format_line(string) -> (
	f_cmd(s) -> __format('lb', s);
	f_arg(s) -> __format('bi', s);
	f_desc(s) -> __format('gi', s);
	[cmd_s, desc_s] = split(' - ', string);
	args = [];
	while (cmd_s ~ '<[a-z]+>', length(cmd_s),
		args += cmd_s ~ '<[a-z]+>';
		cmd_s = replace_first(cmd_s, ' <[a-z]+>');
	);
	cmd = if (cmd_s, f_cmd(cmd_s), '');
	for (args, cmd += ' ' + f_arg(_));
	sep = format('g ->');
	return(__prefix() + cmd + ' ' + sep + ' ' + f_desc(desc_s));
);

__tell(msg) -> (
	run(str('tellraw %s {"text":%s}', global_target, escape_nbt(msg)));
);

__on_player_uses_item(pl, item, hand) -> (
	if (hand != 'mainhand', return());
	[itype, icount, inbt] = item;
	if (itype == global_item_type && inbt != null, (
		iname = parse_nbt(inbt:'display':'Name'):'text';
		if (iname == global_item_name, (
			light_area();
		));
	));
);

// Calculate the total volume being scanned
__get_volume() -> (
	dy = if (global_height < 0, -global_height, global_height * 2);
	return(global_radius * global_radius * dy);
);

// Command handler for "/smart_lighter"
help() -> (
	print(format('eb ---------------- Smart Lighter ----------------'));
	print(__format_line(' - This menu'));
	print(__format_line('run - Execute the script'));
	print(__format_line('save - Save current configuration to disk'));
	print(__format_line('load - Reload the most recently saved configuration'));
	print(__format_line('give - Give the player the [Auto Lighter] item'));
	print(__format_line('config - Display current configuration'));
	print(__format_line('config show - Display current configuration compactly'));
	print(__format_line('config repeat - Display current repeat status'));
	print(__format_line('config repeat true - Enable automatic repeat'));
	print(__format_line('config repeat false - Disable automatic repeat'));
	print(__format_line('config radius - Display current radius'));
	print(__format_line('config radius <int> - Set radius value'));
	print(__format_line('config height - Display current height configuration'));
	print(__format_line('config height <int> - Set max height difference. Negative values place torches only below the player'));
	print(__format_line('config light - Display current light-processing configuration'));
	print(__format_line('config light cave - Exclude blocks with adequate sky light'));
	print(__format_line('config light surface - Include blocks with adequate sky light'));
	print(__format_line('config light both - Ignore sky light levels entirely'));
	print(__format_line('config delay - Display current delay (in ticks) between lighting attempts'));
	print(__format_line('config delay <ticks> - Set delay (in ticks) between lighting attempts'));
	print(__format_line('config min - Display current minimum allowable light level'));
	print(__format_line('config min <int> - Change minimum allowable light level'));
);

// Command handler for "/smart_lighter config ..."
config(option, value) -> (
	leader(outer(option)) -> (__prefix() + format('eb config ' + option) + ' -> ');
	lprint(msg) -> print(leader() + msg);
	lvprint(...words) -> lprint(__vjoin(...words));
	if (option == null, (
		config('repeat', null);
		config('radius', null);
		config('height', null);
		config('light', null);
		config('delay', null);
		config('min', null);
		vol = __format('eb', __get_volume());
		print(__vjoin(format('il Current volume:'), vol, format('il blocks')));
	),
	option == 'show_raw', (
		fmtopt(opt) -> __prefix() + format('eb config ' + opt) + ' ';
		oprint(opt, val) -> print(fmtopt(opt) + __format('lb', val));
		oprint('repeat', global_enable_repeat);
		oprint('radius', global_radius);
		oprint('height', global_height);
		oprint('light', global_light_mode);
		oprint('delay', global_delay);
		oprint('min', global_min_level);
	),
	option == 'repeat', (
		if (value != null, global_enable_repeat = value);
		desc = if (global_enable_repeat, 'enabled', 'disabled');
		lvprint('Automatic repeat is', __format('lb', desc));
	),
	option == 'radius', (
		if (value != null, global_radius = value);
		lvprint('Processing', __plural(global_radius, 'block'), 'around the player');
	),
	option == 'height', (
		if (value != null, global_height = value);
		dy = abs(global_height);
		rel = if (global_height < 0, 'below (negative)', 'above and below (positive)');
		lvprint('Scanning', __plural(dy, 'block'), __format('lb', rel), 'the player');
	),
	option == 'light', (
		if (value != null, global_light_mode = value);
		lprint(if (
			global_light_mode == 'cave',
				__vjoin(format('lb Excluding'), format('w blocks with adequate sky light')),
			global_light_mode == 'surface',
				__vjoin(format('lb Including'), format('w blocks with adequate sky light')),
			global_light_mode == 'both',
				__vjoin(format('lb Ignoring'), format('w sky light levels entirely')),
			__vjoin(format('rb Invalid mode!'), __format('rb', global_light_mode))
		));
	),
	option == 'delay', (
		if (value != null, global_delay = value);
		ntick = __plural(global_delay, 'tick');
		nsec = '(' + format(str('l %.2fs', global_delay / 20)) + ')';
		lvprint('Delaying for', ntick, nsec, 'after placing a torch');
	),
	option == 'min', (
		if (value != null, global_min_level = value);
		lvprint('Minimum light level set to', __format('lb', global_min_level));
	),
	( // Should be unreachable unless there's a bug in the code
		print(format('rb Unknown configuration term ', 'ri ' + option));
	));
);

// Command handler for "/smart_lighter save"
save_config() -> (
	__save_config();
	print(format('gi Saved current configuration'));
);

// Command handler for "/smart_lighter load"
load_config() -> (
	if (__load_config(), (
		print(format('gi Loaded configuration:'));
		config(null, null);
	), (
		print(format('rb Failed to load configuration'));
	));
);

// True if a torch can be placed at the given position
__can_have_torch(blk) -> (
	lpos = pos(blk);
	blk_down = block(pos_offset(lpos, 'down'));
	return(solid(blk_down)
		&& blk_down != 'ice'
		&& (air(blk) || blk == 'snow' || blk == 'fire' || blk == 'fern'
			|| blk == 'dead_bush' || blk == 'crimson_roots' || blk == 'warped_roots'
			|| blk == 'nether_sprouts' || blk == 'vine' || blk == 'large_fern'));
);

// True if a torch should be placed at the given location
__should_have_torch(blk, mode, min_level) -> (
	is_unlit = if (mode == 'cave', (
		block_light(blk) < min_level && sky_light(blk) < min_level
	), mode == 'surface', (
		block_light(blk) < min_level && sky_light(blk) >= min_level
	), mode == 'both', (
		block_light(blk) < min_level
	), (
		__tell(str('Invalid mode %s', mode))
	));
	return(is_unlit && __can_have_torch(blk));
);

// Place one torch at a dark location between p1 and p2
__do_light_area(p1, p2) -> (
	[x1, y1, z1] = p1;
	[x2, y2, z2] = p2;
	__debug(str('__do_light_area([%d,%d,%d],[%d,%d,%d])', x1, y1, z1, x2, y2, z2));
	b = block(global_block);
	volume(p1, p2,
		if (__should_have_torch(_, global_light_mode, global_min_level),
			set(_, b);
			return(_); // To avoid placing torches before the area is updated
		);
	);
	return(null);
);

// Deduce the two points defining the configured area; ensures p1:y <= p2:y
__determine_points() -> (
	pl = map(pos(player()), floor(_));
	dy_min = abs(global_height);
	dy_max = if (global_height < 0, 0, abs(global_height));
	p1 = pl - [global_radius, dy_min, global_radius];
	p2 = pl + [global_radius, dy_max, global_radius];
	return([p1, p2]);
);

// Determines the maximum y-level within the configured region
__calculate_max_y() -> (
	[p1, p2] = __determine_points();
	[x1, y1, z1] = p1;
	[x2, y2, z2] = p2;
	max_y = 0;
	volume([x1, y1, z1], [x2, y2, z2], (
		max_y = max(top('surface', _), max_y);
	));
	return(max_y);
);

// Place one torch in the configured area around the player
__light_area_once() -> (
	[p1, p2] = __determine_points();
	[x1, y1, z1] = p1;
	[x2, y2, z2] = p2;
	__debug(str('__light_area_once(%s, %s)', p1, p2));
	if (global_curr_y != null, y1 = global_curr_y);
	if (global_max_y != null, y2 = global_max_y);
	return(if (y1 < y2, (
		__do_light_area([x1, y1, z1], [x2, y2, z2])
	), null));
);

// Light the configured area around the player. Repeats every global_delay
// ticks if global_enable_repeat is true. Resets global_is_running once done.
__light_area() -> (
	b = __light_area_once();
	if (b != null, (
		__tell(str('Set the block %s at %s to %s', b, str(pos(b)), global_block));
		global_curr_y = pos(b):1;
		if (global_enable_repeat, (
			schedule(global_delay, '__light_area')
		));
	), (
		__tell('No blocks found that needed to be lit');
		global_is_running = false;
		global_curr_y = null;
		global_max_y = null;
	));
);

// Command handler for "/smart_lighter run"
light_area() -> (
	__debug('Invoked light_area()');
	if (global_is_running, (
		__tell('light_area() is still running, please wait a moment')
	), (
		// Reset processing variables and start the lighting routine
		global_is_running = true;
		global_curr_y = null;
		global_max_y = __calculate_max_y();
		__debug(str('global_max_y = %d', global_max_y));
		__light_area();
	));
);

// Command handler for "/smart_lighter give"
give_lighter() -> (
	// "Double-escape" necessary to convert the name to a string
	name = str('{"text":%s}', escape_nbt(global_item_name));
	item = str('%s{display:{Name:%s}}', global_item_type, escape_nbt(name));
	[rc, resp, err] = run(str('give %s %s', global_target, item));
	if (!rc, (
		print(__vjoin(
			format('l /give'),
			__format('t', global_target),
			__format('e', item),
			format('r failed:')));
		print(__format('rb', err));
	), (
		print(join(' ', resp));
	));
);

