package mil.navy.nrl.xop.android;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.LinkedList;
import java.util.List;

import edu.drexel.informatics.daemon.DaemonService;
import mil.navy.nrl.xop.android.R;

public class MGENDaemonService extends DaemonService {
    public static String MGEN_PORT = "MGEN_PORT";
    public static String MGEN_ADDRESS = "MGEN_ADDRESS";
    public static String MGEN_IFACE = "MGEN_IFACE";

    private SharedPreferences sharedPref;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(getDaemonLogTag(), "Creating MGENDaemonService");
        sharedPref = getSharedPreferences("xopSHAREDPREFS", 0);
    }

    @Override
    protected String getBinaryName() {
        return "mgen";
    }

    @Override
    protected String getDaemonLogTag() {
        return "mgen";
    }

    @Override
    protected String[] getBinaryArguments() {
        // ipv4 INPUT /data/local/tmp/mcm.mgn
        String[] retVal = buildMGENEvents();
        return retVal;
    }

    private String[] buildMGENEvents() {
        String bindInterface = sharedPref.getString(getString(R.string.xop_bind_interface), "eth0");
        Log.i(getDaemonLogTag(), "bindInterface: " + bindInterface);
        String mgenPort = sharedPref.getString(getString(R.string.mgen_port), "56797");
        String address = sharedPref.getString(getString(R.string.mgen_address), "225.239.240.251");
        List<String> params = new LinkedList<>();

        String listenEvent = "\"LISTEN UDP $1\"".replace("$1", mgenPort);
        String joinEvent = "\"JOIN $ADDRESS INTERFACE $INTERFACE\""
                .replace("$ADDRESS", address).replace("$INTERFACE", bindInterface);
        String trafficGenEvent = "\"ON 5 UDP SRC 4006 DST $ADDRESS/$LISTENPORT INTERFACE $INTERFACE PERIODIC [0.25 100]]\""
                .replace("$ADDRESS", address).replace("$INTERFACE", bindInterface).replace("$LISTENPORT", mgenPort);
        String[] events = new String[]{listenEvent, joinEvent, trafficGenEvent};
        for (String event : events) {
            params.add("event");
            params.add(event);
        }
        params.add("OUTPUT");
        long ts = System.currentTimeMillis();
        params.add("/sdcard/mcm-" + ts + ".out");
        Log.d(getDaemonLogTag(), "params: " + params.toArray());
        return params.toArray(new String[]{});
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        String tickerText = getText(R.string.mgen_ticker_text).toString();
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent, flags);
        Log.d(getDaemonLogTag(), "Starting MGEN");
        Notification notification = new Notification.Builder(this)
                .setContentTitle(getText(R.string.mgen_title))
                .setContentText(tickerText)
                .setSmallIcon(R.drawable.icon)
                .setContentIntent(pendingIntent)
                //.setTicker(tickerText)
                .build();
        this.startForeground(53, notification);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        this.stopForeground(true);


    }

}
