package com.pennas.canvasnowplayingplugin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.pennas.canvasnowplayingplugin.NowPlayingPlugin.Track;

public class NowPlayingReceiver extends BroadcastReceiver {
	public static final String PLAY_STATE_CHANGED = "com.android.music.playstatechanged";
	private static final String ALBUM = "album";
	private static final String TRACK = "track";
	private static final String ARTIST = "artist";
	private static final String ID = "id";
	private static final String PLAYING = "playing";
	
	@Override
	public final void onReceive(Context context, Intent intent) {
		//Log.i(NowPlayingPlugin.LOG_TAG, "onReceive: " + intent.getAction());
		process_intent(context, intent, false);
	}
	
	public static void process_intent(Context context, Intent intent, boolean force) {
		//dump_bundle(intent.getExtras());
		if (intent.getAction().equals(PLAY_STATE_CHANGED)) {
			boolean playing = intent.getBooleanExtra(PLAYING, false);
			if (playing || force) {
				Track t = new Track();
				t.artist = intent.getStringExtra(ARTIST);
				t.title = intent.getStringExtra(TRACK);
				t.album = intent.getStringExtra(ALBUM);
				t.id = intent.getLongExtra(ID, 0);
				NowPlayingPlugin.set_track_details(t, context);
			}
		}
	}
	
	/*private static void dump_bundle(Bundle b) {
		if (b != null) {
			for (String key : b.keySet()) {
				Object o = b.get(key);
				if (o != null) {
					o.getClass().getName();
					Log.i(NowPlayingPlugin.LOG_TAG, ".. extra: '" + key + "': '" + o + "' (" + o.getClass().getName() + ")");
				}
			}
		}
	}*/
}
