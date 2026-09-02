package net.minelight.fabric.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minelight.fabric.MineLightMod;
import net.minelight.fabric.SoundBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Taps every positional sound the server plays.
 *
 * <p>A dedicated server has no audio and Fabric API has no sound event, but
 * {@code ServerLevel.playSeededSound} is the funnel every gameplay sound
 * passes through on its way to a {@code ClientboundSoundPacket}. Listening
 * here gives sound blocks the sounds players actually hear, at the tick they
 * hear them.</p>
 *
 * <p>Both overloads have to be hooked: they build different packets and
 * neither delegates to the other. Explosions need a third hook — their sound
 * rides along inside {@code ClientboundExplodePacket} and never goes near
 * {@code playSeededSound}, so without it the loudest thing in the game is the
 * one thing a sound block cannot hear.</p>
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelSoundMixin {

    /** Vanilla plays the explosion sound from the explode packet at volume 4. */
    private static final float EXPLOSION_VOLUME = 4.0f;

    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At("HEAD"))
    private void minelight$onPositionalSound(Entity source, double x, double y, double z,
                                             Holder<SoundEvent> sound, SoundSource category,
                                             float volume, float pitch, long seed,
                                             CallbackInfo ci) {
        minelight$feed(x, y, z, sound, volume, pitch);
    }

    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At("HEAD"))
    private void minelight$onEntitySound(Entity source, Entity target,
                                         Holder<SoundEvent> sound, SoundSource category,
                                         float volume, float pitch, long seed,
                                         CallbackInfo ci) {
        minelight$feed(target.getX(), target.getY(), target.getZ(), sound, volume, pitch);
    }

    @Inject(method = "explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/util/random/WeightedList;Lnet/minecraft/core/Holder;)V",
            at = @At("HEAD"))
    private void minelight$onExplosion(Entity source, DamageSource damage,
                                       ExplosionDamageCalculator calculator,
                                       double x, double y, double z, float radius, boolean fire,
                                       Level.ExplosionInteraction interaction,
                                       ParticleOptions small, ParticleOptions large,
                                       WeightedList<ExplosionParticleInfo> particles,
                                       Holder<SoundEvent> sound,
                                       CallbackInfo ci) {
        minelight$feed(x, y, z, sound, EXPLOSION_VOLUME, 1.0f);
    }

    private static void minelight$feed(double x, double y, double z,
                                       Holder<SoundEvent> sound, float volume, float pitch) {
        SoundBridge bridge = MineLightMod.soundBridge();
        if (bridge == null || sound == null) {
            return; // sounds can play before the engine starts, and after it stops
        }
        SoundEvent event = sound.value();
        bridge.onSound(x, y, z, event.location().getPath(), volume, pitch, event.getRange(volume));
    }
}
