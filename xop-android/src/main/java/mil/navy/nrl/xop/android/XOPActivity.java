package mil.navy.nrl.xop.android;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Layout;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Date;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import mil.navy.nrl.xop.android.BuildConfig;
import mil.navy.nrl.xop.android.R;
import edu.drexel.xop.util.XOP;
import edu.drexel.xop.util.XopProperties;
import edu.drexel.xop.util.logger.LogListener;
import edu.drexel.xop.util.logger.LogRead;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * Run and manage XOP on Android
 *
 * @author Nick Gunia
 */
public class XOPActivity extends Activity implements LogListener {
	private static Logger logger = LogUtils.getLogger(XOPActivity.class
			.getName());
	private static final int UPDATE_TEXT = 1;
	private boolean infoViewIsVisible = false;
	private int scrollDelta;
	private String text = "";
	private Handler updateText;
	private SharedPreferences sharedPref;

	private Layout layout;
	private LinearLayout interactiveView;
	private TextView infoView;

	private Intent xopServiceIntent;
	private Intent gcsdIntent;

	private static Button indicator;
	private static Button startButton;
	private static Button stopButton;
    private static Button startGCSDButton;
    private static Button stopGCSDButton;


	public enum XOState {
		STOPPED, RUNNING, TRANSITIONING
	}

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_layout);
		if (sharedPref == null) {
			sharedPref = getSharedPreferences("xopSHAREDPREFS", 0);
			loadSharedPreferences();
		}
		// set up main view
		interactiveView = (LinearLayout) findViewById(R.id.interactive_view);
		// set up text view
		infoView = (TextView) findViewById(R.id.info_view);
		infoViewInit();
		// set initial visibilities
		infoView.setVisibility(View.INVISIBLE);
		interactiveView.setVisibility(View.VISIBLE);
		// button references
		startButton = (Button) findViewById(R.id.start_xop_button);
		stopButton = (Button) findViewById(R.id.stop_xop_button);
		indicator = (Button) findViewById(R.id.state_button);
        startGCSDButton = (Button) findViewById(R.id.start_gcsd_button);
        stopGCSDButton = (Button) findViewById(R.id.stop_gcsd_button);
        fillGCSDParams();

		Log.i("XOPActivity", "filesDir: " + getFilesDir());

        // set the initial light color and start/stop button availability and
		// set up broadcast receiver
		setStateOnAppOpen();
		xopServiceIntent = new Intent(this, XOPService.class);
		registerReceiver(broadcastReceiver, new IntentFilter(
				XOPService.INDICATOR_UPDATE_BROADCAST));
        gcsdIntent = new Intent(this, GCSDaemonService.class);
	}

	@Override
	protected void onResume() {
		super.onResume();
		setStateOnAppOpen();
		registerReceiver(broadcastReceiver, new IntentFilter(
				XOPService.INDICATOR_UPDATE_BROADCAST));
	}

	@Override
	protected void onPause() {
		super.onPause();
		unregisterReceiver(broadcastReceiver);
	}

	private void loadSharedPreferences() {
		Map<String, ?> keys = sharedPref.getAll();
		for (Map.Entry<String, ?> entry : keys.entrySet()) {
			XopProperties.setProperty(entry.getKey(), entry.getValue()
					.toString());
		}
	}

	@Override
	public void onBackPressed() {
		if (infoViewIsVisible) {
			infoViewIsVisible = false;
			infoView.setVisibility(View.INVISIBLE);
			interactiveView.setVisibility(View.VISIBLE);
		} else {
			Intent intent = new Intent();
			intent.setAction(Intent.ACTION_MAIN);
			intent.addCategory(Intent.CATEGORY_HOME);
			startActivity(intent);
			// use the home button, logs get messed up otherwise
			// super.onBackPressed();
		}
	}

	// ///////////////////////////////////////
	// Service and Indicator Light Methods //
	// ///////////////////////////////////////

	// receives the XO state the affect the indicator light
	private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			setState((XOState) intent.getSerializableExtra("indicator_state"));
		}
	};

    public void startGCSD(View v) {
        logger.info("start GCSD");
        String gcsdId = extractGCSDId(false);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(GCSDaemonService.GCSD_ID, gcsdId);
        editor.commit();

        startGCSDButton.setEnabled(false);
        stopGCSDButton.setEnabled(true);

		/*
		String bindInterface = sharedPref.getString(getString(R.string.xop_bind_interface),"eth0");
		String tickerText = getText(R.string.gcsd_ticker_text).toString().replace("$1", bindInterface).replace("$2", gcsdId);
		PendingIntent pendingIntent = PendingIntent.getService(this, 0, gcsdIntent, 0);

		Notification notification = new Notification.Builder(this)
				.setContentTitle(getText(R.string.gcsd_title))
				.setContentText(getText(R.string.gcsd_message))
				.setSmallIcon(R.drawable.icon)
				.setContentIntent(pendingIntent)
				.setTicker(tickerText)
				.build();
		startForeground(47, notification);
		*/

        startService(gcsdIntent);
    }

    public void stopGCSD(View v){
        logger.info("stop GCSD");
        extractGCSDId(true);

        stopGCSDButton.setEnabled(false);
        startGCSDButton.setEnabled(true);
        stopService(gcsdIntent);
    }

	private String extractGCSDId(boolean setEnabled) {
		TextView gcsIdTv = (TextView) findViewById(R.id.gcsd_id);
		gcsIdTv.setEnabled(setEnabled);
		String gcsdId = gcsIdTv.getText().toString();
		return gcsdId;
	}

    /**
     * test if the bind interface is set and has an IPv4 address associated with it
     * @return true if the interface is available, false otherwise
     */
    private boolean bindInterfaceAvailable() {
        logger.warning("checking bind interface");
        String bindInterfaceStr = sharedPref.getString("xop.bind.interface", XOP.BIND.INTERFACE  );
        try {
            NetworkInterface ni = NetworkInterface.getByName(bindInterfaceStr);
            logger.info("ni: "+ni);
            if( ni != null ) {
                ni.getInetAddresses();
                InetAddress bindAddress = null;
                for( InterfaceAddress ifaceAddr : ni.getInterfaceAddresses() ){
                    if( logger.isLoggable(Level.FINE) ) logger.fine("ifaceAddr: " + ifaceAddr);
                    if( ifaceAddr.getAddress() instanceof Inet4Address) {
                        bindAddress = ifaceAddr.getAddress();
                    }
                }
                if( bindAddress == null ) {
                    new AlertDialog.Builder(this).setMessage("No IPv4 address found for any interfaces in " + bindInterfaceStr).setTitle("XOP not starting").create().show();
                    logger.severe("No IPv4 address found for any interfaces in " + bindInterfaceStr);
                    return false;
                }
            } else {
                new AlertDialog.Builder(this).setMessage("Network Interface is null").setTitle("XOP not starting").create().show();
                logger.severe("network interface is null");
                return false;
            }
        } catch (SocketException e) {
            new AlertDialog.Builder(this).setMessage("Socket Exception with message, '" + e.getMessage() + "' occurred.").setTitle("XOP not starting").create().show();
            e.printStackTrace();
            return false;
        }
        return true;
    }

