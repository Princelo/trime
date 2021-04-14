/*
 * Copyright (C) 2015-present, osfans
 * waxaca@163.com https://github.com/osfans
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.osfans.trime;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.Paint.Align;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;
import com.osfans.trime.enums.KeyEventType;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 顯示{@link Keyboard 鍵盤}及{@link Key 按鍵} */
public class KeyboardView extends View implements View.OnClickListener {

  /** 處理按鍵、觸摸等輸入事件 */
  public interface OnKeyboardActionListener {

    /**
     * Called when the user presses a key. This is sent before the {@link #onKey} is called. For
     * keys that repeat, this is only called once.
     *
     * @param primaryCode the unicode of the key being pressed. If the touch is not on a valid key,
     *     the value will be zero.
     */
    void onPress(int primaryCode);

    /**
     * Called when the user releases a key. This is sent after the {@link #onKey} is called. For
     * keys that repeat, this is only called once.
     *
     * @param primaryCode the code of the key that was released
     */
    void onRelease(int primaryCode);

    void onEvent(Event event);

    /**
     * Send a key press to the listener.
     *
     * @param primaryCode this is the key that was pressed
     * @param mask the codes for all the possible alternative keys with the primary code being the
     *     first. If the primary key code is a single character such as an alphabet or number or
     *     symbol, the alternatives will include other characters that may be on the same key or
     *     adjacent keys. These codes are useful to correct for accidental presses of a key adjacent
     *     to the intended key.
     */
    void onKey(int primaryCode, int mask);

    /**
     * Sends a sequence of characters to the listener.
     *
     * @param text the sequence of characters to be displayed.
     */
    void onText(CharSequence text);

    /** Called when the user quickly moves the finger from right to left. */
    void swipeLeft();

    /** Called when the user quickly moves the finger from left to right. */
    void swipeRight();

    /** Called when the user quickly moves the finger from up to down. */
    void swipeDown();

    /** Called when the user quickly moves the finger from down to up. */
    void swipeUp();
  }

  private static final boolean DEBUG = false;
  private static final int NOT_A_KEY = -1;
  private static final int[] LONG_PRESSABLE_STATE_SET = {android.R.attr.state_long_pressable};
  private static String TAG = KeyboardView.class.getSimpleName();

  private Keyboard mKeyboard;
  private int mCurrentKeyIndex = NOT_A_KEY;
  private int mLabelTextSize;
  private int mKeyTextSize;
  private ColorStateList mKeyTextColor;
  private StateListDrawable mKeyBackColor;
  private int key_symbol_color, hilited_key_symbol_color;
  private int mSymbolSize;
  private Paint mPaintSymbol;
  private float mShadowRadius;
  private int mShadowColor;
  private float mBackgroundDimAmount;
  private Drawable mBackground;

  private TextView mPreviewText;
  private PopupWindow mPreviewPopup;
  private int mPreviewOffset;
  private int mPreviewHeight;
  // Working variable
  private final int[] mCoordinates = new int[2];

  private PopupWindow mPopupKeyboard;
  private boolean mMiniKeyboardOnScreen;
  private View mPopupParent;
  private int mMiniKeyboardOffsetX;
  private int mMiniKeyboardOffsetY;
  private Map<Key, View> mMiniKeyboardCache;
  private Key[] mKeys;

  /** Listener for {@link OnKeyboardActionListener}. */
  private OnKeyboardActionListener mKeyboardActionListener;

  private static final int MSG_SHOW_PREVIEW = 1;
  private static final int MSG_REMOVE_PREVIEW = 2;
  private static final int MSG_REPEAT = 3;
  private static final int MSG_LONGPRESS = 4;

  private static final int DELAY_BEFORE_PREVIEW = 0;
  private static final int DELAY_AFTER_PREVIEW = 70;
  private static final int DEBOUNCE_TIME = 70;

  private int mVerticalCorrection;
  private int mProximityThreshold;

  private boolean mShowPreview = true;

  private int mLastX;
  private int mLastY;
  private int mStartX;
  private int mStartY;

  private boolean mProximityCorrectOn;

  private Paint mPaint;
  private Rect mPadding;

  private long mDownTime;
  private long mLastMoveTime;
  private int mLastKey;
  private int mLastCodeX;
  private int mLastCodeY;
  private int mCurrentKey = NOT_A_KEY;
  private int mDownKey = NOT_A_KEY;
  private long mLastKeyTime;
  private long mCurrentKeyTime;
  private int[] mKeyIndices = new int[12];
  private GestureDetector mGestureDetector;
  private int mRepeatKeyIndex = NOT_A_KEY;
  private int mPopupLayout;
  private boolean mAbortKey;
  private Key mInvalidatedKey;
  private Rect mClipRegion = new Rect(0, 0, 0, 0);
  private boolean mPossiblePoly;
  private SwipeTracker mSwipeTracker = new SwipeTracker();
  private int mSwipeThreshold;
  private boolean mDisambiguateSwipe;

  // Variables for dealing with multiple pointers
  private int mOldPointerCount = 1;
  private int[] mComboCodes = new int[10];
  private int mComboCount = 0;
  private boolean mComboMode = false;

  private static int REPEAT_INTERVAL = 50; // ~20 keys per second
  private static int REPEAT_START_DELAY = 400;
  private static int LONGPRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();

  private static int MAX_NEARBY_KEYS = 12;
  private int[] mDistances = new int[MAX_NEARBY_KEYS];

  // For multi-tap
  private int mLastSentIndex;
  private long mLastTapTime;
  private static int MULTITAP_INTERVAL = 800; // milliseconds
  private StringBuilder mPreviewLabel = new StringBuilder(1);

  /** Whether the keyboard bitmap needs to be redrawn before it's blitted. * */
  private boolean mDrawPending;
  /** The dirty region in the keyboard bitmap */
  private Rect mDirtyRect = new Rect();
  /** The keyboard bitmap for faster updates */
  private Bitmap mBuffer;
  /** Notes if the keyboard just changed, so that we could possibly reallocate the mBuffer. */
  private boolean mKeyboardChanged;
  /** The canvas for the above mutable keyboard bitmap */
  private Canvas mCanvas;
  /** The accessibility manager for accessibility support */
  //private AccessibilityManager mAccessibilityManager;
  /** The audio manager for accessibility support */
  //private AudioManager mAudioManager;
  /**
   * Whether the requirement of a headset to hear passwords if accessibility is enabled is
   * announced.
   */
  private boolean mHeadsetRequiredToHearPasswordsAnnounced;

  private boolean mShowHint = true;

  private Method findStateDrawableIndex;
  private Method getStateDrawable;

  private static class MyHandler extends Handler {
    private final WeakReference<KeyboardView> mKeyboardView;

    public MyHandler(KeyboardView view) {
      mKeyboardView = new WeakReference<KeyboardView>(view);
    }

    @Override
    public void handleMessage(Message msg) {
      KeyboardView view = mKeyboardView.get();
      switch (msg.what) {
        case MSG_SHOW_PREVIEW:
          view.showKey(msg.arg1, msg.arg2);
          break;
        case MSG_REMOVE_PREVIEW:
          view.mPreviewText.setVisibility(INVISIBLE);
          break;
        case MSG_REPEAT:
          if (view.repeatKey()) {
            Message repeat = Message.obtain(this, MSG_REPEAT);
            sendMessageDelayed(repeat, REPEAT_INTERVAL);
          }
          break;
        case MSG_LONGPRESS:
          view.openPopupIfRequired((MotionEvent) msg.obj);
          break;
      }
    }
  }

  private final MyHandler mHandler = new MyHandler(this);

  public void setShowHint(boolean value) {
    mShowHint = value;
  }

