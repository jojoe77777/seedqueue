package me.contaria.seedqueue.mixin.compat.worldpreview;

import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import fast_reset.client.interfaces.FRMinecraftServer;
import fast_reset.client.interfaces.FRThreadExecutor;
import me.contaria.seedqueue.SeedQueueEntry;
import me.contaria.seedqueue.compat.ModCompat;
import me.contaria.seedqueue.interfaces.SQMinecraftServer;
import me.voidxwalker.worldpreview.interfaces.WPMinecraftServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTask;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.thread.ReentrantThreadExecutor;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.security.KeyPair;
import java.util.function.Function;

@Mixin(value = MinecraftServer.class, priority = 9999)
public abstract class MinecraftServerMixin extends ReentrantThreadExecutor<ServerTask> implements SQMinecraftServer, WPMinecraftServer {

    public MinecraftServerMixin(String string) {
        super(string);
    }

    @Unique
    protected volatile boolean killed;
    @Unique
    protected volatile boolean discarded;
    @Unique
    protected volatile boolean revived;
    @Unique
    private volatile boolean tooLateToKill;
    @Unique
    private boolean shouldConfigurePreview;

    @Shadow
    public abstract void setKeyPair(KeyPair keyPair);

    /*@ModifyExpressionValue(
            method = "createWorlds",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/ServerWorldProperties;isInitialized()Z"
            )
    )
    private boolean setShouldConfigurePreview(boolean isInitialized) {
        this.shouldConfigurePreview = !isInitialized;
        return isInitialized;
    }

    @ModifyVariable(
            method = "prepareStartRegion",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerChunkManager;getTotalChunksLoadedCount()I"
            )
    )
    private ServerWorld configureWorldPreview(ServerWorld serverWorld) {
        return serverWorld;
    }*/

    @Shadow protected abstract void runTasksTillTickEnd();

    @Shadow protected abstract void shutdown();

    @Shadow private boolean stopped;

    @Shadow protected abstract void exit();

    @Shadow public abstract void close();

    @WrapOperation(
            method = "prepareStartRegion",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerChunkManager;addTicket(Lnet/minecraft/server/world/ChunkTicketType;Lnet/minecraft/util/math/ChunkPos;ILjava/lang/Object;)V"
            )
    )
    private void captureChunkTicketInformation(ServerChunkManager chunkManager, ChunkTicketType<Object> ticketType, ChunkPos pos, int radius, Object argument, Operation<Void> original, @Share("removeTicket") LocalRef<Runnable> removeTicket, @Share("addTicket") LocalRef<Runnable> addTicket) {
        removeTicket.set(() -> chunkManager.removeTicket(ticketType, pos, radius, argument));
        addTicket.set(() -> chunkManager.addTicket(ticketType, pos, radius, argument));
        original.call(chunkManager, ticketType, pos, radius, argument);
    }

    @Inject(
            method = "prepareStartRegion",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerChunkManager;getTotalChunksLoadedCount()I",
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void killWorldGen(WorldGenerationProgressListener worldGenerationProgressListener, CallbackInfo ci, @Share("removeTicket") LocalRef<Runnable> removeTicket, @Share("addTicket") LocalRef<Runnable> addTicket) {
        if (this.killed) {
            removeTicket.get().run();
            ((FRThreadExecutor) this).fast_reset$cancelFutures();

            //worldGenerationProgressListener.stop();
            //this.shutdown();
            //ci.cancel();
            //return;
            //System.out.println("3 Killing " + Thread.currentThread().getName() + " at " + System.currentTimeMillis());
            while(!this.discarded && !this.revived) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if(this.revived){
                System.out.println("Reviving");
                addTicket.get().run();
                this.killed = false;
            } else {
                ci.cancel();
            }
            //System.out.println("Finally discarding");
            //ci.cancel();
            /*this.stopped = true;
            this.shutdown();
            this.exit();*/
        }
    }

    @ModifyReturnValue(
            method = "shouldKeepTicking",
            at = @At("RETURN")
    )
    private boolean killRunningTasks(boolean shouldKeepTicking) {
        return shouldKeepTicking && !this.killed;
    }

    /*@Redirect(
            method = "prepareStartRegion",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;runTasksTillTickEnd()V"
            )
    )
    private void pauseServerDuringWorldGen2(MinecraftServer server, WorldGenerationProgressListener worldGenerationProgressListener) {
        if(this.killed){
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return;
        }
        this.runTasksTillTickEnd();
    }*/

    @ModifyExpressionValue(
            method = "runServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;setupServer()Z"
            )
    )
    private synchronized boolean killServer(boolean original) {
        this.tooLateToKill = true;
        return original && !this.killed;
    }

    public synchronized boolean sq$kill() {
        if (this.tooLateToKill) {
            return false;
        }
        return this.killed = true;
    }

    public synchronized boolean sq$discard() {
        return this.discarded = true;
    }

    public synchronized boolean sq$revive() {
        return this.revived = true;
    }

}
