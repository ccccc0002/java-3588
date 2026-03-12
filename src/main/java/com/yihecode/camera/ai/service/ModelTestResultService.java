package com.yihecode.camera.ai.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ModelTestResultService {

    public List<Map<String, Object>> flattenDetections(JSONArray predictArray) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (predictArray == null) {
            return result;
        }
        for (int i = 0; i < predictArray.size(); i++) {
            JSONObject root = toObject(predictArray.get(i));
            if (root == null) {
                continue;
            }
            collectFromRoot(result, root);
        }
        return result;
    }

    public String saveAnnotatedImage(String uploadDir,
                                     String sourceFile,
                                     List<Map<String, Object>> detections) throws IOException {
        if (StrUtil.isBlank(uploadDir) || StrUtil.isBlank(sourceFile)) {
            return null;
        }
        if (detections == null || detections.isEmpty()) {
            return null;
        }
        File source = new File(uploadDir + "/" + sourceFile);
        if (!source.exists()) {
            return null;
        }

        BufferedImage image = ImageIO.read(source);
        if (image == null) {
            return null;
        }

        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(244, 56, 56));
            graphics.setStroke(new BasicStroke(2.0f));
            graphics.setFont(new Font("SansSerif", Font.BOLD, 14));
            for (Map<String, Object> detection : detections) {
                int[] box = parsePosition(detection.get("position"));
                if (box == null) {
                    continue;
                }
                int x1 = Math.max(0, Math.min(box[0], image.getWidth() - 1));
                int y1 = Math.max(0, Math.min(box[1], image.getHeight() - 1));
                int x2 = Math.max(0, Math.min(box[2], image.getWidth() - 1));
                int y2 = Math.max(0, Math.min(box[3], image.getHeight() - 1));
                if (x2 <= x1 || y2 <= y1) {
                    continue;
                }
                graphics.drawRect(x1, y1, x2 - x1, y2 - y1);

                String label = resolveLabel(detection);
                if (StrUtil.isBlank(label)) {
                    continue;
                }
                FontMetrics metrics = graphics.getFontMetrics();
                int textW = metrics.stringWidth(label) + 8;
                int textH = metrics.getHeight();
                int textY = Math.max(textH, y1);

                graphics.setColor(new Color(244, 56, 56, 210));
                graphics.fillRect(x1, textY - textH, textW, textH);
                graphics.setColor(Color.WHITE);
                graphics.drawString(label, x1 + 4, textY - 4);
                graphics.setColor(new Color(244, 56, 56));
            }
        } finally {
            graphics.dispose();
        }

        String ext = FileUtil.extName(source.getName());
        String format = StrUtil.isBlank(ext) ? "jpg" : ext.toLowerCase();
        String targetName = FileUtil.mainName(source.getName()) + "_annotated_" + System.currentTimeMillis() + "." + format;
        File target = new File(source.getParentFile(), targetName);
        ImageIO.write(image, format, target);
        return toRelativePath(uploadDir, target);
    }

    private void collectFromRoot(List<Map<String, Object>> out, JSONObject root) {
        if (isDetectionObject(root)) {
            Map<String, Object> detection = normalizeDetection(root, null);
            if (detection != null) {
                out.add(detection);
            }
        }

        appendChildren(out, root, "data");
        appendChildren(out, root, "detections");
        appendChildren(out, root, "alerts");
    }

    private void appendChildren(List<Map<String, Object>> out, JSONObject root, String key) {
        JSONArray array = root.getJSONArray(key);
        if (array == null || array.isEmpty()) {
            return;
        }
        for (int i = 0; i < array.size(); i++) {
            JSONObject child = toObject(array.get(i));
            if (child == null) {
                continue;
            }
            Map<String, Object> detection = normalizeDetection(child, root);
            if (detection != null) {
                out.add(detection);
            }
        }
    }

    private Map<String, Object> normalizeDetection(JSONObject detectionObj, JSONObject root) {
        int[] box = parsePosition(firstNonNull(
                detectionObj.get("position"),
                detectionObj.get("bbox"),
                detectionObj.get("box")
        ));
        if (box == null) {
            return null;
        }

        Map<String, Object> detection = new LinkedHashMap<>();
        detection.put("position", List.of(box[0], box[1], box[2], box[3]));

        String type = firstNonBlank(
                detectionObj.getString("type"),
                detectionObj.getString("label"),
                detectionObj.getString("class_name"),
                detectionObj.getString("class")
        );
        if (StrUtil.isNotBlank(type)) {
            detection.put("type", type);
        }

        Double confidence = firstDouble(
                toDouble(detectionObj.get("confidence")),
                toDouble(detectionObj.get("score"))
        );
        if (confidence != null) {
            detection.put("confidence", confidence);
        }

        Long algorithmId = firstLong(
                detectionObj.getLong("algorithm_id"),
                root == null ? null : root.getLong("algorithm_id")
        );
        if (algorithmId != null) {
            detection.put("algorithm_id", algorithmId);
        }

        String algorithmName = firstNonBlank(
                detectionObj.getString("algorithm_name"),
                root == null ? null : root.getString("algorithm_name")
        );
        if (StrUtil.isNotBlank(algorithmName)) {
            detection.put("algorithm_name", algorithmName);
        }
        return detection;
    }

    private boolean isDetectionObject(JSONObject obj) {
        if (obj == null) {
            return false;
        }
        return obj.containsKey("position") || obj.containsKey("bbox") || obj.containsKey("box");
    }

    private JSONObject toObject(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JSONObject) {
            return (JSONObject) value;
        }
        try {
            return JSON.parseObject(JSON.toJSONString(value));
        } catch (Exception e) {
            return null;
        }
    }

    private int[] parsePosition(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof JSONArray) {
            JSONArray arr = (JSONArray) obj;
            return parseArray(arr.toJavaList(Object.class));
        }
        if (obj instanceof List) {
            return parseArray((List<?>) obj);
        }
        JSONObject object = toObject(obj);
        if (object == null) {
            return null;
        }

        if (object.containsKey("x") && object.containsKey("y") && object.containsKey("w") && object.containsKey("h")) {
            int x = object.getIntValue("x");
            int y = object.getIntValue("y");
            int w = object.getIntValue("w");
            int h = object.getIntValue("h");
            return new int[]{x, y, x + w, y + h};
        }
        if (object.containsKey("left") && object.containsKey("top") && object.containsKey("right") && object.containsKey("bottom")) {
            return new int[]{
                    object.getIntValue("left"),
                    object.getIntValue("top"),
                    object.getIntValue("right"),
                    object.getIntValue("bottom")
            };
        }
        return null;
    }

    private int[] parseArray(List<?> values) {
        if (values == null || values.size() < 4) {
            return null;
        }
        int x1 = toInt(values.get(0));
        int y1 = toInt(values.get(1));
        int x2 = toInt(values.get(2));
        int y2 = toInt(values.get(3));
        return new int[]{x1, y1, x2, y2};
    }

    private int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return (int) Math.round(Double.parseDouble(String.valueOf(value)));
        } catch (Exception ex) {
            return 0;
        }
    }

    private String resolveLabel(Map<String, Object> detection) {
        String type = detection == null ? null : String.valueOf(detection.getOrDefault("type", ""));
        String label = StrUtil.blankToDefault(type, "object");
        Double confidence = toDouble(detection == null ? null : detection.get("confidence"));
        if (confidence == null) {
            return label;
        }
        return label + " " + String.format("%.2f", confidence);
    }

    private String toRelativePath(String rootDir, File file) {
        try {
            Path root = Path.of(rootDir).toAbsolutePath().normalize();
            Path target = file.toPath().toAbsolutePath().normalize();
            if (target.startsWith(root)) {
                return root.relativize(target).toString().replace("\\", "/");
            }
            return target.toString().replace("\\", "/");
        } catch (Exception e) {
            return file.getPath().replace("\\", "/");
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Double firstDouble(Double first, Double second) {
        return first != null ? first : second;
    }

    private Long firstLong(Long first, Long second) {
        return first != null ? first : second;
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }
}
