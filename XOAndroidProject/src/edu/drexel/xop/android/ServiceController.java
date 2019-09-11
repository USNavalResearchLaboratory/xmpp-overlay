/*
 * (c) 2011 Drexel University 
 */
package edu.drexel.xop.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import edu.drexel.xop.R;

/**
 * file: ServiceController.java
 * @author duc
 * date: May 9, 2012
 *
 * description:
 * <p>Example of explicitly starting and stopping the local service.
 * This demonstrates the implementation of a service that runs in the same
 * process as the rest of the application, which is explicitly started and stopped
 * as desired.</p>
 * 
 * <p>Note that this is implemented as an inner class only keep the sample
 * all together; typically this code would appear in some separate class.
 */
public class ServiceController extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.local_service_controller);

        // Watch for button clicks.
        Button button = (Button)findViewById(R.id.start);
        button.setOnClickListener(mStartListener);
        button = (Button)findViewById(R.id.stop);
        button.setOnClickListener(mStopListener);
    }

    private OnClickListener mStartListener = new OnClickListener() {
        public void onClick(View v) {
            // Make sure the service is started.  It will continue running
            // until someone calls stopService().  The Intent we use to find
            // the service explicitly specifies our service component, because
            // we want it running in our own process and don't want other
            // applications to replace it.
            startService(new Intent(ServiceController.this,
                    LocalService.class));
        }
    };

    private OnClickListener mStopListener = new OnClickListener() {
        public void onClick(View v) {
            // Cancel a previous call to startService().  Note that the
            // service will not actually stop at this point if there are
            // still bound clients.
            stopService(new Intent(ServiceController.this,
                    LocalService.class));
        }
    };
}
