LANdini_GUI {
	var
	// lan manager vars
	network, connected=false, from_local_port=0, to_local_port=0, user_list,

	// gui vars
	color, themes,

	// gui fields
	win,
	connection_status_txt, local_status_txt, user_list_label,
	from_local_port_label, from_local_port_numbox,
	to_local_port_label, to_local_port_numbox,
	see_local_in_button, see_lan_pings_button,
	see_lan_in_button, stage_map_button,
	test_button,
	network_time_txt, become_time_server_button, stop_sync_button,

	// gui update vars
	<>update_interval=0.12, osc_monitor_update_interval=0.2, update_gui_task;


	*new {arg theNetwork;
		^super.new.init(theNetwork);
	}

	init {arg theNetwork;
		if(theNetwork.isKindOf(LANdini_LAN_Manager),
			{
				network = theNetwork;
				"network found, and is version number %".format(network.version).postln;
				this.continueInit;
			},
			{
				Error(
					"invalid LANdini_LAN_Manager value: % is kind of %"
					.format(theNetwork, theNetwork.class)
				).throw;
			}
		);
	}

	continueInit {arg theNetwork;

		themes = IdentityDictionary[];

		themes.put(\warmorange, IdentityDictionary[]);
		themes[\warmorange].put(\primary, ["#BC7C2E", "#8E6A3D", "#7B4B0F", "#DEA560", "#DEB482"]);
		themes[\warmorange].put(\complementary, ["#235878", "#2A485A", "#0B364F", "#5796BB", "#72A0BB"]);

		themes.put(\coolblue, IdentityDictionary[]);
		themes[\coolblue].put(\primary, [ "#4383D1", "#7195C2", "#84B4F0", "#215A9F", "#103A6C" ]);
		themes[\coolblue].put(\complementary, [ "#E1AE90", "#E5BBA1", "#F4C9B0", "#A86944", "#8D5F44" ]);

		color = IdentityDictionary[];
		//Color.fromHexString("DCA787").blend(Color.fromHexString("#E5B498")).hexString
		this.set_theme(\coolblue);

		/*
		[\primary, \complementary].do({|key|
		color[key] = color[key].collect({|i| i.complementary})
		});
		*/
		color.put(\background, color[\primary][2] );
		color.put(\view_bg, color[\primary][0]);
		color.put(\highlight, color[\complementary][3] );
		color.put(\textDarker, color[\primary][4] );
		color.put(\textLighter, color[\complementary][2] );
		color.put(\lanListLighter, color[\complementary][1] );
		color.put(\lanListDarker, color[\complementary][0] );
		color.put(\numbox, color[\complementary][4] );
		color.put(\myNameTextColor, Color.green.blend(color[\textDarker]));
		color.put(\myNameTextBackground, Color.black.blend(color[\background]));



		this.make_gui;
	}

	set_theme {arg name;
		var theme = themes[name.asSymbol];
		if (theme.notNil, {
			color.put(\primary, theme[\primary].collect({|i| Color.fromHexString(i)}));
			color.put(\complementary, theme[\complementary].collect({|i| Color.fromHexString(i)}) );
		});
	}

	make_gui {
		Task({

			"win".postln;

			win = Window("LANdini, version %".format(network.version));
			win.view.background = color[\background];

			"com port".postln;

			from_local_port_label = StaticText().string_(" change local receive port: ");
			from_local_port_label.align('right');
			from_local_port_label.background_(color[\view_bg]);
			from_local_port_label.stringColor_(color[\textLighter]);
			from_local_port_label.maxHeight_(30);
			from_local_port_label.minWidth_(250);

			from_local_port_numbox = NumberBox().value_(0);
			from_local_port_numbox.stringColor_(color[\textLighter]);
			from_local_port_numbox.maxWidth_(50);
			from_local_port_numbox.maxHeight_(30);
			from_local_port_numbox.action = {|num|
				var val = num.value.asInteger;
				network.set_from_local_port(val);
			};

			to_local_port_label = StaticText().string_(" change local send port: ");
			to_local_port_label.align('right');
			to_local_port_label.background_(color[\view_bg]);
			to_local_port_label.stringColor_(color[\textLighter]);
			to_local_port_label.maxHeight_(30);
			to_local_port_label.minWidth_(250);

			to_local_port_numbox = NumberBox().value_(0);
			to_local_port_numbox.stringColor_(color[\textLighter]);
			to_local_port_numbox.maxWidth_(50);
			to_local_port_numbox.maxHeight_(30);
			to_local_port_numbox.action = {|num|
				var val = num.value.asInteger;
				network.set_to_local_port(val);
			};

			"connection status".postln;

			network_time_txt = StaticText().string_(" network time: 0");
			network_time_txt.background_(color[\view_bg]);
			network_time_txt.stringColor_(color[\textLighter]);
			network_time_txt.maxHeight_(30);
			network_time_txt.minWidth_(400);

			connection_status_txt = StaticText().string_(" connection status: looking for LAN...");
			connection_status_txt.background_(color[\view_bg]);
			connection_status_txt.stringColor_(color[\textLighter]);
			connection_status_txt.maxHeight_(30);
			connection_status_txt.minWidth_(400);

			"local port status".postln;

			local_status_txt = StaticText().string_(
				" local ports: in=% | out=%".format(from_local_port, to_local_port)
			);
			local_status_txt.background_(color[\view_bg]);
			local_status_txt.stringColor_(color[\textLighter]);
			local_status_txt.maxHeight_(30);
			local_status_txt.minWidth_(400);

			"buttons".postln;

			see_local_in_button = Button.new().states_(
				[ ["see local in", color[\view_bg], color[\textLighter] ] ]
			);
			see_local_in_button.action = {this.create_OSC_monitoring_window(from_local_port)};

			see_lan_in_button = Button.new().states_(
				[ ["see LAN in", color[\view_bg], color[\textLighter]] ]
			);
			see_lan_in_button.action = {this.create_OSC_monitoring_window(network.me.port, false)};

			see_lan_pings_button = Button.new().states_(
				[ ["see LAN pings", color[\view_bg], color[\textLighter]] ]
			);
			see_lan_pings_button.action = {this.create_OSC_monitoring_window(network.me.port, true)};

			stage_map_button = Button.new().states_(
				[ ["see stage map", color[\view_bg], color[\textLighter]] ]
			);
			stage_map_button.action = {LANdini_StageMap.new(network)};


			test_button = Button.new().states_(
				[
					[
						"send [/landini/test, 0, 2.3] to local app",
						color[\view_bg],
						color[\textLighter]
					]
				]
			);

			test_button.action = {network.send_to_target_app(['/landini/test', 0, 2.3])};


			"tree view".postln;

			user_list_label = StaticText().string_(" LAN User List: ");
			user_list_label.align('right');
			user_list_label.background_(color[\view_bg]);
			user_list_label.stringColor_(color[\textLighter]);

			user_list = TreeView();
			user_list.background_(color[\view_bg]);
			user_list.columns = ["name", "ip", "port"];

			"layout".postln;

			win.layout = VLayout(
				connection_status_txt,
				network_time_txt,
				10,
				local_status_txt,
				8,
				HLayout(
					[from_local_port_label, align: 'left'],
					[from_local_port_numbox, align: 'left']
				),
				HLayout(
					[to_local_port_label, align: 'left'],
					[to_local_port_numbox, align: 'left']
				),
				8,
				HLayout(
					see_local_in_button,
					see_lan_in_button
				),
				HLayout(
					see_lan_pings_button,
					stage_map_button
				),
				test_button,
				30,
				user_list_label,
				user_list
			);
			win.front;

			win.onClose_({this.stop_update_gui_task});

			this.start_update_gui_task;
		}).play(AppClock);
	}

	update_gui {

		var lan_list_names, gui_list_names, my_name;

		if(network.connected != connected, {
			connected = network.connected;
			case(
				{connected==true}, {
					connection_status_txt.string_(
						" connection status: \n connected to LAN at %, on port %"
						.format(network.me.ip, network.me.port)
					);
				},
				{connected==false}, {
					connection_status_txt.string(
						" connection status: looking for LAN..."
					);
				}
			);
		});

		if(network.to_local_port != to_local_port, {
			to_local_port = network.to_local_port;
			to_local_port_numbox.value_(to_local_port);
			local_status_txt.string_(
				" local ports: in=% | out=%".format(from_local_port, to_local_port);
			);
		});

		if(network.from_local_port != from_local_port, {
			from_local_port = network.from_local_port;
			from_local_port_numbox.value_(from_local_port);
			local_status_txt.string_(
				" local ports: in=% | out=%".format(from_local_port, to_local_port);
			);
		});

		if(network.in_sync==true,
			{
				if(network.sync_server_name.asString == network.me.name,
					{
						network_time_txt.string = " serving network time: %".format(network.network_time);
					},
					{
						//newfix Jan 7: display the time server
						network_time_txt.string = " receiving network time from %: %".format(
							network.sync_server_name.asString,
							network.network_time
						);
					}
				);
			},
			{
				network_time_txt.string = " local time: %".format(network.network_time);
			}
		);

		lan_list_names = network.userlist.names.asSet; // .names now includes "me"
		gui_list_names = user_list.numItems.collect({|i|
			user_list.itemAt(i).strings[0].asString
		}).asSet;

		if ( (lan_list_names -- gui_list_names).size > 0, {
			"updating the user list!".postln;

			user_list.numItems.do({
				user_list.removeItem(user_list.itemAt(0))
			});

			"removed old items".postln;

			"adding me...".postln;

			user_list.addItem(
				[
					network.userlist.me.name.asString,
					network.userlist.me.addr.ip.asString,
					network.userlist.me.addr.port.asString
				]
			);

			"added me".postln;

			network.userlist.doNotMe({|usr|
				user_list.addItem(
					[
						usr.name.asString,
						usr.addr.ip.asString,
						usr.addr.port.asString
					]
				);
			});

			"added everyone else".postln;

			user_list.numItems.do({|i|
				user_list.itemAt(i).textColors_( color[\textDarker] ! user_list.numColumns );
				if(i.even,
					{
						user_list.itemAt(i).colors_(
							color[\lanListLighter] ! user_list.numColumns
						);
					},
					{
						user_list.itemAt(i).colors_(
							color[\lanListDarker] ! user_list.numColumns
						);
					}
				);
			});

			"did the row coloring".postln;

			user_list.itemAt(0).textColors_( color[\myNameTextColor] ! user_list.numColumns );
			user_list.itemAt(0).colors_( color[\myNameTextBackground] ! user_list.numColumns );

			"colored me green".postln;
		}); // end if


	}

	start_update_gui_task {
		if(update_gui_task.isNil, {
			update_gui_task = Task({
				loop({
					this.update_gui;
					update_interval.wait;
				});
			}).play(AppClock);
		});
	}

	stop_update_gui_task {
		if(update_gui_task.notNil, {
			update_gui_task.stop;
			update_gui_task = nil;
		});
	}

	close {
		win.close;
	}

	// OSC monitoring windows

	create_OSC_monitoring_window {arg port, is_ping_monitor=false;
		Task({
			var window, filtertxt, filter, output, display, display_text;
			var listeners, source, startingX, updateTask, lastIncomingMsgTime, lastGUIUpdateTime;

			case(
				{port==from_local_port}, {
					source="incoming local OSC";
					listeners = network.api_responders.keys;
					startingX = 0;
				},

				{ (port==network.me.port) && (is_ping_monitor==true) }, {
					source="LAN pings, etc";
					listeners = network.network_responders.keys.select({|i|
						i.asString.contains("member")
					});
					startingX = 400;
				},

				{ (port==network.me.port) && (is_ping_monitor==false) }, {
					source="incoming LAN OSC";
					listeners = network.network_responders.keys.reject({|i|
						i.asString.contains("member")
					});
					startingX = 800;
				},

				{
					Error("listening window given unused port number: ".format(port)).throw;
				}
			);

			window = Window.new(
				"listening to % on port %".format(source, port),
				Rect(startingX, 0, 600, 400);
			);

			output = "";

			filtertxt = StaticText.new();
			filtertxt.string = "filter incoming messages by typing in the field below:";
			filter = TextField.new();
			display = TextView.new();
			display.background = color[\view_bg].blend(Color.black);

			window.layout = VLayout(filtertxt, filter, display);

			lastIncomingMsgTime = 0;
			lastGUIUpdateTime = Main.elapsedTime;

			listeners = listeners.collect({|msgName|
				OSCFunc(
					path: msgName,
					recvPort: port,
					func: {|msg, time|
						display_text.value(time, msg);
						lastIncomingMsgTime = Main.elapsedTime;
					}
				);
			});

			display_text = {|time, msg|
				Task({
					var osc_path, filter_txt;
					filter_txt = filter.string;
					osc_path = msg[0].asString;
					if( (filter_txt=="") or: (osc_path.contains(filter_txt)), {
						var temp;
						temp = time.asString ++ "   " ++ msg.asString ++ "\n";
						output = temp ++ output;
					});
				}).play(AppClock);
			};

			"listeners for %:".format(source).postln;
			listeners.do({|i| i.postln});

			updateTask = Task({
				loop({
					if(lastIncomingMsgTime > lastGUIUpdateTime, {
						display.string = output;
						display.stringColor = color[\textLighter];
					});
					lastGUIUpdateTime = Main.elapsedTime;
					osc_monitor_update_interval.wait;
				});
			}).play(AppClock);

			window.onClose = {
				listeners.do({|responder| responder.clear});
				updateTask.stop;
			};

			window.front;
		}).play(AppClock);
	}

}
