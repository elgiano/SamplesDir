SamplesDir[] {

	classvar <>loadMode = \syncFolder;

	var <server, <path, <recursive, <>normalize, <>verbose, <lazy;
	var <buffers;
	var <shortKeys, <sortedKeys;
	var <loadingCondition;
	var loadingBufferPaths;

	*new { | path, recursive = true,
		server(Server.default),
		lazy = false,
		normalize = false, verbose = false|
		path ?? {
			Error("[SamplesDir] Can't initialize without a path").throw;
		};
		// drop trailing '/'
		if(path.endsWith(Platform.pathSeparator.asString)) {
			path = path.drop(-1)
		};
		^super.newCopyArgs(
			server, path, recursive, normalize, verbose, lazy
		).init;
	}

	init {
		buffers = IdentityDictionary[];
		shortKeys = IdentityDictionary[];
		loadingCondition = Condition();

		loadingBufferPaths = this.class.getSoundFilesInPath(path,recursive).asSet;
		this.prAttachToServer;
	}

	// through this class we use ++ instead of add
	// to avoid problems with Strings and .flatten

	*getSoundFilesInPath { |path="", recursive=true|
		// depth-first: recurse into evt. subfolders
		var soundFilesInSubfolders = [], soundFiles = [];
		if(recursive){
			var subFolders = PathName(path).folders;
			subFolders.do { |subPath|
				var thisSubfolder = this.getSoundFilesInPath(subPath.fullPath, recursive);
				soundFilesInSubfolders = soundFilesInSubfolders ++ thisSubfolder;
			};
		};

		PathName(path).files.do { |file|
			var filePath = file.fullPath;
			SoundFile.use(filePath) {|soundFile|
				if(soundFile.isOpen) {
					soundFiles = soundFiles.add(filePath);
				}
			}
		};

		^soundFilesInSubfolders ++ soundFiles;
	}

	// Collection interface

	at { |key|
		var shortKeysMatches;
		var exact = this.buffers[key];
		exact !? { ^exact };
		if(key.isNumber) {
			var atNum = this.atNum(key);
			atNum !? { ^atNum }
		};
		shortKeysMatches = this.shortKeys[key];
		shortKeysMatches !? {
			if(shortKeysMatches.size == 1){
				^this.buffers[shortKeysMatches[0]];
			} {
				warn("[Sample Library] key '%' is ambiguous.".format(key));
				"Options:".postln;
				shortKeys.do(_.postln);
			}
		};
		^nil;
	}

	keys { ^buffers.keys }

	all { ^buffers.values.flat }
	do { |action| this.all.do(action) }
	collect { |action| ^this.all.collect(action) }
	select { |action| ^this.all.select(action) }
	reject { |action| ^this.all.reject(action) }

	size { ^buffers.values.flatten.size }

	atNum { |n| ^this.all@@n }

	choose { |key|
		key !? { ^this[key].choose };
		^this.all.choose
	}


	clear { |freeBuffers = true|
		if (freeBuffers) { this do: _.free };
		if (loadingCondition.test) { loadingCondition = Condition(false) };
		buffers.clear;
		shortKeys.clear;
	}

	openFolder { path.openOS }

	// key->basename

	getBufByPath { |path, key|
		var bufs = if (key.isNil) { this.all } { this[key.asSymbol] };
		var exact = bufs.detect { |buf| buf.path == path };
		var basename;
		exact !? { ^exact };
		basename = path.basename;
		^bufs.detect { |buf| buf.path.basename == basename };
	}

	getBufByBasename { |basename, key|
		var bufs = if (key.isNil) { this.all } { this[key.asSymbol] };
		^bufs.detect { |buf| buf.path.basename == basename }
	}

	bufnumToKeyBasename { |bufnum|
		var key, buf = server.cachedBufferAt(bufnum);
		buf ?? {
			"[SamplesDir] buf % not found on server %".format(bufnum, server).warn;
			^nil
		};
		key = this.keys.detect { |k| this[k].includes(buf) };
		^[key, buf.path.basename]
	}

	// strip spaces?
	// but then it would make sense to strip all characters that don't work with symbols \ syntax
	*stringToKey { |string|
		^string.asSymbol;
	}

	// loading

	waitForLoading { |action|
		if (lazy) {
			"[SamplesDir] .waitForLoading doesn't need to wait when lazy=true".warn;
			action.value;
		} {
			forkIfNeeded {
				this.loadingCondition.wait;
				action !? { action.() }
			}
		}
	}

	// PRIVATE implementation

	prAttachToServer {
		ServerBoot.add(this, server);
		if(server.serverRunning) {
			this.doOnServerBoot;
		}{
			"[SamplesDir] will load buffers after server boot".postln;
		};
	}

	doOnServerBoot {
		this.clear;
		if (lazy.not) {
			this.prLoadBuffers;
		};
		if(normalize) {
			this.waitForLoading {
				"[SamplesDir] normalizing buffers".postln;
				this.all.do(_.normalize)
			}
		}
	}

	prLoadBuffers {
		forkIfNeeded {
			var start = Date.getDate.rawSeconds;
			"[SamplesDir] Loading sample library: %".format(path).postln;
			"[SamplesDir] TOTAL: % files\n".format(loadingBufferPaths.size).postln;
			this.prLoadFiles(loadingBufferPaths);
			this.prSortKeys;
			"[SamplesDir] Done loading! Took % seconds".format(
				(Date.getDate.rawSeconds - start).round(0.001)
			).postln;
		}
	}

	prLoadFiles { |filePaths|
		var dict = this.prGetSubfolderDict(filePaths, path);
		switch (SamplesDir.loadMode)
		{ \syncAll } { this.prLoadDict(dict) }
		{ \syncFolder } {
			dict.keysValuesDo(this.prLoadFilesSyncFolder(_, _))
		}
		{ \syncEach } {
			dict.keysValuesDo(this.prLoadFilesSyncEach(_, _))
		} {
			Error("SamplesDir: invalid loadMode '%'".format(SamplesDir.loadMode)).throw
		};
	}

	prGetSubfolderDict { |paths, basePath|
		var dict = IdentityDictionary[];
		paths.do{ |path|
			var relativePath = path.asRelativePath(basePath);
			var subfolders = relativePath.split(Platform.pathSeparator).drop(-1);
			var key;
			if(subfolders.isEmpty) { subfolders = ["/"] };
			key = this.class.stringToKey(
				subfolders.join(Platform.pathSeparator)
			);

			this.prAddShortKey(subfolders.last, key);
			dict[key] = dict[key].add(path);
		};

		^dict;
	}

	prLoadDict { |dict|
		var condition = Condition.new;
		var bundle, newBuffers = IdentityDictionary[];

		"[SamplesDir] loading % sound files".format(dict.values.flatten.size).postln;

		bundle = this.server.makeBundle(false) {
			newBuffers = dict.collect { |paths|
				paths.collect { |path|
					Buffer.read(this.server, path).doOnInfo_{ |buf|
						this.prOnBufLoaded(path, buf)
					}
				}

			}
		};

		bundle = if ((bundle.collect {|item| [item].bundleSize }).sum < 8192) {
			[bundle]
		} {
			bundle.clumpBundles
		};

		bundle.do { |item|
			var id = this.server.addr.makeSyncResponder(condition);
			this.server.addr.sendBundle(nil, *(item ++ [["/sync", id]]));
			condition.wait;
		};

		newBuffers.keysValuesDo { |key, bufs|
			key = this.class.stringToKey(key);
			this.buffers[key] = this.buffers[key] ++ bufs;
		}
	}

	prLoadFilesSyncFolder { |key, filePaths|
		var condition = Condition.new;
		var bundle, newBuffers;

		key = this.class.stringToKey(key);
		"[SamplesDir] %: loading % sound files".format(key, filePaths.size).postln;

		bundle = this.server.makeBundle(false) {
			newBuffers = filePaths.collect { |path|
				Buffer.read(this.server, path).doOnInfo_{ |buf|
					this.prOnBufLoaded(path, buf)
				}
			}
		};

		bundle = if ((bundle.collect {|item| [item].bundleSize }).sum < 8192) {
			[bundle]
		} {
			bundle.clumpBundles
		};

		bundle.do { |item|
			var id = this.server.addr.makeSyncResponder(condition);
			this.server.addr.sendBundle(nil, *(item ++ [["/sync", id]]));
			condition.wait;
		};
		this.buffers[key] = this.buffers[key] ++ newBuffers;
	}

	prLoadFilesSyncEach { |key, filePaths|
		var condition = Condition.new;
		var bundle, newBuffers;

		key = this.class.stringToKey(key);
		"[SamplesDir] %: loading % sound files".format(key, filePaths.size).postln;

		server.makeBundle(0) {
			var newBuffers;
			newBuffers = filePaths.collect {|path|
				var newBuf;
				newBuf = Buffer.read(this.server, path, action: {|buf|
					this.prOnBufLoaded(path, buf)
				});
				server.sync;
				newBuf;
			};
			server.sync;
			this.buffers[key] = this.buffers[key] ++ newBuffers;
		}
	}

	prOnBufLoaded { |path, buf|
		if (verbose) { "[SamplesDir] loaded: %".format(path).postln };
		loadingBufferPaths.remove(path);
		loadingCondition.test_(loadingBufferPaths.size == 0).signal;
	}

	prAddShortKey { |short, full|
		shortKeys[short] = shortKeys[short] ?? { Set[] };
		shortKeys[short] = shortKeys[short] ++ this.class.stringToKey(full).bubble;
	}

	prSortKeys {
		sortedKeys = buffers.keys.asArray.sort;
	}

	lazyLoad { |paths|
		paths = paths.collect { |p|
			var exact;
			p = if (PathName(p).isRelativePath) { path +/+ p } { p };
			exact = loadingBufferPaths.detect(_==p);
			exact ?? {
				loadingBufferPaths.detect { |bp| bp.basename == p.basename };
			}
		};
		this.prLoadFiles(paths);
	}

	// copy one or more files to library
	import { |path, key = nil|
		var copyPaths;
		if (path.isString) { path = [path] };
		copyPaths = path.collect {|p|
			this.prCopyToLib(p, key)
		};
		"[SamplesDir] Loading % new bufs".format(copyPaths.size).postln;
		this.prLoadFiles(copyPaths);
		this.prSortKeys;
	}

	prCopyToLib { |path, key|
		var destPath = this.path +/+  (key ?? path.dirname.basename);
		if (File.exists(destPath).not) {
			File.mkdir(destPath);
		};
		destPath = destPath +/+ path.basename;
		if (File.exists(destPath)) {
			"[SamplesDir] not copying %: it already exists".format(destPath).postln;
		} {
			"[SamplesDir] copying % to %".format(path, destPath).postln;
			File.copy(path, destPath);
		};
		^destPath;
	}

}
