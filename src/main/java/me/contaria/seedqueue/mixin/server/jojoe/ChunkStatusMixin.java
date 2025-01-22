package me.contaria.seedqueue.mixin.server.jojoe;

import com.mojang.datafixers.util.Either;
import me.contaria.seedqueue.SeedQueue;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ChunkTaskPrioritySystem;
import net.minecraft.server.world.ServerLightingProvider;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureManager;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Mixin(ChunkStatus.class)
public abstract class ChunkStatusMixin {

    /*@Inject(method = "runGenerationTask", at = @At("HEAD"))
    private static void getChunkPriority(ServerWorld world, ChunkGenerator chunkGenerator, StructureManager structureManager, ServerLightingProvider lightingProvider, Function<Chunk, CompletableFuture<Either<Chunk, ChunkHolder.Unloaded>>> function, List<Chunk> chunks, CallbackInfoReturnable<CompletableFuture<Either<Chunk, ChunkHolder.Unloaded>>> cir) {
        String threadName = Thread.currentThread().getName();
        /*if(!threadName.contains("Random Speedrun")) {
            return;
        }
        //String level = world.getServer().getSaveProperties().getLevelName();
        //System.out.println("Started gen task for " + level + " at " + System.currentTimeMillis());
        //SeedQueue.jojoe.put(Thread.currentThread().getName(), System.currentTimeMillis());
    }

    /*@Inject(method = "runGenerationTask", at = @At("RETURN"))
    private static void getChunkPriority2(ServerWorld world, ChunkGenerator chunkGenerator, StructureManager structureManager, ServerLightingProvider lightingProvider, Function<Chunk, CompletableFuture<Either<Chunk, ChunkHolder.Unloaded>>> function, List<Chunk> chunks, CallbackInfoReturnable<CompletableFuture<Either<Chunk, ChunkHolder.Unloaded>>> cir) {
        String threadName = Thread.currentThread().getName();
        String level = world.getServer().getSaveProperties().getLevelName();
        //System.out.println("Finished gen task for " + level + " at " + System.currentTimeMillis());
        //SeedQueue.jojoe.put(Thread.currentThread().getName(), System.currentTimeMillis());
    }*/

}
