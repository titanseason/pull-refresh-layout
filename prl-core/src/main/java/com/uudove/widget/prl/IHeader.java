package com.uudove.widget.prl;

import android.support.annotation.NonNull;
import android.view.View;
import android.view.ViewGroup;

public interface IHeader {

    int STATE_IDLE = 0;

    int STATE_DRAGGING = 1;

    int STATE_DRAGGING_MAX_RANGE = 2;

    int STATE_REFRESHING = 3;

    int STATE_LOAD_COMPLETE = 4;

    @NonNull
    View onCreateHeaderView(@NonNull ViewGroup parent);

    void onHeaderStateChanged(@NonNull PullRefreshLayout layout, int state, float offsetInPercent);

}
