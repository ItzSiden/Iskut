package dev.itzsiden.skyvoxel.client.items;

import dev.itzsiden.skyvoxel.engine.entity.EntityModel;
import dev.itzsiden.skyvoxel.engine.items.ItemDefinition;
import dev.itzsiden.skyvoxel.engine.items.ItemDefinitionRegistry;
import dev.itzsiden.skyvoxel.engine.items.ItemEquipmentSlot;
import dev.itzsiden.skyvoxel.engine.items.ItemStack;
import dev.itzsiden.skyvoxel.engine.scene.Material;
import dev.itzsiden.skyvoxel.engine.scene.Mesh;
import dev.itzsiden.skyvoxel.engine.scene.RenderObject;
import dev.itzsiden.skyvoxel.engine.scene.Scene;
import dev.itzsiden.skyvoxel.engine.scene.Transform;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Camera-space first-person hand/item render. Item geometry is the same .vmodel used by icons and drops. */
public final class HeldItemViewModel {
    // Rest pose of the arm in camera space.
    private static final float ARM_X = 0.50f, ARM_Y = -0.32f, ARM_DEPTH = 0.88f, ARM_SCALE = 1.02f;
    private static final float ARM_YAW  = (float) Math.toRadians(14.0);
    private static final float ARM_ROLL = (float) Math.toRadians(24.0);
    // The swing is timed here, not by the caller: one click = exactly one swing.
    private static final float SWING_SECONDS = 0.32f;
    private boolean swinging;
    private boolean swingArmed = true;
    private long swingStartNanos;
    // Shoulder in arm-local space: below the sleeve's lower end, so it is off-screen.
    private static final float SHOULDER_Y = -1.15f;

    private final Mesh cubeMesh;
    private final ItemVisualRegistry visuals;
    private final Scene scene = new Scene();
    private final List<PartBinding> arm = new ArrayList<>();
    private final List<PartBinding> held = new ArrayList<>();
    private final float[] anim = new float[9];
    private final float[] shoulder = new float[3];
    private final float[] palm = new float[3];
    private String currentItemId = "";
    // Model-space point that should sit in the palm. Prefer semantic grip/handle parts.
    private float heldPivotX, heldPivotY, heldPivotZ;
    private boolean hasHeldPivot;

    private record PartBinding(EntityModel.Part part, RenderObject object, float scale, float localRoll, float[] matrix) { }

    public HeldItemViewModel(Mesh cubeMesh, ItemVisualRegistry visuals) {
        this.cubeMesh = Objects.requireNonNull(cubeMesh, "cubeMesh");
        this.visuals = Objects.requireNonNull(visuals, "visuals");
        createArm();
    }

    public Scene scene() { return scene; }

    private static float rad(float deg) { return (float) Math.toRadians(deg); }

