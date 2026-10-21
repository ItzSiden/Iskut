package dev.itzsiden.skyvoxel.client.entity;

import dev.itzsiden.skyvoxel.engine.entity.Entity;
import dev.itzsiden.skyvoxel.engine.entity.EntityType;
import dev.itzsiden.skyvoxel.engine.entity.EntityWorld;
import dev.itzsiden.skyvoxel.engine.physics.StaticCollisionWorld;
import dev.itzsiden.skyvoxel.render.api.UiDrawList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Minecraft-style nameplates anchored to each model's actual world-space head point.
 * The UI is billboarded toward the viewport, but its anchor is projected afresh every
 * rendered frame. No camera-relative keyframes, easing or low-rate cached screen positions.
 */
public final class EntityHologramRenderer {
    private static final float MAX_DISTANCE = 48.0f;
    private EntityHologramRenderer() { }

    public static UiDrawList build(EntityWorld world, float[] vp, int width, int height,
                                   float cameraX, float cameraY, float cameraZ, StaticCollisionWorld collisionWorld) {
        UiDrawList.Builder draw = new UiDrawList.Builder();
        List<Label> labels = new ArrayList<>();
        for (Entity entity : world.entities()) {
            if (!entity.showNameplate()) continue;

            // World-space anchor: directly over the highest authored model part (hair/crown/etc.).
            float x = entity.x();
            float y = entity.y() + entity.model().headAnchorHeight() + 0.12f;
            float z = entity.z();
            float dx = x - cameraX, dy = y - cameraY, dz = z - cameraZ;
            float distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared > MAX_DISTANCE * MAX_DISTANCE) continue;
            double distance = Math.sqrt(distanceSquared);
            if (collisionWorld != null && distance > 1.0) {
                var obstruction = collisionWorld.raycast(cameraX, cameraY, cameraZ, dx, dy, dz,
                    Math.max(0.0, distance - 0.18));
                if (obstruction.isPresent()) continue;
            }

            float clipX = vp[0] * x + vp[4] * y + vp[8] * z + vp[12];
            float clipY = vp[1] * x + vp[5] * y + vp[9] * z + vp[13];
            float clipZ = vp[2] * x + vp[6] * y + vp[10] * z + vp[14];
            float clipW = vp[3] * x + vp[7] * y + vp[11] * z + vp[15];
            if (clipW <= 0.05f || clipZ < -clipW || clipZ > clipW) continue;
            float nx = clipX / clipW, ny = clipY / clipW;
            if (nx < -1.15f || nx > 1.15f || ny < -1.15f || ny > 1.15f) continue;
            float screenX = (nx * 0.5f + 0.5f) * width;
            float screenY = (0.5f - ny * 0.5f) * height;
            labels.add(new Label(entity, screenX, screenY, distanceSquared));
        }

        // Far-to-near painter's order; nearest labels stay most legible when they overlap.
        labels.sort(Comparator.comparingDouble(Label::distanceSquared).reversed());
        for (Label label : labels) drawLabel(draw, label);
        return draw.build();
    }

    private static void drawLabel(UiDrawList.Builder draw, Label label) {
        Entity entity = label.entity();
        float distance = (float) Math.sqrt(label.distanceSquared());
        float scale = Math.max(0.72f, Math.min(1.0f, 1.04f - distance * 0.008f));
        float x = label.x(), anchorY = label.y();
        String subtitle = entity.subtitle().strip();
        String name = entity.displayName();
        boolean health = entity.showHealthBar();
        float padX = 5.0f * scale;
        float subtitleH = subtitle.isBlank() ? 0.0f : 10.0f * scale;
        float nameH = 14.0f * scale;
        float gap = subtitle.isBlank() ? 0.0f : 1.0f * scale;
        float healthGap = health ? 3.0f * scale : 0.0f;
        float barH = health ? 3.0f * scale : 0.0f;
        float totalH = subtitleH + gap + nameH + healthGap + barH;
        float subtitleWidth = subtitle.length() * 5.4f * scale;
        float nameWidth = name.length() * 7.0f * scale;
        float plateWidth = Math.max(subtitleWidth, nameWidth) + padX * 2.0f;
        float top = anchorY - totalH - 2.0f * scale;

        // Plain translucent black plate, like classic in-world nameplates. No neon border/card.
        draw.fillRect(x - plateWidth * 0.5f, top, plateWidth, totalH, 0x99000000);
        float textY = top;
        if (!subtitle.isBlank()) {
            draw.text(x, textY, subtitle.toUpperCase(java.util.Locale.ROOT),
                9.0f * scale, accent(entity.type()), true, false, UiDrawList.TextAlign.CENTER);
            textY += subtitleH + gap;
        }
        draw.text(x, textY, name, 12.0f * scale, 0xFFF4F6F8,
            true, true, UiDrawList.TextAlign.CENTER);
        if (health) {
            float barWidth = plateWidth - padX * 2.0f;
            float barY = top + subtitleH + gap + nameH + healthGap;
            draw.fillRect(x - barWidth * 0.5f, barY, barWidth, barH, 0xFF30363D);
            float ratio = entity.healthRatio();
            int healthColor = ratio > 0.5f ? 0xFF58C878 : (ratio > 0.25f ? 0xFFE4B74E : 0xFFE45B63);
            draw.fillRect(x - barWidth * 0.5f, barY, barWidth * ratio, barH, healthColor);
        }
    }

    private static int accent(EntityType type) {
        return switch (type) {
            case PLAYER -> 0xFF8DD9EA;
            case NPC -> 0xFFE8C77D;
            case MOB -> 0xFFE8877E;
            case PET -> 0xFF8DCEA5;
            case ITEM -> 0xFFA6BDD9;
        };
    }

    private record Label(Entity entity, float x, float y, float distanceSquared) { }
}