  public void reset(Context context) {
    Config config = Config.get(context);
    key_symbol_color = config.getColor("key_symbol_color");
    hilited_key_symbol_color = config.getColor("hilited_key_symbol_color");
    mShadowColor = config.getColor("shadow_color");

    mSymbolSize = config.getPixel("symbol_text_size");
    mKeyTextSize = config.getPixel("key_text_size");
    mVerticalCorrection = config.getPixel("vertical_correction");
    setProximityCorrectionEnabled(config.getBoolean("proximity_correction"));
    mPreviewOffset = config.getPixel("preview_offset");
    mPreviewHeight = config.getPixel("preview_height");
    mLabelTextSize = config.getPixel("key_long_text_size");
    if (mLabelTextSize == 0) mLabelTextSize = mKeyTextSize;

    mBackgroundDimAmount = config.getFloat("background_dim_amount");
    mShadowRadius = config.getFloat("shadow_radius");
    float mRoundCorner = config.getFloat("round_corner");

    mKeyBackColor = new StateListDrawable();
    mKeyBackColor.addState(
        Key.KEY_STATE_PRESSED_ON, config.getColorDrawable("hilited_on_key_back_color"));
    mKeyBackColor.addState(
        Key.KEY_STATE_PRESSED_OFF, config.getColorDrawable("hilited_off_key_back_color"));
    mKeyBackColor.addState(Key.KEY_STATE_NORMAL_ON, config.getColorDrawable("on_key_back_color"));
    mKeyBackColor.addState(Key.KEY_STATE_NORMAL_OFF, config.getColorDrawable("off_key_back_color"));
    mKeyBackColor.addState(
        Key.KEY_STATE_PRESSED, config.getColorDrawable("hilited_key_back_color"));
    mKeyBackColor.addState(Key.KEY_STATE_NORMAL, config.getColorDrawable("key_back_color"));

    mKeyTextColor =
        new ColorStateList(
            Key.KEY_STATES,
            new int[] {
              config.getColor("hilited_on_key_text_color"),
              config.getColor("hilited_off_key_text_color"),
              config.getColor("on_key_text_color"),
              config.getColor("off_key_text_color"),
              config.getColor("hilited_key_text_color"),
              config.getColor("key_text_color")
            });

    Integer color = config.getColor("preview_text_color");
    if (color != null) mPreviewText.setTextColor(color);
    Integer previewBackColor = config.getColor("preview_back_color");
    if (previewBackColor != null) {
      GradientDrawable background = new GradientDrawable();
      background.setColor(previewBackColor);
      background.setCornerRadius(mRoundCorner);
      mPreviewText.setBackgroundDrawable(background);
    }
    int mPreviewTextSizeLarge = config.getInt("preview_text_size");
    mPreviewText.setTextSize(mPreviewTextSizeLarge);
    mShowPreview = config.getShowPreview();

    mPaint.setTypeface(config.getFont("key_font"));
    mPaintSymbol.setTypeface(config.getFont("symbol_font"));
    mPaintSymbol.setColor(key_symbol_color);
    mPaintSymbol.setTextSize(mSymbolSize);
    mPreviewText.setTypeface(config.getFont("preview_font"));

    REPEAT_INTERVAL = config.getRepeatInterval();
    REPEAT_START_DELAY = config.getLongTimeout() + 1;
    LONGPRESS_TIMEOUT = config.getLongTimeout();
    MULTITAP_INTERVAL = config.getLongTimeout();
    invalidateAllKeys();
  }

