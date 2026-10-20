package test.mixin;
import test.LifecycleHarness;
import com.dripps.voxyserver.client.service.RemoteIngestService;
import com.dripps.voxyserver.network.LODBatchResultPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Test-only deterministic pause: makes in-flight cancellation observable on fast CPUs. */
@Mixin(value=RemoteIngestService.class,remap=false)
public class WorkerBarrier {
    @Inject(method="process",at=@At("HEAD"),cancellable=true)
    private void pauseRealIncomingBatch(CallbackInfoReturnable<Integer> ci) {
        while(LifecycleHarness.holdIngest) {
            try { Thread.sleep(10); }
            catch(InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                ci.setReturnValue(LODBatchResultPayload.STALE);
                return;
            }
        }
    }
}
