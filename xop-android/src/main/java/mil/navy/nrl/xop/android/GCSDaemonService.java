package mil.navy.nrl.xop.android;


import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import edu.drexel.informatics.daemon.DaemonService;
import mil.navy.nrl.xop.android.R;

/**
 * GCSD daemon service, uses android-daemon-service from BellerophonMobile
 * Created by duc on 7/28/17.
 */

public class GCSDaemonService extends DaemonService {
    public static String GCSD_ID = "GCSD_ID";

    private SharedPreferences sharedPref;

    @Override
    public void onCreate(){
        super.onCreate();
        Log.i(getDaemonLogTag(), "Creating GCSDaemonService");
        sharedPref = getSharedPreferences("xopSHAREDPREFS", 0);
    }

    @Override
    protected String getBinaryName() {
        return "gcsd-android";
    }

    @Override
    protected String getDaemonLogTag() {
        return "gcsd";
    }

    @Override
    protected String[] getBinaryArguments() {
        String gcsdId = sharedPref.getString(GCSD_ID,"0");
        String bindInterface = sharedPref.getString(getString(R.string.xop_bind_interface),"eth0");
        Log.i(getDaemonLogTag(), "gcsdId: "+ gcsdId);
        Log.i(getDaemonLogTag(), "bindInterface: "+ bindInterface);
        String[] retVal = new String[] {"-f", getAssetPath()+"/data/gcsd-config.json", "-i", gcsdId, "-I", bindInterface};
        return retVal;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        super.onStartCommand(intent, flags, startId);
        String gcsdId = sharedPref.getString(GCSD_ID,"0");
        String bindInterface = sharedPref.getString(getString(R.string.xop_bind_interface),"eth0");
        String tickerText = getText(R.string.gcsd_ticker_text).toString().replace("$1", bindInterface).replace("$2", gcsdId);
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent, flags);

        Notification notification = new Notification.Builder(this)
                .setContentTitle(getText(R.string.gcsd_title))
                //.setContentTitle(getText(R.string.gcsd_message))
                //.setContentText(getText(R.string.gcsd_message))
                .setContentText(tickerText)
                .setSmallIcon(R.drawable.icon)
                .setContentIntent(pendingIntent)
                //.setTicker(tickerText)
                .build();
        this.startForeground(47, notification);
        Toast.makeText(getApplicationContext(), "GCSD Running!", Toast.LENGTH_SHORT).show();

        return START_STICKY;
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        this.stopForeground(true);
    }

}
