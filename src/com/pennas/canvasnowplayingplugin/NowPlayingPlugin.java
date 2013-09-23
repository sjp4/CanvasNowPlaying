package com.pennas.canvasnowplayingplugin;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import java.util.Arrays;

import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;

import com.pennas.pebblecanvas.plugin.PebbleCanvasPlugin;

public class NowPlayingPlugin extends PebbleCanvasPlugin {
	public static final String LOG_TAG = "CANV_NOW_PLAY";
	
	private static final int ID_NOW_PLAYING = 1;
	private static final int ID_ALBUM_ART = 2;
	
	private static final String[] MASKS = { "%A", "%T", "%L" };
	private static final int MASK_ARTIST = 0;
	private static final int MASK_TITLE = 1;
	private static final int MASK_ALBUM = 2;
	private static final String PREF_ART = "ART";
	
	// send plugin metadata to Canvas when requested
	@Override
	protected ArrayList<PluginDefinition> get_plugin_definitions(Context context) {
		Log.i(LOG_TAG, "get_plugin_definitions");
		
		// create a list of plugins provided by this app
		ArrayList<PluginDefinition> plugins = new ArrayList<PluginDefinition>();
		
		// now playing (text)
		TextPluginDefinition tplug = new TextPluginDefinition();
		tplug.id = ID_NOW_PLAYING;
		tplug.name = context.getString(R.string.plugin_name_now_playing);
		tplug.format_mask_descriptions = new ArrayList<String>(Arrays.asList(context.getResources().getStringArray(R.array.format_mask_descs)));
		// populate example content for each field (optional) to be display in the format mask editor
		ArrayList<String> examples = new ArrayList<String>();
		examples.add(current_track.artist);
		examples.add(current_track.title);
		examples.add(current_track.album);
		tplug.format_mask_examples = examples;
		tplug.format_masks = new ArrayList<String>(Arrays.asList(MASKS));
		tplug.default_format_string = "%A - %T";
		plugins.add(tplug);
		
		// album art
		ImagePluginDefinition iplug = new ImagePluginDefinition();
		iplug.id = ID_ALBUM_ART;
		iplug.name = context.getString(R.string.plugin_name_album_art);
		plugins.add(iplug);
		
		return plugins;
	}
	
	private static boolean process_just_started = true;
	private static boolean got_now_playing = false;
	
	// send current text values to canvas when requested
	@Override
	protected String get_format_mask_value(int def_id, String format_mask, Context context, String param) {
		//Log.i(LOG_TAG, "get_format_mask_value def_id = " + def_id + " format_mask = '" + format_mask + "'");
		if (process_just_started) {
			Log.i(LOG_TAG, "process_just_started");
			process_just_started = false;
			if (!got_now_playing) {
				load_from_prefs(context);
			}
		}
		
		if (def_id == ID_NOW_PLAYING) {
			// which field to return current value for?
			if (format_mask.equals(MASKS[MASK_ARTIST])) {
				return current_track.artist;
			} else if (format_mask.equals(MASKS[MASK_TITLE])) {
				return current_track.title;
			} else if (format_mask.equals(MASKS[MASK_ALBUM])) {
				return current_track.album;
			}
		}
		Log.i(LOG_TAG, "no matching mask found");
		return null;
	}
	
	// save values to preferences every time they change, because:
	//  - this process might be killed
	//  - values may not be requested by canvas straight away
	//  - will return values on first load
	private static void load_from_prefs(Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		current_track.artist = prefs.getString(MASKS[MASK_ARTIST], null);
		current_track.title = prefs.getString(MASKS[MASK_TITLE], null);
		current_track.album = prefs.getString(MASKS[MASK_ALBUM], null);
		String uri = prefs.getString(PREF_ART, null);
		if (uri != null) {
			current_track.album_art_uri = Uri.parse(uri);
		}
		Log.i(LOG_TAG, "loaded artist = " + current_track.artist + " title = "
				 + current_track.title + " album = " + current_track.album + " art = " + current_track.album_art_uri);
	}
	
	private static void save_to_prefs(Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		prefs.edit().putString(MASKS[MASK_ARTIST], current_track.artist).commit();
		prefs.edit().putString(MASKS[MASK_TITLE], current_track.title).commit();
		prefs.edit().putString(MASKS[MASK_ALBUM], current_track.album).commit();
		String art;
		if (current_track.album_art_uri == null) {
			art = null;
		} else {
			art = current_track.album_art_uri.toString();
		}
		prefs.edit().putString(PREF_ART, art).commit();
	}

