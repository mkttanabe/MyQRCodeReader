/*
 * Copyright (C) 2008 ZXing authors
 * Copyright (C) 2013 KLab Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.klab.myqrcodereader;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import android.os.Handler;
import android.os.Looper;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.ResultPointCallback;

final class MyDecodeThread extends Thread {

    private final MyActivity activity;
    private final Map<DecodeHintType, Object> hints;
    private Handler handler;
    private final CountDownLatch handlerInitLatch;
    private Collection<BarcodeFormat> decodeFormats;

    MyDecodeThread(MyActivity activity, ResultPointCallback resultPointCallback) {

        this.activity = activity;
        handlerInitLatch = new CountDownLatch(1);
        hints = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);
        decodeFormats = EnumSet.noneOf(BarcodeFormat.class);
        decodeFormats.addAll(EnumSet.of(BarcodeFormat.QR_CODE));
        decodeFormats.addAll(EnumSet.of(
                BarcodeFormat.UPC_A,
                BarcodeFormat.UPC_E, 
                BarcodeFormat.EAN_13, 
                BarcodeFormat.EAN_8,
                BarcodeFormat.RSS_14));
        decodeFormats.addAll(EnumSet.of(
                BarcodeFormat.CODE_39,
                BarcodeFormat.CODE_93,
                BarcodeFormat.CODE_128,
                BarcodeFormat.ITF,
                BarcodeFormat.CODABAR));
        decodeFormats.addAll(EnumSet.of(BarcodeFormat.DATA_MATRIX));
        // DecodeHint に検出対象とするフォーマットとパターン検出点通知用コールバックを設定
        hints.put(DecodeHintType.POSSIBLE_FORMATS, decodeFormats);
        hints.put(DecodeHintType.NEED_RESULT_POINT_CALLBACK, resultPointCallback);
    }

    public Handler getHandler() {
        try {
            handlerInitLatch.await(); // countDown() 待ち
        } catch (InterruptedException ie) {
        }
        return handler;
    }

    @Override
    public void run() {
        Looper.prepare();
        // スレッドのハンドラを MyDecodeHandler に
        handler = new MyDecodeHandler(activity, hints);
        handlerInitLatch.countDown();
        Looper.loop();
    }

}
