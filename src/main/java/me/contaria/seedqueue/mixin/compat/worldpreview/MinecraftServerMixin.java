package me.contaria.seedqueue.mixin.compat.worldpreview;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import fast_reset.client.interfaces.FRThreadExecutor;
import me.contaria.seedqueue.interfaces.SQMinecraftServer;
import me.voidxwalker.worldpreview.interfaces.WPMinecraftServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTask;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.thread.ReentrantThreadExecutor;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MinecraftServer.class)
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
        }
    }

    @ModifyReturnValue(
            method = "shouldKeepTicking",
            at = @At("RETURN")
    )
    private boolean killRunningTasks(boolean shouldKeepTicking) {
        return shouldKeepTicking && !this.killed;
    }

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
