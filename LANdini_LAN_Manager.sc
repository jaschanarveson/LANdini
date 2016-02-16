/* This class is in charge of building up and maintaining a list of active users on the network.

Slow broadcasts of name/ip/port are sent and received among all users on the network, and when a LANdini_LAN_Network received a broadcast, it sends back a receipt message with its list of users, so that the two users can compare notes and update their respective lists if needed.

Pings are sent out at regular intervlas, and users are dropped if no ping from arrives in time.  Pings also carry information about that user's most recently received GD and OGD message IDs, and requests for re-sends are made in the case of discrepancies.

LANdini_LAN_Manager uses LANdini_User_Collection and LANdini_User.
*/

LANdini_LAN_Manager {

	var <connected=false, i_have_been_warned = false,
	<>lan_check_interval = 0.5, <>drop_user_interval = 2.0,

	<>check_user_interval = 0.3, <>broadcast_interval = 0.731,
	<>ping_interval = 0.329, <>inter_ping_interval = 0.01,
	<>request_wait_interval = 0.001,

	<me, <userlist,

	<to_local_port = 50505, <from_local_port = 50506, <target_app_addr,

	<version = 0.22, gui, connection_task, broadcast_task, drop_user_task,
	ping_and_msg_IDs_task, broadcast_addr,
	<network_responders, <api_responders,
	<sync_server_name, <adjustment_to_get_network_time = 0, sync_task,
	<>sync_request_interval = 0.321, <>in_sync=false, smallest_rtt=1;

	*new {
		^super.new.init;
	}

	init {
		target_app_addr = NetAddr.new("127.0.0.1", to_local_port);
		this.check_for_LAN;
	}

	check_for_LAN {
		if (connection_task.isNil, {
			connection_task = Task({
				inf.do({|i|
					if(Pipe.findValuesForKey("ifconfig", "inet").size>1,
						{
							connection_task.stop;
							connection_task=nil;
							this.init_LAN;
						},{
							"Still looking for a LAN...".postln;
						}
					);
					lan_check_interval.wait;
				});
			}).play(SystemClock);
		});
	}

	init_LAN {
		var allMyIPs, myIP, myPort, myName;

		allMyIPs = Pipe.findValuesForKey("ifconfig", "inet")
		.reject({|ipstring| ipstring[0..2]=="127"})
		.reject({|ipstring| ipstring.last=="0"});

		sync_server_name = "noSyncServer";

		if(allMyIPs.size>0, // if this is true, you're on a LAN
			{
				myIP = allMyIPs.last; // thre's probably be only one, anyway
				myPort = NetAddr.langPort;
				// network name is formatted to drop the ".local" at the end
				myName = Pipe.new("uname -n", "r").getLine.split($.).at(0);
				me = LANdini_User.new(myName, myIP, myPort, this);
				connected = true;
				"Connected to LAN at % on port %".format(me.ip, me.port).postln;
				connected = true;
				userlist = LANdini_User_Collection.new(me);
				userlist.me = me;
				NetAddr.broadcastFlag = true;
				broadcast_addr = NetAddr.broadcast;
				"got to the responders".postln;
				this.set_up_api_responders;
				"got to the network responders".postln;
				this.set_up_network_responders;
				"got to broadcast task".postln;
				this.start_broadcast_task;
				"got to ping task".postln;
				this.start_ping_and_msg_IDs_task;
				"got to show gui".postln;
				this.show_gui;
				SystemClock.sched(3, {
					"starting drop user task".postln;
					this.start_drop_user_task;
				});
			},{
				"Still looking for a LAN...".postln;
				this.check_for_LAN;
			}
		);
	}

	show_gui {
		gui = LANdini_GUI.new(this);
	}

	hide_gui {
		gui.close;
	}

	set_to_local_port {|num|
		if(num.isKindOf(Integer), {
			to_local_port = num;
			target_app_addr.port = to_local_port;
			"new target app addr: %".format(target_app_addr).postln;
		});
	}

	set_from_local_port {|num|
		if(num.isKindOf(Integer), {
			from_local_port = num;
			"new local listening port: %".format(from_local_port).postln;
			this.set_up_api_responders;
		});
	}

	send_to_target_app {|msg|
		target_app_addr.sendMsg(*msg);
	}

	//--------//
	//        //
	//  API   //
	//        //
	//--------//

	// osc messages that user patches use to communicate with LANdiniOSC

	set_up_api_responders {
		"api responder set up".postln;

		if(api_responders.size>0, {
			api_responders.do({|responder|
				responder.clear
			});
		});

		api_responders = IdentityDictionary[];

		[
			['/send', {|msg| this.process_api_msg(*msg)}],

			['/send/GD', {|msg| this.process_api_msg(*msg)}],

			['/send/OGD', {|msg| this.process_api_msg(*msg)}],

			['/numUsers', {|msg| this.receive_get_num_users_request}],

			['/userNames', {|msg| this.receive_get_names_request}],

			['/stageMap', {|msg| this.receive_get_stage_map_request}],

			['/networkTime', {|msg| this.receive_get_network_time_request}],

			['/myName', {|msg| this.receive_my_name_request}]

		].do({|pair|
			var msgName, respFunc;
			msgName=pair[0];
			respFunc=pair[1];
			api_responders.put(msgName,
				OSCFunc(
					path: msgName,
					func: respFunc,
					recvPort: from_local_port
				);
			);
		});

		"set up % new api responders:".format(api_responders.size).postln;
		api_responders.keys.do({|key|
			"% responder listening on port %".format(key, api_responders[key].recvPort).postln;
		});

	}

	receive_my_name_request {
		target_app_addr.sendMsg('/landini/myName', me.name.asString)
	}

	receive_get_names_request {
		var names = userlist.namesButMe.collect({|i| i.asString});
		target_app_addr.sendMsg('/landini/userNames', *names);
	}

	receive_get_num_users_request {
		target_app_addr.sendMsg('/landini/numUsers', userlist.size.asInt);
	}

	receive_get_stage_map_request {
		var stagemap = [];
		userlist.sort({|a,b| a.x_pos < b.x_pos}).do({|usr|
			stagemap = stagemap.addAll([usr.name.asString, usr.x_pos.asInt, usr.y_pos.asInt]);
		});
		target_app_addr.sendMsg('/landini/stageMap', *stagemap);
	}

	receive_get_network_time_request {
		var time;
		if(in_sync==true,
			{time = this.network_time},
			{time = -1}
		);
		target_app_addr.sendMsg('/landini/networkTime', time);
	}

	process_api_msg {|protocol...msg|
		var name, usr;

		name = msg[0].asString;
		if( (name == "all") || (name == "allButMe") ,
			{
				usr = name;
			},
			{
				usr = {userlist.at(name)}.try;
			}
		);
		msg = msg.drop(1);

		case(

			{usr=="all"}, {
				switch(protocol,

					'/send', {
						userlist.do({|usr|
							this.send(usr, msg);
						})
					},

					'/send/GD', {
						userlist.do({|usr|
							this.send_GD(usr, msg);
						});
					},

					'/send/OGD', {
						userlist.do({|usr|
							this.send_OGD(usr, msg);
						});
					}
				);
			},

			{usr=="allButMe"}, {
				switch(protocol,

					'/send', {
						userlist.doNotMe({|usr|
							this.send(usr, msg);
						})
					},

					'/send/GD', {
						userlist.doNotMe({|usr|
							this.send_GD(usr, msg);
						});
					},

					'/send/OGD', {
						userlist.doNotMe({|usr|
							this.send_OGD(usr, msg);
						});
					}
				);
			},

			{usr.isKindOf(LANdini_User)}, {
				switch(protocol,
					'/send', {this.send(usr, msg)},
					'/send/GD', {this.send_GD(usr, msg)},
					'/send/OGD', {this.send_OGD(usr, msg)}
				);
			},

			{usr.isNil}, {"invalid user name".postln}
		);

	}


	//-------------------//
	//                   //
	//  network stuff    //
	//                   //
	//-------------------//


	// responders for osc messages coming from other copies of LANdiniOSC on the network

	set_up_network_responders {

		if(network_responders.size>0, {
			network_responders.do({|responder|
				responder.clear;
			});
		});

		network_responders = IdentityDictionary[];

		[
			// .drop(1) is used to stip away the redundant osc paths
			['/landini/member/broadcast',
				{|msg| this.receive_member_broadcast( *msg.drop(1) )}
			],

			['/landini/member/reply',
				{|msg| this.receive_member_reply( *msg.drop(1) )}
			],

			['/landini/member/ping_and_msg_IDs',
				{|msg| this.receive_ping_and_msg_IDs( *msg.drop(1) )}
			],

			['/landini/msg',
				{|msg| this.receive_msg( *msg.drop(1) )}
			],

			['/landini/msg/GD',
				{|msg| this.receive_GD( *msg.drop(1) )}
			],

			['/landini/msg/OGD',
				{|msg| this.receive_OGD( *msg.drop(1) )}
			],

			['/landini/request/missing/GD',
				{|msg| this.receive_missing_GD_request( *msg.drop(1) )}
			],

			['/landini/request/missing/OGD',
				{|msg| this.receive_missing_OGD_request( *msg.drop(1) )}
			],

			['/landini/sync/new_server',
				{|msg| this.receive_new_sync_server( *msg.drop(1) )};
			],

			['/landini/sync/request',
				{|msg| this.receive_sync_request( *msg.drop(1) )}
			],

			['/landini/sync/reply',
				{|msg| this.receive_sync_reply( *msg.drop(1) )}
			],

			['/landini/sync/stop',
				{|msg| this.receive_sync_stop( *msg.drop(1) )}
			],

			['/landini/sync/got_stop',
				{|msg| this.receive_sync_stop_acknowledgement( *msg.drop(1) )}
			]

		].do({|pair|
			var msgName, respFunc;
			msgName = pair[0];
			respFunc = pair[1];
			network_responders.put(msgName,
				OSCFunc(
					path: msgName,
					func: respFunc
				);
			);
		});
	}

	//-------------------//
	//                   //
	//  broadcast stuff  //
	//                   //
	//-------------------//

	start_broadcast_task {
		if(broadcast_task.isNil, {
			broadcast_task = Task({
				10.do({
					broadcast_addr.sendMsg(
						'/landini/member/broadcast',
						me.name, me.ip, me.port, version
					);
					0.1.wait;
				});

				inf.do({
					broadcast_addr.sendMsg(
						'/landini/member/broadcast',
						me.name, me.ip, me.port, version
					);
					broadcast_interval.wait;
				});
			}).play(SystemClock);
		});
	}

	receive_member_broadcast {arg theirName, theirIP, theirPort, theirVersion;
		var reply_msg, fromUsr;

		theirVersion = theirVersion.asFloat.round(0.01);

		/*
		This has been simplified a lot: now, when I receive a broadcast, I check if I have
		that person already and, if I don't, I just send them my info, not the whole
		user list.  If I do have them, I don't do anything.
		*/

		if( (theirVersion > version) && (i_have_been_warned == false), {
			var tempWin, tempText;

			i_have_been_warned = true;

			Task({
				tempWin = Window.new(
					"warning",
					Rect.aboutPoint(Window.screenBounds.center, 200, 100)
				);
				tempWin.background = Color.red;

				tempText = StaticText.new(tempWin, Rect(0, 0, 400, 100));
				tempText.stringColor = Color.white;
				tempText.string = "You are not using the latest version of LANdini.\nYou are using version %,\nand someone else is using version %".format(version, theirVersion);
				tempText.font = Font.new("Calibri", 16);
				tempText.align = 'center';

				tempWin.front;
			}).play(AppClock);
		});

		if(theirName.asString != me.name.asString, {

			fromUsr = {userlist.at(theirIP.asString)}.try;

			if(fromUsr.isNil, {
				// newfix Feb 18: make sure theirPort is an Int
				var nameAsString, ipAsString, portAsInt;
				nameAsString = theirName.asString;
				ipAsString = theirIP.asString;
				portAsInt = theirPort.asInteger;
				Task({
					this.assimilate_member_info(nameAsString, ipAsString, portAsInt);
					fromUsr = {userlist.at(ipAsString)}.try;
				}).play(SystemClock);

				if(fromUsr.isNil,
					{
						"user % mysteriously failed to be added to the user list".format(theirName).postln;
					},
					{
						"REPLY to %".format(theirIP).postln;
						fromUsr.addr.sendMsg(
							'/landini/member/reply',
							me.name.asString,
							me.ip.asString,
							me.port.asInt
						);
					}
				);
			});


		});
	}

	receive_member_reply {arg theirName, theirIP, theirPort;
		var replyingUser;

		replyingUser = {userlist.at(theirName.asString)}.try;

		if(replyingUser.isNil, {
			this.assimilate_member_info(theirName.asString, theirIP.asString, theirPort.asInt);
		});
	}


	//------------------------//
	//                        //
	// ping and msg ID stuff  //
	//                        //
	//------------------------//

	start_ping_and_msg_IDs_task {
		if(ping_and_msg_IDs_task.isNil, {
			ping_and_msg_IDs_task = Task({
				loop({
					userlist.doNotMe({|usr|
						usr.addr.sendMsg(
							'/landini/member/ping_and_msg_IDs',
							me.name,
							userlist.me.x_pos,
							userlist.me.y_pos,
							usr.last_outgoing_GD_ID,
							usr.min_GD_ID,
							usr.last_outgoing_OGD_ID,
							usr.last_performed_OGD_ID,
							sync_server_name
						);
						inter_ping_interval.wait;
					});
					(ping_interval - (userlist.size - 1 * inter_ping_interval)).wait;
				});
			}).play(SystemClock);
		});
	}

	receive_ping_and_msg_IDs {arg name, theirX, theirY, last_gd_ID, their_min_GD, last_ogd_ID, last_performed_OGD, sync_server_ping_name;

		var usr = {userlist.at(name.asString)}.try;

		if(usr.notNil, {
			usr.receive_ping(theirX, theirY, last_gd_ID, their_min_GD, last_ogd_ID, last_performed_OGD);
		});

		if( (sync_server_ping_name == "noSyncServer") || (sync_server_ping_name != sync_server_name),
			{
				this.deal_with_new_sync_server_name(sync_server_ping_name);
			}
		);
	}


	// group stuff - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

	assimilate_member_info {arg theirName, theirIP, theirPort;
		var usr;
		"assimilate_member_info called on % at % on port %".format(theirName, theirIP, theirPort).postln;
		usr = {LANdini_User.new(theirName, theirIP, theirPort, this)}.try;
		if(usr.notNil, {
			"adding user %".format(theirName).postln;
			userlist.add(usr);
		});
	}

	start_drop_user_task {
		if(drop_user_task.isNil, {
			drop_user_task = Task({
				loop({
					userlist.doNotMe({|usr|
						if( (Main.elapsedTime - usr.last_ping) > drop_user_interval, {
							"dropped %".format(usr.name).postln;
							if(usr.name.asString == sync_server_name.asString, {
								this.stop_sync_task;
								this.reset_sync_vars;
							});
							userlist.remove(usr);
						});
					});
					check_user_interval.wait;
				});
			}).play(SystemClock);
		});
	}


	//- - - - - - //
	//            //
	// time stuff //
	//            //
	//- - - - - - //

	network_time {
		var time;
		time = Main.elapsedTime + adjustment_to_get_network_time;
		^time;
	}

	become_sync_server {
		"becoming sync server!".postln;
		this.stop_sync_task;
		sync_server_name = me.name;
		adjustment_to_get_network_time = 0;
		in_sync = true;
	}

	deal_with_new_sync_server_name {arg new_name;
		if(new_name.asString == "noSyncServer",
			{
				var allNames;
				"pinged sync server name is noSyncServer".postln;
				allNames = userlist.names; // .names now includes "me" - use .namesButMe to exclude "me"
				allNames = allNames.sort;
				"here's allNames: %".format(allNames).postln;
				if(allNames[0] == me.name, { // new sync servers are decided alphabetically...
					if(sync_server_name != me.name,
						{
							this.become_sync_server;
						},
						{
							"i am already the sync server".postln;
						}
					);
				});
			},
			{
				var usr;
				usr = {userlist.at(new_name)}.try;
				if(usr.notNil, {
					sync_server_name = new_name;
				});
				this.start_sync_task; // does nothing if the task is already running
			}
		);
	}

	reset_sync_vars {
		adjustment_to_get_network_time = 0;
		in_sync = false;
		sync_server_name = "noSyncServer";
		smallest_rtt = 1;
	}

	receive_sync_request {arg theirName, theirTime;
		var usr = {userlist.at(theirName)}.try;
		if( usr.notNil,
			{
				if(usr.name != me.name,
					{
						usr.addr.sendMsg(
							'/landini/sync/reply', me.name, theirTime, Main.elapsedTime
						);
					},
					{
						"i should not be sending myself sync requests".postln;
						this.stop_sync_task;
					}
				);
			},
			{
				"time server is not in the userlist".postln;
				this.stop_sync_task;
				this.reset_sync_vars;
			}
		);
	}

	receive_sync_reply {arg timeServerName, myOldTime, timeServerTime;
		var usr = {userlist.at(timeServerName)}.try;
		if( (usr.notNil) && (timeServerName == sync_server_name),
			{
				var now, rtt, serverTime;
				in_sync = true;
				now = Main.elapsedTime;
				rtt = now - myOldTime;
				smallest_rtt = min(smallest_rtt, rtt);
				serverTime = timeServerTime + (smallest_rtt/2);
				adjustment_to_get_network_time = serverTime - now;
			},
			{
				"stopping sync task becuase of sync server name discrepancy".postln;
				this.stop_sync_task;
				this.reset_sync_vars;
			}
		);
	}

	start_sync_task {
		if(sync_task.isNil, {
			sync_task = Task({
				var server;
				loop({
					server = {userlist.at(sync_server_name)}.try;
					if(server.notNil, {
						server.addr.sendMsg(
							'/landini/sync/request', me.name, Main.elapsedTime
						);
					});
					sync_request_interval.wait;
				});
			}).play(SystemClock);
		});
	}

	stop_sync_task {
		if(sync_task.notNil, {
			sync_task.stop;
			sync_task = nil;
		});
	}

	//----------------------//
	//                      //
	// normal send methods  //
	//                      //
	//----------------------//

	send {arg usr, msg;
		if(usr.notNil, {
			usr.send(msg);
		});
	}

	receive_msg {arg from ...args;
		var usr = {userlist.at(from.asString)}.try;
		if(usr.notNil, {
			usr.receive_msg(args);
		});
	}


	//-----------------------------------//
	//                                   //
	// GD (Guaranteed Delivery) methods  //
	//                                   //
	//-----------------------------------//

	send_GD {arg usr, msg;
		if(usr.notNil, {
			usr.send_GD(msg);
		});
	}

	receive_GD {arg from, id ...args;
		var usr = {userlist.at(from.asString)}.try;
		if(usr.notNil, {
			usr.receive_GD(id, args);
		});
	}

	receive_missing_GD_request {arg from ...missed_IDs;
		var usr;
		usr = {userlist.at(from)}.try;
		if(usr.notNil, {
			usr.receive_missing_GD_request(missed_IDs);
		});
	}



	//--------------------------------------------//
	//                                            //
	// OGD (Ordered Guaranteed Delivery) methods  //
	//                                            //
	//--------------------------------------------//

	send_OGD {arg usr, msg;
		if(usr.notNil, {
			usr.send_OGD(msg);
		});
	}

	receive_OGD {arg from, id ...args;
		var usr = {userlist.at(from.asString)}.try;
		if(usr.notNil, {
			usr.receive_OGD(id, args);
		});
	}

	receive_missing_OGD_request {arg from ...missed_IDs;
		var usr;
		usr = {userlist.at(from)}.try;
		if(usr.notNil, {
			usr.receive_missing_OGD_request(missed_IDs);
		});
	}

}
