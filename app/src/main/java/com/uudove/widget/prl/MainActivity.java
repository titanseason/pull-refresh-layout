package com.uudove.widget.prl;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;

public class MainActivity extends Activity implements OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.default_prl).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.default_prl:
                onDefaultPRLClick();
                break;
        }
    }

    private void onDefaultPRLClick() {
        Intent intent = new Intent(this, PRLDefaultActivity.class);
        startActivity(intent);
    }
}