  public KeyboardView(Context context, AttributeSet attrs) {
    super(context, attrs);

    try {
      findStateDrawableIndex =
          StateListDrawable.class.getMethod(
              Build.VERSION.SDK_INT + Build.VERSION.PREVIEW_SDK_INT >= Build.VERSION_CODES.Q ?  "findStateDrawableIndex" : "getStateDrawableIndex",
              int[].class);
      getStateDrawable = StateListDrawable.class.getMethod("getStateDrawable", int.class);
    } catch (Exception ex) {
      Log.e(TAG, "Get Drawable Exception: " + ex);
    }

    LayoutInflater inflate =
        (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    mPreviewText = (TextView) inflate.inflate(R.layout.keyboard_key_preview, (ViewGroup) null);
    mPaint = new Paint();
    mPaint.setAntiAlias(true);
    mPaint.setTextAlign(Align.CENTER);
    mPaintSymbol = new Paint();
    mPaintSymbol.setAntiAlias(true);
    mPaintSymbol.setTextAlign(Align.CENTER);
    reset(context);

    mPreviewPopup = new PopupWindow(context);
    mPreviewPopup.setContentView(mPreviewText);
    mPreviewPopup.setBackgroundDrawable(null);
    mPreviewPopup.setTouchable(false);

    mPopupLayout = R.layout.keyboard_popup_keyboard;
    mPopupKeyboard = new PopupWindow(context);
    mPopupKeyboard.setBackgroundDrawable(null);
    //mPopupKeyboard.setClippingEnabled(false);

    mPopupParent = this;
    //mPredicting = true;

    mPadding = new Rect(0, 0, 0, 0);
    mMiniKeyboardCache = new HashMap<Key, View>();

    mSwipeThreshold = (int) (500 * getResources().getDisplayMetrics().density);
    mDisambiguateSwipe = true;

    resetMultiTap();
    initGestureDetector();
  }

  private void initGestureDetector() {
    mGestureDetector =
        new GestureDetector(
            getContext(),
            new GestureDetector.SimpleOnGestureListener() {
              @Override
              public boolean onFling(
                  MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) {
                if (mPossiblePoly) return false;
                final float absX = Math.abs(velocityX);
                final float absY = Math.abs(velocityY);
                float deltaX = me2.getX() - me1.getX();
                float deltaY = me2.getY() - me1.getY();
                int travelX = 0; //getWidth() / 2; // Half the keyboard width
                int travelY = 0; //getHeight() / 2; // Half the keyboard height
                mSwipeTracker.computeCurrentVelocity(10);
                final float endingVelocityX = mSwipeTracker.getXVelocity();
                final float endingVelocityY = mSwipeTracker.getYVelocity();
                boolean sendDownKey = false;
                int type = 0;
                if (velocityX > mSwipeThreshold && absY < absX && deltaX > travelX) {
                  if (mDisambiguateSwipe && endingVelocityX < velocityX / 4) {
                    sendDownKey = true;
                    type = KeyEventType.SWIPE_RIGHT.ordinal();
                  } else {
                    swipeRight();
                    return true;
                  }
                } else if (velocityX < -mSwipeThreshold && absY < absX && deltaX < -travelX) {
                  if (mDisambiguateSwipe && endingVelocityX > velocityX / 4) {
                    sendDownKey = true;
                    type = KeyEventType.SWIPE_LEFT.ordinal();
                  } else {
                    swipeLeft();
                    return true;
                  }
                } else if (velocityY < -mSwipeThreshold && absX < absY && deltaY < -travelY) {
                  if (mDisambiguateSwipe && endingVelocityY > velocityY / 4) {
                    sendDownKey = true;
                    type = KeyEventType.SWIPE_UP.ordinal();
                  } else {
                    swipeUp();
                    return true;
                  }
                } else if (velocityY > mSwipeThreshold && absX < absY / 2 && deltaY > travelY) {
                  if (mDisambiguateSwipe && endingVelocityY < velocityY / 4) {
                    sendDownKey = true;
                    type = KeyEventType.SWIPE_DOWN.ordinal();
                  } else {
                    swipeDown();
                    return true;
                  }
                }

                if (sendDownKey) {
                  showPreview(NOT_A_KEY);
                  showPreview(mDownKey, type);
                  detectAndSendKey(mDownKey, mStartX, mStartY, me1.getEventTime(), type);
                  return true;
                }
                return false;
              }
            });

    mGestureDetector.setIsLongpressEnabled(false);
  }

  public void setOnKeyboardActionListener(OnKeyboardActionListener listener) {
    mKeyboardActionListener = listener;
  }

  /**
   * Returns the {@link OnKeyboardActionListener} object.
   *
   * @return the listener attached to this keyboard
   */
  protected OnKeyboardActionListener getOnKeyboardActionListener() {
    return mKeyboardActionListener;
  }

  private void setKeyboardBackground() {
    if (mKeyboard == null) return;
    Drawable d = mPreviewText.getBackground();
    if (d instanceof GradientDrawable) {
      ((GradientDrawable) d).setCornerRadius(mKeyboard.getRoundCorner());
      mPreviewText.setBackgroundDrawable(d);
    }
    d = mKeyboard.getBackground();
    setBackgroundDrawable(d);
  }

  /**
   * Attaches a keyboard to this view. The keyboard can be switched at any time and the view will
   * re-layout itself to accommodate the keyboard.
   *
   * @see Keyboard
   * @see #getKeyboard()
   * @param keyboard the keyboard to display in this view
   */
  public void setKeyboard(Keyboard keyboard) {
    if (mKeyboard != null) {
      showPreview(NOT_A_KEY);
    }
    // Remove any pending messages
    removeMessages();
    mRepeatKeyIndex = NOT_A_KEY;
    mKeyboard = keyboard;
    List<Key> keys = mKeyboard.getKeys();
    mKeys = keys.toArray(new Key[keys.size()]);
    setKeyboardBackground();
    requestLayout();
    // Hint to reallocate the buffer if the size changed
    mKeyboardChanged = true;
    invalidateAllKeys();
    computeProximityThreshold(keyboard);
    mMiniKeyboardCache.clear(); // Not really necessary to do every time, but will free up views
    // Switching to a different keyboard should abort any pending keys so that the key up
    // doesn't get delivered to the old or new keyboard
    mAbortKey = true; // Until the next ACTION_DOWN
  }

  /**
   * Returns the current keyboard being displayed by this view.
   *
   * @return the currently attached keyboard
   * @see #setKeyboard(Keyboard)
   */
  public Keyboard getKeyboard() {
    return mKeyboard;
  }

  /**
   * 設定鍵盤的Shift鍵狀態
   *
   * @param on 是否保持Shift按下狀態
   * @param shifted 是否按下Shift
   * @return Shift鍵狀態是否改變
   * @see Keyboard#setShifted(boolean, boolean) KeyboardView#isShifted()
   */
  public boolean setShifted(boolean on, boolean shifted) {
    if (mKeyboard != null) {
      if (mKeyboard.setShifted(on, shifted)) {
        // The whole keyboard probably needs to be redrawn
        invalidateAllKeys();
        return true;
      }
    }
    return false;
  }

  private boolean resetShifted() {
    if (mKeyboard != null) {
      if (mKeyboard.resetShifted()) {
        // The whole keyboard probably needs to be redrawn
        invalidateAllKeys();
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the state of the shift key of the keyboard, if any.
   *
   * @return true if the shift is in a pressed state, false otherwise. If there is no shift key on
   *     the keyboard or there is no keyboard attached, it returns false.
   * @see KeyboardView#setShifted(boolean, boolean)
   */
  public boolean isShifted() {
    if (mKeyboard != null) {
      return mKeyboard.isShifted();
    }
    return false;
  }

  /**
   * 返回鍵盤是否爲大寫狀態
   *
   * @return true 如果大寫
   */
  public boolean isCapsOn() {
    if (mKeyboard != null && mKeyboard.getmShiftKey() != null)
      return mKeyboard.getmShiftKey().isOn();
    return false;
  }

  /**
   * Enables or disables the key feedback popup. This is a popup that shows a magnified version of
   * the depressed key. By default the preview is enabled.
   *
   * @param previewEnabled whether or not to enable the key feedback popup
   * @see #isPreviewEnabled()
   */
  public void setPreviewEnabled(boolean previewEnabled) {
    mShowPreview = previewEnabled;
  }

  /**
   * Returns the enabled state of the key feedback popup.
   *
   * @return whether or not the key feedback popup is enabled
   * @see #setPreviewEnabled(boolean)
   */
  public boolean isPreviewEnabled() {
    return mShowPreview;
  }

  public void setVerticalCorrection(int verticalOffset) {}

  private void setPopupParent(View v) {
    mPopupParent = v;
  }

  private void setPopupOffset(int x, int y) {
    mMiniKeyboardOffsetX = x;
    mMiniKeyboardOffsetY = y;
    if (mPreviewPopup.isShowing()) {
      mPreviewPopup.dismiss();
    }
  }

  /**
   * When enabled, calls to {@link OnKeyboardActionListener#onKey} will include key codes for
   * adjacent keys. When disabled, only the primary key code will be reported.
   *
   * @param enabled whether or not the proximity correction is enabled
   */
  private void setProximityCorrectionEnabled(boolean enabled) {
    mProximityCorrectOn = enabled;
  }

  /**
   * 檢查是否允許距離校正
   *
   * @return 是否允許距離校正
   */
  public boolean isProximityCorrectionEnabled() {
    return mProximityCorrectOn;
  }

  /**
   * 關閉彈出鍵盤
   *
   * @param v 鍵盤視圖
   */
  @Override
  public void onClick(View v) {
    dismissPopupKeyboard();
  }

  @Override
  public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    // Round up a little
    if (mKeyboard == null) {
      setMeasuredDimension(
          getPaddingLeft() + getPaddingRight(), getPaddingTop() + getPaddingBottom());
    } else {
      int width = mKeyboard.getMinWidth() + getPaddingLeft() + getPaddingRight();
      if (MeasureSpec.getSize(widthMeasureSpec) < width + 10) {
        width = MeasureSpec.getSize(widthMeasureSpec);
      }
      setMeasuredDimension(width, mKeyboard.getHeight() + getPaddingTop() + getPaddingBottom());
    }
  }

  /**
   * 計算水平和豎直方向的相鄰按鍵中心的平均距離的平方，這樣不需要做開方運算
   *
   * @param keyboard 鍵盤
   */
  private void computeProximityThreshold(Keyboard keyboard) {
    if (keyboard == null) return;
    final Key[] keys = mKeys;
    if (keys == null) return;
    int length = keys.length;
    int dimensionSum = 0;
    for (int i = 0; i < length; i++) {
      Key key = keys[i];
      dimensionSum += Math.min(key.getWidth(), key.getHeight()) + key.getGap();
    }
    if (dimensionSum < 0 || length == 0) return;
    mProximityThreshold = (int) (dimensionSum * Keyboard.SEARCH_DISTANCE / length);
    mProximityThreshold *= mProximityThreshold; // Square it
  }

  @Override
  public void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    if (mKeyboard != null) {
      //mKeyboard.resize(w, h);
    }
    // Release the buffer, if any and it will be reallocated on the next draw
    mBuffer = null;
  }

  @Override
  public void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (mDrawPending || mBuffer == null || mKeyboardChanged) {
      onBufferDraw();
    }
    canvas.drawBitmap(mBuffer, 0, 0, null);
  }

  private void onBufferDraw() {
    if (mBuffer == null || mKeyboardChanged) {
      if (mBuffer == null
          || mKeyboardChanged
              && (mBuffer.getWidth() != getWidth() || mBuffer.getHeight() != getHeight())) {
        // Make sure our bitmap is at least 1x1
        final int width = Math.max(1, getWidth());
        final int height = Math.max(1, getHeight());
        mBuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        mCanvas = new Canvas(mBuffer);
      }
      invalidateAllKeys();
      mKeyboardChanged = false;
    }

    if (mKeyboard == null) return;

    mCanvas.save();
    final Canvas canvas = mCanvas;
    canvas.clipRect(mDirtyRect);

    final Paint paint = mPaint;
    Drawable keyBackground;
    final Rect clipRegion = mClipRegion;
    final Rect padding = mPadding;
    final int kbdPaddingLeft = getPaddingLeft();
    final int kbdPaddingTop = getPaddingTop();
    final Key[] keys = mKeys;
    final Key invalidKey = mInvalidatedKey;

    boolean drawSingleKey = false;
    if (invalidKey != null && canvas.getClipBounds(clipRegion)) {
      // Is clipRegion completely contained within the invalidated key?
      if (invalidKey.getX() + kbdPaddingLeft - 1 <= clipRegion.left
          && invalidKey.getY() + kbdPaddingTop - 1 <= clipRegion.top
          && invalidKey.getX() + invalidKey.getWidth() + kbdPaddingLeft + 1 >= clipRegion.right
          && invalidKey.getY() + invalidKey.getHeight() + kbdPaddingTop + 1 >= clipRegion.bottom) {
        drawSingleKey = true;
      }
    }
    canvas.drawColor(0x00000000, PorterDuff.Mode.CLEAR);
    final int keyCount = keys.length;
    final float symbolBase = padding.top - mPaintSymbol.getFontMetrics().top;
    final float hintBase = -padding.bottom - mPaintSymbol.getFontMetrics().bottom;
    for (int i = 0; i < keyCount; i++) {
      final Key key = keys[i];
      if (drawSingleKey && invalidKey != key) {
        continue;
      }
      int[] drawableState = key.getCurrentDrawableState();
      keyBackground = key.getBackColorForState(drawableState);
      if (keyBackground == null) {
        try {
          int index = (int) findStateDrawableIndex.invoke(mKeyBackColor, drawableState);
          keyBackground = (Drawable) getStateDrawable.invoke(mKeyBackColor, index);
        } catch (Exception ex) {
          Log.e(TAG, "Get Drawable Exception" + ex);
        }
      }
      if (keyBackground instanceof GradientDrawable) {
        ((GradientDrawable) keyBackground)
            .setCornerRadius(
                key.getRound_corner() != null ? key.getRound_corner() : mKeyboard.getRoundCorner());
      }
      Integer color = key.getTextColorForState(drawableState);
      mPaint.setColor(color != null ? color : mKeyTextColor.getColorForState(drawableState, 0));
      color = key.getSymbolColorForState(drawableState);
      mPaintSymbol.setColor(
          color != null ? color : (key.isPressed() ? hilited_key_symbol_color : key_symbol_color));

      // Switch the character to uppercase if shift is pressed
      String label = key.getLabel();
      String hint = key.getHint();
      int left = (key.getWidth() - padding.left - padding.right) / 2 + padding.left;
      int top = padding.top;

      final Rect bounds = keyBackground.getBounds();
      if (key.getWidth() != bounds.right || key.getHeight() != bounds.bottom) {
        keyBackground.setBounds(0, 0, key.getWidth(), key.getHeight());
      }
      canvas.translate(key.getX() + kbdPaddingLeft, key.getY() + kbdPaddingTop);
      keyBackground.draw(canvas);

      if (!Function.isEmpty(label)) {
        // For characters, use large font. For labels like "Done", use small font.
        if (key.getKey_text_size() != null) {
          paint.setTextSize(key.getKey_text_size());
        } else {
          paint.setTextSize(label.length() > 1 ? mLabelTextSize : mKeyTextSize);
        }
        // Draw a drop shadow for the text
        paint.setShadowLayer(mShadowRadius, 0, 0, mShadowColor);
        if (!"".equals(key.getDrawIcon())) {
          Bitmap ic = null;
          if ("enter".equals(key.getDrawIcon())) {
            ic = BitmapFactory.decodeResource(getResources(), R.drawable.ic_key_enter);
          }
          if ("delete".equals(key.getDrawIcon())) {
            ic = BitmapFactory.decodeResource(getResources(), R.drawable.ic_key_del);
          }
          if ("delete_material_dark".equals(key.getDrawIcon())) {
            ic = BitmapFactory.decodeResource(getResources(), R.drawable.ic_key_del_material_dark);
          }
          if ("delete_material_light".equals(key.getDrawIcon())) {
            ic = BitmapFactory.decodeResource(getResources(), R.drawable.ic_key_del_material_light);
          }
          if ("shift".equals(key.getDrawIcon())) {
            ic = BitmapFactory.decodeResource(getResources(), R.drawable.ic_key_shift_narrow_on);
          }
          if ("shift_off".equals(key.getDrawIcon())) {
            ic = BitmapFactory.decodeResource(getResources(), R.drawable.ic_key_shift_narrow_off);
          }
          if ("shift_dark".equals(key.getDrawIcon())) {
            ic = BitmapFactory.decodeResource(getResources(), R.drawable.ic_key_shift_narrow_on_material_dark);
          }
          if ("shift_dark_off".equals(key.getDrawIcon())) {
            ic = BitmapFactory.decodeResource(getResources(), R.drawable.ic_key_shift_narrow_off_material_dark);
          }
          if (null != ic) {
            int drawX = key.getWidth()/2 - ic.getWidth()/2;
            int drawY = key.getHeight()/2 - ic.getHeight()/2;
            canvas.drawBitmap(ic, drawX, drawY, paint);
          }
        } else
        // Draw the text
        canvas.drawText(
            label,
            left + key.getKey_text_offset_x(),
            (key.getHeight() - padding.top - padding.bottom) / 2
                + (paint.getTextSize() - paint.descent()) / 2
                + top
                + key.getKey_text_offset_y(),
            paint);
        if (mShowHint) {
          if (key.getLongClick() != null) {
            mPaintSymbol.setTextSize(
                key.getSymbol_text_size() != null ? key.getSymbol_text_size() : mSymbolSize);
            mPaintSymbol.setShadowLayer(mShadowRadius, 0, 0, mShadowColor);
            if ("".equals(key.getHintLocation()) || "top".equals(key.getHintLocation()))
              if (!"".equals(key.getDrawHintIcon())) {
                Bitmap ic = null;
                if ("select_all".equals(key.getDrawHintIcon())) {
                  ic = BitmapFactory.decodeResource(getResources(), R.drawable.abc_ic_menu_selectall_mtrl_alpha);
                }
                if ("copy".equals(key.getDrawHintIcon())) {
                  ic = BitmapFactory.decodeResource(getResources(), R.drawable.abc_ic_menu_copy_mtrl_am_alpha);
                }
                if (null != ic) {
                  int drawX = key.getWidth()/2 - ic.getWidth()/2;
                  float drawY = key.getHeight() / 10;
                  canvas.drawBitmap(ic, drawX, drawY, paint);
                }
              } else
            canvas.drawText(
                key.getSymbolLabel(),
                left + key.getKey_symbol_offset_x(),
                symbolBase + key.getKey_symbol_offset_y(),
                mPaintSymbol);
            if ("bottom".equals(key.getHintLocation())) {

            }
            if ("top_right".equals(key.getHintLocation())) {
              if (!"".equals(key.getDrawHintIcon())) {
                Bitmap ic = null;
                if ("select_all".equals(key.getDrawHintIcon())) {
                  ic = BitmapFactory.decodeResource(getResources(), R.drawable.abc_ic_menu_selectall_mtrl_alpha);
                }
                if ("copy".equals(key.getDrawHintIcon())) {
                  ic = BitmapFactory.decodeResource(getResources(), R.drawable.abc_ic_menu_copy_mtrl_am_alpha);
                }
                if (null != ic) {
                  int drawX = key.getWidth() / 5 * 3;
                  float drawY = key.getHeight() / 10;
                  canvas.drawBitmap(ic, drawX, drawY, paint);
                }
              } else
                canvas.drawText(
                        key.getSymbolLabel(),
                        key.getWidth() / 4 * 3,
                        symbolBase + key.getKey_symbol_offset_y(),
                        mPaintSymbol);
            }
            if ("top_left".equals(key.getHintLocation())) {

            }
            if ("bottom_right".equals(key.getHintLocation())) {

            }
            if ("bottom_left".equals(key.getHintLocation())) {

            }
          }

          if (!Function.isEmpty(hint)) {
            mPaintSymbol.setShadowLayer(mShadowRadius, 0, 0, mShadowColor);
            canvas.drawText(
                hint,
                left + key.getKey_hint_offset_x(),
                key.getHeight() + hintBase + key.getKey_hint_offset_y(),
                mPaintSymbol);
          }
        }

        // Turn off drop shadow
        paint.setShadowLayer(0, 0, 0, 0);
      }
      canvas.translate(-key.getX() - kbdPaddingLeft, -key.getY() - kbdPaddingTop);
    }
    mInvalidatedKey = null;
    // Overlay a dark rectangle to dim the keyboard
    if (mMiniKeyboardOnScreen) {
      paint.setColor((int) (mBackgroundDimAmount * 0xFF) << 24);
      canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
    }

    boolean mShowTouchPoints = true;
    if (DEBUG && mShowTouchPoints) {
      paint.setAlpha(128);
      paint.setColor(0xFFFF0000);
      canvas.drawCircle(mStartX, mStartY, 3, paint);
      canvas.drawLine(mStartX, mStartY, mLastX, mLastY, paint);
      paint.setColor(0xFF0000FF);
      canvas.drawCircle(mLastX, mLastY, 3, paint);
      paint.setColor(0xFF00FF00);
      canvas.drawCircle((mStartX + mLastX) / 2, (mStartY + mLastY) / 2, 2, paint);
    }
    mCanvas.restore();
    mDrawPending = false;
    mDirtyRect.setEmpty();
  }

  private int getKeyIndices(int x, int y, int[] allKeys) {
    final Key[] keys = mKeys;
    int primaryIndex = NOT_A_KEY;
    int closestKey = NOT_A_KEY;
    int closestKeyDist = mProximityThreshold + 1;
    java.util.Arrays.fill(mDistances, Integer.MAX_VALUE);
    int[] nearestKeyIndices = mKeyboard.getNearestKeys(x, y);
    final int keyCount = nearestKeyIndices.length;
    for (int i = 0; i < keyCount; i++) {
      final Key key = keys[nearestKeyIndices[i]];
      int dist = 0;
      boolean isInside = key.isInside(x, y);
      if (isInside) {
        primaryIndex = nearestKeyIndices[i];
      }

      if ((mProximityCorrectOn && (dist = key.squaredDistanceFrom(x, y)) < mProximityThreshold)
          || isInside) {
        // Find insertion point
        final int nCodes = 1;
        if (dist < closestKeyDist) {
          closestKeyDist = dist;
          closestKey = nearestKeyIndices[i];
        }

        if (allKeys == null) continue;

        for (int j = 0; j < mDistances.length; j++) {
          if (mDistances[j] > dist) {
            // Make space for nCodes codes
            System.arraycopy(mDistances, j, mDistances, j + nCodes, mDistances.length - j - nCodes);
            System.arraycopy(allKeys, j, allKeys, j + nCodes, allKeys.length - j - nCodes);
            allKeys[j] = key.getCode();
            mDistances[j] = dist;
            break;
          }
        }
      }
    }
    if (primaryIndex == NOT_A_KEY) {
      primaryIndex = closestKey;
    }
    return primaryIndex;
  }

  private void releaseKey(int code) {
    if (mComboMode) {
      if (mComboCount > 9) mComboCount = 9;
      mComboCodes[mComboCount++] = code;
    } else {
      mKeyboardActionListener.onRelease(code);
      if (mComboCount > 0) {
        for (int i = 0; i < mComboCount; i++) {
          mKeyboardActionListener.onRelease(mComboCodes[i]);
        }
        mComboCount = 0;
      }
    }
  }

  private void detectAndSendKey(int index, int x, int y, long eventTime, int type) {
    if (index != NOT_A_KEY && index < mKeys.length) {
      final Key key = mKeys[index];
      if (key.isShift() && !key.sendBindings(type)) {
        setShifted(key.isShiftLock(), !isShifted());
      } else {
        if (key.getClick().isRepeatable()) {
          if (type > 0) mAbortKey = true;
          if (!key.hasEvent(type)) return;
        }
        int code = key.getCode(type);
        //TextEntryState.keyPressedAt(key, x, y);
        int[] codes = new int[MAX_NEARBY_KEYS];
        Arrays.fill(codes, NOT_A_KEY);
        getKeyIndices(x, y, codes);
        mKeyboardActionListener.onEvent(key.getEvent(type));
        releaseKey(code);
        resetShifted();
      }
      mLastSentIndex = index;
      mLastTapTime = eventTime;
    }
  }

  private void detectAndSendKey(int index, int x, int y, long eventTime) {
    detectAndSendKey(index, x, y, eventTime, 0);
  }

  private void showPreview(int keyIndex, int type) {
    int oldKeyIndex = mCurrentKeyIndex;
    final PopupWindow previewPopup = mPreviewPopup;

    mCurrentKeyIndex = keyIndex;
    // Release the old key and press the new key
    final Key[] keys = mKeys;
    if (oldKeyIndex != mCurrentKeyIndex) {
      if (oldKeyIndex != NOT_A_KEY && keys.length > oldKeyIndex) {
        Key oldKey = keys[oldKeyIndex];
        oldKey.onReleased(mCurrentKeyIndex == NOT_A_KEY);
        invalidateKey(oldKeyIndex);
      }
      if (mCurrentKeyIndex != NOT_A_KEY && keys.length > mCurrentKeyIndex) {
        Key newKey = keys[mCurrentKeyIndex];
        newKey.onPressed();
        invalidateKey(mCurrentKeyIndex);
      }
    }
    // If key changed and preview is on ...
    if (oldKeyIndex != mCurrentKeyIndex && mShowPreview) {
      mHandler.removeMessages(MSG_SHOW_PREVIEW);
      if (previewPopup.isShowing()) {
        if (keyIndex == NOT_A_KEY) {
          mHandler.sendMessageDelayed(
              mHandler.obtainMessage(MSG_REMOVE_PREVIEW), DELAY_AFTER_PREVIEW);
        }
      }
      if (keyIndex != NOT_A_KEY) {
        if (previewPopup.isShowing() && mPreviewText.getVisibility() == VISIBLE) {
          // Show right away, if it's already visible and finger is moving around
          showKey(keyIndex, type);
        } else {
          mHandler.sendMessageDelayed(
              mHandler.obtainMessage(MSG_SHOW_PREVIEW, keyIndex, type), DELAY_BEFORE_PREVIEW);
        }
      }
    }
  }

  private void showPreview(int keyIndex) {
    showPreview(keyIndex, 0);
  }

  private void showKey(final int keyIndex, int type) {
    final PopupWindow previewPopup = mPreviewPopup;
    final Key[] keys = mKeys;
    if (keyIndex < 0 || keyIndex >= mKeys.length) return;
    Key key = keys[keyIndex];
    mPreviewText.setCompoundDrawables(null, null, null, null);
    mPreviewText.setText(key.getPreviewText(type));
    mPreviewText.measure(
        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    int popupWidth =
        Math.max(
            mPreviewText.getMeasuredWidth(),
            key.getWidth() + mPreviewText.getPaddingLeft() + mPreviewText.getPaddingRight());
    final int popupHeight = mPreviewHeight;
    ViewGroup.LayoutParams lp = mPreviewText.getLayoutParams();
    if (lp != null) {
      lp.width = popupWidth;
      lp.height = popupHeight;
    }
    int mPopupPreviewY;
    int mPopupPreviewX;
    boolean mPreviewCentered = false;
    if (!mPreviewCentered) {
      mPopupPreviewX = key.getX() - mPreviewText.getPaddingLeft() + getPaddingLeft();
      mPopupPreviewY = key.getY() - popupHeight + mPreviewOffset;
    } else {
      // TODO: Fix this if centering is brought back
      mPopupPreviewX = 160 - mPreviewText.getMeasuredWidth() / 2;
      mPopupPreviewY = -mPreviewText.getMeasuredHeight();
    }
    mHandler.removeMessages(MSG_REMOVE_PREVIEW);
    getLocationInWindow(mCoordinates);
    mCoordinates[0] += mMiniKeyboardOffsetX; // Offset may be zero
    mCoordinates[1] += mMiniKeyboardOffsetY; // Offset may be zero

    // Set the preview background state
    mPreviewText
        .getBackground()
        .setState(key.getPopupResId() != 0 ? LONG_PRESSABLE_STATE_SET : EMPTY_STATE_SET);
    mPopupPreviewX += mCoordinates[0];
    mPopupPreviewY += mCoordinates[1];

    // If the popup cannot be shown above the key, put it on the side
    getLocationOnScreen(mCoordinates);
    if (mPopupPreviewY + mCoordinates[1] < 0) {
      // If the key you're pressing is on the left side of the keyboard, show the popup on
      // the right, offset by enough to see at least one key to the left/right.
      if (key.getX() + key.getWidth() <= getWidth() / 2) {
        mPopupPreviewX += (int) (key.getWidth() * 2.5);
      } else {
        mPopupPreviewX -= (int) (key.getWidth() * 2.5);
      }
      mPopupPreviewY += popupHeight;
    }

    if (previewPopup.isShowing()) {
      //previewPopup.update(mPopupPreviewX, mPopupPreviewY, popupWidth, popupHeight);
      previewPopup.dismiss(); //禁止窗口動畫
    }
    previewPopup.setWidth(popupWidth);
    previewPopup.setHeight(popupHeight);
    previewPopup.showAtLocation(mPopupParent, Gravity.NO_GRAVITY, mPopupPreviewX, mPopupPreviewY);
    mPreviewText.setVisibility(VISIBLE);
  }

  /**
   * Requests a redraw of the entire keyboard. Calling {@link #invalidate} is not sufficient because
   * the keyboard renders the keys to an off-screen buffer and an invalidate() only draws the cached
   * buffer.
   *
   * @see #invalidateKey(int)
   */
  public void invalidateAllKeys() {
    mDirtyRect.union(0, 0, getWidth(), getHeight());
    mDrawPending = true;
    invalidate();
  }

  /**
   * Invalidates a key so that it will be redrawn on the next repaint. Use this method if only one
   * key is changing it's content. Any changes that affect the position or size of the key may not
   * be honored.
   *
   * @param keyIndex the index of the key in the attached {@link Keyboard}.
   * @see #invalidateAllKeys
   */
  private void invalidateKey(int keyIndex) {
    if (mKeys == null) return;
    if (keyIndex < 0 || keyIndex >= mKeys.length) {
      return;
    }
    final Key key = mKeys[keyIndex];
    mInvalidatedKey = key;
    mDirtyRect.union(
        key.getX() + getPaddingLeft(),
        key.getY() + getPaddingTop(),
        key.getX() + key.getWidth() + getPaddingLeft(),
        key.getY() + key.getHeight() + getPaddingTop());
    onBufferDraw();
    invalidate(
        key.getX() + getPaddingLeft(),
        key.getY() + getPaddingTop(),
        key.getX() + key.getWidth() + getPaddingLeft(),
        key.getY() + key.getHeight() + getPaddingTop());
  }

  private void invalidateKeys(List<Key> keys) {
    if (keys == null || keys.size() == 0) return;
    for (Key key : keys) {
      mDirtyRect.union(
          key.getX() + getPaddingLeft(),
          key.getY() + getPaddingTop(),
          key.getX() + key.getWidth() + getPaddingLeft(),
          key.getY() + key.getHeight() + getPaddingTop());
    }
    onBufferDraw();
    invalidate();
  }

  public void invalidateComposingKeys() {
    List<Key> keys = mKeyboard.getComposingKeys();
    if (keys != null && keys.size() > 5) invalidateAllKeys();
    else invalidateKeys(keys);
  }

  private boolean openPopupIfRequired(MotionEvent me) {
    // Check if we have a popup layout specified first.
    if (mPopupLayout == 0) {
      return false;
    }
    if (mCurrentKey < 0 || mCurrentKey >= mKeys.length) {
      return false;
    }
    showPreview(NOT_A_KEY);
    showPreview(mCurrentKey, KeyEventType.LONG_CLICK.ordinal());
    Key popupKey = mKeys[mCurrentKey];
    boolean result = onLongPress(popupKey);
    if (result) {
      mAbortKey = true;
      showPreview(NOT_A_KEY);
    }
    return result;
  }

  /**
   * Called when a key is long pressed. By default this will open any popup keyboard associated with
   * this key through the attributes popupLayout and popupCharacters.
   *
   * @param popupKey the key that was long pressed
   * @return true if the long press is handled, false otherwise. Subclasses should call the method
   *     on the base class if the subclass doesn't wish to handle the call.
   */
  private boolean onLongPress(Key popupKey) {
    int popupKeyboardId = popupKey.getPopupResId();

    if (popupKeyboardId != 0) {
      View mMiniKeyboardContainer = mMiniKeyboardCache.get(popupKey);
      KeyboardView mMiniKeyboard;
      if (mMiniKeyboardContainer == null) {
        LayoutInflater inflater =
            (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mMiniKeyboardContainer = inflater.inflate(mPopupLayout, null);
        mMiniKeyboard =
            (KeyboardView) mMiniKeyboardContainer.findViewById(android.R.id.keyboardView);
        View closeButton = mMiniKeyboardContainer.findViewById(android.R.id.closeButton);
        if (closeButton != null) closeButton.setOnClickListener(this);
        mMiniKeyboard.setOnKeyboardActionListener(
            new OnKeyboardActionListener() {
              @Override
              public void onEvent(Event event) {
                mKeyboardActionListener.onEvent(event);
                dismissPopupKeyboard();
              }

              @Override
              public void onKey(int primaryCode, int mask) {
                mKeyboardActionListener.onKey(primaryCode, mask);
                dismissPopupKeyboard();
              }

              @Override
              public void onText(CharSequence text) {
                mKeyboardActionListener.onText(text);
                dismissPopupKeyboard();
              }

              @Override
              public void swipeLeft() {}

              @Override
              public void swipeRight() {}

              @Override
              public void swipeUp() {}

              @Override
              public void swipeDown() {}

              @Override
              public void onPress(int primaryCode) {
                mKeyboardActionListener.onPress(primaryCode);
              }

              @Override
              public void onRelease(int primaryCode) {
                mKeyboardActionListener.onRelease(primaryCode);
              }
            });
        //mInputView.setSuggest(mSuggest);
        Keyboard keyboard;
        if (popupKey.getPopupCharacters() != null) {
          keyboard =
              new Keyboard(
                  getContext(),
                  popupKey.getPopupCharacters(),
                  -1,
                  getPaddingLeft() + getPaddingRight());
        } else {
          keyboard = new Keyboard(getContext());
        }
        mMiniKeyboard.setKeyboard(keyboard);
        mMiniKeyboard.setPopupParent(this);
        mMiniKeyboardContainer.measure(
            MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.AT_MOST));

        mMiniKeyboardCache.put(popupKey, mMiniKeyboardContainer);
      } else {
        mMiniKeyboard =
            (KeyboardView) mMiniKeyboardContainer.findViewById(android.R.id.keyboardView);
      }
      getLocationInWindow(mCoordinates);
      int mPopupX = popupKey.getX() + getPaddingLeft();
      int mPopupY = popupKey.getY() + getPaddingTop();
      mPopupX = mPopupX + popupKey.getWidth() - mMiniKeyboardContainer.getMeasuredWidth();
      mPopupY = mPopupY - mMiniKeyboardContainer.getMeasuredHeight();
      final int x = mPopupX + mMiniKeyboardContainer.getPaddingRight() + mCoordinates[0];
      final int y = mPopupY + mMiniKeyboardContainer.getPaddingBottom() + mCoordinates[1];
      mMiniKeyboard.setPopupOffset(x < 0 ? 0 : x, y);
      mMiniKeyboard.setShifted(false, isShifted());
      mPopupKeyboard.setContentView(mMiniKeyboardContainer);
      mPopupKeyboard.setWidth(mMiniKeyboardContainer.getMeasuredWidth());
      mPopupKeyboard.setHeight(mMiniKeyboardContainer.getMeasuredHeight());
      mPopupKeyboard.showAtLocation(this, Gravity.NO_GRAVITY, x, y);
      mMiniKeyboardOnScreen = true;
      //mMiniKeyboard.onTouchEvent(getTranslatedEvent(me));
      invalidateAllKeys();
      return true;
    } else {
      Key key = popupKey;
      if (key.getLongClick() != null) {
        removeMessages();
        mAbortKey = true;
        Event e = key.getLongClick();
        mKeyboardActionListener.onEvent(e);
        releaseKey(e.getCode());
        resetShifted();
        return true;
      }
      if (key.isShift() && !key.sendBindings(KeyEventType.LONG_CLICK.ordinal())) {
        setShifted(!key.isOn(), !key.isOn());
        return true;
      }
    }
    return false;
  }

  /*
  @Override
  public boolean onHoverEvent(MotionEvent event) {
      if (mAccessibilityManager.isTouchExplorationEnabled() && event.getPointerCount() == 1) {
          final int action = event.getAction();
          switch (action) {
              case MotionEvent.ACTION_HOVER_ENTER: {
                  event.setAction(MotionEvent.ACTION_DOWN);
              } break;
              case MotionEvent.ACTION_HOVER_MOVE: {
                  event.setAction(MotionEvent.ACTION_MOVE);
              } break;
              case MotionEvent.ACTION_HOVER_EXIT: {
                  event.setAction(MotionEvent.ACTION_UP);
              } break;
          }
          return onTouchEvent(event);
      }
      return true;
  }
  */

  @Override
  public boolean performClick() {
    return super.performClick();
  }

  @Override
  public boolean onTouchEvent(MotionEvent me) {
    // Convert multi-pointer up/down events to single up/down events to
    // deal with the typical multi-pointer behavior of two-thumb typing
    final int index = me.getActionIndex();
    final int pointerCount = me.getPointerCount();
    final int action = me.getActionMasked();
    boolean result = false;
    final long now = me.getEventTime();

    mComboMode = false;
    if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_CANCEL) {
      mComboCount = 0;
    } else if (pointerCount > 1
        || action == MotionEvent.ACTION_POINTER_DOWN
        || action == MotionEvent.ACTION_POINTER_UP) {
      mComboMode = true;
    }

    if (action == MotionEvent.ACTION_POINTER_UP
        || (mOldPointerCount > 1 && action == MotionEvent.ACTION_UP)) {
      //並擊鬆開前的虛擬按鍵事件
      MotionEvent ev =
          MotionEvent.obtain(
              now,
              now,
              MotionEvent.ACTION_POINTER_DOWN,
              me.getX(index),
              me.getY(index),
              me.getMetaState());
      result = onModifiedTouchEvent(ev, false);
      ev.recycle();
    }

    if (action == MotionEvent.ACTION_POINTER_DOWN) {
      //並擊中的按鍵事件，需要按鍵提示
      MotionEvent ev =
          MotionEvent.obtain(
              now, now, MotionEvent.ACTION_DOWN, me.getX(index), me.getY(index), me.getMetaState());
      result = onModifiedTouchEvent(ev, false);
      ev.recycle();
    } else {
      result = onModifiedTouchEvent(me, false);
    }

    if (action != MotionEvent.ACTION_MOVE) mOldPointerCount = pointerCount;
    performClick();
    return result;
  }

  private boolean onModifiedTouchEvent(MotionEvent me, boolean possiblePoly) {
    final int pointerCount = me.getPointerCount();
    final int index = me.getActionIndex();
    int touchX = (int) me.getX(index) - getPaddingLeft();
    int touchY = (int) me.getY(index) - getPaddingTop();
    if (touchY >= -mVerticalCorrection) touchY += mVerticalCorrection;
    final int action = me.getActionMasked();
    final long eventTime = me.getEventTime();
    int keyIndex = getKeyIndices(touchX, touchY, null);
    mPossiblePoly = possiblePoly;

    // Track the last few movements to look for spurious swipes.
    if (action == MotionEvent.ACTION_DOWN) mSwipeTracker.clear();
    mSwipeTracker.addMovement(me);

    // Ignore all motion events until a DOWN.
    if (mAbortKey && action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_CANCEL) {
      return true;
    }

    if (mGestureDetector.onTouchEvent(me)) {
      showPreview(NOT_A_KEY);
      mHandler.removeMessages(MSG_REPEAT);
      mHandler.removeMessages(MSG_LONGPRESS);
      return true;
    }

    // Needs to be called after the gesture detector gets a turn, as it may have
    // displayed the mini keyboard
    if (mMiniKeyboardOnScreen && action != MotionEvent.ACTION_CANCEL) {
      return true;
    }

    switch (action) {
      case MotionEvent.ACTION_DOWN:
      case MotionEvent.ACTION_POINTER_DOWN:
        mAbortKey = false;
        mStartX = touchX;
        mStartY = touchY;
        mLastCodeX = touchX;
        mLastCodeY = touchY;
        mLastKeyTime = 0;
        mCurrentKeyTime = 0;
        mLastKey = NOT_A_KEY;
        mCurrentKey = keyIndex;
        mDownKey = keyIndex;
        mDownTime = me.getEventTime();
        mLastMoveTime = mDownTime;
        if (action == MotionEvent.ACTION_POINTER_DOWN) break; //並擊鬆開前的虛擬按鍵事件
        checkMultiTap(eventTime, keyIndex);
        mKeyboardActionListener.onPress(keyIndex != NOT_A_KEY ? mKeys[keyIndex].getCode() : 0);
        if (mCurrentKey >= 0 && mKeys[mCurrentKey].getClick().isRepeatable()) {
          mRepeatKeyIndex = mCurrentKey;
          Message msg = mHandler.obtainMessage(MSG_REPEAT);
          mHandler.sendMessageDelayed(msg, REPEAT_START_DELAY);
          // Delivering the key could have caused an abort
          if (mAbortKey) {
            mRepeatKeyIndex = NOT_A_KEY;
            break;
          }
        }
        if (mCurrentKey != NOT_A_KEY) {
          Message msg = mHandler.obtainMessage(MSG_LONGPRESS, me);
          mHandler.sendMessageDelayed(msg, LONGPRESS_TIMEOUT);
        }
        showPreview(keyIndex, 0);
        break;

      case MotionEvent.ACTION_MOVE:
        boolean continueLongPress = false;
        if (keyIndex != NOT_A_KEY) {
          if (mCurrentKey == NOT_A_KEY) {
            mCurrentKey = keyIndex;
            mCurrentKeyTime = eventTime - mDownTime;
          } else {
            if (keyIndex == mCurrentKey) {
              mCurrentKeyTime += eventTime - mLastMoveTime;
              continueLongPress = true;
            } else if (mRepeatKeyIndex == NOT_A_KEY) {
              resetMultiTap();
              mLastKey = mCurrentKey;
              mLastCodeX = mLastX;
              mLastCodeY = mLastY;
              mLastKeyTime = mCurrentKeyTime + eventTime - mLastMoveTime;
              mCurrentKey = keyIndex;
              mCurrentKeyTime = 0;
            }
          }
        }
        if (!mComboMode && !continueLongPress) {
          // Cancel old longpress
          mHandler.removeMessages(MSG_LONGPRESS);
          // Start new longpress if key has changed
          if (keyIndex != NOT_A_KEY) {
            Message msg = mHandler.obtainMessage(MSG_LONGPRESS, me);
            mHandler.sendMessageDelayed(msg, LONGPRESS_TIMEOUT);
          }
        }
        showPreview(mCurrentKey);
        mLastMoveTime = eventTime;
        break;

      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_POINTER_UP:
        removeMessages();
        if (keyIndex == mCurrentKey) {
          mCurrentKeyTime += eventTime - mLastMoveTime;
        } else {
          resetMultiTap();
          mLastKey = mCurrentKey;
          mLastKeyTime = mCurrentKeyTime + eventTime - mLastMoveTime;
          mCurrentKey = keyIndex;
          mCurrentKeyTime = 0;
        }
        if (mCurrentKeyTime < mLastKeyTime
            && mCurrentKeyTime < DEBOUNCE_TIME
            && mLastKey != NOT_A_KEY) {
          mCurrentKey = mLastKey;
          touchX = mLastCodeX;
          touchY = mLastCodeY;
        }
        showPreview(NOT_A_KEY);
        Arrays.fill(mKeyIndices, NOT_A_KEY);
        // If we're not on a repeating key (which sends on a DOWN event)
        if (mRepeatKeyIndex != NOT_A_KEY && !mAbortKey) repeatKey();
        if (mRepeatKeyIndex == NOT_A_KEY && !mMiniKeyboardOnScreen && !mAbortKey) {
          detectAndSendKey(
              mCurrentKey,
              touchX,
              touchY,
              eventTime,
              (mOldPointerCount > 1 || mComboMode) ? KeyEventType.COMBO.ordinal() : 0);
        }
        invalidateKey(keyIndex);
        mRepeatKeyIndex = NOT_A_KEY;
        break;
      case MotionEvent.ACTION_CANCEL:
        removeMessages();
        dismissPopupKeyboard();
        mAbortKey = true;
        showPreview(NOT_A_KEY);
        invalidateKey(mCurrentKey);
        break;
    }
    mLastX = touchX;
    mLastY = touchY;
    return true;
  }

  private boolean repeatKey() {
    Key key = mKeys[mRepeatKeyIndex];
    detectAndSendKey(mCurrentKey, key.getX(), key.getY(), mLastTapTime);
    return true;
  }

  private void swipeRight() {
    mKeyboardActionListener.swipeRight();
  }

  private void swipeLeft() {
    mKeyboardActionListener.swipeLeft();
  }

  private void swipeUp() {
    mKeyboardActionListener.swipeUp();
  }

  private void swipeDown() {
    mKeyboardActionListener.swipeDown();
  }

  public void closing() {
    if (mPreviewPopup.isShowing()) {
      mPreviewPopup.dismiss();
    }
    removeMessages();

    dismissPopupKeyboard();
    mBuffer = null;
    mCanvas = null;
    mMiniKeyboardCache.clear();
  }

  private void removeMessages() {
    mHandler.removeMessages(MSG_REPEAT);
    mHandler.removeMessages(MSG_LONGPRESS);
    mHandler.removeMessages(MSG_SHOW_PREVIEW);
  }

  @Override
  public void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    closing();
  }

