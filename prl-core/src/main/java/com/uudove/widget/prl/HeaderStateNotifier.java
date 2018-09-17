package com.uudove.widget.prl;

import android.support.annotation.Nullable;

import com.uudove.widget.prl.PullRefreshLayout.OnHeaderRefreshListener;

class HeaderStateNotifier {

    private PullRefreshLayout layout;
    private OnHeaderRefreshListener mHeaderRefreshListener;
    private IHeader mHeaderInterface;
    private int state = -1;

    HeaderStateNotifier(PullRefreshLayout layout) {
        this.layout = layout;
    }

    void setOnHeaderRefreshListener(@Nullable OnHeaderRefreshListener listener) {
        this.mHeaderRefreshListener = listener;
    }

    void setHeaderInterface(IHeader headerInterface) {
        this.mHeaderInterface = headerInterface;
    }

    void onHeaderStateChanged(int state) {
        if (this.state != state) {
            if (mHeaderRefreshListener != null && state == IHeader.STATE_REFRESHING) {
                mHeaderRefreshListener.onHeaderRefresh(layout);
            }
            if (mHeaderInterface != null) {
                mHeaderInterface.onHeaderStateChanged(layout, state, 0);
            }
            this.state = state;
        }
    }
}
