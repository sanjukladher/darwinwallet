package com.ndu.mobile.darwinwallet;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.HashMap;

// Need the following import to get access to the app resources, since this
// class is in a sub-package.

// ----------------------------------------------------------------------

public class BillRecognizerActivity extends Activity implements IRecognitionEvent, IAutoFocusEvent {
	
	private static final int LOADING_SUCCESS = 1532;
	private static final int LOADING_FAIL = 1533;
	
    private Preview mPreview;  
    Camera mCamera;
    int numberOfCameras; 
    int cameraCurrentlyLocked;
          
    private Recognizer recognizer = null;
    private PowerManager.WakeLock wl;

    private Voice voice;
    // The first rear facing camera
    //int defaultCameraId;

    // SOUNDS
    private SoundPool soundPool = null;
    private HashMap<Integer, Integer> soundPoolMap;

	private static final int SOUND_FOCUS_COMPLETE 		= 1;
	private static final int SOUND_RECOGNITION_EVENT 	= 2;

    private ProgressDialog dialog = null;
    
    private TextView lblOutput;
    
    private CurrencyInfo loadedCurrency = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hide the window title.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);


        setContentView(R.layout.main);
        
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "Darwin Wallet Lock");
        
        loadSounds();
        recognizer = new Recognizer(this, this, this); 
        

        // Create a RelativeLayout container that will hold a SurfaceView,
        // and set it as the content of our activity.
        mPreview = new Preview(this, recognizer);
        ((FrameLayout) findViewById(R.id.frmPreview)).addView(mPreview);
        //setContentView(mPreview);

        lblOutput = (TextView) findViewById(R.id.lblOutput);
        voice = new Voice(this);
        loadSounds();
        
        
        // Tells android to always use the volume keys for adjusting media volume (not ringer)
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        

    	//defaultCameraId = 0;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
    	
    	doManualFocus();
    	
    	return super.onTouchEvent(event);
    }
    
    private void doManualFocus()
    {
    	// Do we have a camera obj and recognizer obj?
    	if ((mCamera != null) && (recognizer != null))
    	{   
    		// Is the dialog cleared out (indicating we're no longer "training"?
    		if ((dialog == null) || (dialog.isShowing() == false)) 
    		{
    			// Is the preference set allowing the user to touch to Focus?
    			if (SettingsActivity.getTouchToFocus(this))
    				recognizer.startAutoFocus(mCamera);
    		}
    	}
    }
    
    private void trainRecognizer(CurrencyInfo currency)
    {

        //recognizer.train(locality);


		if ((dialog == null) || (dialog.isShowing() == false))
		{
			dialog = ProgressDialog.show(this, "", 
					"Loading " + currency.getDescription() + "...", true, true);
			WorkerThread workerThread = new WorkerThread(handler, currency);
			workerThread.start();
		} 
		
    } 
    private void loadSounds()
    {
    	if (soundPool != null)
    		return;
    	
		soundPool = new SoundPool(4, AudioManager.STREAM_MUSIC, 100);
		soundPoolMap = new HashMap<Integer, Integer>();
	    soundPoolMap.put(SOUND_FOCUS_COMPLETE, soundPool.load(this, R.raw.sound_focuscomplete, 1));
	    soundPoolMap.put(SOUND_RECOGNITION_EVENT, soundPool.load(this, R.raw.sound_recognitionevent, 1));
    }
	private void playSound(int sound)
	{
		/*
		 * Updated: The next 4 lines calculate the current volume in a scale
		 * of 0.0 to 1.0
		 */
		AudioManager mgr = (AudioManager) this
				.getSystemService(Context.AUDIO_SERVICE);
		float streamVolumeCurrent = mgr.getStreamVolume(AudioManager.STREAM_MUSIC);
		float streamVolumeMax = mgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		float volume = streamVolumeCurrent / streamVolumeMax;

		/* Play the sound with the correct volume */
		soundPool.play(soundPoolMap.get(sound), volume, volume, 1, 0, 1f);

	}
    
    void cameraIsReady(int width, int height, int imgFormat)
    {

        PixelFormat pxFormat = new PixelFormat();
        PixelFormat.getPixelFormatInfo(imgFormat, pxFormat);
        recognizer.allocateBuffer(width, height, pxFormat.bitsPerPixel);

        mCamera.addCallbackBuffer(recognizer.getBuffer()); 
        mCamera.setPreviewCallbackWithBuffer(recognizer);
        
        if (SettingsActivity.getFlash(this))
        {
	        Parameters params = mCamera.getParameters();
	        params.setFlashMode(Parameters.FLASH_MODE_TORCH);
	        mCamera.setParameters(params);
        }
        
    }
    
    @Override
    protected void onResume() {
        super.onResume();

        wl.acquire();
        

        recognizer.setAutoFocus(SettingsActivity.getAutoFocus(this));
        
        CurrencyInfo prefCurrency = new CurrencyInfo(this, SettingsActivity.getCurrency(this));

        if ((loadedCurrency == null) || (loadedCurrency.equals(prefCurrency) == false))
        	trainRecognizer(prefCurrency);

        // Open the default i.e. the first rear facing camera.
        mCamera = Camera.open();


        
//        if (mCamera == null)
//        {
//        	// Possible on some tablets, no back-facing camera
//            int cameraCount = 0;
//            //Camera cam = null;
//            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
//            cameraCount = Camera.getNumberOfCameras();
//            for ( int camIdx = 0; camIdx < cameraCount; camIdx++ ) {
//                Camera.getCameraInfo( camIdx, cameraInfo );
//
//                try {
//                	mCamera = Camera.open( camIdx );
//                	if (mCamera != null)
//                		break;
//                } catch (RuntimeException e) {
//                	String s = "test";
//                } 
//            } 
//        } 
        
        
//        if (mCamera != null)
//        {
//        	Parameters parameters = mCamera.getParameters();
//	        //parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
//	        mCamera.setParameters(parameters);
//        }
        //Parameters params = mCamera.getParameters();
        //mCamera.setParameters(params);


        //cameraCurrentlyLocked = defaultCameraId;
        mPreview.setCamera(mCamera); 

    } 

    @Override
    protected void onPause() {
        super.onPause();

        // Because the Camera object is a shared resource, it's very
        // important to release it when the activity is paused.
        wl.release();
        if (mCamera != null) {
            mPreview.setCamera(null);
            mCamera.release();
            mCamera = null;
        }
    }

    @Override
    protected void onDestroy() {
    	super.onDestroy();
    	
    	if (voice != null)
    		voice.shutdown();
    }


	private int miss_count = 0;
	@Override
	public void recognitionEvent(RecognitionResult result) {
		
		if (result.match_found)
		{
			lblOutput.setTextColor(Color.GREEN);
			lblOutput.setText(loadedCurrency.getSymbol() + result.bill_value);
			voice.speakWithoutCallback(loadedCurrency.getSymbol() + result.bill_value);
			playSound(SOUND_RECOGNITION_EVENT);
		}
		else
		{
			String output = "Scanning";
			for (int i = 0; i < miss_count + 1; i++)
				output = output + ".";
			
			miss_count++;
			if (miss_count > 2)
				miss_count = 0;

			lblOutput.setTextColor(Color.RED);
			lblOutput.setText(output);
		}
	}

	@Override
	public void autoFocusUpdate(boolean finished) {
		if (finished == false)
		{
			voice.speakWithoutCallback("Focusing");
		}
		else
		{
			playSound(SOUND_FOCUS_COMPLETE);
		}
	}

    
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		

		if (keyCode == KeyEvent.KEYCODE_SEARCH)
		{
			toggleFlash();

			return true;
		}
		else if (keyCode == KeyEvent.KEYCODE_CAMERA)
		{
			recognizer.startAutoFocus(mCamera);
			
			return true;
		}
		
		return super.onKeyDown(keyCode, event);
	}
    
	private void toggleFlash()
	{
		try {

			Parameters parameters = mCamera.getParameters();

			if (parameters.getFlashMode().equals(
					Camera.Parameters.FLASH_MODE_TORCH)) {
				// parameters.setWhiteBalance(Parameters.WHITE_BALANCE_AUTO);
				parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
				mCamera.setParameters(parameters);
				voice.speakWithoutCallback("Flash off");
				SettingsActivity.setFlash(this, false);
			} else {
				// parameters.setWhiteBalance(Parameters.WHITE_BALANCE_AUTO);
				parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
				// parameters.setColorEffect(Parameters.EFF);
				// String wb = parameters.getWhiteBalance();

				// Log.d("BillRecognizerActivity", "White balance: " + wb);

				// int exp = parameters.getExposureCompensation();
				// Log.d("BillRecognizerActivity", "Exposure comp: " + exp);
				// parameters.setWhiteBalance(Parameters.WHITE_BALANCE_DAYLIGHT);
				// parameters.setWhiteBalance(Parameters.WHITE_BALANCE_DAYLIGHT);
				mCamera.setParameters(parameters);
				voice.speakWithoutCallback("Flash on");
				SettingsActivity.setFlash(this, true);
			}
		} catch (RuntimeException e) {
			// Means we tried to set a bad parameter. Just ignore it, don't
			// crash the app...
			Log.d("BillRecognizerActivity", "SET PARAMS ERROR!");
		}
	}
	
    @Override 
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    } 
    

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.settings)
		{
        	Intent settingsIntent = new Intent(this, SettingsActivity.class);
        	this.startActivity(settingsIntent);
			return true;
		}
		else if (item.getItemId() == R.id.tipstricks)
		{
        	Intent tipsTricksIntent = new Intent(this, TipsnTricksActivity.class);
        	this.startActivity(tipsTricksIntent);
			return true;
		}
		else if (item.getItemId() == R.id.focus)
		{
			doManualFocus();
		}
		else if (item.getItemId() == R.id.light)
		{
			toggleFlash();
		}
    	
        return super.onOptionsItemSelected(item);
    }


    // Define the Handler that receives messages from the thread and update the progress
    final Handler handler = new Handler() {
        @Override
		public void handleMessage(Message msg) {
            int status_code = msg.getData().getInt("status_code");

            //if (dialog.isShowing())
            //{
	            if (status_code == LOADING_SUCCESS){
	            	CurrencyInfo currency = msg.getData().getParcelable("currency_code");
	            	loadedCurrency = currency;
	            	
	            	voice.speakWithoutCallback("Loading complete");
	            }
	            else if (status_code == LOADING_FAIL)
	            {
	            	voice.speakWithoutCallback("Loading failed.");
	            }
	            
	            
	        	dialog.dismiss();
	        	dialog = null;
            //}
        }
    };

    /** Performs login asynchronously.  While this is running, the main window waits */
    private class WorkerThread extends Thread {

        
        Handler mHandler;
        private CurrencyInfo currency;

        WorkerThread(Handler h, CurrencyInfo currency) {
            mHandler = h;
            this.currency = currency;
        }
       
        @Override
		public void run() {
        	
        	
        	Bundle b = new Bundle();
        	Message msg = mHandler.obtainMessage();

        	boolean success = recognizer.train(this.currency.getCode());
        	
        	if (success)
        		b.putInt("status_code", LOADING_SUCCESS);
        	else
        		b.putInt("status_code", LOADING_FAIL);
        	
        	b.putParcelable("currency_code", this.currency);
        	
            msg.setData(b);
            mHandler.sendMessage(msg);
        }
        
    }
    

}

// ----------------------------------------------------------------------
