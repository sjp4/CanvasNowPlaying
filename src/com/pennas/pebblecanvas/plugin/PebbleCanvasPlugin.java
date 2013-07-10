package com.pennas.pebblecanvas.plugin;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.util.Log;

public abstract class PebbleCanvasPlugin extends BroadcastReceiver {
	private static final int INTERFACE_VERSION = 1;
	
	private static final String ABS_LOG_TAG = "CANV_PLUG";
	// canvas -> plugins
	public static final String CANVAS_ACTION_REQUEST_DEFINITIONS = "com.pennas.pebblecanvas.plugin.REQUEST_DEFINITIONS";
	public static final String CANVAS_ACTION_REQUEST_UPDATE = "com.pennas.pebblecanvas.plugin.REQUEST_UPDATE";
	// plugins -> canvas
	public static final String CANVAS_ACTION_DEFINITION = "com.pennas.pebblecanvas.plugin.DEFINITION";
	public static final String CANVAS_ACTION_UPDATE = "com.pennas.pebblecanvas.plugin.UPDATE";
	public static final String CANVAS_ACTION_NOTIFY_UPDATE = "com.pennas.pebblecanvas.plugin.NOTIFY_UPDATE";
	
	// definition fields
	public static final String CANVAS_DEFINITION_ID = "ID";
	public static final String CANVAS_DEFINITION_NAME = "NAME";
	public static final String CANVAS_DEFINITION_PACKAGE = "PACKAGE";
	public static final String CANVAS_DEFINITION_INTERFACE_VERSION = "INTERFACE_VERSION";
	public static final String CANVAS_DEFINITION_PLUGIN_VERSION = "PLUGIN_VERSION";
	public static final String CANVAS_DEFINITION_TYPE = "TYPE";
	public static final String CANVAS_DEFINITION_FORMAT_MASKS = "FORMAT_MASKS";
	public static final String CANVAS_DEFINITION_FORMAT_DESCS = "FORMAT_DESCS";
	public static final String CANVAS_DEFINITION_FORMAT_EXAMPLES = "FORMAT_EXAMPLES";
	public static final String CANVAS_DEFINITION_DEFAULT_FORMAT_STRING = "FORMAT_DEFAULT";
	
	// value fields
	public static final String CANVAS_VALUE_FORMAT_MASKS = "FORMAT_MASK";
	public static final String CANVAS_VALUE_FORMAT_MASK_VALUES = "MASK_VALUES";
	public static final String CANVAS_VALUE_IMAGE = "IMAGE";
	
	// plugin types
	public static final int TYPE_TEXT = 1;
	public static final int TYPE_IMAGE = 2;
	
	private static final String PEBBLE_CANVAS_PACKAGE = "com.pennas.pebblecanvas";
	private static final String PEBBLE_CANVAS_PLUGIN_RECEIVER = PEBBLE_CANVAS_PACKAGE + ".plugin.PluginReceiver";
	
	public static final int NO_VALUE = -999;
	private static ArrayList<PluginDefinition> stored_defs;
	
	@Override
	public final void onReceive(Context context, Intent intent) {
		//Log.i(ABS_LOG_TAG, "onReceive: " + intent.getAction());
		// Canvas requested definitions - send them
		if (intent.getAction().equals(CANVAS_ACTION_REQUEST_DEFINITIONS)) {
			Log.i(ABS_LOG_TAG, "defs");
			if (stored_defs == null) {
				stored_defs = get_plugin_definitions(context);
			}
			if (stored_defs == null) return;
			for (PluginDefinition def : stored_defs) {
				send_definition(def, context);
			}
		// Canvas requested values for a specific plugin - send them
		} else if (intent.getAction().equals(CANVAS_ACTION_REQUEST_UPDATE)) {
			Log.i(ABS_LOG_TAG, "update");
			String pkg = intent.getStringExtra(CANVAS_DEFINITION_PACKAGE);
			if (pkg == null) return;
			if (!pkg.equals(context.getPackageName())) return;
			
			if (stored_defs == null) {
				Log.i(ABS_LOG_TAG, "call get_plugin_definitions");
				stored_defs = get_plugin_definitions(context);
			}
			if (stored_defs == null) {
				Log.i(ABS_LOG_TAG, "stored_defs == null");
				return;
			}
			
			// which id to get value of?
			
			int def_id = intent.getIntExtra(CANVAS_DEFINITION_ID, NO_VALUE);
			if (def_id == NO_VALUE) {
				Log.i(ABS_LOG_TAG, "def_id == NO_VALUE");
				return;
			}
			
			for (PluginDefinition def : stored_defs) {
				if (def.id == def_id) {
					if (def instanceof TextPluginDefinition) {
						// which format masks are required?
						ArrayList<String> format_masks = intent.getStringArrayListExtra(CANVAS_VALUE_FORMAT_MASKS);
						if (format_masks == null) return;
						
						send_value_string(def_id, format_masks, context);
					} else if (def instanceof ImagePluginDefinition) {
						send_value_image(def_id, context);
					}
					break;
				} // def id
			} // for
		}
	}
	
