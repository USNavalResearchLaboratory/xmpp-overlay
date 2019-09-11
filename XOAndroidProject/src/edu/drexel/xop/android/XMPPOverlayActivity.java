package edu.drexel.xop.android;

import edu.drexel.xop.Run;
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class XMPPOverlayActivity extends Activity
{
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
		TextView tv = new TextView(this);
		tv.setText("Starting XO...");
		setContentView(tv);
		Run.main(new String[0]);
    }
}
