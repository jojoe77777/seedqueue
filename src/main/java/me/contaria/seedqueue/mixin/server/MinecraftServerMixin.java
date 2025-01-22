package me.contaria.seedqueue.mixin.server;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import me.contaria.seedqueue.SeedQueue;
import me.contaria.seedqueue.SeedQueueEntry;
import me.contaria.seedqueue.SeedQueueExecutorWrapper;
import me.contaria.seedqueue.interfaces.SQMinecraftServer;
import me.contaria.seedqueue.mixin.accessor.EntityAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.ServerTask;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.thread.ReentrantThreadExecutor;
import net.minecraft.world.level.ServerWorldProperties;
import net.minecraft.world.level.storage.LevelStorage;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin extends ReentrantThreadExecutor<ServerTask> implements SQMinecraftServer {

    @Shadow
    private volatile boolean loading;

    @Shadow
    @Final
    protected LevelStorage.Session session;

    @Shadow
    @Final
    private Executor workerExecutor;

    @Unique
    private CompletableFuture<SeedQueueEntry> seedQueueEntry;

    @Unique
    private volatile boolean pauseScheduled;

    @Unique
    private volatile boolean paused;

    @Unique
    private final AtomicInteger maxEntityId = new AtomicInteger(EntityAccessor.seedQueue$getMAX_ENTITY_ID().get());

    @Shadow
    public abstract PlayerManager getPlayerManager();

    public MinecraftServerMixin(String string) {
        super(string);
    }

    @ModifyExpressionValue(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Util;getServerWorkerExecutor()Ljava/util/concurrent/Executor;"
            )
    )
    private Executor wrapExecutor(Executor executor) {
        if (SeedQueue.inQueue()) {
            return new SeedQueueExecutorWrapper(executor);
        }
        return executor;
    }

    @ModifyVariable(
            method = "<init>",
            at = @At("TAIL"),
            argsOnly = true
    )
    private Thread modifyServerThreadProperties(Thread thread) {
        if (SeedQueue.inQueue()) {
            thread.setPriority(SeedQueue.config.serverThreadPriority);
        }
        thread.setName(thread.getName() + " - " + this.session.getDirectoryName());
        return thread;
    }

    @Inject(
            method = "<init>",
            at = @At("TAIL")
    )
    private void setSeedQueueEntry(CallbackInfo ci) {
        if (SeedQueue.inQueue()) {
            this.seedQueueEntry = new CompletableFuture<>();
        }
    }

    @Inject(
            method = "loadWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/WorldGenerationProgressListenerFactory;create(I)Lnet/minecraft/server/WorldGenerationProgressListener;"
            )
    )
    private void setThreadLocalSeedQueueEntry(CallbackInfo ci) {
        this.seedQueue$getEntry().ifPresent(SeedQueue.LOCAL_ENTRY::set);
    }

    @Inject(
            method = "setupSpawn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/network/SpawnLocating;findServerSpawnPoint(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/ChunkPos;Z)Lnet/minecraft/util/math/BlockPos;"
            )
    )
    private static void pauseServerDuringWorldSetup(ServerWorld serverWorld, ServerWorldProperties serverWorldProperties, boolean bl, boolean bl2, boolean bl3, CallbackInfo ci) {
        ((SQMinecraftServer) serverWorld.getServer()).seedQueue$tryPausingServer();
    }

    @Inject(
            method = "prepareStartRegion",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;runTasksTillTickEnd()V",
                    shift = At.Shift.AFTER
            )
    )
    private void pauseServerDuringWorldGen(CallbackInfo ci) {
        this.seedQueue$tryPausingServer();
    }



    /*@WrapOperation(
            method = "prepareStartRegion",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerChunkManager;addTicket(Lnet/minecraft/server/world/ChunkTicketType;Lnet/minecraft/util/math/ChunkPos;ILjava/lang/Object;)V"
            )
    )
    private void captureChunkTicketInformation(ServerChunkManager chunkManager, ChunkTicketType<Object> ticketType, ChunkPos pos, int radius, Object argument, Operation<Void> original) {
        SeedQueueEntry entry = this.seedQueue$getEntry().orElse(null);
        if (entry == null) {
            original.call(chunkManager, ticketType, pos, radius, argument);
            return;
        }
        entry.setTicketInformation(chunkManager, ticketType, pos, radius, argument);
        original.call(chunkManager, ticketType, pos, radius, argument);
    }/*

    @Inject(
            method = "prepareStartRegion",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerChunkManager;getTotalChunksLoadedCount()I",
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void killWorldGen(WorldGenerationProgressListener worldGenerationProgressListener, CallbackInfo ci, @Share("removeTicket") LocalRef<Runnable> removeTicket) {
        SeedQueueEntry entry = this.seedQueue$getEntry().orElse(null);
        if (entry != null && entry.deadPaused) {
            removeTicket.get().run();
            worldGenerationProgressListener.stop();
            ci.cancel();
        }
    }

    @ModifyReturnValue(
            method = "shouldKeepTicking",
            at = @At("RETURN")
    )
    private boolean killRunningTasks(boolean shouldKeepTicking) {
        SeedQueueEntry entry = this.seedQueue$getEntry().orElse(null);
        if(entry == null){
            return shouldKeepTicking;
        }
        return shouldKeepTicking && !entry.deadPaused;
    }*/






    @Inject(
            method = "loadWorld",
            at = @At("TAIL")
    )
    private void discardWorldPreviewPropertiesOnLoad(CallbackInfo ci) {
        if (!SeedQueue.config.shouldUseWall()) {
            this.seedQueue$getEntry().ifPresent(entry -> entry.setWorldPreviewProperties(null));
        }
    }

    @WrapOperation(
            method = "runServer",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/server/MinecraftServer;loading:Z",
                    opcode = Opcodes.PUTFIELD
            )
    )
    private void pauseServer(MinecraftServer server, boolean value, Operation<Void> original) {
        // "loading" is a bad mapping and actually means something more like "finishedLoading"
        if (this.loading || !this.seedQueue$inQueue()) {
            original.call(server, value);
            return;
        }

        original.call(server, value);

        SeedQueue.LOGGER.info("Finished loading \"{}\".", this.session.getDirectoryName());
        this.seedQueue$tryPausingServer();
        this.seedQueueEntry = null;
    }

    @Override
    public Optional<SeedQueueEntry> seedQueue$getEntry() {
        return Optional.ofNullable(this.seedQueueEntry).map(CompletableFuture::join);
    }

    @Override
    public boolean seedQueue$inQueue() {
        return this.seedQueueEntry != null;
    }

    @Override
    public void seedQueue$setEntry(SeedQueueEntry entry) {
        this.seedQueueEntry.complete(entry);
    }

    @Override
    public boolean seedQueue$shouldPause() {
        SeedQueueEntry entry = this.seedQueue$getEntry().orElse(null);
        if (entry == null || entry.isLoaded() || entry.isDiscarded() || entry.isDying()) {
            return false;
        }
        if (this.pauseScheduled || entry.isReady()) {
            return true;
        }
        if (!entry.hasWorldPreview()) {
            return false;
        }
        if (entry.isLocked()) {
            return false;
        }
        if (SeedQueue.config.resumeOnFilledQueue && entry.isMaxWorldGenerationReached() && SeedQueue.isFull() ) {
            return false;
        }
        if (SeedQueue.config.maxWorldGenerationPercentage < 100 && entry.getProgressPercentage() >= SeedQueue.config.maxWorldGenerationPercentage) {
            entry.setMaxWorldGenerationReached();
            return true;
        }
        return false;
    }

    @Override
    public synchronized void seedQueue$tryPausingServer() {
        if (!this.isOnThread()) {
            throw new IllegalStateException("Tried to pause the server from another thread!");
        }

        if (!this.seedQueue$shouldPause()) {
            return;
        }

        try {
            this.paused = true;
            this.pauseScheduled = false;
            SeedQueue.ping();
            this.wait();
        } catch (InterruptedException e) {
            throw new RuntimeException("Failed to pause server in SeedQueue!", e);
        } finally {
            this.paused = false;
        }
    }

    @Override
    public boolean seedQueue$isPaused() {
        return this.paused;
    }

    @Override
    public boolean seedQueue$isScheduledToPause() {
        return this.pauseScheduled;
    }

    @Override
    public synchronized void seedQueue$schedulePause() {
        if (!this.paused) {
            this.pauseScheduled = true;
        }
    }

    @Override
    public synchronized void seedQueue$unpause() {
        this.pauseScheduled = false;
        if (this.paused) {
            this.notify();
            this.paused = false;
            /*SeedQueueEntry entry = this.seedQueue$getEntry().orElse(null);
            if (entry == null) {
                return;
            }
            System.out.println("PAUSEDEBUG Unpausing " + entry.getSession().getDirectoryName() + " at " + System.currentTimeMillis());
*/
        }
    }

    @Override
    public void seedQueue$setExecutor(Executor executor) {
        ((SeedQueueExecutorWrapper) this.workerExecutor).setExecutor(executor);
    }

    @Override
    public void seedQueue$resetExecutor() {
        ((SeedQueueExecutorWrapper) this.workerExecutor).resetExecutor();
    }

    @Override
    public int seedQueue$incrementAndGetEntityID() {
        return this.maxEntityId.incrementAndGet();
    }
}
