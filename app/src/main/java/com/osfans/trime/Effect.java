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
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.view.KeyEvent;
import java.util.Locale;
import android.os.VibrationEffect;
import android.os.Build;

/** 處理按鍵聲音、震動、朗讀等效果 */
class Effect {
  private static final int MAX_VOLUME = 101; //100%音量時只響一下，暫從100改成101
  private int duration = 10;
  private long durationLong;
  private VibrationEffect vibrationeffect;
  private int amplitude = -1;
  private int volume = 100;
  private float volumeFloat;

  private final Context context;

  private boolean vibrateOn;
  private Vibrator vibrator;
  private boolean soundOn;
  private AudioManager audioManager;
  private SoundManager soundManager;
  private boolean isSpeakCommit, isSpeakKey;
  private TextToSpeech mTTS;

  public Effect(Context context) {
    this.context = context;
  }

  public void reset() {
    SharedPreferences pref = Function.getPref(context);
    duration = pref.getInt("key_vibrate_duration", duration);
    durationLong = duration * 1L;
    amplitude = pref.getInt("key_vibrate_amplitude",amplitude);
    vibrateOn = pref.getBoolean("key_vibrate", false) && (duration > 0);
    if(vibrateOn) {
      if  (vibrator == null) {
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrationeffect = VibrationEffect.createOneShot(durationLong, (amplitude == 0) ? VibrationEffect.DEFAULT_AMPLITUDE : amplitude);
        }
    }

    volume = pref.getInt("key_sound_volume", volume);
    volumeFloat = (float) (1 - (Math.log(MAX_VOLUME - volume) / Math.log(MAX_VOLUME)));
    soundOn = pref.getBoolean("key_sound", false);
    audioManager = null;
    if (soundOn) {
      audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
      soundManager = new SoundManager(context);
      soundManager.setVolumeFloat(volumeFloat);
      soundManager.setSoundType(Integer.parseInt(pref.getString("sound_effect", "0")));
    }

    isSpeakCommit = pref.getBoolean("speak_commit", false);
    isSpeakKey = pref.getBoolean("speak_key", false);
    if (mTTS == null && (isSpeakCommit || isSpeakKey)) {
      mTTS =
          new TextToSpeech(
              context,
              new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                  //初始化結果
                }
              });
    }
  }

  public void vibrate() {
    if (vibrateOn && (vibrator != null)) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && (vibrationeffect != null))
        vibrator.vibrate(vibrationeffect);
      else
        vibrator.vibrate(durationLong);// deprecated in api level 26
    }
  }

  public void playSound(final int code) {
    if (soundOn && (audioManager != null)) {
      switch (code) {
        case KeyEvent.KEYCODE_DEL:
          soundManager.playBackspaceSound();
          break;
        case KeyEvent.KEYCODE_ENTER:
          soundManager.playEnterSound();
          break;
        case KeyEvent.KEYCODE_SPACE:
          soundManager.playSpaceSound();
          break;
        default:
          soundManager.playSound();
          break;
      }
    }
  }

  public void setLanguage(Locale loc) {
    if (mTTS != null) mTTS.setLanguage(loc);
  }

  private void speak(CharSequence text) {
    if (text != null && mTTS != null) mTTS.speak(text.toString(), TextToSpeech.QUEUE_FLUSH, null);
  }

  public void speakCommit(CharSequence text) {
    if (isSpeakCommit) speak(text);
  }

  public void speakKey(CharSequence text) {
    if (isSpeakKey) speak(text);
  }

  public void speakKey(int code) {
    if (code <= 0) return;
    String text =
        KeyEvent.keyCodeToString(code)
            .replace("KEYCODE_", "")
            .replace("_", " ")
            .toLowerCase(Locale.getDefault());
    speakKey(text);
  }

  public void destory() {
    if (mTTS != null) {
      mTTS.stop();
      mTTS.shutdown();
      mTTS = null;
    }
  }
}
