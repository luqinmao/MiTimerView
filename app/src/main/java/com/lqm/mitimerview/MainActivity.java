package com.lqm.mitimerview;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    private CircularSeekBar circularSeekbar;
    private TimerView timerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        timerView = (TimerView) findViewById(R.id.timer_view);

    }


    public void onStart(View view){
        timerView.startTimer(45);
    }

    public void onCancel(View view){
        timerView.cancelTimer();
    }
}