  private void dismissPopupKeyboard() {
    if (mPopupKeyboard.isShowing()) {
      mPopupKeyboard.dismiss();
      mMiniKeyboardOnScreen = false;
      invalidateAllKeys();
    }
  }

  public boolean handleBack() {
    if (mPopupKeyboard.isShowing()) {
      dismissPopupKeyboard();
      return true;
    }
    return false;
  }

  private void resetMultiTap() {
    mLastSentIndex = NOT_A_KEY;
    int mTapCount = 0;
    mLastTapTime = -1;
    boolean mInMultiTap = false;
  }

  private void checkMultiTap(long eventTime, int keyIndex) {
    if (keyIndex == NOT_A_KEY) return;
    Key key = mKeys[keyIndex];
    if (eventTime > mLastTapTime + MULTITAP_INTERVAL || keyIndex != mLastSentIndex) {
      resetMultiTap();
    }
  }

  /** 識別滑動手勢 */
  private static class SwipeTracker {

    static final int NUM_PAST = 4;
    static final int LONGEST_PAST_TIME = 200;

    final float mPastX[] = new float[NUM_PAST];
    final float mPastY[] = new float[NUM_PAST];
    final long mPastTime[] = new long[NUM_PAST];

    float mYVelocity;
    float mXVelocity;

