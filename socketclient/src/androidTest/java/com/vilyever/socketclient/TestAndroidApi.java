package com.vilyever.socketclient;

import android.os.CountDownTimer;
import android.os.Looper;
import android.os.SystemClock;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TestAndroidApi {

    @Test
    public void testCountDownTimer() {

        System.out.println("tick == " + (SystemClock.elapsedRealtime()+Long.MAX_VALUE));

        Looper.prepare();

        new CountDownTimer(6000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                System.out.println("tick -- " + millisUntilFinished);
            }

            @Override
            public void onFinish() {
                this.start();
            }
        }.start();

        Looper.loop();
    }
}
