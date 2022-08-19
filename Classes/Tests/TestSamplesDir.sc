TestSamplesDir : UnitTest {

	// new

	test_new_noargs_throw {
		this.assertException({
			SamplesDir()
		}, Error, "should throw if created with no args")
	}

	test_new_nopath_throw {
		this.assertException({
			SamplesDir([1,2,3])
		}, Error, "should throw if created with invalid path")
	}

	test_new_resourceDir {
		this.assertNoException({
			SamplesDir(Platform.resourceDir +/+ "sounds")
		}, "should init ok with resourceDir+/+sounds")
	}

	// class methods
	test_pathMatchOrThrow_invalidPath_throw { |path|
		this.assertException({
			SamplesDir.pathMatchOrThrow(Platform.resourceDir +/+ "szozuznzdzs")
		}, Error, "should throw if passed an invalid path")
	}

	test_pathMatchOrThrow_resourceDir {
		this.assertNoException({
			SamplesDir.pathMatchOrThrow(Platform.resourceDir)
		}, "should accept resourceDir")
	}

	test_getSoundFilesInPath_resourceDir {
		this.assert(
			SamplesDir.getSoundFilesInPath(Platform.resourceDir+/+"sounds").notEmpty,
			"should find audiofiles in resourceDir+/+sounds"
		)
	}

	test_getSoundFilesInPath_classLib {
		this.assert(
			SamplesDir.getSoundFilesInPath(Platform.classLibraryDir).isEmpty,
			"should not find audiofiles in SCClassLibrary"
		)
	}

	test_stringToKey_nil {
		this.assertEquals(
			SamplesDir.stringToKey.class, Symbol,
			"should produce a Symbol for nil (a.k.a. when called with no arguments)"
		)
	}

	test_stringToKey_string {
		this.assertEquals(
			SamplesDir.stringToKey("testKey"), \testKey,
			"should convert \"testKey\" to a Symbol \testKey"
		)
	}
}


TestSamplesDir_server : UnitTest {
	var server;
	setUp { server = Server(this.class.name) }
	tearDown {
		if(server.serverRunning) { server.quit };
		server.remove;
	}

	// new

	test_new_noargs_noattach {
		var sl = try {
			SamplesDir(server:server)
		};
		var addedToBoot = ServerBoot.objects[server].includes(sl);
		this.assert( addedToBoot.not , "should not attach to server if creation failed")
	}

	test_new_attach {
		var sl = SamplesDir(Platform.resourceDir, server:server);
		var addedToBoot = ServerBoot.objects[server].includes(sl);
		this.assert( addedToBoot , "should attach to server on creation");
		ServerBoot.remove(sl);
	}

	test_load_resourceDir_preBoot {
		var sl = SamplesDir(Platform.resourceDir +/+ "sounds", server: server);
		server.bootSync;
		this.wait(sl.loadingCondition, "buffer loading timeout on server boot", 2);
		this.assert(sl.size > 0, "didn't load any buffer on server boot")
	}

	test_load_resourceDir_postBoot {
		var sl;
		server.bootSync;
		sl = SamplesDir(Platform.resourceDir +/+ "sounds", server: server);
		this.wait(sl.loadingCondition, "buffer loading timeout after server boot", 2);
		this.assert(sl.size > 0, "didn't load any buffer after server boot")
	}
}