	// send bitmap value to canvas when requested
	@Override
	protected Bitmap get_bitmap_value(int def_id, Context context, String param) {
		Log.i(LOG_TAG, "get_bitmap_value def_id = " + def_id);
		
		if (def_id == ID_ALBUM_ART){
			InputStream in = null;
			if (current_track.album_art_uri == null) {
				return null;
			}
		    try {
		    	Log.i(LOG_TAG, "loading album art: " + current_track.album_art_uri);
				in = context.getContentResolver().openInputStream(current_track.album_art_uri);
				Bitmap artwork = BitmapFactory.decodeStream(in);
				return artwork;
			} catch (FileNotFoundException e) {
				return null;
			} catch (IllegalArgumentException e) {
				return null;
			} catch (IllegalStateException e) {
				return null;
			} finally {
				if (in != null) {
					try {
						in.close();
					} catch (IOException e) { /* */ }
				}
			}
		}
		Log.i(LOG_TAG, "no matching id found");
		return null;
	}
	
	private static Track current_track = new Track();
	
	// only notify canvas of an update if it has actually changed
	private static void set_album_art(Uri uri, Context context) {
		if ( ((current_track.album_art_uri == null) && (uri != null)) 
		  || ((current_track.album_art_uri != null) && (uri != null) && !current_track.album_art_uri.equals(uri))
		  || (uri == null) ) {
			Log.i(LOG_TAG, "set_album_art: " + uri);
			current_track.album_art_uri = uri;
			notify_canvas_updates_available(ID_ALBUM_ART, context);
		}
		// always save here (also saves track info)
		save_to_prefs(context);
	}
	
	public static class Track {
		String artist, title, album;
		long id;
		Uri album_art_uri;
	}
	
	// only notify canvas of an update if it has actually changed
	public static void set_track_details(Track track, Context context) {
		if ( ((current_track.artist == null) && (track.artist != null)) 
		  || ((current_track.artist != null) && (track.artist != null) && !current_track.artist.equals(track.artist))
		  || ((current_track.title == null) && (track.title != null)) 
		  || ((current_track.title != null) && (track.title != null) && !current_track.title.equals(track.title))
		  || ((current_track.album == null) && (track.album != null)) 
		  || ((current_track.album != null) && (track.album != null) && !current_track.album.equals(track.album)) ) {
			Log.i(LOG_TAG, "set_track_details artist '" + track.artist + "' title '" + track.title + "' album '" + track.album + "'");
			current_track.album = track.album;
			current_track.artist = track.artist;
			current_track.id = track.id;
			current_track.title = track.title;
			got_now_playing = true;
			notify_canvas_updates_available(ID_NOW_PLAYING, context);
			get_album_art(context);
			// prefs saved in get_album_art
		}
	}
	
	private static final Uri ARTWORK_URI = Uri.parse("content://media/external/audio/albumart");
	private static final long NO_ID = -9999;
	
	private static void get_album_art(Context context) {
		//Log.i(LOG_TAG, "get_album_art");
		long album_id = NO_ID;
		String [] track_cols = new String [] { MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.ARTIST };
		String query = MediaStore.Audio.Media._ID + " = " + current_track.id;
		Cursor curTracks = context.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track_cols, query, null, null);
		curTracks.moveToFirst();
		if (!curTracks.isAfterLast()) {
			String artist_check = curTracks.getString(1);
			// if track ID came from poweramp or similar, it might match wrong db entry. check
			if (artist_check.equals(current_track.artist)) {
				album_id = curTracks.getLong(0);
				Log.i(LOG_TAG, "album_id 1 = " + album_id);
			} else {
				Log.i(LOG_TAG, "album_id 1 didn't match artist");
			}
		}
		curTracks.close();
		
		// try matching on artist/title/album (for eg poweramp which supplies non-standard track id)
		if (album_id == NO_ID) {
			query = "(" + MediaStore.Audio.Media.ARTIST + " = \"" + current_track.artist + "\")"
			 + " AND (" + MediaStore.Audio.Media.TITLE + " = \"" + current_track.title + "\")";
			curTracks = context.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track_cols, query, null, null);
			curTracks.moveToFirst();
			if (!curTracks.isAfterLast()) {
				album_id = curTracks.getLong(0);
				Log.i(LOG_TAG, "album_id 2 = " + album_id);
			}
			curTracks.close();
		}
		
		if (album_id != NO_ID) {
			Uri uri = ContentUris.withAppendedId(ARTWORK_URI, album_id);
			set_album_art(uri, context);
		} else {
			set_album_art(null, context);
		}
	}

}
