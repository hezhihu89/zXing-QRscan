package com.dtr.zxing.activity;

import android.app.Activity;
import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.dtr.zxing.R;

/**
 * @项目名 ：GooglePlay
 * @包名 ：com.dtr.zxing.activity
 * @User ： hezhihu89 by：贺志虎
 * @创建时间 ：2016 年 01 月 05 日   09时 43分.
 * @类的描述 : TODO：
 */
public class MainActivity extends Activity implements View.OnClickListener {

    private Button mScan;
    private Button mCreate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_layout);
        initView();
        initEvent();

    }

    private void initView() {
        mScan = (Button) findViewById(R.id.scan);
        mCreate = (Button) findViewById(R.id.createQR);
    }

    private void initEvent() {
        mScan.setOnClickListener(this);
        mCreate.setOnClickListener(this);

    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            case R.id.scan:
                toScan();
                break;
        }
    }

    private void toScan() {

        Intent intent = new Intent(this, CapureActivity.class);
        startActivity(intent);
    }
}
