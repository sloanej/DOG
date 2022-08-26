package org.projectdog;

import android.graphics.RectF;

import org.tensorflow.lite.examples.detection.tflite.Detector;

public class QueueableResult implements Comparable<QueueableResult> {
    public final RectF location;
    public final String title;
    public final int priority;
    public final String cueText;
    public QueueableResult(Detector.Recognition modelOutput, int priority, String cueText) {
        this.location = modelOutput.getLocation();
        this.title = modelOutput.getTitle();
        this.priority = priority;
        this.cueText = cueText;
    }

    public QueueableResult(String title, int priority, String cueText) {
        this.location = null;
        this.title = title;
        this.priority = priority;
        this.cueText = cueText;
    }

    @Override
    public int compareTo(QueueableResult detectorResult) {
        return this.priority - detectorResult.priority;
    }
}
