package com.atoningpenchant.client;

import com.atoningpenchant.AtoningPenchant;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * 3D-модель кинжала для меню. Читает обычный JSON из Blockbench (assets/atoning_penchant/models/entity/spinel_dagger.json),
 * поэтому модель можно менять в Blockbench и просто подкладывать новый файл. Поддерживаются элементы-кубоиды с
 * гранями (uv), поворотом вокруг origin. Текстура - textures/entity/spinel_dagger.png.
 */
public final class SpinelDaggerModel {
    public static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(AtoningPenchant.MOD_ID, "models/entity/spinel_dagger.json");
    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AtoningPenchant.MOD_ID, "textures/entity/spinel_dagger.png");

    /** Углы грани в порядке обхода (как в ванильном FaceInfo): 0/1 = min/max по x, y, z. */
    private static final String[] DIRECTIONS = {"down", "up", "north", "south", "west", "east"};
    private static final int[][][] CORNERS = {
            {{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}},   // down
            {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},   // up
            {{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}},   // north
            {{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}},   // south
            {{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}},   // west
            {{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}}    // east
    };
    private static final float[][] NORMALS = {
            {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
    };

    private record Face(float[][] pos, float[][] uv, float[] normal) {}

    private final List<Face> faces = new ArrayList<>();
    /** Центр модели (вокруг него кинжал вращается) и высота - в единицах модели (1/16 блока). */
    public final float centerX, centerY, centerZ, height;

    private SpinelDaggerModel(JsonObject json) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

        for (JsonElement element : json.getAsJsonArray("elements")) {
            JsonObject object = element.getAsJsonObject();
            float[] from = floats(object.getAsJsonArray("from"));
            float[] to = floats(object.getAsJsonArray("to"));

            Quaternionf rotation = new Quaternionf();
            float[] origin = {8, 8, 8};
            if (object.has("rotation")) {
                JsonObject rot = object.getAsJsonObject("rotation");
                origin = floats(rot.getAsJsonArray("origin"));
                float rx = rot.has("x") ? rot.get("x").getAsFloat() : 0;
                float ry = rot.has("y") ? rot.get("y").getAsFloat() : 0;
                float rz = rot.has("z") ? rot.get("z").getAsFloat() : 0;
                rotation.rotationXYZ((float) Math.toRadians(rx), (float) Math.toRadians(ry), (float) Math.toRadians(rz));
            }

            JsonObject faceObjects = object.getAsJsonObject("faces");
            for (int d = 0; d < DIRECTIONS.length; d++) {
                if (!faceObjects.has(DIRECTIONS[d])) continue;
                float[] uv = floats(faceObjects.getAsJsonObject(DIRECTIONS[d]).getAsJsonArray("uv"));

                float[][] pos = new float[4][3];
                float[][] coords = new float[4][2];
                for (int i = 0; i < 4; i++) {
                    int[] c = CORNERS[d][i];
                    Vector3f v = new Vector3f(c[0] == 1 ? to[0] : from[0], c[1] == 1 ? to[1] : from[1], c[2] == 1 ? to[2] : from[2]);
                    v.sub(origin[0], origin[1], origin[2]);
                    rotation.transform(v);
                    v.add(origin[0], origin[1], origin[2]);
                    pos[i] = new float[]{v.x, v.y, v.z};

                    // порядок uv как в ванильных моделях: вершины 0,1 берут u0, вершины 2,3 - u1
                    coords[i] = new float[]{(i < 2 ? uv[0] : uv[2]) / 16.0F, (i == 0 || i == 3 ? uv[1] : uv[3]) / 16.0F};

                    minX = Math.min(minX, v.x); maxX = Math.max(maxX, v.x);
                    minY = Math.min(minY, v.y); maxY = Math.max(maxY, v.y);
                    minZ = Math.min(minZ, v.z); maxZ = Math.max(maxZ, v.z);
                }

                Vector3f normal = new Vector3f(NORMALS[d][0], NORMALS[d][1], NORMALS[d][2]);
                rotation.transform(normal);
                faces.add(new Face(pos, coords, new float[]{normal.x, normal.y, normal.z}));
            }
        }

        this.centerX = (minX + maxX) / 2.0F;
        this.centerY = (minY + maxY) / 2.0F;
        this.centerZ = (minZ + maxZ) / 2.0F;
        this.height = Math.max(maxY - minY, 0.001F);
    }

    /** Загружает модель из ресурсов. Если файла нет или он сломан - null (кинжал просто не рисуется). */
    public static SpinelDaggerModel load(ResourceManager resourceManager) {
        try (Reader reader = resourceManager.getResourceOrThrow(MODEL).openAsReader()) {
            return new SpinelDaggerModel(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception e) {
            AtoningPenchant.LOGGER.error("Could not load the dagger model {}", MODEL, e);
            return null;
        }
    }

    /** Рисует модель. Размеры берутся из текущей матрицы PoseStack (в единицах модели). */
    public void render(PoseStack.Pose pose, VertexConsumer consumer, int argbColor, int light, int overlay) {
        for (Face face : faces) {
            for (int i = 0; i < 4; i++) {
                float[] p = face.pos[i];
                consumer.addVertex(pose, p[0], p[1], p[2])
                        .setColor(argbColor)
                        .setUv(face.uv[i][0], face.uv[i][1])
                        .setOverlay(overlay)
                        .setLight(light)
                        .setNormal(pose, face.normal[0], face.normal[1], face.normal[2]);
            }
        }
    }

    /** Попадает ли точка экрана (x, y) в любую грань модели при данной матрице. Нужна для наведения курсора. */
    public boolean contains(Matrix4f matrix, double px, double py) {
        Vector3f v = new Vector3f();
        float[] xs = new float[4];
        float[] ys = new float[4];
        for (Face face : faces) {
            for (int i = 0; i < 4; i++) {
                matrix.transformPosition(face.pos[i][0], face.pos[i][1], face.pos[i][2], v);
                xs[i] = v.x;
                ys[i] = v.y;
            }
            if (insideQuad(xs, ys, px, py)) return true;
        }
        return false;
    }

    private static boolean insideQuad(float[] xs, float[] ys, double px, double py) {
        boolean positive = false;
        boolean negative = false;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            double cross = (xs[j] - xs[i]) * (py - ys[i]) - (ys[j] - ys[i]) * (px - xs[i]);
            if (cross > 1.0E-4) positive = true;
            if (cross < -1.0E-4) negative = true;
        }
        // грань, повёрнутая ребром к зрителю (вырожденная), не считается
        double area = 0;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            area += xs[i] * ys[j] - xs[j] * ys[i];
        }
        return Math.abs(area) > 0.01 && !(positive && negative);
    }

    private static float[] floats(JsonArray array) {
        float[] result = new float[array.size()];
        for (int i = 0; i < result.length; i++) result[i] = array.get(i).getAsFloat();
        return result;
    }
}
