package com.uudove.widget.prl;

import android.animation.ObjectAnimator;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

class IHeaderDefaultImpl implements IHeader {

    private ImageView mHeaderIcon;
    private ProgressBar mLoadingProgress;
    private TextView mHeaderTitle;

    @NonNull
    @Override
    public View onCreateHeaderView(@NonNull ViewGroup parent) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.prl_header_default_layout, parent,
                false);
        mHeaderIcon = view.findViewById(R.id.prl_header_icon);
        mLoadingProgress = view.findViewById(R.id.prl_header_progress);
        mHeaderTitle = view.findViewById(R.id.prl_header_text);
        return view;
    }

    @Override
    public void onHeaderStateChanged(@NonNull PullRefreshLayout layout, int state, float offsetInPercent) {
        if (state == STATE_IDLE || state == STATE_DRAGGING) {
            mHeaderIcon.setVisibility(View.VISIBLE);
            mLoadingProgress.setVisibility(View.GONE);
            mHeaderTitle.setText(R.string.prl_header_pull_to_refresh);

            startRotate(mHeaderIcon, 0);

        } else if (state == STATE_DRAGGING_MAX_RANGE) {
            startRotate(mHeaderIcon, 180);

        } else if (state == STATE_REFRESHING) {
            mHeaderIcon.setVisibility(View.GONE);
            mLoadingProgress.setVisibility(View.VISIBLE);
            mHeaderTitle.setText(R.string.prl_header_loading);

        } else if (state == STATE_LOAD_COMPLETE) {
            mHeaderIcon.setVisibility(View.VISIBLE);
            mLoadingProgress.setVisibility(View.GONE);
            mHeaderTitle.setText(R.string.prl_header_load_complete);

            mHeaderIcon.clearAnimation();
            mHeaderIcon.setRotation(0);
        }
    }

    private void startRotate(View view, float destDegree) {
        float currentRotation = view.getRotation();
        if (currentRotation != destDegree) {
            view.clearAnimation();
            long duration = (long) (400 * Math.abs(destDegree - currentRotation) / 360);
            if (duration < 20) {
                view.setRotation(destDegree);
            } else {
                ObjectAnimator animator = ObjectAnimator.ofFloat(view, "rotation", currentRotation, destDegree);
                animator.setDuration(duration);
                animator.start();
            }
        }
    }
}
