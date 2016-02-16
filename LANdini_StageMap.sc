LANdini_StageMap {
	var network, userlist;
	var window, width, height, in_left, in_top, maxBounds, screen, edge, scale;
	var userReps, stage_map_update_task;
	var updater, updateInterval = 0.101;

	*new {arg theNetwork;
		^super.new.init(theNetwork);
	}

	init {arg theNetwork;
		if(theNetwork.isKindOf(LANdini_LAN_Manager),
			{
				network = theNetwork;
				userlist = theNetwork.userlist;
				"network found, and is version number %".format(theNetwork.version).postln;
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

	// stage map window

	continueInit {

		var audienceLabel;

		maxBounds = Rect(0, 0, 1000, 600);
		screen = Window.screenBounds;
		edge = LANdini_StageMap_User_Rep.edgeSize;
		userReps = IdentityDictionary[];
		if( (screen.width/edge) < network.userlist.size,
			{
				var newEdge;
				newEdge = ( (screen.width*0.8) / (network.userlist.size));
				scale = newEdge/edge;
				"scale is %".format(scale).postln;
				LANdini_StageMap_User_Rep.scale = scale;
				width = screen.width * 0.8;
				height = width * 0.6;
			},{
				scale=1;
				LANdini_StageMap_User_Rep.scale = 1;
				width = (network.userlist.size) * edge;
			}
		);
		width = network.userlist.size * (edge * scale);
		height = width * 0.6;

		width = width.max(maxBounds.width);
		height = height.max(maxBounds.height);

		in_left = screen.width - width / 2;
		in_top = screen.height - height / 2;

		window = Window.new("stage map", Rect(in_left, in_top, width, height)).front;
		window.background = Color.new(0.3, 0.3, 0.3);

		audienceLabel = StaticText.new(
			window,
			bounds: Rect(
				0,
				window.bounds.height - 40,
				window.bounds.width,
				30
			)
		);
		audienceLabel.string = "(audience)";
		audienceLabel.stringColor = Color.black;
		audienceLabel.align = 'center';

		network.userlist.do({arg usr;
			{
				"calling create func for %".format(usr.name).postln;
				this.create_user_rep(usr);
			}.defer;
		});

		this.start_stage_map_update_task;

		updater = {|thing, what ...args|
			switch(what,
				'user_add',
				{
					var name, usr;
					name = args[0];
					usr = network.userlist.at(name.asString);
					this.create_user_rep(usr);
				},

				'user_remove',
				{
					var name;
					name = args[0];
					this.remove_user_rep(name);
				}
			);
		};

		network.userlist.addDependant(updater);

		window.onClose = {
			this.stop_stage_map_update_task;
			network.userlist.removeDependant(updater);
		};
	}


	start_stage_map_update_task {
		if(stage_map_update_task.isNil,
			{
				stage_map_update_task = Task({
					var myPoint, otherPoints;
					loop({
						var newX, newY;

						// update everyone's position
						network.userlist.doNotMe({arg usr;
							userReps.at(usr.name.asSymbol).pos_(usr.x_pos, usr.y_pos);
						});

						myPoint = userReps.at(network.me.name.asSymbol).pos;

						newX = myPoint.x;
						newY = myPoint.y;

						network.userlist.doNotMe({arg usr;
							var theirPoint;
							theirPoint = userReps.at(usr.name.asSymbol).pos;
							if(theirPoint.dist(myPoint) < edge,
								{
									if(myPoint.x < theirPoint.x,
										{newX = theirPoint.x - edge},
										{newX = theirPoint.x + edge}
									);
									if(myPoint.y < theirPoint.y,
										{newY = theirPoint.y - edge},
										{newY = theirPoint.y + edge}
									);
								}
							);
						});

						newX = newX.max(0).min(window.bounds.width - edge);
						newY = newY.max(0).min(window.bounds.height - edge);

						network.me.x_pos = newX;
						network.me.y_pos = newY;

						userReps.at(network.me.name.asSymbol).pos_(newX, newY);

						updateInterval.wait;
					});

				}).play(AppClock);
			}
		)
	}

	stop_stage_map_update_task {
		if(stage_map_update_task != nil,
			{
				stage_map_update_task.stop;
				stage_map_update_task = nil;
			}
		);
	}


	remove_user_rep {arg usrname;
		"removing user rep for %".format(usrname).postln;
		{
			var rep;
			rep = userReps.at(usrname.asSymbol);
			userReps.removeAt(usrname.asSymbol);
			rep.remove;
		}.defer;
	}

	get_individualized_stagemap_drawing_point {arg usr;
		var newX, newY, userNames, alphabeticalIndex;

		if( (usr.x_pos < 0) || (usr.y_pos < 0),
			{
				userNames = network.userlist.names.sort({arg a, b; a < b});
				alphabeticalIndex = userNames.indexOf(network.me.name.asString);

				newX = (alphabeticalIndex * edge).min(window.bounds.width - edge);
				newY = 0;
			},
			{
				newX = usr.x_pos;
				newY = usr.y_pos;
			}
		);
		"new Point for % is (%, %)".format(usr.name, newX, newY).postln;
		^newX@newY;
	}

	create_user_rep {arg usr;
		{
			var edgeSize = edge * scale, newPoint, existingPoints;
			"creating user rep for %".format(usr.name).postln;

			// places them if they haven't placed themsevles yet
			newPoint = this.get_individualized_stagemap_drawing_point(usr);

			existingPoints = userReps.collect({arg rep; rep.pos});

			while(
				{existingPoints.select({arg point; newPoint.dist(point) < edge}).size > 0},
				{
					if(newPoint.x < (window.bounds.width - edge),
						// go right until you find a spot to put the new person
						{newPoint.x = newPoint.x + 1},
						{
							// if you hit the right edge, go down
							if(newPoint.y < (window.bounds.height - edge),
								{newPoint.y = newPoint.y + 1},
								{
									//if you hit the bottom, go left
									if(newPoint.x > 0,
										{newPoint.x = newPoint.x - 1},
										{
											//if you hit the left, go up
											if(newPoint.y > 0,
												{newPoint.y = newPoint.y + 1}
											);
										}
									);
								}
							);
						}
					);
				}
			);

			userReps.put(
				usr.name.asSymbol,
				LANdini_StageMap_User_Rep.new(window, newPoint.x, newPoint.y, usr.name, network)
			);

			userReps.do({arg rep;
				var name, r, g, b, color;
				name = rep.name.asString;
				r = name.wrapAt(0).ascii.linexp(65, 122, 0.1, 0.9).round(0.01);
				g = name.wrapAt(1).ascii.linexp(65, 122, 0.1, 0.9).round(0.01);
				b = name.wrapAt(2).ascii.linexp(65, 122, 0.1, 0.9).round(0.01);
				color = Color.new(r, g, b);
				rep.color = color;
			});
		}.defer;
	}

}
