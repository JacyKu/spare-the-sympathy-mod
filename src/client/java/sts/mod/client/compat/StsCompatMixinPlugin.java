package sts.mod.client.compat;

import java.util.List;
import java.util.Set;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Mixin plugin for {@code sparethesympathy.compat.mixins.json}: the compatibility
 * fixes in that config target Unofficial Monumenta Mod and are only applied
 * when it is actually installed (the classes are not a build dependency, so
 * the mixins target them by name).
 */
public final class StsCompatMixinPlugin implements IMixinConfigPlugin {
	private static final String UMM_MOD_ID = "unofficial-monumenta-mod";

	private static boolean ummLoaded() {
		return FabricLoader.getInstance().isModLoaded(UMM_MOD_ID);
	}

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return ummLoaded();
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
