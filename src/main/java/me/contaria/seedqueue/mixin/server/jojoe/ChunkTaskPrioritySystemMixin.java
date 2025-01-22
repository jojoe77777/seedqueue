package me.contaria.seedqueue.mixin.server.jojoe;

import me.contaria.seedqueue.SeedQueue;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ChunkTaskPrioritySystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkTaskPrioritySystem.class)
public abstract class ChunkTaskPrioritySystemMixin {

    @Inject(method = "createMessage(Lnet/minecraft/server/world/ChunkHolder;Ljava/lang/Runnable;)Lnet/minecraft/server/world/ChunkTaskPrioritySystem$Task;", at = @At("HEAD"))
    private static void getChunkPriority(ChunkHolder holder, Runnable runnable, CallbackInfoReturnable<ChunkTaskPrioritySystem.Task<Runnable>> cir) {
        String threadName = Thread.currentThread().getName();
        if(!threadName.contains("Random Speedrun")) {
            return;
        }
        //System.out.println("Creating message for " + threadName);
        //SeedQueue.jojoe.put(Thread.currentThread().getName(), System.currentTimeMillis());
    }

}
