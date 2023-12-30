package qouteall.dimlib.mixin.client;

import eu.midnightdust.lib.config.MidnightConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.dimlib.DimLibEntry;
import qouteall.dimlib.config.DimLibConfig;

import java.util.Objects;

@Mixin(BackupConfirmScreen.class)
public class MixinBackupConfirmScreen extends Screen {
    
    @Shadow
    @Final
    protected BackupConfirmScreen.Listener onProceed;
    @Shadow
    private Checkbox eraseCache;
    @Shadow
    private MultiLineLabel message;
    @Unique
    private boolean dimlib_isExperimentalWarning = false;
    
    protected MixinBackupConfirmScreen(Component title) {
        super(title);
        throw new RuntimeException();
    }
    
    /**
     * {@link WorldOpenFlows#askForBackup}
     */
    @SuppressWarnings("JavadocReference")
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInitEnd(
        Runnable runnable, BackupConfirmScreen.Listener listener,
        Component component, Component component2, boolean bl, CallbackInfo ci
    ) {
        dimlib_isExperimentalWarning = Objects.equals(
            component,
            Component.translatable("selectWorld.backupQuestion.experimental")
        );
    }
    
    @Inject(method = "init", at = @At("RETURN"))
    private void onInitEnd(CallbackInfo ci) {
        if (dimlib_isExperimentalWarning) {
            int i = (this.message.getLineCount() + 1) * 9;
            addRenderableWidget(Button
                .builder(
                    Component.translatable(
                        "dimlib.i_know_what_i_am_doing_and_disable_warning"
                    ),
                    button -> {
                        DimLibConfig.suppressExperimentalWarning = true;
                        MidnightConfig.write(DimLibEntry.MODID);
                        
                        this.onProceed.proceed(false, this.eraseCache.selected());
                    }
                )
                .bounds(
                    this.width / 2 - 200, 124 + i + 40,
                    400, 20
                )
                .build()
            );
            
        }
    }
}
