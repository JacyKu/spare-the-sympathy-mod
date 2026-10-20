package sts.mod.client.compat.replaymod;

import java.util.List;
import java.util.Set;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * ReplayMod compatibility mixins are optional: the targeted jGui classes are
 * bundled (and relocated) inside ReplayMod, so they only exist when ReplayMod
 * is installed.
 */
public final class StsReplayModMixinPlugin implements IMixinConfigPlugin {
	private static final String REPLAYMOD_ID = "replaymod";

	private static boolean replayModLoaded() {
		return FabricLoader.getInstance().isModLoaded(REPLAYMOD_ID);
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
		return replayModLoaded();
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