    public void update(
            ItemStack selected,
            ItemDefinitionRegistry catalog,
            float camX, float camY, float camZ,
            float yaw, float pitch,
            float walkBob, float swing
    ) {
        String id = selected == null ? "" : selected.itemId();
        ItemDefinition definition = id.isEmpty() ? null : catalog.find(id).orElse(null);
        if (!id.equals(currentItemId)) rebuildHeld(id, definition);

        Basis basis = Basis.from(yaw, pitch);
        float bob = Math.max(-0.018f, Math.min(0.018f, walkBob));
        // Start one swing when the input rises from idle. The shape of the caller's value
        // (ramp, bell curve, pulse) no longer matters, so it can't cause a double swing.
        float raw = Math.max(0.0f, Math.min(1.0f, swing));
        long now = System.nanoTime();
        if (raw < 0.02f) {
            swingArmed = true;
        } else if (swingArmed && !swinging) {
            swinging = true;
            swingArmed = false;
            swingStartNanos = now;
        }
        float a = 0.0f; // linear swing progress 0 -> 1
        if (swinging) {
            a = (now - swingStartNanos) / 1.0e9f / SWING_SECONDS;
            if (a >= 1.0f) {
                swinging = false;
                a = 0.0f;
            }
        }

        // ---- Single swing: fast slash out, slower return. One forward, one back. ----
        float windup = 0.0f; // wind-up removed: it read as an extra movement
        float strike = (float) Math.sin(Math.sqrt(a) * Math.PI);   // fast out, slow back
        float twist  = (float) Math.sin(a * a * Math.PI);          // peaks late

        // Rotation around the SHOULDER (camera space). Negative pitch = hand goes forward/down.
        float swPitch = -strike * rad(52) + windup * rad(9);
        float swRoll  =  strike * rad(24) - windup * rad(7);       // sweeps toward screen center
        float swYaw   = -twist  * rad(16);
        buildRotation(anim, swYaw, swPitch, swRoll);

        // Small translation on top (camera space: +x right, +y up, +z toward camera)
        float tx = -strike * 0.07f + windup * 0.02f;
        float ty = bob + strike * 0.03f - windup * 0.02f;
        float tz = -strike * 0.09f + windup * 0.05f;

        // Shoulder + palm positions at rest (camera-local)
        restPoint(shoulder, ARM_X, ARM_Y, ARM_DEPTH, ARM_YAW, ARM_ROLL, ARM_SCALE, 0f, SHOULDER_Y, 0f);
        float hover = (float) Math.sin(now / 1.0e9 * 2.4) * 0.012f;
        restPoint(palm,     ARM_X, ARM_Y, ARM_DEPTH, ARM_YAW, ARM_ROLL, ARM_SCALE, 0f, 0.12f + hover, 0.035f);

        updateParts(arm, basis, camX, camY, camZ,
            ARM_X, ARM_Y, ARM_DEPTH,
            ARM_YAW, ARM_ROLL, ARM_SCALE,
            0f, 0f, 0f,
            anim, shoulder[0], shoulder[1], shoulder[2], tx, ty, tz);

        if (!held.isEmpty()) {
            // Item sits in the palm, then gets the exact same shoulder swing as the arm.
            float flick = heldRoll(definition) - strike * 0.35f;           // extra wrist flick
            boolean isWeapon = definition != null && definition.equipmentSlot() == ItemEquipmentSlot.WEAPON;
            float sway = isWeapon ? 0.0f : (float) Math.sin(now / 1.0e9 * 1.3) * 0.18f;
            float yawOff = heldYaw(definition) + twist * 0.10f + sway;
            float nudgeX = 0.0f;
            float nudgeY = 0.0f;

            updateParts(held, basis, camX, camY, camZ,
                palm[0] + nudgeX, palm[1] + nudgeY, -palm[2],
                yawOff, flick, itemScale(definition),
                heldPivotX, heldPivotY, heldPivotZ,
                anim, shoulder[0], shoulder[1], shoulder[2], tx, ty, tz);
        }
    }

    private void rebuildHeld(String itemId, ItemDefinition definition) {
        for (PartBinding binding : held) scene.remove(binding.object().id());
        held.clear();
        currentItemId = itemId;
        if (definition == null || !shouldShowInHand(definition)) return;
        EntityModel model = visuals.model(itemId).orElse(null);
        if (model == null) return;
        heldPivotX = heldPivotY = heldPivotZ = 0.0f;
        hasHeldPivot = false;
        EntityModel.Part grip = findGripPart(model);
        if (grip != null) {
            heldPivotX = grip.offsetX();
            heldPivotY = grip.offsetY();
            heldPivotZ = grip.offsetZ();
            hasHeldPivot = true;
        }
        float size = 1.0f;
        int index = 0;
        for (EntityModel.Part part : model.parts()) {
            RenderObject object = new RenderObject("viewmodel/item/" + itemId + "/" + index++ + "/" + part.id(),
                part.mesh(), part.material(), new Transform()).setCollidable(false);
            scene.add(object);
            held.add(new PartBinding(part, object, size, 0.0f, new float[16]));
        }
    }

    private void createArm() {
        Material sleeve = new Material("iskut_arc_sleeve", 0.14f, 0.19f, 0.27f);
        Material cuff = new Material("iskut_arc_cuff", 0.22f, 0.30f, 0.40f);
        Material plate = new Material("iskut_arc_plate", 0.27f, 0.36f, 0.48f);
        Material glow = new Material("iskut_arc_glow", 0.30f, 0.85f, 0.95f);

        // Straight forearm. The lower end runs off the bottom of the screen.
        addArmPart("sleeve", sleeve,
            0.000f, -0.570f, 0.000f,
            0.200f, 1.000f, 0.200f,
            0.0f);

        // Glowing cyan band.
        addArmPart("trim", glow,
            0.000f, -0.170f, 0.000f,
            0.212f, 0.020f, 0.212f,
            0.0f);

        // Cuff.
        addArmPart("cuff", cuff,
            0.000f, -0.100f, 0.000f,
            0.215f, 0.060f, 0.215f,
            0.0f);

        // Wrist plate, narrower than before so the arm flows into the hand.
        addArmPart("wrist_plate", plate,
            0.000f, -0.062f, 0.000f,
            0.225f, 0.034f, 0.225f,
            0.0f);

        // Open palm, longer than it is wide so it reads as a hand and not a tray.
        addArmPart("palm", plate,
            0.000f, -0.040f, 0.035f,
            0.200f, 0.040f, 0.230f,
            0.0f);

        // Finger plate: ONE tapered slab (no separate fingers) that continues the palm.
        addArmPart("finger_plate", plate,
            0.000f, -0.047f, 0.215f,
            0.170f, 0.026f, 0.130f,
            0.0f);
        addArmPart("finger_tip", plate,
            0.000f, -0.050f, 0.295f,
            0.135f, 0.020f, 0.030f,
            0.0f);

        // Thumb plate on the side.
        addArmPart("thumb_plate", plate,
            -0.125f, -0.046f, 0.050f,
            0.060f, 0.030f, 0.110f,
            0.0f);

        // Glowing emitter in the middle of the palm.
        addArmPart("emitter", glow,
            0.000f, -0.0185f, 0.035f,
            0.110f, 0.006f, 0.110f,
            0.0f);
    }

