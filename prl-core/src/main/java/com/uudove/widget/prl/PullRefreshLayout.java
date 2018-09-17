package com.uudove.widget.prl;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.NestedScrollingChild;
import android.support.v4.view.NestedScrollingChildHelper;
import android.support.v4.view.NestedScrollingParent;
import android.support.v4.view.NestedScrollingParentHelper;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ListViewCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AbsListView;
import android.widget.ListView;

import static android.support.v4.widget.ViewDragHelper.INVALID_POINTER;

public class PullRefreshLayout extends ViewGroup implements NestedScrollingChild, NestedScrollingParent {
    private static final String LOG_TAG = PullRefreshLayout.class.getSimpleName();

    private static final boolean HEADER_DEFAULT_ENABLED = true;

    private NestedScrollingParentHelper mNestedScrollingParentHelper;
    private NestedScrollingChildHelper mNestedScrollingChildHelper;
    private final int[] mParentScrollConsumed = new int[2];
    private final int[] mParentOffsetInWindow = new int[2];

    // header section
    private final IHeader mDefaultHeaderInterface = new IHeaderDefaultImpl();
    private HeaderStateNotifier mHeaderStateNotifier;
    private IHeader mHeaderInterface;
    private View mHeaderView;
    private boolean isHeaderEnabled = HEADER_DEFAULT_ENABLED;
    private boolean isHeaderRefreshing;
    private int mHeaderOriginalOffsetTop;
    private int mHeaderCurrentOffsetTop;

    // target section
    private View mTarget;
    private OnChildScrollUpCallback mChildScrollUpCallback;
    private int mCurrentTargetOffsetTop;
    private boolean mReturningToStart;

    // for touch event
    private static final float DRAG_RATE = .5f;
    private int mActivePointerId = INVALID_POINTER;
    private float mInitialDownY;
    private float mInitialMotionY;
    private boolean mIsBeingDragged;
    private int mTouchSlop;
    private int mTotalDragDistance = -1;
    private float mTotalUnconsumed;
    private boolean mNestedScrollInProgress;

    public PullRefreshLayout(Context context) {
        super(context);
        initLayout(context, null);
    }

