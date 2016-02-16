/* This is a simple user profile, treated as a data type by LANdini_User_Collection and LANdini_LAN_Manager. Info about networking parameters like name, ip, and port are stored along with a time-stamp of the last time a ping was received from this user, which is used for maintaining a current list of active users on the LANdini_LAN_Manager

Right now, LANdini_User also maintains info about message IDs for Guaranteed Delivery and Guaranteed Ordered Delivery.  This profile is used to store info about the communication between this host computer and the compuer represented by this user profile.

If I am a user and X is a user, any GD or OGD message I sent to X will be indexed and stored in the profile I have for X on my computer.  Likewise, any GD or OGD messages received from X will have their IDs updated in my profile for X, and any messages that need to be queued for excecution in OGD will also be stored in my profile for X.  In this way, the myriad book-keeping between me and all the other users on a network can be kept tidy and compartamentalized.*/

LANdini_User {
	var <>name, <>ip, <>port, <>addr, <>last_ping, <>network,

	// guaranteed delivery vars
	<last_outgoing_GD_ID, last_incoming_GD_ID, performed_GD_IDs, <>min_GD_ID, sent_GD_msgs,

	// ordered guaranteed delivery vars
	<last_outgoing_OGD_ID, last_incoming_OGD_ID, <last_performed_OGD_ID,
	missing_OGD_IDs, msg_queue_for_OGD, sent_OGD_msgs,

	// sync vars
	new_sync_server_announcement_task, stop_sync_announcement_task,

	// these are for the user-map
	<>x_pos, <>y_pos;

	*new {arg theName, theIP, thePort, theNetwork;
		^super.new.init(theName, theIP, thePort, theNetwork);
	}

	init {arg theName, theIP, thePort, theNetwork;
		"~ creating user profile for %, at % on %".format(theName, theIP, thePort).postln;

		if(theName.isKindOf(String) || theName.isKindOf(Symbol),
			{
				name = theName.asString;
			},{
				Error(
					"invalid data type: name wasn't a string: %, is kind of %"
					.format(theName, theName.class)
				).throw;
			}
		);

		if(theIP.isKindOf(String)  || theIP.isKindOf(Symbol),
			{
				theIP = theIP.asString;
				if(theIP.split($.).size==4,
					{
						ip=theIP;
					},{
						Error(
							"ip address not in proper IPv4 format: %".format(theIP)
						).throw;
					}
				);
			},{
				Error(
					"invalid data type: ip wasn't a string: %".format(theIP)
				).throw;
			}
		);

		if(thePort.isKindOf(Integer),
			{
				port = thePort;
			},{
				Error(
					"invalid data type: port wasn't an integer: % is kind of %"
					.format(thePort, thePort.class)
				).throw;
			}
		);

		if(theNetwork.isKindOf(LANdini_LAN_Manager),
			{
				network = theNetwork;
			},
			{
				Error(
					"invalid LANdini_LAN_Manager value: % is kind of %"
					.format(theNetwork, theNetwork.class)
				).throw;
			}
		);

		// guaranteed delivery book-keeping
		last_outgoing_GD_ID = -1;
		last_incoming_GD_ID = -1;
		min_GD_ID = -1;
		performed_GD_IDs = Set[];
		sent_GD_msgs = IdentityDictionary[];

		// guaranteed ordered delivery book-keeping
		last_outgoing_OGD_ID = -1;
		last_incoming_OGD_ID = -1;
		last_performed_OGD_ID = -1;
		missing_OGD_IDs = Set[];
		msg_queue_for_OGD = IdentityDictionary[];
		sent_OGD_msgs = IdentityDictionary[];

		// basic net ID stuff
		addr = NetAddr(ip, port);
		last_ping = Main.elapsedTime;

		// stage map stuff
		x_pos = -1;
		y_pos = -1;

	}

	== {arg aUser;
		if(aUser.isKindOf(LANdini_User),
			{
				if( (aUser.name==this.name) && (aUser.ip==this.ip) && (aUser.port==this.port),
					{^true},
					{^false}
				);
			},{
				^false;
			}
		);
	}

	!= {arg aUser;
		^(aUser==this).not;
	}

	=== {arg aUser;
		^(aUser==this)
	}


	//---------------//
	//               //
	//  ping method  //
	//               //
	//---------------//

	receive_ping {
		arg newX, newY, last_GD_ID_they_sent_me, their_min_GD,
		last_OGD_ID_they_sent_me, last_OGD_they_performed;
		var obsolete_GD_IDs, temp_missing_GDs, obsolete_OGD_IDs;

		x_pos = newX;
		y_pos = newY;

		last_ping = Main.elapsedTime;

		// GD stuff:

		// - update which msg i should be at

		if ( last_GD_ID_they_sent_me > last_incoming_GD_ID, {
			last_incoming_GD_ID = last_GD_ID_they_sent_me;
		});

		// - remove un-needed stored msgs

		obsolete_GD_IDs = sent_GD_msgs.keys.asArray.select({|i| i < their_min_GD});
		if(obsolete_GD_IDs.size>0, {
			obsolete_GD_IDs.do({|id|
				sent_GD_msgs.removeAt(id);
			});
		});

		// - request missing msgs, if any
		// the checking for missing IDs has been folded in to this function

		this.request_missing_GDs(last_incoming_GD_ID);


		// OGD

		obsolete_OGD_IDs = sent_OGD_msgs.keys.select({|i| i <= last_OGD_they_performed});
		obsolete_OGD_IDs.do({|id|
			sent_OGD_msgs.removeAt(id);
		});

		if( last_OGD_ID_they_sent_me > last_performed_OGD_ID, {
			var missing, ids;
			ids = msg_queue_for_OGD.keys;  // this is a Set
			missing = ( (last_performed_OGD_ID + 1) .. last_OGD_ID_they_sent_me ).difference(ids);
			this.request_missing_OGDs(missing);
		});

	}

	//-------------//
	//             //
	// sync stuff  //
	//             //
	//-------------//


	start_announcing_new_sync_server {
		if(new_sync_server_announcement_task.isNil, {
			new_sync_server_announcement_task = Task({
				loop({
					addr.sendMsg('/landini/sync/new_server', network.me.name);
					0.5.wait;
				})
			}).play(SystemClock);
		});
	}

	stop_announcing_new_sync_server {
		if(new_sync_server_announcement_task.notNil, {
			new_sync_server_announcement_task.stop;
			new_sync_server_announcement_task = nil;
		});
	}

	start_announcing_stop_sync {
		if(stop_sync_announcement_task.isNil, {
			stop_sync_announcement_task = Task({
				loop({
					addr.sendMsg('/landini/sync/stop', network.me.name);
					0.5.wait;
				})
			}).play(SystemClock);
		});
	}

	stop_announcing_stop_sync {
		if(stop_sync_announcement_task.notNil, {
			stop_sync_announcement_task.stop;
			stop_sync_announcement_task = nil;
		});
	}


	//----------------------//
	//                      //
	// normal send methods  //
	//                      //
	//----------------------//

	send {arg msg;
		addr.sendMsg('/landini/msg', network.me.name, *msg);
	}

	receive_msg {arg msg;
		network.target_app_addr.sendMsg(*msg);
	}


	//-----------------------------------//
	//                                   //
	// GD (Guaranteed Delivery) methods  //
	//                                   //
	//-----------------------------------//

	// sending and recieving GD stuff

	send_GD {arg msg;
		var id;
		id = last_outgoing_GD_ID + 1;
		last_outgoing_GD_ID = id;
		sent_GD_msgs.put(id, msg);
		addr.sendMsg('/landini/msg/GD', network.me.name, id, *msg);
	}

	receive_GD {arg id, msg;
		if( (id > min_GD_ID) && (performed_GD_IDs.includes(id).not), {
			var ids, min;
			ids = performed_GD_IDs.copy;
			min = min_GD_ID.copy;

			network.target_app_addr.sendMsg(*msg);
			ids.add(id);
			while(
				{
					ids.includes(min+1);
				},
				{
					ids.remove(min+1);
					min = min+1;
				}
			);

			min_GD_ID = min;
			performed_GD_IDs = ids;
		});

		this.request_missing_GDs(id);  // see if any are missing, after all that
	}

	request_missing_GDs {arg most_recent_GD_ID;
		var missing;
		if(min_GD_ID < most_recent_GD_ID, {
			missing = (min_GD_ID .. most_recent_GD_ID).drop(1).drop(-1).difference(performed_GD_IDs);
			if(missing.size > 0, {
				addr.sendMsg('/landini/request/missing/GD', network.me.name, *missing);
			});
		});
	}

	receive_missing_GD_request {arg missed_IDs;
		missed_IDs.do({|missedID|
			var msg = sent_GD_msgs[missedID];
			if (msg.notNil, {
				addr.sendMsg('/landini/msg/GD', network.me.name, missedID, *msg);
			});
		});
	}

	//--------------------------------------------//
	//                                            //
	// OGD (Ordered Guaranteed Delivery) methods  //
	//                                            //
	//--------------------------------------------//

	send_OGD {arg msg;
		var id;
		id = last_outgoing_OGD_ID + 1;
		last_outgoing_OGD_ID = id;
		sent_OGD_msgs.put(id, msg);
		addr.sendMsg('/landini/msg/OGD', network.me.name, id, *msg);
	}

	receive_OGD {arg id, msg;
		if(id > last_performed_OGD_ID, {
			var nextID, nextMsg;
			if(id > last_incoming_OGD_ID, {
				if( (id - last_incoming_OGD_ID) > 1, {
					var missing = ( (last_incoming_OGD_ID+ 1) .. id );
					this.request_missing_OGDs(missing);
				});
				last_incoming_OGD_ID = id;
			});
			nextID = last_performed_OGD_ID + 1;
			if(msg_queue_for_OGD[id].isNil, {
				msg_queue_for_OGD.put(id, msg);
			});
			missing_OGD_IDs.remove(id); // no harm if id isn't actually missing
			while(
				{
					nextMsg = msg_queue_for_OGD[nextID].copy;
					nextMsg.notNil;
				},
				{
					network.target_app_addr.sendMsg(*nextMsg);
					msg_queue_for_OGD.removeAt(nextID);
					last_performed_OGD_ID = nextID;
					nextID = nextID+1;
				}
			);
		});
	}

	request_missing_OGDs {|missing|
		var all_missing;
		missing_OGD_IDs = missing_OGD_IDs.union(missing);
		all_missing = missing_OGD_IDs.asArray.sort;
		if(all_missing.size > 0, {
			addr.sendMsg('/landini/request/missing/OGD', network.me.name, *all_missing);
		});
	}

	receive_missing_OGD_request {arg missed_IDs;
		missed_IDs.do({|missedID|
			var msg = sent_OGD_msgs[missedID];
			if (msg.notNil, {
				addr.sendMsg('/landini/msg/OGD', network.me.name, missedID, *msg);
			});
		});
	}

}