public void startXOPService(View v) {
//        if( bindInterfaceAvailable() ) {
            startButton.setEnabled(false);
            startService(xopServiceIntent);
//        }
    }

	public void stopXOPService(View v) {
		stopService(xopServiceIntent);
		startButton.setEnabled(true);
	}

	private void setState(XOState state) {
		switch (state) {
		case STOPPED: // red
			indicator.setBackgroundResource(R.drawable.indicator_red);
			startButton.setEnabled(true);
			break;
		case RUNNING: // green
			indicator.setBackgroundResource(R.drawable.indicator_green);
			stopButton.setEnabled(true);
			break;
		case TRANSITIONING: // yellow
			indicator.setBackgroundResource(R.drawable.indicator_yellow);
			startButton.setEnabled(false);
			stopButton.setEnabled(false);
			break;
		}
	}

	private void setStateOnAppOpen() {
		setState(XOState.TRANSITIONING);
		if (isServiceRunning("mil.navy.nrl.xop.android.XOPService")) {
			setState(XOState.RUNNING);
		} else {
			setState(XOState.STOPPED);
		}
		fillGCSDParams();
	}

	private void fillGCSDParams(){
        TextView gcsIdTv = (TextView) findViewById(R.id.gcsd_id);
        String gcsdId = gcsIdTv.getText().toString();
        if( "0".equals(gcsdId) ){
            gcsIdTv.setText(sharedPref.getString(GCSDaemonService.GCSD_ID, "00"));
        }
        if( isServiceRunning(GCSDaemonService.class.getName())){
            Log.i("XOP", "GCSD Already Running");

            startGCSDButton.setEnabled(false);
            stopGCSDButton.setEnabled(true);
        } else {
            startGCSDButton.setEnabled(true);
            stopGCSDButton.setEnabled(false);
        }
    }

	private boolean isServiceRunning(String serviceName) {
		boolean serviceRunning = false;
		ActivityManager am = (ActivityManager) this
				.getSystemService(ACTIVITY_SERVICE);
		for (ActivityManager.RunningServiceInfo rsi : am.getRunningServices(Integer.MAX_VALUE)) {
			if (rsi.service.getClassName().equals(serviceName)) {
				serviceRunning = true;
			}
		}
		return serviceRunning;
	}

	// ////////////////////
	// Action Bar Items //
	// ////////////////////

	// populates the action bar
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu items for use in the action bar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_activity_actions, menu);
		return super.onCreateOptionsMenu(menu);
	}

	private void openPreferenceView() {
		Intent intent = new Intent(this, XOPPreferencesActivity.class);
		startActivity(intent);
	}

	private void openInfoView() {
		infoViewIsVisible = true;
		interactiveView.setVisibility(View.INVISIBLE);
		infoView.setVisibility(View.VISIBLE);
	}

	// handle action bar item clicks
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		boolean retVal = false;
		// Handle presses on the action bar items
		switch (item.getItemId()) {
		case R.id.action_settings:
			openPreferenceView();
			retVal = true;
			break;
		case R.id.action_view_log:
			openInfoView();
			infoViewInit();
			retVal = true;
			break;
		case R.id.version:
			Log.i("XOP", "opening version view");
			logger.info("opening version");
			handleVersionView();
			retVal = true;
			break;
		default:
			retVal = super.onOptionsItemSelected(item);
		}
		return retVal;
	}

	private void handleVersionView(){
		Log.i("XOP", "opening version view");
		logger.info("opening version");

		View versionView = getLayoutInflater().inflate(R.layout.version_layout, null, false);
		TextView buildDateTv = (TextView) versionView.findViewById(R.id.about_builddate);
		Date buildDate = new Date(BuildConfig.TIMESTAMP);
		buildDateTv.setText(getString(R.string.about_build).replace("%s", "" + buildDate));

		String versionNumber = BuildConfig.VERSION_NAME;
		TextView versionTv = (TextView) versionView.findViewById(R.id.about_version);
		versionTv.setText(getString(R.string.about_version).replace("%s", versionNumber));

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setView(versionView);
		builder.show();

	}


	// TODO find a better way to do the log view
	private void infoViewInit() {
		infoView.setText(text);
		infoView.setMovementMethod(new ScrollingMovementMethod());
		infoView.setFreezesText(true);
		infoView.setTextColor(Color.WHITE); // this stops the text from fading
											// out when scrolling
		infoView.setFocusable(true);

		// android UI elements cannot be modified outside of the UI thread
		updateText = new Handler() {
			public void handleMessage(Message msg) {
				if (msg.what == UPDATE_TEXT) {
					text = msg.obj.toString();
					infoView.append(text);
					// scroll to the bottom
					layout = infoView.getLayout();
					if (layout != null)
						scrollDelta = layout.getLineBottom(infoView
								.getLineCount() - 1)
								- infoView.getScrollY()
								- infoView.getHeight();
					if (scrollDelta > 0)
						infoView.scrollBy(0, scrollDelta);
				}
			}
		};
		LogRead.addListener(this, Level.FINEST);
	}

	// ///////////////////////
	// Information Buttons //
	// ///////////////////////

	public void popUpLocalUsers(View v) {
        logger.fine("Return local users");
//		ArrayList<String> localUsers = new ArrayList<>(XOProxy
//				.getInstance().getLocalUsers());
//		popUpWindow(localUsers);
	}

	public void popUpRemoteUsers(View v) {
        logger.fine("Return locally discovered users");
//		ArrayList<String> remoteUsers = new ArrayList<>(XOProxy
//				.getInstance().getRemoteUsers());
//		popUpWindow(remoteUsers);
	}

	public void popUpRooms(View v) {
        logger.fine("Return the rooms created/discovered");
//        ArrayList<String> rooms = new ArrayList<>(XOProxy
//				.getInstance().getRooms());
//		popUpWindow(rooms);
	}

	public void popUpRoomsForOccupants(View v) {
        logger.fine("Get the occupants in each room");
//		ArrayList<String> rooms = new ArrayList<>(XOProxy
//				.getInstance().getRooms());
//		popUpSpinner(rooms);
	}

	/*@SuppressWarnings("deprecation")
	private void popUpSpinner(ArrayList<String> content) {

		// ensure that there is content to display
		if (content.isEmpty()) {
			Toast.makeText(getApplicationContext(), "None Found",
					Toast.LENGTH_SHORT).show();
			return;
		}

		LayoutInflater layoutInflater = (LayoutInflater) getBaseContext()
				.getSystemService(LAYOUT_INFLATER_SERVICE);
		View popupView = layoutInflater.inflate(R.layout.popup_spinner, null,
				true);
		final PopupWindow pw = new PopupWindow(popupView,
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, true);

		// populate the spinner
		final Spinner popupSpinner = (Spinner) popupView
				.findViewById(R.id.roomspinner);
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(
				XOPActivity.this, android.R.layout.simple_spinner_item, content);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		popupSpinner.setAdapter(adapter);

		// close the popup if user clicks outside of popup
		pw.setBackgroundDrawable(new BitmapDrawable()); // TODO: BitmapDrawable() just closes the view, need to find something else to replace
		pw.setOutsideTouchable(true);
		// show the popup
		pw.showAtLocation(this.findViewById(R.id.main_view), Gravity.CENTER, 0,
				0);

		// add a button to pull up the occupants of the room selected in the
		// spinner
		Button viewOccupants = (Button) popupView
				.findViewById(R.id.viewOccupants);
		viewOccupants.setOnClickListener(new Button.OnClickListener() {
			@Override
			public void onClick(View v) {
				ArrayList<String> occupants = new ArrayList<String>(XOProxy
						.getInstance().getRoomOccupants(
								(String) popupSpinner.getSelectedItem()));
				popUpWindow(occupants);
			}
		});
	}*/

	/*@SuppressWarnings("deprecation")
	private void popUpWindow(ArrayList<String> content) {

		// ensure that there is content to display
		if (content.isEmpty()) {
			Toast.makeText(getApplicationContext(), "None Found",
					Toast.LENGTH_SHORT).show();
			return;
		}

		LayoutInflater layoutInflater = (LayoutInflater) getBaseContext()
				.getSystemService(LAYOUT_INFLATER_SERVICE);
		View popupView = layoutInflater.inflate(R.layout.popup, null, true);
		PopupWindow pw = new PopupWindow(popupView, LayoutParams.WRAP_CONTENT,
				LayoutParams.WRAP_CONTENT, true);
		ListView popupListView = (ListView) popupView
				.findViewById(R.id.popupListView);
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(
				XOPActivity.this, android.R.layout.simple_list_item_1, content);

		// populate list view
		popupListView.setAdapter(adapter);

		// close the popup if user clicks outside of popup
		pw.setBackgroundDrawable(new BitmapDrawable()); // TODO: BitmapDrawable() just closes the view, need to find something else to replace
		pw.setOutsideTouchable(true);
		// show the popup
		pw.showAtLocation(this.findViewById(R.id.main_view), Gravity.CENTER, 0,
				0);
	}*/

	// /////////////////////
	// Interface Methods //
	// /////////////////////

	@Override
	public void processLogMessage(String from, Level level, String message) {
		updateText.obtainMessage(UPDATE_TEXT,
				"[" + level.toString() + "]" + from + ":" + message + "\n")
				.sendToTarget();
	}

	@Override
	public void close() {
		// nothing to do here
	}

	@Override
	public void flush() {
		// nothing to do here
	}
}
