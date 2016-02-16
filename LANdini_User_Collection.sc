/* This provides the LANdini_LAN_Manager with a simple way to store the list of active users and perform simple routine tasks on them, like searching by name or ip, adding to and removing from the list, iterating over the list, etc. */

LANdini_User_Collection {
	var <userlist, <>me;

	*new {arg someUsers;
		^super.new.init(someUsers);
	}

	init {arg someUsers;
		userlist = [];
		this.add(someUsers);
	}

	add {arg addInput;
		if(addInput!=nil, {
			if(addInput.isKindOf(Collection),
				{
					if(addInput.select({|i| i.isKindOf(LANdini_User).not}).size==0,
						{
							addInput.do({|i| this.addSingleUser(i)});
						},{
							Error("input contains something other than LANdini_Users").throw;
						}
					);
				},{
					if(addInput.isKindOf(LANdini_User),
						{
							this.addSingleUser(addInput);
						},{
							Error("input isn't a LANdini_User").throw;
						}
					);
				}
			);
		});
	}

	addSingleUser {arg newUser;
		if(userlist.select({|i| i==newUser}).size==0, {
			userlist = userlist.add(newUser);
		});
		this.changed('user_add', newUser.name);
	}

	remove {arg removeInput;
		if(removeInput!=nil, {
			if(removeInput.isKindOf(Collection),
				{
					if(removeInput.select({|i| i.isKindOf(LANdini_User).not}).size==0,
						{
							removeInput.do({|i| this.removeSingleUser(i)});
						},{
							Error("input contains something other than LANdini_Users").throw;
						}
					);
				},{
					if(removeInput.isKindOf(LANdini_User),
						{
							this.removeSingleUser(removeInput);
						},{
							Error("input isn't a LANdini_User").throw;
						}
					);
				}
			);
		});
	}

	removeSingleUser {arg byeUser;
		if(userlist.select({|i| i == byeUser}).size>0, {
			userlist = userlist.reject({|i| i == byeUser});
		});
		this.changed('user_remove', byeUser.name);
	}

	at {arg string;
		// test if it's actually a string
		if(string.isKindOf(String) || string.isKindOf(Symbol),
			{
				string = string.asString;
				//test if it's a name or an ip
				if(string.split($.).size==4,
					{
						^userlist.select({|i| i.ip==string}).at(0);
					},{
						^userlist.select({|i| i.name==string}).at(0);
					}
				);
			},{
				Error("bad input: not a string or symbol").throw;
			}
		);
	}

	size {^userlist.size}

	namesButMe {
		var temp;
		temp = [];
		this.doNotMe({|usr| temp = temp.add(usr.name)});
		^temp;
	}

	names {
		var temp;
		temp = [];
		this.do({|usr| temp = temp.add(usr.name)});
		^temp;
	}

	includes {arg user;
		^(userlist.select({|i| i==user}).size>0)
	}

	do {arg function;
		^userlist.do(function);
	}

	doNotMe {arg function;
		var others;
		others = userlist.reject({|i| i==me});
		^others.do(function);
	}

	sort {arg function;
		^userlist.sort(function);
	}
}
