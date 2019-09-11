/*
 * (c) 2011 Drexel University 
 */
package edu.drexel.xop.android;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;
import edu.drexel.xop.R;

/**
 * file: ServiceBinding.java
 * @author duc
 * date: May 9, 2012
 *
 * description:
 * Example of binding and unbinding to the local service.
 * This demonstrates the implementation of a service which the client will
 * bind to, receiving an object through which it can communicate with the service.</p>
 * 
 * <p>Note that this is implemented as an inner class only keep the sample
 * all together; typically this code would appear in some separate class.
 */
public class ServiceBinding extends Activity {
    private boolean mIsBound;


    private LocalService mBoundService;
    
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mBoundService = ((LocalService.LocalBinder)service).getService();
            
            // Tell the user about this for our demo.
            Toast.makeText(ServiceBinding.this, R.string.local_service_connected,
                    Toast.LENGTH_SHORT).show();
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mBoundService = null;
            Toast.makeText(ServiceBinding.this, R.string.local_service_disconnected,
                    Toast.LENGTH_SHORT).show();
        }
    };
    
    void doBindService() {
        // Establish a connection with the service.  We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        bindService(new Intent(ServiceBinding.this, 
                LocalService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }
    
    void doUnbindService() {
        if (mIsBound) {
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        doUnbindService();
    }


    private OnClickListener mBindListener = new OnClickListener() {
        public void onClick(View v) {
            doBindService();
        }
    };

    private OnClickListener mUnbindListener = new OnClickListener() {
        public void onClick(View v) {
            doUnbindService();
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.local_service_binding);

        // Watch for button clicks.
        Button button = (Button)findViewById(R.id.bind);
        button.setOnClickListener(mBindListener);
        button = (Button)findViewById(R.id.unbind);
        button.setOnClickListener(mUnbindListener);
    }
}