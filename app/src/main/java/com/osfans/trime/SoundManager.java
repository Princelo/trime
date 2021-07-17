package com.osfans.trime;

import java.util.HashMap;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioManager;
import android.media.SoundPool;

import android.os.Build;

/**
 * @author Liabiao
 * @description 该类负责按键音效方案加载，音量大小控制功能
 */

public final class SoundManager {
    final private SoundPool mSoundPool;// 用来创建和播放声音
    final private SoundPool mSoundPoolDeletes;// 用来创建和播放声音
    final private SoundPool mSoundPoolSpaces;// 用来创建和播放声音
    final private HashMap<Integer, Integer> mSoundPoolMap;// 用来存储多声道
    final private HashMap<Integer, Integer> mSoundPoolDeletesMap;// 用来存储多声道
    final private HashMap<Integer, Integer> mSoundPoolSpacesMap;// 用来存储多声道
    final private AudioManager mAudioManager;// 服务句柄
    private final float maxVolume = 1;// 音量最大值
    private float mVolume;// 音量大小，范围为1-100
    private int mSoundScheme;// 声音方案

    // 声音方案
    private final static int SOUND_MODERN = 0;
    private final static int SOUND_TRADITIONAL = 1;
    private final static int SOUND_BLIP = 2;
    private final static int SOUND_ANDROID = 3;
    private final static int SOUND_XPERIA = 4;
    private final static int SOUND_SYSTEM = 5;

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public SoundManager(final Context context) {
        mSoundPool = new SoundPool(4, AudioManager.STREAM_SYSTEM, 0);// 4个声道
        mSoundPoolDeletes = new SoundPool(4, AudioManager.STREAM_SYSTEM, 0);// 4个声道
        mSoundPoolSpaces = new SoundPool(4, AudioManager.STREAM_SYSTEM, 0);// 4个声道
        mSoundPoolMap = new HashMap<>();
        mSoundPoolDeletesMap = new HashMap<>();
        mSoundPoolSpacesMap = new HashMap<>();
        mAudioManager = (AudioManager) context
                .getSystemService(Context.AUDIO_SERVICE);
        mSoundScheme = 0;
        this.addSound(context, SOUND_XPERIA, R.raw.res_raw_keypress_sound);
        this.addSound(context, SOUND_MODERN, R.raw.fx_modern_standard);
        this.addSoundDeletes(context, SOUND_MODERN, R.raw.fx_modern_delete);
        this.addSoundSpaces(context, SOUND_MODERN, R.raw.fx_modern_spacebar);
        this.addSound(context, SOUND_BLIP, R.raw.fx_blip_standard);
        this.addSoundDeletes(context, SOUND_BLIP, R.raw.fx_blip_delete);
        this.addSoundSpaces(context, SOUND_BLIP, R.raw.fx_blip_spacebar);
        this.addSound(context, SOUND_TRADITIONAL, R.raw.fx_traditional_standard);
        this.addSoundDeletes(context, SOUND_TRADITIONAL, R.raw.fx_traditional_function);
        this.addSoundSpaces(context, SOUND_TRADITIONAL, R.raw.fx_traditional_function);
        this.addSound(context, SOUND_ANDROID, R.raw.fx_android_standard);
        this.addSoundDeletes(context, SOUND_ANDROID, R.raw.fx_android_function);
        this.addSoundSpaces(context, SOUND_ANDROID, R.raw.fx_android_function);
    }

    // 初始化函数,参数依次为：上下文句柄、音量强度、音效方案
    public final void setVolume(final int vol) {
        final float percent = vol / 100.0f;// 音量百分比
        mVolume = maxVolume * percent;
    }

    public final void setVolumeFloat(final float val) {
        this.mVolume = val;
    }

    public final void setSoundType(final int soundType) {
        mSoundScheme = soundType;// 声音方案
    }

    // 该函数负责添加声道
    private void addSound(final Context context, final int index,
                          final int SoundID) {
        mSoundPoolMap.put(index, mSoundPool.load(context, SoundID, 1));// 加载默认按键音效
    }
    private void addSoundDeletes(final Context context, final int index,
                                 final int SoundID) {
        mSoundPoolDeletesMap.put(index, mSoundPoolDeletes.load(context, SoundID, 1));// 加载默认按键音效
    }
    private void addSoundSpaces(final Context context, final int index,
                                final int SoundID) {
        mSoundPoolSpacesMap.put(index, mSoundPoolSpaces.load(context, SoundID, 1));// 加载默认按键音效
    }

    // 播放声音函数
    public final void playSound() {
        if (mSoundScheme != SOUND_SYSTEM) {
            mSoundPool.play(mSoundPoolMap.get(mSoundScheme),
                    mVolume, mVolume, 1, 0, 1f);
        } else {
            mAudioManager.playSoundEffect(
                    AudioManager.FX_KEYPRESS_STANDARD, mVolume);
        }
    }

    public final void playEnterSound() {
        if (mSoundScheme != SOUND_SYSTEM && mSoundScheme != SOUND_XPERIA) {
            mSoundPool.play(mSoundPoolMap.get(mSoundScheme),
                    mVolume, mVolume, 1, 0, 1f);
        } else {
            mAudioManager.playSoundEffect(
                    AudioManager.FX_KEYPRESS_RETURN, mVolume);
        }
    }

    public final void playSpaceSound() {
        if (mSoundScheme != SOUND_SYSTEM && mSoundScheme != SOUND_XPERIA) {
            mSoundPoolSpaces.play(mSoundPoolSpacesMap.get(mSoundScheme),
                    mVolume, mVolume, 1, 0, 1f);
        } else {
            mAudioManager.playSoundEffect(
                    AudioManager.FX_KEYPRESS_SPACEBAR, mVolume);
        }
    }

    public final void playBackspaceSound() {
        if (mSoundScheme != SOUND_SYSTEM && mSoundScheme != SOUND_XPERIA) {
            mSoundPoolDeletes.play(mSoundPoolDeletesMap.get(mSoundScheme),
                    mVolume, mVolume, 1, 0, 1f);
        } else {
            mAudioManager.playSoundEffect(
                    AudioManager.FX_KEYPRESS_DELETE, mVolume);
        }
    }
}
