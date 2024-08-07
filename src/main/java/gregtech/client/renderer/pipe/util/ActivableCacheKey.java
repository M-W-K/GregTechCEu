package gregtech.client.renderer.pipe.util;

import org.jetbrains.annotations.Nullable;

public class ActivableCacheKey extends CacheKey {

    private final boolean active;

    public ActivableCacheKey(float thickness, boolean active) {
        super(thickness);
        this.active = active;
    }

    public static ActivableCacheKey of(@Nullable Float thickness, @Nullable Boolean active) {
        float thick = thickness == null ? 0.5f : thickness;
        boolean act = active != null && active;
        return new ActivableCacheKey(thick, act);
    }

    public boolean isActive() {
        return active;
    }

    // activeness is merely a way to pass information onwards, it does not result in separate mappings.
}