    private void addArmPart(String id, Material material, float x, float y, float z,
                            float w, float h, float d, float angleDegrees) {
        EntityModel.Part part = new EntityModel.Part(id, cubeMesh, material, x, y, z, w, h, d, 0.0f);
        RenderObject object = new RenderObject("viewmodel/arm/" + id, part.mesh(), part.material(), new Transform())
            .setCollidable(false);
        scene.add(object);
        arm.add(new PartBinding(part, object, 1.0f, (float) Math.toRadians(angleDegrees), new float[16]));
    }

    /** Camera-local position (x right, y up, z backward) of an arm-local point at rest. */
    private static void restPoint(float[] out, float baseX, float baseY, float baseDepth,
                                  float yaw, float roll, float scale,
                                  float ox, float oy, float oz) {
        ox *= scale; oy *= scale; oz *= scale;
        float cr = (float) Math.cos(roll), sr = (float) Math.sin(roll);
        float cy = (float) Math.cos(yaw),  sy = (float) Math.sin(yaw);
        out[0] = baseX + cr * (cy * ox + sy * oz) - sr * oy;
        out[1] = baseY + sr * (cy * ox + sy * oz) + cr * oy;
        out[2] = -baseDepth + (-sy * ox + cy * oz);
    }

    /** A = Rz(roll) * Rx(pitch) * Ry(yaw), row-major. */
    private static void buildRotation(float[] a, float yaw, float pitch, float roll) {
        float cx = (float) Math.cos(pitch), sx = (float) Math.sin(pitch);
        float cy = (float) Math.cos(yaw),   sy = (float) Math.sin(yaw);
        float cz = (float) Math.cos(roll),  sz = (float) Math.sin(roll);
        a[0] = cz * cy - sz * sx * sy;  a[1] = -sz * cx;  a[2] = cz * sy + sz * sx * cy;
        a[3] = sz * cy + cz * sx * sy;  a[4] =  cz * cx;  a[5] = sz * sy - cz * sx * cy;
        a[6] = -cx * sy;                a[7] =  sx;       a[8] = cx * cy;
    }

