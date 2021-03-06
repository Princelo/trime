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

package com.osfans.trime.ime.core;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import com.osfans.trime.Candidate;
import com.osfans.trime.Composition;
import com.osfans.trime.Config;
import com.osfans.trime.Effect;
import com.osfans.trime.Event;
import com.osfans.trime.Function;
import com.osfans.trime.IntentReceiver;
import com.osfans.trime.Key;
import com.osfans.trime.Keyboard;
import com.osfans.trime.KeyboardSwitch;
import com.osfans.trime.KeyboardView;
import com.osfans.trime.R;
import com.osfans.trime.Rime;
import com.osfans.trime.Speech;
import com.osfans.trime.enums.InlineModeType;
import com.osfans.trime.enums.WindowsPositionType;
import com.osfans.trime.settings.components.ColorPickerDialog;
import com.osfans.trime.settings.components.SchemaPickerDialog;
import com.osfans.trime.settings.components.ThemePickerDialog;
import com.osfans.trime.util.LocaleUtils;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** {@link InputMethodService ?????????}????????? */
public class Trime extends InputMethodService
    implements KeyboardView.OnKeyboardActionListener, Candidate.CandidateListener {
  private static Logger Log = Logger.getLogger(Trime.class.getSimpleName());
  private static Trime self;
  private KeyboardView mKeyboardView; //?????????
  private KeyboardSwitch mKeyboardSwitch;
  private Config mConfig; //??????
  private Effect mEffect; //??????
  private Candidate mCandidate; //??????
  private Composition mComposition; //??????
  private LinearLayout mCompositionContainer;
  private FrameLayout mCandidateContainer;
  private PopupWindow mFloatingWindow;
  private PopupTimer mFloatingWindowTimer = new PopupTimer();
  private RectF mPopupRectF = new RectF();
  private AlertDialog mOptionsDialog; //?????????

  private int orientation;
  private boolean canCompose;
  private boolean enterAsLineBreak;
  private boolean mShowWindow = true; //??????????????????
  private String movable; //???????????????????????????
  private int winX, winY; //???????????????
  private int candSpacing; //????????????????????????
  private boolean cursorUpdated = false; //??????????????????
  private int min_length;
  private boolean mTempAsciiMode; //?????????????????????
  private boolean mAsciiMode; //?????????????????????
  private boolean reset_ascii_mode; //?????????????????????
  private String auto_caps; //??????????????????
  private Locale[] locales = new Locale[2];
  private boolean keyUpNeeded; //RIME??????????????????keyUp??????
  private boolean mNeedUpdateRimeOption = true;
  private String lastCommittedText;

  private WindowsPositionType winPos; //????????????????????????
  private InlineModeType inlinePreedit; //????????????

  private IntentReceiver mIntentReceiver;
  static private Handler syncBackgroundHandler=new  Handler(new Handler.Callback() {
    @Override
    public boolean handleMessage(Message msg) {
      if(!((Trime)msg.obj).isShowInputRequested()){ //?????????????????????????????????????????????????????????????????????5??????????????????
        Function.syncBackground((Trime)msg.obj);
        ((Trime)msg.obj).loadConfig();
      }
      return false;
    }
  });

  @Override
  public void onWindowHidden(){
    boolean sync_bg = mConfig.getSyncBackground();
    if(sync_bg){
      Message msg = new Message();
      msg.obj = this;
      syncBackgroundHandler.sendMessageDelayed(msg,5000); // ??????????????????5???????????????????????????
    }
  }

  private boolean isWinFixed() {
    return VERSION.SDK_INT < VERSION_CODES.LOLLIPOP
        || (winPos != WindowsPositionType.LEFT
            && winPos != WindowsPositionType.RIGHT
            && winPos != WindowsPositionType.LEFT_UP
            && winPos != WindowsPositionType.RIGHT_UP);
  }

  public void updateWindow(int offsetX, int offsetY) {
    winPos = WindowsPositionType.DRAG;
    winX = offsetX;
    winY = offsetY;
    mFloatingWindow.update(winX, winY, -1, -1, true);
  }

  public static int getStatusBarHeight() {
    int result = 0;
    int resourceId = self.getResources().getIdentifier("status_bar_height", "dimen", "android");
    if (resourceId > 0) {
      result = self.getResources().getDimensionPixelSize(resourceId);
    }
    return result;
  }

  public static int[] getLocationOnScreen(View v) {
    final int[] position = new int[2];
    v.getLocationOnScreen(position);
    return position;
  }

  private class PopupTimer extends Handler implements Runnable {
    private int mParentLocation[] = new int[2];

    void postShowFloatingWindow() {
      if (Function.isEmpty(Rime.getCompositionText())) {
        hideComposition();
        return;
      }
      mCompositionContainer.measure(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
      mFloatingWindow.setWidth(mCompositionContainer.getMeasuredWidth());
      mFloatingWindow.setHeight(mCompositionContainer.getMeasuredHeight());
      post(this);
    }

    void cancelShowing() {
      if (null != mFloatingWindow && mFloatingWindow.isShowing()) mFloatingWindow.dismiss();
      removeCallbacks(this);
    }

    @Override
    public void run() {
      if (mCandidateContainer == null || mCandidateContainer.getWindowToken() == null) return;
      if (!mShowWindow) return;
      int x = 0, y = 0;
      mParentLocation = getLocationOnScreen(mCandidateContainer);
      if (isWinFixed() || !cursorUpdated) {
        //setCandidatesViewShown(true);
        switch (winPos) {
          case TOP_RIGHT:
            x = mCandidateContainer.getWidth() - mFloatingWindow.getWidth();
            y = candSpacing;
            break;
          case TOP_LEFT:
            x = 0;
            y = candSpacing;
            break;
          case BOTTOM_RIGHT:
            x = mCandidateContainer.getWidth() - mFloatingWindow.getWidth();
            y = mParentLocation[1] - mFloatingWindow.getHeight() - candSpacing;
            break;
          case DRAG:
            x = winX;
            y = winY;
            break;
          case FIXED:
          case BOTTOM_LEFT:
          default:
            x = 0;
            y = mParentLocation[1] - mFloatingWindow.getHeight() - candSpacing;
            break;
        }
      } else {
        //setCandidatesViewShown(false);
        x = (int) mPopupRectF.left;
        if (winPos == WindowsPositionType.RIGHT
            || winPos == WindowsPositionType.RIGHT_UP) {
          x = (int) mPopupRectF.right;
        }
        y = (int) mPopupRectF.bottom + candSpacing;
        if (winPos == WindowsPositionType.LEFT_UP
            || winPos == WindowsPositionType.RIGHT_UP) {
          y =
              (int) mPopupRectF.top
                  - mFloatingWindow.getHeight()
                  - candSpacing;
        }
      }
      if (x < 0) x = 0;
      if (x > mCandidateContainer.getWidth() - mFloatingWindow.getWidth()) {
        x = mCandidateContainer.getWidth() - mFloatingWindow.getWidth();
      }
      if (y < 0) y = 0;
      if (y > mParentLocation[1] - mFloatingWindow.getHeight() - candSpacing) { //candSpacing?????????????????????????????????
        y = mParentLocation[1] - mFloatingWindow.getHeight() - candSpacing;
      }
      y -= getStatusBarHeight(); //??????????????????
      if (!mFloatingWindow.isShowing()) {
        mFloatingWindow.showAtLocation(mCandidateContainer, Gravity.START | Gravity.TOP, x, y);
      } else {
        mFloatingWindow.update(x, y, mFloatingWindow.getWidth(), mFloatingWindow.getHeight());
      }
    }
  }

  public void loadConfig() {
    inlinePreedit = mConfig.getInlinePreedit();
    winPos = mConfig.getWinPos();
    movable = mConfig.getString("layout/movable");
    candSpacing = mConfig.getPixel("layout/spacing");
    min_length = mConfig.getInt("layout/min_length");
    reset_ascii_mode = mConfig.getBoolean("reset_ascii_mode");
    auto_caps = mConfig.getString("auto_caps");
    mShowWindow = mConfig.getShowWindow();
    mNeedUpdateRimeOption = true;
  }

  private boolean updateRimeOption() {
    if (mNeedUpdateRimeOption) {
      String soft_cursor_key = "soft_cursor";
      Rime.setOption(soft_cursor_key, mConfig.getSoftCursor()); //?????????
      String horizontal_key = "horizontal";
      Rime.setOption("_" + horizontal_key, mConfig.getBoolean(horizontal_key)); //????????????
      mNeedUpdateRimeOption = false;
    }
    return true;
  }

  public void resetEffect() {
    if (mEffect != null) mEffect.reset();
  }

  public void vibrateEffect() {
    if (mEffect != null) mEffect.vibrate();
  }

  public void soundEffect() {
    if (mEffect != null) mEffect.playSound(0);
  }

  @Override
  public void onCreate() {
    super.onCreate();
    self = this;
    mIntentReceiver = new IntentReceiver();
    mIntentReceiver.registerReceiver(this);

    mEffect = new Effect(this);
    mConfig = Config.get(this);
    mNeedUpdateRimeOption = true;
    loadConfig();
    resetEffect();
    mKeyboardSwitch = new KeyboardSwitch(this);

    @Nullable String s;
    s = mConfig.getString("locale");
    if (Function.isEmpty(s)) s = "";
    locales[0] = LocaleUtils.INSTANCE.stringToLocale(s);
    if (locales[0].equals(new Locale(s))) locales[0] = Locale.getDefault();

    s = mConfig.getString("latin_locale");
    if (Function.isEmpty(s)) s = "en_US";
    locales[1] = LocaleUtils.INSTANCE.stringToLocale(s);
    if (locales[1].equals(new Locale(s))) locales[0] = Locale.ENGLISH;
    /*
    String[] ss;
    s = mConfig.getString("locale");
    if (Function.isEmpty(s)) s = "";
    ss = s.split("[-_]");
    if (ss.length == 2) locales[0] = new Locale(ss[0], ss[1]);
    else if (ss.length == 3) locales[0] = new Locale(ss[0], ss[1], ss[2]);
    else locales[0] = Locale.getDefault();
    s = mConfig.getString("latin_locale");
    if (Function.isEmpty(s)) s = "en_US";
    ss = s.split("[-_]");
    if (ss.length == 1) locales[1] = new Locale(ss[0]);
    else if (ss.length == 2) locales[1] = new Locale(ss[0], ss[1]);
    else if (ss.length == 3) locales[1] = new Locale(ss[0], ss[1], ss[2]);
    else locales[0] = Locale.ENGLISH;*/

    orientation = getResources().getConfiguration().orientation;
    // Use the following line to debug IME service.
    //android.os.Debug.waitForDebugger();
  }

  public void onOptionChanged(String option, boolean value) {
    switch (option) {
      case "ascii_mode":
        if (!mTempAsciiMode) mAsciiMode = value; //??????????????????????????????
        mEffect.setLanguage(locales[value ? 1 : 0]);
        break;
      case "_hide_comment":
        setShowComment(!value);
        break;
      case "_hide_candidate":
        if (mCandidateContainer != null)
          mCandidate.setVisibility(!value ? View.VISIBLE : View.GONE);
        setCandidatesViewShown(canCompose && !value);
        break;
      case "_hide_key_hint":
        if (mKeyboardView != null) mKeyboardView.setShowHint(!value);
        break;
      default:
        if (option.startsWith("_keyboard_") && option.length() > 10 && value && mKeyboardSwitch != null) {
          String keyboard = option.substring(10);
          mKeyboardSwitch.setKeyboard(keyboard);
          mTempAsciiMode = mKeyboardSwitch.getAsciiMode();
          bindKeyboardToInputView();
        } else if (option.startsWith("_key_") && option.length() > 5 && value) {
          boolean bNeedUpdate = mNeedUpdateRimeOption;
          if (bNeedUpdate) mNeedUpdateRimeOption = false; //?????????onMessage???setOption
          String key = option.substring(5);
          onEvent(new Event(key));
          if (bNeedUpdate) mNeedUpdateRimeOption = true;
        }
    }
    if (mKeyboardView != null) mKeyboardView.invalidateAllKeys();
  }

  public void invalidate() {
    Rime.get(this);
    if (mConfig != null) mConfig.destroy();
    mConfig = new Config(this);
    reset();
    mNeedUpdateRimeOption = true;
  }

  private void hideComposition() {
    if (movable.contentEquals("once")) winPos = mConfig.getWinPos();
    mFloatingWindowTimer.cancelShowing();
  }

  private void loadBackground() {
    GradientDrawable gd = new GradientDrawable();
    gd.setStroke(mConfig.getPixel("layout/border"), mConfig.getColor("border_color"));
    gd.setCornerRadius(mConfig.getFloat("layout/round_corner"));
    Drawable d = mConfig.getDrawable("layout/background");
    if (d == null) {
      gd.setColor(mConfig.getColor("text_back_color"));
      d = gd;
    }
    if (mConfig.hasKey("layout/alpha")) {
      int alpha = mConfig.getInt("layout/alpha");
      if (alpha <= 0) alpha = 0;
      else if (alpha >= 255) alpha = 255;
      d.setAlpha(alpha);
    }
    mFloatingWindow.setBackgroundDrawable(d);
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP)
      mFloatingWindow.setElevation(mConfig.getPixel("layout/elevation"));
    Integer candidateBgColor = mConfig.getColor("candidate_bg_color");
    if (candidateBgColor != null)
      mCandidateContainer.setBackgroundColor(candidateBgColor);
    else
    mCandidateContainer.setBackgroundColor(mConfig.getColor("back_color"));
  }

  public void resetKeyboard() {
    if (mKeyboardView != null) {
      mKeyboardView.setShowHint(!Rime.getOption("_hide_key_hint"));
      mKeyboardView.reset(this); //????????????????????????
    }
  }

  public void resetCandidate() {
    if (mCandidateContainer != null) {
      loadBackground();
      setShowComment(!Rime.getOption("_hide_comment"));
      mCandidate.setVisibility(!Rime.getOption("_hide_candidate") ? View.VISIBLE : View.GONE);
      mCandidate.reset(this);
      mShowWindow = mConfig.getShowWindow();
      mComposition.setVisibility(mShowWindow ? View.VISIBLE : View.GONE);
      mComposition.reset(this);
    }
  }

  /** ??????????????????????????????????????? !!???????????????????????????Rime.setOption???????????????????????? */
  private void reset() {
    mConfig.reset();
    loadConfig();
    if (mKeyboardSwitch != null) mKeyboardSwitch.reset(this);
    resetCandidate();
    hideComposition();
    resetKeyboard();
    resetEffect();
  }

  public void initKeyboard() {
    reset();
    mNeedUpdateRimeOption = true; //?????????Rime.onMessage?????????set_option????????????
    bindKeyboardToInputView();
    updateComposing(); //???????????????????????????
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    mIntentReceiver.unregisterReceiver(this);
    self = null;
    if (mConfig.isDestroyOnQuit()) {
      Rime.destroy();
      mConfig.destroy();
      mConfig = null;
      System.exit(0); //????????????
    }
  }

  public static Trime getService() {
    if (self == null)
      self = new Trime();
    return self;
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    if (orientation != newConfig.orientation) {
      // Clear composing text and candidates for orientation change.
      escape();
      orientation = newConfig.orientation;
    }
    super.onConfigurationChanged(newConfig);
  }

  @Override
  public void onUpdateCursorAnchorInfo(CursorAnchorInfo cursorAnchorInfo) {
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      int i = cursorAnchorInfo.getComposingTextStart();
      if ((winPos == WindowsPositionType.LEFT || winPos == WindowsPositionType.LEFT_UP) && i >= 0) {
        mPopupRectF = cursorAnchorInfo.getCharacterBounds(i);
      } else {
        mPopupRectF.left = cursorAnchorInfo.getInsertionMarkerHorizontal();
        mPopupRectF.top = cursorAnchorInfo.getInsertionMarkerTop();
        mPopupRectF.right = mPopupRectF.left;
        mPopupRectF.bottom = cursorAnchorInfo.getInsertionMarkerBottom();
      }
      cursorAnchorInfo.getMatrix().mapRect(mPopupRectF);
      if (mCandidateContainer != null) {
        mFloatingWindowTimer.postShowFloatingWindow();
      }
    }
  }

  @Override
  public void onUpdateSelection(
      int oldSelStart,
      int oldSelEnd,
      int newSelStart,
      int newSelEnd,
      int candidatesStart,
      int candidatesEnd) {
    super.onUpdateSelection(
        oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
    if ((candidatesEnd != -1) && ((newSelStart != candidatesEnd) || (newSelEnd != candidatesEnd))) {
      //?????????????????????????????????
      if ((newSelEnd < candidatesEnd) && (newSelEnd >= candidatesStart)) {
        int n = newSelEnd - candidatesStart;
        Rime.RimeSetCaretPos(n);
        updateComposing();
      }
    }
    if ((candidatesStart == -1 && candidatesEnd == -1) && (newSelStart == 0 && newSelEnd == 0)) {
      //???????????????????????????
      escape();
    }
    // Update the caps-lock status for the current cursor position.
    updateCursorCapsToInputView();
  }

  @Override
  public void onComputeInsets(InputMethodService.Insets outInsets) {
    super.onComputeInsets(outInsets);
    outInsets.contentTopInsets = outInsets.visibleTopInsets;
  }

  @Override
  public View onCreateInputView() {
    mKeyboardView = (KeyboardView) getLayoutInflater().inflate(R.layout.input, (ViewGroup) null);
    mKeyboardView.setOnKeyboardActionListener(this);
    mKeyboardView.setShowHint(!Rime.getOption("_hide_key_hint"));
    return mKeyboardView;
  }

  void setShowComment(boolean show_comment) {
    if (mCandidateContainer != null) mCandidate.setShowComment(show_comment);
    mComposition.setShowComment(show_comment);
  }

  @TargetApi(VERSION_CODES.M)
  @Override
  public View onCreateCandidatesView() {
    LayoutInflater inflater = getLayoutInflater();
    mCompositionContainer =
        (LinearLayout) inflater.inflate(R.layout.composition_container, (ViewGroup) null);
    hideComposition();
    mFloatingWindow = new PopupWindow(mCompositionContainer);
    mFloatingWindow.setClippingEnabled(false);
    mFloatingWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
    mFloatingWindow.setWindowLayoutType(getDialogType());
    mComposition = (Composition) mCompositionContainer.getChildAt(0);

    mCandidateContainer =
        (FrameLayout) inflater.inflate(R.layout.candidate_container, (ViewGroup) null);
    mCandidate = (Candidate) mCandidateContainer.findViewById(R.id.candidate);
    mCandidate.setCandidateListener(this);
    setShowComment(!Rime.getOption("_hide_comment"));
    mCandidate.setVisibility(!Rime.getOption("_hide_candidate") ? View.VISIBLE : View.GONE);
    loadBackground();
    return mCandidateContainer;
  }

  /**
   * ??????????????????????????????????????????????????????????????????????????????
   *
   * @param attribute ????????????{@link EditorInfo ??????}
   * @param restarting ????????????
   */
  @Override
  public void onStartInput(EditorInfo attribute, boolean restarting) {
    super.onStartInput(attribute, restarting);
    canCompose = false;
    enterAsLineBreak = false;
    mTempAsciiMode = false;
    int inputType = attribute.inputType;
    int inputClass = inputType & InputType.TYPE_MASK_CLASS;
    int variation = inputType & InputType.TYPE_MASK_VARIATION;
    String keyboard = null;
    switch (inputClass) {
      case InputType.TYPE_CLASS_NUMBER:
      case InputType.TYPE_CLASS_PHONE:
      case InputType.TYPE_CLASS_DATETIME:
        mTempAsciiMode = true;
        keyboard = "number";
        break;
      case InputType.TYPE_CLASS_TEXT:
        if (variation == InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE) {
          // Make enter-key as line-breaks for messaging.
          enterAsLineBreak = true;
        }
        if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            || variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
            || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
            || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) {
          mTempAsciiMode = true;
          keyboard = ".ascii";
        } else {
          canCompose = true;
        }
        break;
      default: //0
        canCompose = (inputType > 0); //0x80000 FX??????????????????
        if (canCompose) break;
        return;
    }
    Rime.get(this);
    if (reset_ascii_mode) mAsciiMode = false;
    // Select a keyboard based on the input type of the editing field.
    mKeyboardSwitch.init(getMaxWidth()); //??????????????????????????????
    mKeyboardSwitch.setKeyboard(keyboard);
    updateAsciiMode();
    canCompose = canCompose && !Rime.isEmpty();
    if (!onEvaluateInputViewShown()) setCandidatesViewShown(canCompose); //?????????????????????????????????????????????
    if (mConfig.isShowStatusIcon()) showStatusIcon(R.drawable.ic_status); //???????????????
  }

  @Override
  public void showWindow(boolean showInput) {
    super.showWindow(showInput);
    updateComposing();
  }

  @Override
  public void onStartInputView(EditorInfo attribute, boolean restarting) {
    super.onStartInputView(attribute, restarting);
    bindKeyboardToInputView();
    setCandidatesViewShown(!Rime.isEmpty()); //?????????????????????????????????
  }

  @Override
  public void onFinishInputView(boolean finishingInput) {
    super.onFinishInputView(finishingInput);
    // Dismiss any pop-ups when the input-view is being finished and hidden.
    mKeyboardView.closing();
    escape();
    try {
      hideComposition();
    } catch (Exception e) {
      Log.info("Fail to show the PopupWindow.");
    }
  }

  private void bindKeyboardToInputView() {
    if (mKeyboardView != null) {
      // Bind the selected keyboard to the input view.
      Keyboard sk = (Keyboard) mKeyboardSwitch.getCurrentKeyboard();
      mKeyboardView.setKeyboard(sk);
      updateCursorCapsToInputView();
    }
  }

  //?????????????????????
  private void updateCursorCapsToInputView() {
    if (auto_caps.contentEquals("false") || Function.isEmpty(auto_caps)) return;
    if ((auto_caps.contentEquals("true") || Rime.isAsciiMode())
        && (mKeyboardView != null && !mKeyboardView.isCapsOn())) {
      InputConnection ic = getCurrentInputConnection();
      if (ic != null) {
        int caps = 0;
        EditorInfo ei = getCurrentInputEditorInfo();
        if ((ei != null) && (ei.inputType != EditorInfo.TYPE_NULL)) {
          caps = ic.getCursorCapsMode(ei.inputType);
        }
        mKeyboardView.setShifted(false, caps != 0);
      }
    }
  }

  private boolean isComposing() {
    return Rime.isComposing();
  }

  /**
   * ?????????????????????
   *
   * @param text ?????????????????????
   */
  public void commitText(CharSequence text, boolean isRime) {
    if (text == null) return;
    mEffect.speakCommit(text);
    InputConnection ic = getCurrentInputConnection();
    if (ic != null) {
      ic.commitText(text, 1);
      lastCommittedText = text.toString();
    }
    if (isRime && !isComposing()) Rime.commitComposition(); //????????????
    ic.clearMetaKeyStates(KeyEvent.getModifierMetaStateMask()); //????????????????????????????????????
  }

  public void commitText(CharSequence text) {
    commitText(text, true);
  }

  /**
   * ???Rime????????????????????????
   *
   * @return ??????????????????
   */
  private boolean commitText() {
    boolean r = Rime.getCommit();
    if (r) commitText(Rime.getCommitText());
    updateComposing();
    return r;
  }

  /**
   * ????????????????????????
   *
   * @return ??????????????????
   */
  private CharSequence getLastText() {
    InputConnection ic = getCurrentInputConnection();
    if (ic != null) {
      return ic.getTextBeforeCursor(1, 0);
    }
    return "";
  }

  private boolean handleAciton(int code, int mask) { //????????????
    InputConnection ic = getCurrentInputConnection();
    if (ic == null) return false;
    if (Event.hasModifier(mask, KeyEvent.META_CTRL_ON)) {
      // android.R.id. + selectAll, startSelectingText, stopSelectingText, cut, copy, paste, copyUrl, or switchInputMethod
      if (VERSION.SDK_INT >= VERSION_CODES.M) {
        if (code == KeyEvent.KEYCODE_V
            && Event.hasModifier(mask, KeyEvent.META_ALT_ON)
            && Event.hasModifier(mask, KeyEvent.META_SHIFT_ON)) {
          return ic.performContextMenuAction(android.R.id.pasteAsPlainText);
        }
        if (code == KeyEvent.KEYCODE_S && Event.hasModifier(mask, KeyEvent.META_ALT_ON)) {
          CharSequence cs = ic.getSelectedText(0);
          if (cs == null) ic.performContextMenuAction(android.R.id.selectAll);
          return ic.performContextMenuAction(android.R.id.shareText);
        }
        if (code == KeyEvent.KEYCODE_Y) return ic.performContextMenuAction(android.R.id.redo);
        if (code == KeyEvent.KEYCODE_Z) return ic.performContextMenuAction(android.R.id.undo);
      }
      if (code == KeyEvent.KEYCODE_A) return ic.performContextMenuAction(android.R.id.selectAll);
      if (code == KeyEvent.KEYCODE_X) return ic.performContextMenuAction(android.R.id.cut);
      if (code == KeyEvent.KEYCODE_C) return ic.performContextMenuAction(android.R.id.copy);
      if (code == KeyEvent.KEYCODE_V) return ic.performContextMenuAction(android.R.id.paste);
    }
    return false;
  }

  /**
   * ?????????{@link KeyEvent#KEYCODE_BACK Back???}??????????????????
   *
   * @param keyCode {@link KeyEvent#getKeyCode() ??????}
   * @return ???????????????Back?????????
   */
  private boolean handleBack(int keyCode) {
    if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
      requestHideSelf(0);
      return true;
    }
    return false;
  }

  private boolean onRimeKey(int[] event) {
    updateRimeOption();
    boolean ret = Rime.onKey(event);
    commitText();
    return ret;
  }

  private boolean composeEvent(KeyEvent event) {
    int keyCode = event.getKeyCode();
    if (keyCode == KeyEvent.KEYCODE_MENU) return false; //?????????Menu???
    if (keyCode >= Key.getSymbolStart()) return false; //???????????????????????????
    if (event.getRepeatCount() == 0 && KeyEvent.isModifierKey(keyCode)) {
      boolean ret =
          onRimeKey(
              Event.getRimeEvent(
                  keyCode, event.getAction() == KeyEvent.ACTION_DOWN ? 0 : Rime.META_RELEASE_ON));
      if (isComposing()) setCandidatesViewShown(canCompose); //????????????????????????????????????
      return ret;
    }
    if (!canCompose || Rime.isVoidKeycode(keyCode)) return false;
    return true;
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    Log.info("onKeyDown=" + event);
    if (composeEvent(event) && onKeyEvent(event)) return true;
    return super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    Log.info("onKeyUp=" + event);
    if (composeEvent(event) && keyUpNeeded) {
      onRelease(keyCode);
      return true;
    }
    return super.onKeyUp(keyCode, event);
  }

  /**
   * ????????????????????????
   *
   * @param event {@link KeyEvent ????????????}
   * @return ??????????????????
   */
  private boolean onKeyEvent(KeyEvent event) {
    Log.info("onKeyEvent=" + event);
    int keyCode = event.getKeyCode();
    boolean ret = true;
    keyUpNeeded = isComposing();
    if (!isComposing()) {
      if (keyCode == KeyEvent.KEYCODE_DEL
          || keyCode == KeyEvent.KEYCODE_ENTER
          || keyCode == KeyEvent.KEYCODE_ESCAPE
          || keyCode == KeyEvent.KEYCODE_BACK) {
        return false;
      }
    } else if (keyCode == KeyEvent.KEYCODE_BACK) {
      keyCode = KeyEvent.KEYCODE_ESCAPE; //???????????????
    }

    if (event.getAction() == KeyEvent.ACTION_DOWN
        && event.isCtrlPressed()
        && event.getRepeatCount() == 0
        && !KeyEvent.isModifierKey(keyCode)) {
      if (handleAciton(keyCode, event.getMetaState())) return true;
    }

    int c = event.getUnicodeChar();
    String s = String.valueOf((char) c);
    int mask = 0;
    int i = Event.getClickCode(s);
    if (i > 0) {
      keyCode = i;
    } else { //??????????????????
      mask = event.getMetaState();
    }
    ret = handleKey(keyCode, mask);
    if (isComposing()) setCandidatesViewShown(canCompose); //????????????????????????????????????
    return ret;
  }

  private IBinder getToken() {
    final Dialog dialog = getWindow();
    if (dialog == null) {
      return null;
    }
    final Window window = dialog.getWindow();
    if (window == null) {
      return null;
    }
    return window.getAttributes().token;
  }

  @Override
  public void onEvent(Event event) {
    String commit = event.getCommit();
    if (!Function.isEmpty(commit)) {
      commitText(commit, false); //???????????????????????????Rime
      return;
    }
    String s = event.getText();
    if (!Function.isEmpty(s)) {
      onText(s);
      return;
    }
    if (event.getCode() > 0) {
      int code = event.getCode();
      if (code == KeyEvent.KEYCODE_SWITCH_CHARSET) { //????????????
        Rime.toggleOption(event.getToggle());
        commitText();
      } else if (code == KeyEvent.KEYCODE_EISU) { //????????????
        mKeyboardSwitch.setKeyboard(event.getSelect());
        //????????????????????????????????????????????????Rime.onMessage??????
        mTempAsciiMode = mKeyboardSwitch.getAsciiMode(); //???????????????????????????????????????
        updateAsciiMode();
        bindKeyboardToInputView();
        updateComposing();
      } else if (code == KeyEvent.KEYCODE_LANGUAGE_SWITCH) { //???????????????
        IBinder imeToken = getToken();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (event.getSelect().contentEquals(".next")
            && VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
          imm.switchToNextInputMethod(imeToken, false);
        } else if (!Function.isEmpty(event.getSelect())) {
          imm.switchToLastInputMethod(imeToken);
        } else {
          ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker();
        }
      } else if (code == KeyEvent.KEYCODE_FUNCTION) { //???????????????
        String arg =
            String.format(
                event.getOption(),
                getActiveText(1),
                getActiveText(2),
                getActiveText(3),
                getActiveText(4));
        s = Function.handle(this, event.getCommand(), arg);
        if (s != null) {
          commitText(s);
          updateComposing();
        }
      } else if (code == KeyEvent.KEYCODE_VOICE_ASSIST) { //????????????
        new Speech(this).start();
      } else if (code == KeyEvent.KEYCODE_SETTINGS) { //??????
        switch (event.getOption()) {
          case "theme":
            showThemeDialog();
            break;
          case "color":
            showColorDialog();
            break;
          case "schema":
            showSchemaDialog();
            break;
          default:
            Function.showPrefDialog(this);
            break;
        }
      } else if (code == KeyEvent.KEYCODE_PROG_RED) { //????????????
        showColorDialog();
      } else {
        onKey(event.getCode(), event.getMask());
      }
    }
  }

  private boolean handleKey(int keyCode, int mask) { //?????????
    keyUpNeeded = false;
    if (onRimeKey(Event.getRimeEvent(keyCode, mask))) {
      keyUpNeeded = true;
      Log.info("Rime onKey");
    } else if (handleAciton(keyCode, mask)
        || handleOption(keyCode)
        || handleEnter(keyCode)
        || handleBack(keyCode)) {
      Log.info("Trime onKey");
    } else if (VERSION.SDK_INT >= VERSION_CODES.ICE_CREAM_SANDWICH_MR1
        && Function.openCategory(this, keyCode)) {
      Log.info("open category");
    } else {
      keyUpNeeded = true;
      return false;
    }
    return true;
  }

  private void sendKey(InputConnection ic, int key, int meta, int action) {
    long now = System.currentTimeMillis();
    if (ic != null) ic.sendKeyEvent(new KeyEvent(now, now, action, key, 0, meta));
  }

  private void sendKeyDown(InputConnection ic, int key, int meta) {
    sendKey(ic, key, meta, KeyEvent.ACTION_DOWN);
  }

  private void sendKeyUp(InputConnection ic, int key, int meta) {
    sendKey(ic, key, meta, KeyEvent.ACTION_UP);
  }

  private void sendDownUpKeyEvents(int keyCode, int mask) {
    InputConnection ic = getCurrentInputConnection();
    if (ic == null) return;
    int states =
        KeyEvent.META_FUNCTION_ON
            | KeyEvent.META_SHIFT_MASK
            | KeyEvent.META_ALT_MASK
            | KeyEvent.META_CTRL_MASK
            | KeyEvent.META_META_MASK
            | KeyEvent.META_SYM_ON;
    ic.clearMetaKeyStates(states);
    if (mKeyboardView != null && mKeyboardView.isShifted()) {
      if (keyCode == KeyEvent.KEYCODE_MOVE_HOME
          || keyCode == KeyEvent.KEYCODE_MOVE_END
          || keyCode == KeyEvent.KEYCODE_PAGE_UP
          || keyCode == KeyEvent.KEYCODE_PAGE_DOWN
          || (keyCode >= KeyEvent.KEYCODE_DPAD_UP && keyCode <= KeyEvent.KEYCODE_DPAD_RIGHT)) {
        mask |= KeyEvent.META_SHIFT_ON;
      }
    }

    if (Event.hasModifier(mask, KeyEvent.META_SHIFT_ON)) {
      sendKeyDown(
          ic, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON);
    }
    if (Event.hasModifier(mask, KeyEvent.META_CTRL_ON)) {
      sendKeyDown(
          ic, KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON);
    }
    if (Event.hasModifier(mask, KeyEvent.META_ALT_ON)) {
      sendKeyDown(ic, KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON);
    }
    sendKeyDown(ic, keyCode, mask);
    sendKeyUp(ic, keyCode, mask);
    if (Event.hasModifier(mask, KeyEvent.META_ALT_ON)) {
      sendKeyUp(ic, KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON);
    }
    if (Event.hasModifier(mask, KeyEvent.META_CTRL_ON)) {
      sendKeyUp(ic, KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON);
    }
    if (Event.hasModifier(mask, KeyEvent.META_SHIFT_ON)) {
      sendKeyUp(
          ic, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON);
    }
  }

  @Override
  public void onKey(int keyCode, int mask) { //?????????
    if (handleKey(keyCode, mask)) return;
    if (keyCode >= Key.getSymbolStart()) { //??????
      keyUpNeeded = false;
      commitText(Event.getDisplayLabel(keyCode));
      return;
    }
    keyUpNeeded = false;
    sendDownUpKeyEvents(keyCode, mask);
  }

  @Override
  public void onText(CharSequence text) { //?????????
    Log.info("onText=" + text);
    mEffect.speakKey(text);
    String s = text.toString();
    String t;
    Pattern p = Pattern.compile("^(\\{[^{}]+\\}).*$");
    Pattern pText = Pattern.compile("^((\\{Escape\\})?[^{}]+).*$");
    Matcher m;
    //Commit current composition before simulate key sequence
    if (!Rime.isValidText(text) && isComposing()) {
      Rime.commitComposition();
      commitText();
    }
    while (s.length() > 0) {
      m = pText.matcher(s);
      if (m.matches()){
        t =  m.group(1);
        Rime.onText(t);
        if (!commitText() && !isComposing()) commitText(t);
        updateComposing();
      } else {
        m = p.matcher(s);
        t = m.matches() ? m.group(1) : s.substring(0, 1);
        onEvent(new Event(t));
      }
      s = s.substring(t.length());
    }
    keyUpNeeded = false;
  }

  @Override
  public void onPress(int keyCode) {
    mEffect.vibrate();
    mEffect.playSound(keyCode);
    mEffect.speakKey(keyCode);
  }

  @Override
  public void onRelease(int keyCode) {
    if (keyUpNeeded) {
      onRimeKey(Event.getRimeEvent(keyCode, Rime.META_RELEASE_ON));
    }
  }

  @Override
  public void swipeLeft() {
    // no-op
  }

  @Override
  public void swipeRight() {
    // no-op
  }

  @Override
  public void swipeUp() {
    // no-op
  }

  /** ??????????????????????????????????????????????????? */
  @Override
  public void swipeDown() {
    //requestHideSelf(0);
  }

  @Override
  public void onPickCandidate(int i) {
    // Commit the picked candidate and suggest its following words.
    onPress(0);
    if (!isComposing()) {
      if (i >= 0) {
        Rime.toggleOption(i);
        updateComposing();
      }
    } else if (i == -4) onKey(KeyEvent.KEYCODE_PAGE_UP, 0);
    else if (i == -5) onKey(KeyEvent.KEYCODE_PAGE_DOWN, 0);
    else if (Rime.selectCandidate(i)) {
      commitText();
    }
  }

  /** ?????????????????????????????????????????????????????????/????????????/??????????????????????????????????????? */
  private String getActiveText(int type) {
    if (type == 2) return Rime.RimeGetInput(); //????????????
    String s = Rime.getComposingText(); //????????????
    if (Function.isEmpty(s)) {
      InputConnection ic = getCurrentInputConnection();
      CharSequence cs = ic.getSelectedText(0); //?????????
      if (type == 1 && Function.isEmpty(cs)) cs = lastCommittedText; //????????????
      if (Function.isEmpty(cs)) {
        cs = ic.getTextBeforeCursor(type == 4 ? 1024 : 1, 0); //????????????
      }
      if (Function.isEmpty(cs)) cs = ic.getTextAfterCursor(1024, 0); //?????????????????????
      if (cs != null) s = cs.toString();
    }
    return s;
  }

  /** ??????Rime???????????????????????????????????? */
  public void updateComposing() {
    InputConnection ic = getCurrentInputConnection();
    if (inlinePreedit != InlineModeType.INLINE_NONE) { //????????????
      String s = null;
      switch (inlinePreedit) {
        case INLINE_PREVIEW:
          s = Rime.getComposingText();
          break;
        case INLINE_COMPOSITION:
          s = Rime.getCompositionText();
          break;
        case INLINE_INPUT:
          s = Rime.RimeGetInput();
          break;
      }
      if (s == null) s = "";
      if (ic != null) {
        CharSequence cs = ic.getSelectedText(0);
        if (cs == null || !Function.isEmpty(s)) {
          // ???????????????????????????????????????????????????
          ic.setComposingText(s, 1);
        }
      }
    }
    if (ic != null && !isWinFixed() && VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP)
      cursorUpdated = ic.requestCursorUpdates(1);
    if (mCandidateContainer != null) {
      if (mShowWindow) {
        int start_num = mComposition.setWindow(min_length);
        mCandidate.setText(start_num);
        if (isWinFixed() || !cursorUpdated) mFloatingWindowTimer.postShowFloatingWindow();
      } else {
        mCandidate.setText(0);
      }
    }
    if (mKeyboardView != null) mKeyboardView.invalidateComposingKeys();
    if (!onEvaluateInputViewShown()) setCandidatesViewShown(canCompose); //????????????????????????????????????
  }

  public static int getDialogType() {
    int dialogType = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
    if (VERSION.SDK_INT >= VERSION_CODES.P) {
      dialogType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY; //Android P???AlertDialog?????????????????????
    }
    return dialogType;
  }

  private void showDialog(AlertDialog dialog) {
    Window window = dialog.getWindow();
    WindowManager.LayoutParams lp = window.getAttributes();
    if (mCandidateContainer != null) lp.token = mCandidateContainer.getWindowToken();
    lp.type = getDialogType();
    window.setAttributes(lp);
    window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
    dialog.show();
  }

  /** ??????{@link ColorPickerDialog ???????????????} */
  private void showColorDialog() {
      AlertDialog dialog = new ColorPickerDialog(this).getPickerDialog();
      showDialog(dialog);
  }

  /** ??????{@link SchemaPickerDialog ????????????????????????} */
  private void showSchemaDialog() {
      new SchemaPickerDialog(this, mCandidateContainer.getWindowToken()).show();
  }

  /** ??????{@link ThemePickerDialog ???????????????} */
  private void showThemeDialog() {
      new ThemePickerDialog(this, mCandidateContainer.getWindowToken()).show();
  }

  private boolean handleOption(int keyCode) {
    if (keyCode == KeyEvent.KEYCODE_MENU) {
      if (mOptionsDialog != null && mOptionsDialog.isShowing()) return true; //???????????????
      AlertDialog.Builder builder =
          new AlertDialog.Builder(this)
              .setTitle(R.string.ime_name)
              .setIcon(R.mipmap.ic_app_icon_round)
              .setCancelable(true)
              .setNegativeButton(
                  R.string.other_ime,
                  new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface di, int id) {
                      di.dismiss();
                      ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                          .showInputMethodPicker();
                    }
                  })
              .setPositiveButton(
                  R.string.set_ime,
                  new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface di, int id) {
                      Function.showPrefDialog(Trime.this); //????????????
                      di.dismiss();
                    }
                  });
      if (Rime.isEmpty()) builder.setMessage(R.string.no_schemas); //??????????????????
      else {
        builder.setNeutralButton(
            R.string.pref_schemas,
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface di, int id) {
                showSchemaDialog(); //????????????
                di.dismiss();
              }
            });
        builder.setSingleChoiceItems(
            Rime.getSchemaNames(),
            Rime.getSchemaIndex(),
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface di, int id) {
                di.dismiss();
                Rime.selectSchema(id); //????????????
                mNeedUpdateRimeOption = true;
              }
            });
      }
      mOptionsDialog = builder.create();
      showDialog(mOptionsDialog);
      return true;
    }
    return false;
  }

  /**
   * ?????????{@link KeyEvent#KEYCODE_ENTER ?????????}????????????
   *
   * @param keyCode {@link KeyEvent#getKeyCode() ??????}
   * @return ???????????????????????????
   */
  private boolean handleEnter(int keyCode) { //??????
    if (keyCode == KeyEvent.KEYCODE_ENTER) {
      if (enterAsLineBreak) {
        commitText("\n");
      } else {
        sendKeyChar('\n');
      }
      return true;
    }
    return false;
  }

  /** ??????PC?????????Esc???????????????????????????????????????????????? */
  private void escape() {
    if (isComposing()) onKey(KeyEvent.KEYCODE_ESCAPE, 0);
  }

  /** ??????Rime?????????????????? */
  private void updateAsciiMode() {
    Rime.setOption("ascii_mode", mTempAsciiMode || mAsciiMode);
  }
}
