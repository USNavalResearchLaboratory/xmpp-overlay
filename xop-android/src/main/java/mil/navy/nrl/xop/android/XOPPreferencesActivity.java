package mil.navy.nrl.xop.android;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.logging.Logger;

import mil.navy.nrl.xop.android.R;
import edu.drexel.xop.util.XopProperties;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * Manage XOP preferences for android, load shared prefs if available, if not
 * load defaults and save to shared prefs
 * 
 * @author Nick Gunia
 */

public class XOPPreferencesActivity extends Activity {
	private final static String root = Environment
			.getExternalStorageDirectory().toString();
	private static final Logger logger = LogUtils.getLogger(
			XOPPreferencesActivity.class.getName(), root);
	private SharedPreferences sharedPref;
	private SharedPreferences.Editor editor = null;
	private final String DEFAULT_PROPERTIES = "default";
	private final String SHARED_PROPERTIES = "shared";

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.pref_layout);
		// Load preferences
		sharedPref = getSharedPreferences("xopSHAREDPREFS", 0);
		editor = sharedPref.edit();
		updatePreferenceFields(SHARED_PROPERTIES);
	}

	private void updatePreferenceFields(String set) {
		LinearLayout layout = (LinearLayout) findViewById(R.id.pref_lin_layout);
		// start at 2 to skip default/save buttons
		for (int i = 2; i < layout.getChildCount(); i++) {
			// skip dividers
			if (!(layout.getChildAt(i) instanceof RelativeLayout)) {
				continue;
			}
			// never add more than a label/field to each relative sub-layout
			View prefView = ((RelativeLayout) layout.getChildAt(i))
					.getChildAt(1);
			Class<?> prefClass = prefView.getClass();
			String name;
			if (prefClass == EditText.class) {
				int type = ((EditText) prefView).getInputType();
				name = (String) prefView.getTag();
				// bitwise or so the xop.ssl.password field can be accessed
				if (type == InputType.TYPE_CLASS_TEXT
						|| type == (InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_CLASS_TEXT)) {
					// load a string value into EditText
					if (set.equals(SHARED_PROPERTIES)) {
						((EditText) prefView).setText( sharedPref.getString(name, XopProperties.getProperty(name)));
					} else if (set.equals(DEFAULT_PROPERTIES)) {
						((EditText) prefView).setText(XopProperties
								.getProperty(name));
					}
				} else if (type == InputType.TYPE_CLASS_NUMBER) {
					// load a number value into EditText
					if (set.equals(SHARED_PROPERTIES)) {
                        logger.finer("name: "+name);
						((EditText) prefView).setText( String.valueOf(
                                sharedPref.getInt(name,XopProperties.getIntProperty(name))));
					} else if (set.equals(DEFAULT_PROPERTIES)) {
						((EditText) prefView).setText( Integer.toString( XopProperties.getIntProperty(name)));
					}
				}
			} else if (prefClass == ToggleButton.class) {
				name = (String) prefView.getTag();
				// load a boolean value into ToggleButton
				if (set.equals(SHARED_PROPERTIES)) {
					((ToggleButton) prefView).setChecked(sharedPref.getBoolean(
							name, XopProperties.getBooleanProperty(name)));
				} else if (set.equals(DEFAULT_PROPERTIES)) {
					((ToggleButton) prefView).setChecked(XopProperties
							.getBooleanProperty(name));
				}
			}
		}
	}

	public void loadDefaultPreferences(View v) {
		XopProperties.loadDefaults();
		updatePreferenceFields(DEFAULT_PROPERTIES);
		logger.info("properties set to defaults");
	}

	public void saveCurrentPreferences(View view) {
		LinearLayout layout = (LinearLayout) findViewById(R.id.pref_lin_layout);
		String name = null;
		String value = null;
		Toast toast;

		for (int i = 2; i < layout.getChildCount(); i++) {
			// skip dividers
			if (!(layout.getChildAt(i) instanceof RelativeLayout)) {
				continue;
			}
			// never add more than a label/field to each relative sublayout
			View prefView = ((RelativeLayout) layout.getChildAt(i))
					.getChildAt(1);
			Class<?> prefClass = prefView.getClass();

			if (prefClass == EditText.class) {
				int type = ((EditText) prefView).getInputType();
				name = (String) prefView.getTag();
				value = ((EditText) prefView).getText().toString();
				if (type == InputType.TYPE_CLASS_TEXT
						|| type == (InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_CLASS_TEXT)) {
					// save a string value into shared preferences
					editor.putString(name, value);
				} else if (type == InputType.TYPE_CLASS_NUMBER) {
					// save an int value into shared preferences
					editor.putInt(name, Integer.parseInt(value));
				}
			} else if (prefClass == ToggleButton.class) {
				name = (String) prefView.getTag();
				value = ((ToggleButton) prefView).isChecked() ? "true" : "false";
				// save a boolean value into shared preferences
				editor.putBoolean(name, Boolean.parseBoolean(value));
			}
			XopProperties.setProperty(name, value);
		}
		// apply and save changes to sharedPrefs
		if (editor.commit()) {
			toast = Toast.makeText(this, "Properties Saved!",
					Toast.LENGTH_SHORT);
			toast.show();
			logger.info("properties saved!");
		} else {
			logger.info("properties were not saved properly!");
		}
	}
}
