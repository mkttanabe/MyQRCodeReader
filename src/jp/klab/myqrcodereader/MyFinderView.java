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

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import com.google.zxing.ResultPoint;
import com.google.zxing.ResultPointCallback;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder
 * rectangle and partial transparency outside it, as well as the laser scanner
 * animation and result points.
 * 
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class MyFinderView extends View {

    private static final String TAG = "QR";
    private static final long ANIMATION_DELAY = 80L;
    private static final int CURRENT_POINT_OPACITY = 0xA0;
    private static final int MAX_RESULT_POINTS = 20;
    private static final int POINT_SIZE = 8;

    private final Paint paint;
    private final int laserColor;
    private final int resultPointColor;
    private List<ResultPoint> possibleResultPoints;
    private int colorPrev;

    // This constructor is used when the class is built from an XML resource.
    public MyFinderView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Initialize these once for performance rather than calling them every
        // time in onDraw().
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Resources resources = getResources();
        laserColor = resources.getColor(R.color.viewfinder_laser);
        resultPointColor = resources.getColor(R.color.possible_result_points);
        possibleResultPoints = new ArrayList<ResultPoint>(5);
        colorPrev = Color.WHITE;
    }

    @Override
    public void onDraw(Canvas canvas) {
        int width = this.getWidth(); // int width = canvas.getWidth();
        int height = this.getHeight(); //int height =canvas.getHeight();

        // フレーム枠線
        paint.setColor(laserColor);
        canvas.drawRect(0, 0, width, 2, paint);
        canvas.drawRect(0, 0, 2, height, paint);
        canvas.drawRect(0, height, width, height - 2, paint);
        canvas.drawRect(this.getWidth() - 2, 0, width, height, paint);

        List<ResultPoint> currentPossible = possibleResultPoints;
        if (!currentPossible.isEmpty()) {
            // ライブラリから通知されたパターン検出点を描画
            possibleResultPoints = new ArrayList<ResultPoint>(5);
            paint.setAlpha(CURRENT_POINT_OPACITY);
            paint.setColor(resultPointColor);
            synchronized (currentPossible) {
                for (ResultPoint point : currentPossible) {
                    canvas.drawCircle((int) point.getX(), (int) point.getY(),
                            POINT_SIZE, paint);
                }
            }
            // 検出点が 4 以上なら所定の文字列を表示
            if (currentPossible.size() >= 4) {
                colorPrev = (colorPrev == Color.RED) ? Color.WHITE : Color.RED;
                paint.setColor(colorPrev);
                int size = width / 8;
                paint.setTextSize(size);
                paint.setStyle(Paint.Style.FILL);
                paint.setTypeface(Typeface.DEFAULT_BOLD);
                paint.setAntiAlias(true);
                canvas.drawText("Warning!", width / 2 - size * 2, height / 2
                        + size / 4, paint);
            }
        }
        // onDraw() 再誘導
        postInvalidateDelayed(ANIMATION_DELAY, 0 - POINT_SIZE, 0 - POINT_SIZE,
                width + POINT_SIZE, height + POINT_SIZE);
        // invalidate();
    }

    public void addPossibleResultPoint(ResultPoint point) {
        List<ResultPoint> points = possibleResultPoints; // 参照渡しであることに注意
        synchronized (points) {
            points.add(point);
            int size = points.size();
            if (size > MAX_RESULT_POINTS) {
                // trim it
                points.subList(0, size - MAX_RESULT_POINTS / 2).clear();
            }
        }
    }
}

// ZXing ライブラリ内に宣言のある「ResultPointCallback」インターフェイスの実装クラス。
// MyActivity 内で生成した本クラスのインスタンスが
// MyDecodeThread ～ MyDecodeHandler 経由でライブラリ側へ渡され
// QR コード識別メソッドから ResultPoint （パターン識別点）情報を添えてコールバックされる
final class MyResultPointCallback implements ResultPointCallback {
    private MyFinderView mFinderView = null;

    MyResultPointCallback(MyFinderView v) {
        mFinderView = v;
    }

    @Override
    public void foundPossibleResultPoint(ResultPoint point) {
        mFinderView.addPossibleResultPoint(point);
    }
}
