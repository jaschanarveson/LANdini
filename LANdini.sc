LANdini {
	*new{arg offlineTestingMode = false;
		^super.new.init(offlineTestingMode);
	}

	init{arg offlineTestingMode;
		if(offlineTestingMode == false,
			{^LANdini_LAN_Manager.new},
			{^LANdini_Fake_LAN_Manager_for_offline_Testing.new}
		);
	}
}