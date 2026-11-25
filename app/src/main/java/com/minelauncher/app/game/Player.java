package com.minelauncher.app.game;

/**
 * Player entity: AABB collision, gravity, jumping, swimming and creative flight.
 */
public class Player {

    public static final double WIDTH = 0.6;
    public static final double HEIGHT = 1.8;
    public static final double EYE = 1.62;

    private static final double GRAVITY = 30.0;
    private static final double JUMP_VELOCITY = 8.6;
    private static final double TERMINAL = -55.0;

    public double x, y, z;      // feet position, centred horizontally
    public double vx, vy, vz;
    public float yaw;           // degrees, 0 = looking towards -Z
    public float pitch;         // degrees, positive = up

    public boolean onGround = true;
    public boolean flying;
    public boolean creative;
    public boolean inWater;

    public void setPosition(double px, double py, double pz) {
        x = px;
        y = py;
        z = pz;
        vx = vy = vz = 0;
    }

    public double eyeX() {
        return x;
    }

    public double eyeY() {
        return y + EYE;
    }

    public double eyeZ() {
        return z;
    }

    public double dirX() {
        double yawRad = Math.toRadians(yaw);
        return -Math.sin(yawRad) * Math.cos(Math.toRadians(pitch));
    }

    public double dirY() {
        return Math.sin(Math.toRadians(pitch));
    }

    public double dirZ() {
        double yawRad = Math.toRadians(yaw);
        return -Math.cos(yawRad) * Math.cos(Math.toRadians(pitch));
    }

    public void update(World world, float forward, float strafe, boolean jump, boolean down, double dt) {
        if (dt <= 0) return;
        if (dt > 0.05) dt = 0.05;

        float yawRad = (float) Math.toRadians(yaw);
        double fx = -Math.sin(yawRad);
        double fz = -Math.cos(yawRad);
        double rx = Math.cos(yawRad);
        double rz = -Math.sin(yawRad);

        double wishX = fx * forward + rx * strafe;
        double wishZ = fz * forward + rz * strafe;
        double wishLen = Math.hypot(wishX, wishZ);
        if (wishLen > 1.0) {
            wishX /= wishLen;
            wishZ /= wishLen;
        }

        inWater = world.get((int) Math.floor(x), (int) Math.floor(y + 0.4), (int) Math.floor(z)) == BlockTypes.WATER;

        if (flying) {
            double speed = 9.0;
            vx = wishX * speed;
            vz = wishZ * speed;
            double vyTarget = 0;
            if (jump) vyTarget += speed * 0.8;
            if (down) vyTarget -= speed * 0.8;
            vy = vyTarget;
            onGround = false;
        } else {
            double speed = inWater ? 2.8 : 4.5;
            double targetX = wishX * speed;
            double targetZ = wishZ * speed;
            double accel = onGround ? 14.0 : 4.5;
            double blend = Math.min(1.0, accel * dt);
            vx += (targetX - vx) * blend;
            vz += (targetZ - vz) * blend;

            if (inWater) {
                vy -= GRAVITY * 0.25 * dt;
                if (vy < -4.0) vy = -4.0;
                if (jump) vy = 3.4;
            } else {
                if (jump && onGround) {
                    vy = JUMP_VELOCITY;
                    onGround = false;
                }
                vy -= GRAVITY * dt;
                if (vy < TERMINAL) vy = TERMINAL;
            }
        }

        move(world, vx * dt, vy * dt, vz * dt);

        // invisible world border
        double margin = WIDTH * 0.5 + 0.05;
        if (x < margin) {
            x = margin;
            vx = 0;
        }
        if (x > World.SX - margin) {
            x = World.SX - margin;
            vx = 0;
        }
        if (z < margin) {
            z = margin;
            vz = 0;
        }
        if (z > World.SZ - margin) {
            z = World.SZ - margin;
            vz = 0;
        }
    }

    private void move(World world, double dx, double dy, double dz) {
        double dist = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        int steps = Math.max(1, (int) Math.ceil(dist / 0.2));
        double sx = dx / steps;
        double sy = dy / steps;
        double sz = dz / steps;
        onGround = false;

        for (int i = 0; i < steps; i++) {
            if (sx != 0) {
                x += sx;
                if (collides(world)) {
                    x -= sx;
                    vx = 0;
                }
            }
            if (sy != 0) {
                y += sy;
                if (collides(world)) {
                    y -= sy;
                    if (sy < 0) onGround = true;
                    vy = 0;
                }
            }
            if (sz != 0) {
                z += sz;
                if (collides(world)) {
                    z -= sz;
                    vz = 0;
                }
            }
        }

        if (sy == 0) {
            // standing still vertically: probe one block below
            onGround = collidesAt(world, x, y - 0.02, z);
        }
    }

    private boolean collides(World world) {
        return collidesAt(world, x, y, z);
    }

    private boolean collidesAt(World world, double px, double py, double pz) {
        double minX = px - WIDTH * 0.5 + 0.001;
        double maxX = px + WIDTH * 0.5 - 0.001;
        double minY = py + 0.001;
        double maxY = py + HEIGHT - 0.001;
        double minZ = pz - WIDTH * 0.5 + 0.001;
        double maxZ = pz + WIDTH * 0.5 - 0.001;

        int x0 = (int) Math.floor(minX);
        int x1 = (int) Math.floor(maxX);
        int y0 = (int) Math.floor(minY);
        int y1 = (int) Math.floor(maxY);
        int z0 = (int) Math.floor(minZ);
        int z1 = (int) Math.floor(maxZ);

        for (int bx = x0; bx <= x1; bx++) {
            for (int by = y0; by <= y1; by++) {
                for (int bz = z0; bz <= z1; bz++) {
                    if (BlockTypes.isSolid(world.get(bx, by, bz))) return true;
                }
            }
        }
        return false;
    }

    /** True when the given block position would intersect the player hitbox. */
    public boolean intersectsBlock(int bx, int by, int bz) {
        double minX = bx;
        double maxX = bx + 1;
        double minY = by;
        double maxY = by + 1;
        double minZ = bz;
        double maxZ = bz + 1;

        return maxX > x - WIDTH * 0.5 && minX < x + WIDTH * 0.5
                && maxY > y && minY < y + HEIGHT
                && maxZ > z - WIDTH * 0.5 && minZ < z + WIDTH * 0.5;
    }
}
