SamplesDirPlayer {

	var <library, <target, <defName, <out;
	var <currentBank;
	var <bankSynths, <group;

	*new { |library, target(Server.default), defName=\SamplesDirPlayer, out = 0|
		target = target.asTarget;
		^super.newCopyArgs(library, target, defName, out).init()
	}

	init {
		currentBank = this.banks.first;

		ServerTree.add(this, target.server);
		if(target.server.serverRunning) {
			this.doOnServerTree();
		}
	}

	banks { ^library.sortedKeys }

	currentBank_ { |nextBank|
		if(this.banks.includes(nextBank)) {
			currentBank = nextBank;
		} {
			"[SamplesDirPlayer] bank '%' not found. Current bank: '%'"
			.format(nextBank, currentBank).warn
		}
	}
	nextBank { |increment = 1|
		currentBank = this.banks.wrapAt(
			this.banks.indexOf(currentBank) + increment
		);
	}

	play { |n, args=(()), wrap = true|

		var buf = if (wrap, library[currentBank]@@n, library[currentBank][n]);
		args = (
			buf: buf,
			out: out
		).putAll(args).asKeyValuePairs;

		bankSynths[currentBank] ?? { bankSynths[currentBank] = SynthDict() };

		bankSynths[currentBank].add(buf.bufnum, Synth.head(group, defName, args))
	}

	release { |n, wrap = true|
		var buf = if (wrap, library[currentBank]@@n, library[currentBank][n]);
		var synths = bankSynths[currentBank].release(buf.bufnum);
	}

	releaseAll { bankSynths.do(_.release) }

	isSamplePlaying { | bufnum, bank = nil|
		bank = bank ? currentBank;
		^bankSynths[bank].isPlaying(bufnum);
	}

	doOnServerTree {
		this.allNotesOff;
		this.prInitGroup;
	}
	prInitGroup {
		group = Group(target)
	}
}

SynthDict[] : IdentityDictionary {

	add { |key, synth|
		this[key] ?? { this[key] = Set[] };
		this[key].add(synth);
		synth.onFree = { |synth| this[key].remove(synth) }
	}

	clear { |key|
		if(key.isNil) {
			this.keys.do(this.clear(_))
		} {
			this[key].clear
		}
	}

	free { |key|
		if(key.isNil) {
			this.keys.do(this.freeAll(_))
		} {
			this[key].do(_.free)
		}
	}

	release { |key|
		if(key.isNil) {
			this.keys.do(this.release(_))
		} {
			this[key].do(_.release)
		}
	}

	isPlaying { |key|
		this[key] ?? { ^false };
		^this[key].empty
	}

}