    public PullRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        initLayout(context, attrs);
    }

    public PullRefreshLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initLayout(context, attrs);
    }

    private void initLayout(Context context, AttributeSet attrs) {
        if (attrs != null) {
            final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PullRefreshLayout);
            isHeaderEnabled = a.getBoolean(R.styleable.PullRefreshLayout_prl_headerEnabled, HEADER_DEFAULT_ENABLED);
            a.recycle();
        }

        setWillNotDraw(false);
        setChildrenDrawingOrderEnabled(true);

        mHeaderStateNotifier = new HeaderStateNotifier(this);

        mNestedScrollingParentHelper = new NestedScrollingParentHelper(this);
        mNestedScrollingChildHelper = new NestedScrollingChildHelper(this);
        setNestedScrollingEnabled(true);

        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int width = getMeasuredWidth();
        final int height = getMeasuredHeight();
        if (getChildCount() == 0) {
            return;
        }
        if (mTarget == null) {
            ensureTarget();
        }
        if (mTarget == null) {
            return;
        }
        final View child = mTarget;
        final int childLeft = getPaddingLeft();
        final int childTop = getPaddingTop();
        final int childWidth = width - getPaddingLeft() - getPaddingRight();
        final int childHeight = height - getPaddingTop() - getPaddingBottom();
        child.layout(childLeft, childTop, childWidth - getPaddingRight(), childHeight - getPaddingBottom());

        if (mHeaderView == null) {
            ensureHeaderView();
        }
        if (mHeaderView != null) {
            int headerHeight = mHeaderView.getMeasuredHeight();
            int headerWidth = mHeaderView.getMeasuredWidth();
            mHeaderView.layout(childLeft + (childWidth - headerWidth) / 2, mHeaderCurrentOffsetTop,
                    childLeft + (childWidth + headerWidth) / 2, mHeaderCurrentOffsetTop + headerHeight);
        }

    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mTarget == null) {
            ensureTarget();
        }
        if (mTarget == null) {
            return;
        }
        mTarget.measure(MeasureSpec.makeMeasureSpec(
                getMeasuredWidth() - getPaddingLeft() - getPaddingRight(),
                MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(
                getMeasuredHeight() - getPaddingTop() - getPaddingBottom(), MeasureSpec.EXACTLY));
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        ensureTarget();
        ensureHeaderView();
        final int action = ev.getActionMasked();
        int pointerIndex;

        if (mReturningToStart && action == MotionEvent.ACTION_DOWN) {
            mReturningToStart = false;
        }

        if (!isHeaderEnabled || mReturningToStart || canChildScrollUp() || isHeaderRefreshing
                || mNestedScrollInProgress) {
            // Fail fast if we're not in a state where a swipe is possible
            return false;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                setTargetOffsetTopAndBottom(mHeaderOriginalOffsetTop - mHeaderView.getTop());
                mActivePointerId = ev.getPointerId(0);
                mIsBeingDragged = false;

                pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    return false;
                }
                mInitialDownY = ev.getY(pointerIndex);
                break;

            case MotionEvent.ACTION_MOVE:
                if (mActivePointerId == INVALID_POINTER) {
                    Log.e(LOG_TAG, "Got ACTION_MOVE event but don't have an active pointer id.");
                    return false;
                }

                pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    return false;
                }
                final float y = ev.getY(pointerIndex);
                startDragging(y);
                break;

            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsBeingDragged = false;
                mActivePointerId = INVALID_POINTER;
                break;
        }

        return mIsBeingDragged;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        final int action = ev.getActionMasked();
        int pointerIndex;

        if (mReturningToStart && action == MotionEvent.ACTION_DOWN) {
            mReturningToStart = false;
        }

        if (!isEnabled() || mReturningToStart || canChildScrollUp() || isHeaderRefreshing
                || mNestedScrollInProgress) {
            // Fail fast if we're not in a state where a swipe is possible
            return false;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mActivePointerId = ev.getPointerId(0);
                mIsBeingDragged = false;
                break;

            case MotionEvent.ACTION_MOVE: {
                pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    Log.e(LOG_TAG, "Got ACTION_MOVE event but have an invalid active pointer id.");
                    return false;
                }

                final float y = ev.getY(pointerIndex);
                startDragging(y);

                if (mIsBeingDragged) {
                    final float overScrollTop = (y - mInitialMotionY) * DRAG_RATE;
                    if (overScrollTop > 0) {
                        moveSpinner(overScrollTop);
                    } else {
                        return false;
                    }
                }
                break;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                pointerIndex = ev.getActionIndex();
                if (pointerIndex < 0) {
                    Log.e(LOG_TAG,
                            "Got ACTION_POINTER_DOWN event but have an invalid action index.");
                    return false;
                }
                mActivePointerId = ev.getPointerId(pointerIndex);
                break;
            }

            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;

            case MotionEvent.ACTION_UP: {
                pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    Log.e(LOG_TAG, "Got ACTION_UP event but don't have an active pointer id.");
                    return false;
                }

                if (mIsBeingDragged) {
                    final float y = ev.getY(pointerIndex);
                    final float overScrollTop = (y - mInitialMotionY) * DRAG_RATE;
                    mIsBeingDragged = false;
                    finishSpinner(overScrollTop);
                }
                mActivePointerId = INVALID_POINTER;
                return false;
            }
            case MotionEvent.ACTION_CANCEL:
                return false;
        }

        return true;
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean b) {
        // if this is a List < L or another view that doesn't support nested
        // scrolling, ignore this request so that the vertical scroll event
        // isn't stolen
        if ((android.os.Build.VERSION.SDK_INT >= 21 || mTarget instanceof AbsListView)
                && (mTarget == null || ViewCompat.isNestedScrollingEnabled(mTarget))) {
            super.requestDisallowInterceptTouchEvent(b);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        reset();
    }

    private void reset() {
        mHeaderInterface = null;
    }

    private void startDragging(float y) {
        final float yDiff = y - mInitialDownY;
        if (yDiff > mTouchSlop && !mIsBeingDragged) {
            mInitialMotionY = mInitialDownY + mTouchSlop;
            mIsBeingDragged = true;
        }
    }

    private void setTargetOffsetTopAndBottom(int offset) {
        if (offset == 0) {
            return;
        }
        if (mHeaderView != null) {
            mHeaderView.bringToFront();
            ViewCompat.offsetTopAndBottom(mHeaderView, offset);
            mHeaderCurrentOffsetTop = mHeaderView.getTop();
        }
        if (mTarget != null) {
            ViewCompat.offsetTopAndBottom(mTarget, offset);
        }
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = ev.getActionIndex();
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mActivePointerId = ev.getPointerId(newPointerIndex);
        }
    }

    // ============ header section ============== //

    /**
     * Set the listener to be notified when a header refresh is triggered via the swipe
     * gesture.
     */
    public void setOnHeaderRefreshListener(@Nullable OnHeaderRefreshListener listener) {
        this.mHeaderStateNotifier.setOnHeaderRefreshListener(listener);
    }

    /**
     * @return Whether the SwipeRefreshWidget is actively showing header refresh progress.
     */
    public boolean isHeaderRefreshing() {
        return isHeaderRefreshing;
    }

    /**
     * Notify the widget that refresh state has changed. Do not call this when
     * refresh is triggered by a swipe gesture.
     *
     * @param refreshing Whether or not the view should show header refresh progress.
     */
    public void setHeaderRefreshing(boolean refreshing) {
        isHeaderRefreshing = refreshing;
    }

    /**
     * Set whether to enable pull down gesture. If not set, default value is {@value #HEADER_DEFAULT_ENABLED}
     *
     * @param headerEnabled true to enable header
     */
    public void setHeaderEnabled(boolean headerEnabled) {
        isHeaderEnabled = headerEnabled;
    }

    /**
     * The header is enabled or not
     *
     * @return true - header is enabled
     */
    public boolean isHeaderEnabled() {
        return isHeaderEnabled;
    }

    /**
     * set the header to be shown
     *
     * @param header header interface
     */
    public void setHeader(@Nullable IHeader header) {
        if (mHeaderView != null) {
            ViewParent parent = mHeaderView.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(mHeaderView);
            }
            mHeaderView = null;
        }
        mHeaderInterface = header;
    }

    private void ensureHeaderView() {
        if (mHeaderView == null) {
            IHeader headerInterface = mHeaderInterface == null ? mDefaultHeaderInterface : mHeaderInterface;
            mHeaderStateNotifier.setHeaderInterface(headerInterface);

            mHeaderView = headerInterface.onCreateHeaderView(this);
            ViewGroup.LayoutParams layoutParams = mHeaderView.getLayoutParams();
            if (layoutParams == null) {
                layoutParams = new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            }
            this.addView(mHeaderView, layoutParams);
            mTotalDragDistance = mHeaderView.getMeasuredHeight();
            if (mTotalDragDistance <= 0) {
                mHeaderView.measure(0, 0);
                mTotalDragDistance = mHeaderView.getMeasuredHeight();
            }
            mHeaderOriginalOffsetTop = -mTotalDragDistance;
            mHeaderCurrentOffsetTop = mHeaderOriginalOffsetTop;
            mCurrentTargetOffsetTop = 0;
            mHeaderView.setVisibility(GONE);
        }
    }

    // ============ target section ============== //
    private void ensureTarget() {
        // Don't bother getting the parent height if the parent hasn't been laid out yet.
        if (mTarget == null) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (!child.equals(mHeaderView)) {
                    mTarget = child;
                    break;
                }
            }
        }
    }

    /**
     * Set a callback to override {@link PullRefreshLayout#canChildScrollUp()} method. Non-null
     * callback will return the value provided by the callback and ignore all internal logic.
     *
     * @param callback Callback that should be called when canChildScrollUp() is called.
     */
    public void setOnChildScrollUpCallback(@Nullable OnChildScrollUpCallback callback) {
        mChildScrollUpCallback = callback;
    }

    /**
     * @return Whether it is possible for the child view of this layout to
     * scroll up. Override this if the child view is a custom view.
     */
    private boolean canChildScrollUp() {
        if (mChildScrollUpCallback != null) {
            return mChildScrollUpCallback.canChildScrollUp(this, mTarget);
        }
        if (mTarget instanceof ListView) {
            return ListViewCompat.canScrollList((ListView) mTarget, -1);
        }
        return mTarget.canScrollVertically(-1);
    }

    private void moveSpinner(float overScrollTop) {
        if (mHeaderView.getVisibility() != View.VISIBLE) {
            mHeaderView.setVisibility(View.VISIBLE);
        }
        if (overScrollTop > mTotalDragDistance) {
            overScrollTop = mTotalDragDistance;
            mHeaderStateNotifier.onHeaderStateChanged(IHeader.STATE_DRAGGING_MAX_RANGE);
        } else {
            mHeaderStateNotifier.onHeaderStateChanged(IHeader.STATE_DRAGGING);
        }

        int targetY = mHeaderOriginalOffsetTop + (int) overScrollTop;
        setTargetOffsetTopAndBottom(targetY - mHeaderCurrentOffsetTop);
    }

    private void finishSpinner(float overScrollTop) {
        if (overScrollTop > mTotalDragDistance) {
            // setRefreshing(true, true /* notify */);
            mHeaderStateNotifier.onHeaderStateChanged(IHeader.STATE_REFRESHING);
        } else {
            // cancel refresh
            isHeaderRefreshing = false;
        }
    }

    // implements of NestedScrollingParent start
    @Override
    public boolean onStartNestedScroll(@NonNull View child, @NonNull View target, int nestedScrollAxes) {
        return isHeaderEnabled() && !mReturningToStart && !isHeaderRefreshing
                && (nestedScrollAxes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
    }

    @Override
    public void onNestedScrollAccepted(@NonNull View child, @NonNull View target, int axes) {
        // Reset the counter of how much leftover scroll needs to be consumed.
        mNestedScrollingParentHelper.onNestedScrollAccepted(child, target, axes);
        // Dispatch up to the nested parent
        startNestedScroll(axes & ViewCompat.SCROLL_AXIS_VERTICAL);
        mTotalUnconsumed = 0;
        mNestedScrollInProgress = true;
    }

    @Override
    public void onStopNestedScroll(@NonNull View target) {
        mNestedScrollingParentHelper.onStopNestedScroll(target);
        mNestedScrollInProgress = false;
        // Finish the spinner for nested scrolling if we ever consumed any
        // unconsumed nested scroll
        if (mTotalUnconsumed > 0) {
            finishSpinner(mTotalUnconsumed);
            mTotalUnconsumed = 0;
        }
        // Dispatch up our nested parent
        stopNestedScroll();

    }

    @Override
    public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {

        // Dispatch up to the nested parent first
        dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, mParentOffsetInWindow);

        // This is a bit of a hack. Nested scrolling works from the bottom up, and as we are
        // sometimes between two nested scrolling views, we need a way to be able to know when any
        // nested scrolling parent has stopped handling events. We do that by using the
        // 'offset in window 'functionality to see if we have been moved from the event.
        // This is a decent indication of whether we should take over the event stream or not.
        final int dy = dyUnconsumed + mParentOffsetInWindow[1];
        if (dy < 0 && !canChildScrollUp()) {
            mTotalUnconsumed += Math.abs(dy);
            moveSpinner(mTotalUnconsumed);
        }
    }

    @Override
    public void onNestedPreScroll(@NonNull View target, int dx, int dy, @NonNull int[] consumed) {
        // If we are in the middle of consuming, a scroll, then we want to move the spinner back up
        // before allowing the list to scroll
        if (dy > 0 && mTotalUnconsumed > 0) {
            if (dy > mTotalUnconsumed) {
                consumed[1] = dy - (int) mTotalUnconsumed;
                mTotalUnconsumed = 0;
            } else {
                mTotalUnconsumed -= dy;
                consumed[1] = dy;
            }
            moveSpinner(mTotalUnconsumed);
        }

        // Now let our nested parent consume the leftovers
        final int[] parentConsumed = mParentScrollConsumed;
        if (dispatchNestedPreScroll(dx - consumed[0], dy - consumed[1], parentConsumed, null)) {
            consumed[0] += parentConsumed[0];
            consumed[1] += parentConsumed[1];
        }
    }

    @Override
    public boolean onNestedFling(@NonNull View target, float velocityX, float velocityY, boolean consumed) {
        return dispatchNestedFling(velocityX, velocityY, consumed);
    }

    @Override
    public boolean onNestedPreFling(@NonNull View target, float velocityX, float velocityY) {
        return dispatchNestedPreFling(velocityX, velocityY);
    }

    @Override
    public int getNestedScrollAxes() {
        return mNestedScrollingParentHelper.getNestedScrollAxes();
    }
    // implements of NestedScrollingParent end

    // implements of NestedScrollingChild start
    @Override
    public void setNestedScrollingEnabled(boolean enabled) {
        mNestedScrollingChildHelper.setNestedScrollingEnabled(enabled);
    }

    @Override
    public boolean isNestedScrollingEnabled() {
        return mNestedScrollingChildHelper.isNestedScrollingEnabled();
    }

    @Override
    public boolean startNestedScroll(int axes) {
        return mNestedScrollingChildHelper.startNestedScroll(axes);
    }

    @Override
    public void stopNestedScroll() {
        mNestedScrollingChildHelper.stopNestedScroll();
    }

    @Override
    public boolean hasNestedScrollingParent() {
        return mNestedScrollingChildHelper.hasNestedScrollingParent();
    }

    @Override
    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, @Nullable int[] offsetInWindow) {
        return mNestedScrollingChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed,
                dxUnconsumed, dyUnconsumed, offsetInWindow);
    }

    @Override
    public boolean dispatchNestedPreScroll(int dx, int dy, @Nullable int[] consumed, @Nullable int[] offsetInWindow) {
        return mNestedScrollingChildHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow);
    }

    @Override
    public boolean dispatchNestedFling(float velocityX, float velocityY, boolean consumed) {
        return mNestedScrollingChildHelper.dispatchNestedFling(velocityX, velocityY, consumed);
    }

    @Override
    public boolean dispatchNestedPreFling(float velocityX, float velocityY) {
        return mNestedScrollingChildHelper.dispatchNestedPreFling(velocityX, velocityY);
    }
    // implements of NestedScrollingChild end

    public interface OnHeaderRefreshListener {

        /**
         * Called when a swipe gesture triggers a header refresh.
         */
        void onHeaderRefresh(@NonNull PullRefreshLayout layout);
    }

    /**
     * Classes that wish to override {@link PullRefreshLayout#canChildScrollUp()} method
     * behavior should implement this interface.
     */
    public interface OnChildScrollUpCallback {
        /**
         * Callback that will be called when {@link PullRefreshLayout#canChildScrollUp()} method
         * is called to allow the implementer to override its behavior.
         *
         * @param parent SwipeRefreshLayout that this callback is overriding.
         * @param child  The child view of PullRefreshLayout.
         * @return Whether it is possible for the child view of parent layout to scroll up.
         */
        boolean canChildScrollUp(@NonNull PullRefreshLayout parent, @Nullable View child);
    }
}