    public void clear() {
      mPastTime[0] = 0;
    }

    public void addMovement(MotionEvent ev) {
      long time = ev.getEventTime();
      final int N = ev.getHistorySize();
      for (int i = 0; i < N; i++) {
        addPoint(ev.getHistoricalX(i), ev.getHistoricalY(i), ev.getHistoricalEventTime(i));
      }
      addPoint(ev.getX(), ev.getY(), time);
    }

    private void addPoint(float x, float y, long time) {
      int drop = -1;
      int i;
      final long[] pastTime = mPastTime;
      for (i = 0; i < NUM_PAST; i++) {
        if (pastTime[i] == 0) {
          break;
        } else if (pastTime[i] < time - LONGEST_PAST_TIME) {
          drop = i;
        }
      }
      if (i == NUM_PAST && drop < 0) {
        drop = 0;
      }
      if (drop == i) drop--;
      final float[] pastX = mPastX;
      final float[] pastY = mPastY;
      if (drop >= 0) {
        final int start = drop + 1;
        final int count = NUM_PAST - drop - 1;
        System.arraycopy(pastX, start, pastX, 0, count);
        System.arraycopy(pastY, start, pastY, 0, count);
        System.arraycopy(pastTime, start, pastTime, 0, count);
        i -= (drop + 1);
      }
      pastX[i] = x;
      pastY[i] = y;
      pastTime[i] = time;
      i++;
      if (i < NUM_PAST) {
        pastTime[i] = 0;
      }
    }

