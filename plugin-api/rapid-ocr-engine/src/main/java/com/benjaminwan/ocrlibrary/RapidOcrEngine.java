package com.benjaminwan.ocrlibrary;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RapidOcrEngine {

    private final OcrEngine engine;

    public RapidOcrEngine(Context context) {
        engine = new OcrEngine(context);
    }

    public Result detect(
            Bitmap input,
            Bitmap output,
            int padding,
            int maxSideLen,
            float boxScoreThresh,
            float boxThresh,
            float unClipRatio,
            boolean doAngle,
            boolean mostAngle
    ) {
        OcrResult raw = engine.detect(
                input,
                output,
                padding,
                maxSideLen,
                boxScoreThresh,
                boxThresh,
                unClipRatio,
                doAngle,
                mostAngle
        );
        return Result.from(raw);
    }

    public static final class Result {
        private static final Result EMPTY = new Result(Collections.emptyList());

        private final List<Block> textBlocks;

        private Result(List<Block> textBlocks) {
            this.textBlocks = textBlocks;
        }

        public List<Block> getTextBlocks() {
            return textBlocks;
        }

        private static Result from(OcrResult raw) {
            if (raw == null) {
                return EMPTY;
            }
            List<TextBlock> textBlocks = raw.getTextBlocks();
            if (textBlocks == null || textBlocks.isEmpty()) {
                return EMPTY;
            }
            List<Block> blocks = new ArrayList<>();
            for (TextBlock block : textBlocks) {
                if (block != null) {
                    blocks.add(Block.from(block));
                }
            }
            return blocks.isEmpty() ? EMPTY : new Result(Collections.unmodifiableList(blocks));
        }
    }

    public static final class Block {
        private final String text;
        private final float confidence;
        private final Rect bounds;

        private Block(String text, float confidence, Rect bounds) {
            this.text = text;
            this.confidence = confidence;
            this.bounds = bounds;
        }

        public String getText() {
            return text;
        }

        public float getConfidence() {
            return confidence;
        }

        public Rect getBounds() {
            return new Rect(bounds);
        }

        private static Block from(TextBlock block) {
            String text = block.getText();
            return new Block(
                    text == null ? "" : text,
                    block.getBoxScore(),
                    boundsOf(block)
            );
        }

        private static Rect boundsOf(TextBlock block) {
            List<Point> points = block.getBoxPoint();
            if (points == null || points.isEmpty()) {
                return new Rect();
            }
            Point first = points.get(0);
            int left = first.getX();
            int top = first.getY();
            int right = left;
            int bottom = top;
            for (Point point : points) {
                left = Math.min(left, point.getX());
                top = Math.min(top, point.getY());
                right = Math.max(right, point.getX());
                bottom = Math.max(bottom, point.getY());
            }
            return new Rect(left, top, right, bottom);
        }
    }
}