	private final void send_definition(PluginDefinition def, Context context) {
		Log.i(ABS_LOG_TAG, "send_definition: " + def.id);
		final Intent intent = new Intent(CANVAS_ACTION_DEFINITION);
		intent.putExtra(CANVAS_DEFINITION_ID, def.id);
		intent.putExtra(CANVAS_DEFINITION_NAME, def.name);
		intent.putExtra(CANVAS_DEFINITION_PACKAGE, context.getPackageName());
		intent.putExtra(CANVAS_DEFINITION_INTERFACE_VERSION, INTERFACE_VERSION);
		
		PackageManager manager = context.getPackageManager();
		PackageInfo info;
		String version = null;
		try {
			info = manager.getPackageInfo(context.getPackageName(), 0);
			version = info.versionName;
		} catch (NameNotFoundException e) { /* */ }
		intent.putExtra(CANVAS_DEFINITION_PLUGIN_VERSION, version);
		
		if (def instanceof TextPluginDefinition) {
        	intent.putExtra(CANVAS_DEFINITION_TYPE, TYPE_TEXT);
        	TextPluginDefinition text_def = (TextPluginDefinition) def;
        	intent.putExtra(CANVAS_DEFINITION_FORMAT_MASKS, text_def.format_masks);
        	intent.putExtra(CANVAS_DEFINITION_FORMAT_DESCS, text_def.format_mask_descriptions);
        	intent.putExtra(CANVAS_DEFINITION_FORMAT_EXAMPLES, text_def.format_mask_examples);
        	intent.putExtra(CANVAS_DEFINITION_DEFAULT_FORMAT_STRING, text_def.default_format_string);
        } else if (def instanceof ImagePluginDefinition) {
        	intent.putExtra(CANVAS_DEFINITION_TYPE, TYPE_IMAGE);
        }
        intent.setClassName(PEBBLE_CANVAS_PACKAGE, PEBBLE_CANVAS_PLUGIN_RECEIVER);
        context.sendBroadcast(intent);
	}
	
	private final void send_value_string(int def_id, ArrayList<String> format_masks, Context context) {
		Log.i(ABS_LOG_TAG, "send_value_string: " + def_id);
		ArrayList<String> value_items = new ArrayList<String>();
		for (String mask : format_masks) {
			value_items.add(get_format_mask_value(def_id, mask, context));
		}
		
		final Intent intent = new Intent(CANVAS_ACTION_UPDATE);
		intent.putExtra(CANVAS_DEFINITION_ID, def_id);
		intent.putExtra(CANVAS_DEFINITION_PACKAGE, context.getPackageName());
		intent.putExtra(CANVAS_VALUE_FORMAT_MASKS, format_masks);
		intent.putExtra(CANVAS_VALUE_FORMAT_MASK_VALUES, value_items);
		intent.setClassName(PEBBLE_CANVAS_PACKAGE, PEBBLE_CANVAS_PLUGIN_RECEIVER);
        context.sendBroadcast(intent);
	}
	
	private static int filename_i = 0;
	private static final int NUM_FILES = 5;
	private static final String FILENAME_PREFIX = "img_tmp_";
	
	private final void send_value_image(int def_id, Context context) {
		final Intent intent = new Intent(CANVAS_ACTION_UPDATE);
		intent.putExtra(CANVAS_DEFINITION_ID, def_id);
		intent.putExtra(CANVAS_DEFINITION_PACKAGE, context.getPackageName());
		intent.setClassName(PEBBLE_CANVAS_PACKAGE, PEBBLE_CANVAS_PLUGIN_RECEIVER);
        Bitmap b = get_bitmap_value(def_id, context);
		
		filename_i++;
		if (filename_i >= NUM_FILES) {
			filename_i = 0;
		}
		
		if (b == null) {
			context.sendBroadcast(intent);
		} else {
			// store on shared storage; don't send directly in intent
			File f = new File(context.getExternalFilesDir(null), FILENAME_PREFIX + filename_i);
			f.delete();
			FileOutputStream fOut = null;
			try {
				fOut = new FileOutputStream(f);
				b.compress(Bitmap.CompressFormat.PNG, 85, fOut);
			    fOut.flush();
			    
			    Log.i(ABS_LOG_TAG, "send_value_image: " + def_id + " / " + f.getAbsolutePath());
				intent.putExtra(CANVAS_VALUE_IMAGE, f.getAbsolutePath());
				context.sendBroadcast(intent);
			} catch (FileNotFoundException e) {
				Log.i(ABS_LOG_TAG, e.toString());
			} catch (IOException e) {
				Log.i(ABS_LOG_TAG, e.toString());
			} finally {
				try {
					if (fOut != null) {
						fOut.close();
					}
				} catch (IOException e) { /* */ }
			}
		}
	}
	
	public abstract class PluginDefinition {
		public int id; // identifier (to separate multiple plugins from the same app)
		public String name;// display name
	}
	
	public final class TextPluginDefinition extends PluginDefinition {
		public ArrayList<String> format_masks; // list of format masks
		public ArrayList<String> format_mask_descriptions; // description of each format mask
		public ArrayList<String> format_mask_examples; // example content of each format mask
		public String default_format_string;
	}
	
	public final class ImagePluginDefinition extends PluginDefinition {
		//
	}
	
	public static final void notify_canvas_updates_available(int def_id, Context context) {
		Log.i(ABS_LOG_TAG, "notify_canvas_updates_available: " + def_id);
		if (context == null) return;
		
		final Intent intent = new Intent(CANVAS_ACTION_NOTIFY_UPDATE);
		intent.putExtra(CANVAS_DEFINITION_ID, def_id);
		intent.putExtra(CANVAS_DEFINITION_PACKAGE, context.getPackageName());
		intent.setClassName(PEBBLE_CANVAS_PACKAGE, PEBBLE_CANVAS_PLUGIN_RECEIVER);
        context.sendBroadcast(intent);
	}
	
	//
	// methods which must be overridden in subclass to provide plugin functionality:-
	//
	
	// return a list of plugin definitions provided by this app. maybe 1
	protected abstract ArrayList<PluginDefinition> get_plugin_definitions(Context context);
	// return the current value for this single format mask item
	protected abstract String get_format_mask_value(int def_id, String format_mask, Context context);
	// return the current bitmap for this plugin
	protected abstract Bitmap get_bitmap_value(int def_id, Context context);
}
