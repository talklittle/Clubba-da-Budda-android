package com.andrewshu.android.clubbadabudda;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.andrewshu.android.clubbadabudda.RPGView.RPGThread;

public class StartActivity extends Activity {
	
    /** A handle to the thread that's actually running the animation. */
    private RPGThread mRPGThread;

    /** A handle to the View in which the game is running. */
    private RPGView mRPGView;

    // the play start button
    private Button mButton;


    /** Called when the activity is first created. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // turn off the window's title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, 
                WindowManager.LayoutParams.FLAG_FULLSCREEN); 

        // tell system to use the layout defined in our XML file
        setContentView(R.layout.main);

        // get handles to the RPGView from XML, and its RPGThread
        mRPGView = (RPGView) findViewById(R.id.lunar);
        mRPGThread = mRPGView.getThread();

        // give the RPGView a handle to the TextView used for messages
        mRPGView.setTextView((TextView) findViewById(R.id.text));

        if (savedInstanceState == null) {
            // we were just launched: set up a new game
            mRPGThread.setState(RPGThread.STATE_READY);
            Log.w(this.getClass().getName(), "SIS is null");
        } else {
            // we are being restored: resume a previous game
            mRPGThread.restoreState(savedInstanceState);
            Log.w(this.getClass().getName(), "SIS is nonnull");
        }
    }
}