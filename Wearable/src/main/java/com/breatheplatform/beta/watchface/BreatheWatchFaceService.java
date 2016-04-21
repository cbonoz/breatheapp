//package com.breatheplatform.beta.watchface;
//
//import android.content.Intent;
//import android.graphics.Canvas;
//import android.graphics.Rect;
//import android.os.Bundle;
//import android.support.wearable.watchface.CanvasWatchFaceService;
//import android.support.wearable.watchface.WatchFaceStyle;
//import android.util.Log;
//import android.view.SurfaceHolder;
//
//import com.breatheplatform.beta.MainActivity;
//
///**
// * Created by cbono on 4/18/16.
// */
//public class BreatheWatchFaceService extends CanvasWatchFaceService {
//    private static final String TAG = "BreatheWatchFaceService";
//
//    @Override
//    public Engine onCreateEngine() {
//        /* provide your watch face implementation */
//
//        return new Engine();
//    }
//
//    private void startMainActivity() {
//        Log.d(TAG, "startMainActivity...");
//        Intent i = new Intent();
//        i.setClass(this, MainActivity.class);
//        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
//        startActivity(i);
//    }
//
//    /* implement service callback methods */
//    private class Engine extends CanvasWatchFaceService.Engine {
//
//        @Override
//        public void onCreate(SurfaceHolder holder) {
//            super.onCreate(holder);
//
//
//            /* initialize your watch face */
//            Log.d(TAG, "create BreatheWatchFaceService");
//            setTouchEventsEnabled(true);
//            setWatchFaceStyle(new WatchFaceStyle.Builder(BreatheWatchFaceService.this)
//                    .setAcceptsTapEvents(true)
//                            // other style customizations
//                    .build());
//
//        }
//
//        //tap anywhere to launch main activity from the watchface
//        @Override
//        public void onTapCommand(int tapType, int x, int y, long eventTime) {
//            if (Log.isLoggable(TAG, Log.DEBUG)) {
//                Log.d(TAG, "Tap Command: " + tapType);
//            }
//            Log.d(TAG, "onTap: start main activity");
//            startMainActivity();
//        }
//
//        @Override
//        public void onPropertiesChanged(Bundle properties) {
//            super.onPropertiesChanged(properties);
//            /* get device features (burn-in, low-bit ambient) */
//        }
//
//        @Override
//        public void onTimeTick() {
//            super.onTimeTick();
//            /* the time changed */
//        }
//
//        @Override
//        public void onAmbientModeChanged(boolean inAmbientMode) {
//            super.onAmbientModeChanged(inAmbientMode);
//            /* the wearable switched between modes */
//        }
//
//        @Override
//        public void onDraw(Canvas canvas, Rect bounds) {
//            /* draw your watch face */
//        }
//
//        @Override
//        public void onVisibilityChanged(boolean visible) {
//            super.onVisibilityChanged(visible);
//            /* the watch face became visible or invisible */
//        }
//    }
//}
