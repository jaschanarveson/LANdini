LANdini_StageMap_User_Rep : QUserView {
	classvar <>edgeSize=60, <>scale=1;
	var mouse_down_x, mouse_down_y, <>color, <name, <network;
	// (2) Set the viewClass to SCUserView
	*viewClass { ^QUserView } // this ensures that SCUserView's primitive is called

	*new {arg theParent, theX, theY, theName, theNetwork;
		var theBounds = Rect(theX, theY, edgeSize, edgeSize);
		^super.new(theParent, theBounds).myInit(theName, theNetwork);
	}

	// (3) Set up your view
	myInit {arg theName, theNetwork;
		// set the draw function of the SCUserView
		if(theNetwork.isKindOf(LANdini_LAN_Manager),
			{
				network = theNetwork;
				this.drawFunc = {this.draw};
				color = Color.rand;
				name = theName;
			},
			{
				"LANdini_StageMap_User_Rep not given a valid LANdini_LAN_Manager:\n% is of type %".format(theNetwork, theNetwork.class).error;
			}
		);
	}


	// (4) define a drawing function for SCPen
	draw{
		// Draw the fill
		Pen.scale(scale, scale);
		Pen.fillColor = color;
		Pen.fillRect(Rect(0,0,edgeSize,edgeSize));
		name.asString.drawCenteredIn(Rect(0,0,edgeSize,edgeSize), Font.default, Color.black);
	}


	// (5) define typical widget methods  (only those you need or adjust as needed)
	valueAction_{ arg val; // most widgets have this
		this.value=val;
		this.doAction;
	}

	value_{ |val|       // in many widgets, you can change the
		// value and refresh the view , but not do the action.
		"i just got a val".postln;
		this.refresh;
	}

	mouseDown{ arg x, y;
		mouse_down_x = x;
		mouse_down_y = y;
	}

	mouseMove{ arg x, y, modifiers, buttonNumber, clickCount;
		if(name.asString == network.me.name.asString, {
			var newBounds;
			newBounds = this.bounds.moveBy(x,y).moveBy(0-mouse_down_x, 0-mouse_down_y);
			newBounds.left = newBounds.left.max(0).min(this.parent.bounds.width - edgeSize);
			newBounds.top = newBounds.top.max(0).min(this.parent.bounds.height - edgeSize);
			this.bounds = newBounds;
		});
	}

	// (7) define default key actions
	// make sure to return "this", if successful, and nil if not successful
	defaultKeyDownAction { arg char, modifiers, unicode,keycode;
		[char, modifiers, unicode, keycode].postln;
	}

	pos {
		var x, y, pnt;
		x = this.bounds.left;
		y = this.bounds.top;
		pnt = x@y;
		^pnt;
	}

	pos_ {arg x, y;
		var deltaX, deltaY, newBounds;
		deltaX = x - this.bounds.left;
		deltaY = y - this.bounds.top;
		newBounds = this.bounds.moveBy(deltaX, deltaY);
		newBounds.left = newBounds.left.max(0).min(this.parent.bounds.width - edgeSize);
		newBounds.top = newBounds.top.max(0).min(this.parent.bounds.height - edgeSize);
		this.bounds = newBounds;
	}
}