    public void computeCurrentVelocity(int units) {
      computeCurrentVelocity(units, Float.MAX_VALUE);
    }

    public void computeCurrentVelocity(int units, float maxVelocity) {
      final float[] pastX = mPastX;
      final float[] pastY = mPastY;
      final long[] pastTime = mPastTime;

      final float oldestX = pastX[0];
      final float oldestY = pastY[0];
      final long oldestTime = pastTime[0];
      float accumX = 0;
      float accumY = 0;
      int N = 0;
      while (N < NUM_PAST) {
        if (pastTime[N] == 0) {
          break;
        }
        N++;
      }

      for (int i = 1; i < N; i++) {
        final int dur = (int) (pastTime[i] - oldestTime);
        if (dur == 0) continue;
        float dist = pastX[i] - oldestX;
        float vel = (dist / dur) * units; // pixels/frame.
        if (accumX == 0) accumX = vel;
        else accumX = (accumX + vel) * .5f;

        dist = pastY[i] - oldestY;
        vel = (dist / dur) * units; // pixels/frame.
        if (accumY == 0) accumY = vel;
        else accumY = (accumY + vel) * .5f;
      }
      mXVelocity = accumX < 0.0f ? Math.max(accumX, -maxVelocity) : Math.min(accumX, maxVelocity);
      mYVelocity = accumY < 0.0f ? Math.max(accumY, -maxVelocity) : Math.min(accumY, maxVelocity);
    }

    public float getXVelocity() {
      return mXVelocity;
    }

    public float getYVelocity() {
      return mYVelocity;
    }
  }
}