    private static void updateParts(List<PartBinding> parts, Basis camera,
                                    float camX, float camY, float camZ,
                                    float baseX, float baseY, float baseDepth,
                                    float itemYaw, float itemRoll, float uniformScale,
                                    float pivotX, float pivotY, float pivotZ,
                                    float[] A, float shX, float shY, float shZ,
                                    float tx, float ty, float tz) {
        float cr = (float) Math.cos(itemRoll), sr = (float) Math.sin(itemRoll);
        float cy = (float) Math.cos(itemYaw),  sy = (float) Math.sin(itemYaw);
        for (PartBinding binding : parts) {
            EntityModel.Part p = binding.part();
            float s = uniformScale * binding.scale();

            // 1) Rest position (camera-local)
            float ox = (p.offsetX() - pivotX) * s;
            float oy = (p.offsetY() - pivotY) * s;
            float oz = (p.offsetZ() - pivotZ) * s;
            float qx = baseX + cr * (cy * ox + sy * oz) - sr * oy;
            float qy = baseY + sr * (cy * ox + sy * oz) + cr * oy;
            float qz = -baseDepth + (-sy * ox + cy * oz);

            // 2) Swing: rotate around the shoulder, then translate
            float dx = qx - shX, dy = qy - shY, dz = qz - shZ;
            float fx = shX + A[0] * dx + A[1] * dy + A[2] * dz + tx;
            float fy = shY + A[3] * dx + A[4] * dy + A[5] * dz + ty;
            float fz = shZ + A[6] * dx + A[7] * dy + A[8] * dz + tz;

            float wx = camX + camera.rightX * fx + camera.upX * fy + camera.backwardX * fz;
            float wy = camY + camera.rightY * fx + camera.upY * fy + camera.backwardY * fz;
            float wz = camZ + camera.rightZ * fx + camera.upZ * fy + camera.backwardZ * fz;

            // 3) Part axes: rest rotation, then swing rotation
            float localRoll = itemRoll + binding.localRoll();
            float partYaw = itemYaw + p.rotationY();
            float lcr = (float) Math.cos(localRoll), lsr = (float) Math.sin(localRoll);
            float lcy = (float) Math.cos(partYaw),   lsy = (float) Math.sin(partYaw);
            float sx = p.width() * s, syScale = p.height() * s, sz = p.depth() * s;

            float[] m = binding.matrix(); // reusable per-part matrix: no per-frame heap churn
            writeAxis(m, 0, camera, A, lcr * lcy, lsr * lcy, -lsy, sx);
            writeAxis(m, 4, camera, A, -lsr, lcr, 0.0f, syScale);
            writeAxis(m, 8, camera, A, lcr * lsy, lsr * lsy, lcy, sz);
            m[3] = m[7] = m[11] = 0.0f;
            m[12] = wx; m[13] = wy; m[14] = wz; m[15] = 1.0f;
            binding.object().transform().setModelMatrix(m);
        }
    }

    private static void writeAxis(float[] matrix, int offset, Basis camera, float[] A,
                                  float vx, float vy, float vz, float scale) {
        float lx = A[0] * vx + A[1] * vy + A[2] * vz;
        float ly = A[3] * vx + A[4] * vy + A[5] * vz;
        float lz = A[6] * vx + A[7] * vy + A[8] * vz;
        matrix[offset]     = (camera.rightX * lx + camera.upX * ly + camera.backwardX * lz) * scale;
        matrix[offset + 1] = (camera.rightY * lx + camera.upY * ly + camera.backwardY * lz) * scale;
        matrix[offset + 2] = (camera.rightZ * lx + camera.upZ * ly + camera.backwardZ * lz) * scale;
    }

    private static EntityModel.Part findGripPart(EntityModel model) {
        for (EntityModel.Part part : model.parts()) {
            String id = part.id().toLowerCase(java.util.Locale.ROOT);
            if (id.equals("grip") || id.equals("handle") || id.equals("hilt") || id.equals("haft")
                || id.contains("weapon_grip") || id.contains("handle_grip")) return part;
        }
        return null;
    }

    private static boolean shouldShowInHand(ItemDefinition definition) {
        return definition.equipmentSlot() == ItemEquipmentSlot.WEAPON
            || definition.equipmentSlot() == ItemEquipmentSlot.NONE;
    }

    private static float heldRoll(ItemDefinition definition) {
        if (definition == null) return 0.40f;
        String category = definition.categoryId().toLowerCase(java.util.Locale.ROOT);
        if (definition.equipmentSlot() == ItemEquipmentSlot.WEAPON || category.contains("tool")) return 0.62f;
        if (category.contains("consumable")) return 0.10f;
        if (category.contains("quest")) return 0.28f;
        return 0.32f;
    }

    private static float heldYaw(ItemDefinition definition) {
        if (definition != null && definition.equipmentSlot() == ItemEquipmentSlot.WEAPON) return -0.10f;
        return 0.08f;
    }

    private static float itemScale(ItemDefinition definition) {
        String category = definition.categoryId().toLowerCase(java.util.Locale.ROOT);
        if (definition.equipmentSlot() == ItemEquipmentSlot.WEAPON || category.contains("tool")) return 0.55f;
        if (category.contains("consumable")) return 0.46f;
        if (category.contains("material") || category.contains("quest")) return 0.44f;
        return 0.44f;
    }

    private record Basis(float rightX, float rightY, float rightZ,
                         float upX, float upY, float upZ,
                         float forwardX, float forwardY, float forwardZ,
                         float backwardX, float backwardY, float backwardZ) {
        static Basis from(float yaw, float pitch) {
            float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
            float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
            float fx = sy * cp, fy = sp, fz = -cy * cp;
            float rx = cy, ry = 0, rz = sy;
            float ux = -sy * sp, uy = cp, uz = cy * sp;
            return new Basis(rx, ry, rz, ux, uy, uz, fx, fy, fz, -fx, -fy, -fz);
        }
    }
}