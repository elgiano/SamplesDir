SamplesDirBrowser {

	var <lib;

	*new { |lib|
		^super.newCopyArgs(lib).init
	}

	init { |lib|
		this.show;
	}

	show {
		var sidebar, navigation, keyList, cwd, bufView, displayBufs;
		var w = Window();

		bufView = ScrollView();
		bufView.canvas = View().background_(Color.grey(0.1));

		cwd = StaticText().string_(this.lib.path.basename);
		navigation = HLayout(
			Button().states_([["^"]]).action_{
				cwd.string_(this.lib.path.basename);
				this.displayBufs(bufView.canvas);
			},
			[cwd, stretch: 4]
		);
		keyList = ListView().items_(this.lib.keys.asArray).action_{
			var key = keyList.items[keyList.value];
			cwd.string = key;
			this.displayBufs(bufView.canvas, key);
		};

		keyList.beginDragAction = {
			var key = keyList.items[keyList.value];
			[key, this.lib[key]]
		};


		sidebar = VLayout(navigation, keyList);

		this.displayBufs(bufView.canvas);
		w.layout = HLayout(sidebar, [bufView, stretch: 4]);
		w.front;
	}


	displayBufs { |canvas, key|
		var bufs, bufViews;
		canvas.removeAll;

		bufs = if (key.notNil) { this.lib[key] } { this.lib.all };
		bufViews = bufs.collect { |buf|
			var view = View().background_(Color.grey(0.3));
			var title = DragSource().setBoth_(false)
			.string_("% (%)".format(buf.path.basename, buf.duration.asStringPrec(4)))
			.object_(buf);

			var sf = SoundFile.openRead(buf.path);
			var sfw = SoundFileView().soundfile_(sf);
			sfw.readWithTask(doneAction: {sf.close});
			sfw.mouseDownAction_{ |v,x,y,m,n,count| if (count == 2) {buf.play} };

			view.layout = VLayout(title,sfw.maxHeight_(50));
			view;
		};

		canvas.layout = GridLayout.rows(*bufViews.clump(4));
	}

}