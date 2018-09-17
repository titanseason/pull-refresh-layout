package com.uudove.widget.prl;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class PRLDefaultActivity extends Activity {

    private ListView mListView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prl_default_layout);
        initView();
        initList();
    }

    private void initView() {
        mListView = findViewById(R.id.list_view);
    }

    private void initList() {
        mListView.setAdapter(new SimpleTextAdapter());
    }

    private static class SimpleTextAdapter extends BaseAdapter {
        private LayoutInflater inflater;

        @Override
        public int getCount() {
            return 100;
        }

        @Override
        public String getItem(int position) {
            return "Item " + position;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (inflater == null) {
                inflater = LayoutInflater.from(parent.getContext());
            }
            if (convertView == null) {
                convertView = inflater.inflate(android.R.layout.simple_list_item_1, parent, false);
            }
            TextView textView = convertView.findViewById(android.R.id.text1);
            textView.setText(getItem(position));

            return convertView;
        }
    }
}
