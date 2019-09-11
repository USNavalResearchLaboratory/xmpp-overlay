package mil.navy.nrl.xop.android;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.net.wifi.WifiManager.WifiLock;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;
import mil.navy.nrl.xop.android.R;
import mil.navy.nrl.xop.android.XOPActivity.XOState;
import edu.drexel.xop.core.XOProxy;
import edu.drexel.xop.net.XopNet;
import edu.drexel.xop.net.XopNetImpl;
import edu.drexel.xop.util.XOP;
import edu.drexel.xop.util.logger.LogUtils;

import java.io.InputStream;
import java.util.logging.Logger;


public class XOPService extends Service {
	private static Logger logger = LogUtils.getLogger(XOPActivity.class.getName());
    private static String TAG = "XOPService";

	private WifiLock wifiLock;
	private MulticastLock multicastLock;
    private String sslPassword = XOP.SSL.PASSWORD;
    static final String INDICATOR_UPDATE_BROADCAST = "mil.navy.nrl.xop.android.updateindicator";
	private Intent intent;

    @Override
	public void onCreate() {
		intent = new Intent(INDICATOR_UPDATE_BROADCAST);
		// set up wifi/multicast locks
		WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
		boolean refCounted = false;
		wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL, "xopwifilock");
		wifiLock.setReferenceCounted(refCounted);
		multicastLock = wifi.createMulticastLock("xopmulticastLock");
		multicastLock.setReferenceCounted(refCounted);
        InputStream is = this.getBaseContext().getResources().openRawResource(R.raw.logging);
        Log.i(TAG, "filesDir: " + getFilesDir());
        LogUtils.loadLoggingProperties(is);

    }

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
        // TODO 2018-12-13 setup SSL keystore only on new instance of XO
        // ClientConnection.setKeyStore(this.getResources().openRawResource(R.raw.keystore),
        // 		this.getResources().openRawResource(R.raw.cacerts), sslPassword);

		// async task to avoid networking on main thread 
		new StartXOPTask().execute(null,null,null);

		// set XOPService as a foreground task
		Intent notificationIntent = new Intent(this, XOPActivity.class);
		notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

		Notification notification = new Notification.Builder(this, NotificationChannel.DEFAULT_CHANNEL_ID)
		.setContentTitle("XOP running")
				.setContentText("XOP")
		.setSmallIcon(R.drawable.icon)
		.setLargeIcon(BitmapFactory.decodeResource(this.getResources(), R.drawable.icon))
		.setContentIntent(pendingIntent)
		.build();

		startForeground(ONGOING_NOTIFICATION_ID, notification); // id selected arbitrarily

		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		// async task to avoid networking on main thread
		new StopXOPTask().execute(null,null,null);
	}

    /**
     *
     */
	private class StartXOPTask extends AsyncTask<Void, Void, Boolean> {
        private String errorMessage = null;
		@Override
		protected void onPreExecute() {
			intent.putExtra("indicator_state", XOState.TRANSITIONING);
			sendBroadcast(intent);	        
			// open wifi manager
			wifiLock.acquire();
			multicastLock.acquire();
		}
		@Override
		protected Boolean doInBackground(Void... arg0) {

            XOProxy proxy = XOProxy.getInstance();
            XopNet xopNet = new XopNetImpl(proxy.getClientManager());
            errorMessage = proxy.init(xopNet);
            if( errorMessage == null ) {
                return true;
            } else {
				logger.severe("Unable to init XOP: " + errorMessage);
				return false;
            }
		}

		@Override
		protected void onPostExecute(Boolean result) {
            String toastText = "XOP Started";
            if ( result == Boolean.TRUE) {
                intent.putExtra("indicator_state", XOState.RUNNING);
            } else {
                intent.putExtra("indicator_state", XOState.STOPPED);
                toastText = errorMessage;
            }
			Toast.makeText(getApplicationContext(), toastText, Toast.LENGTH_SHORT).show();
			sendBroadcast(intent);
		}
	}

	private class StopXOPTask extends AsyncTask<Void, Void, Void> {
		@Override
		protected void onPreExecute() {
			intent.putExtra("indicator_state", XOState.TRANSITIONING);
			sendBroadcast(intent);
		}
		@Override
		protected Void doInBackground(Void... arg0) {

			XOProxy.getInstance().stop();

			// cleans up lock files created to prevent multiple threads from editing log files
			for (java.util.logging.Handler h : logger.getHandlers()) {
				h.close();
			}
			return null;
		}
		@Override
		protected void onPostExecute(Void result) {
			if (multicastLock != null) {
				multicastLock.release();
				multicastLock = null;
			}
			if (wifiLock != null){
				wifiLock.release();
				wifiLock = null;
			}
			Toast.makeText(getApplicationContext(), "XOP Stopped", Toast.LENGTH_SHORT).show();
			intent.putExtra("indicator_state", XOState.STOPPED);
			sendBroadcast(intent);
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		// XOPService is not a bindable service, return null
		return null;
	}
}