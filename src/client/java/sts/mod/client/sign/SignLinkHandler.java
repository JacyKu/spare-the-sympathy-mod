package sts.mod.client.sign;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.phys.BlockHitResult;
import sts.mod.api.StsConfig;
import sts.mod.client.armoury.ArmouryTracker;

/**
 * Build-sharing signs. A sign whose text contains a line
 * <pre>
 * [STS]
 * &lt;build id&gt;
 * </pre>
 * (the marker and the id may sit anywhere among the sign's lines; blank lines
 * after the marker are skipped, and "[STS] &lt;id&gt;" on one line works too)
 * posts the build's short link to chat as a clickable message when
 * right-clicked. The vanilla interaction is left alone, so unwaxed signs can
 * still be edited normally.
 */
public final class SignLinkHandler {
	/** Marker line: "[STS]" alone, or with the id on the same line. */
	private static final Pattern MARKER_LINE = Pattern.compile("^\\[STS]\\s*(.*)$", Pattern.CASE_INSENSITIVE);
	/** Build ids are 8-char base62; accept a little slack for older links. */
	private static final Pattern BUILD_ID = Pattern.compile("[A-Za-z0-9]{4,32}");

	private SignLinkHandler() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register(SignLinkHandler::onUseBlock);
	}

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (level == null || !level.isClientSide() || player != Minecraft.getInstance().player) {
			return InteractionResult.PASS;
		}
		BlockEntity entity = level.getBlockEntity(hit.getBlockPos());
		if (!(entity instanceof SignBlockEntity sign)) {
			return InteractionResult.PASS;
		}
		SignLinkResult result = readSign(sign);
		if (result.marker()) {
			if (result.id() != null) {
				ArmouryTracker.showLinkMessage(StsConfig.siteUrl() + "/b/" + result.id(), "Build link: ");
			} else {
				ArmouryTracker.showMessage("This sign has [STS] but no build id after it.");
			}
		}
		return InteractionResult.PASS;
	}

	public record SignLinkResult(boolean marker, String id) {
	}

	/** The marker/id found on either side of the sign. */
	private static SignLinkResult readSign(SignBlockEntity sign) {
		List<String> lines = new ArrayList<>(8);
		SignText[] sides = { sign.getFrontText(), sign.getBackText() };
		for (SignText text : sides) {
			for (int i = 0; i < 4; i++) {
				lines.add(text.getMessage(i, false).getString());
			}
		}
		return inspect(lines);
	}

	/**
	 * Pure scan over a sign's lines (front lines first, then the back side).
	 * The build id is the first non-empty line after the [STS] marker; blank
	 * lines and surrounding text are ignored.
	 */
	static SignLinkResult inspect(List<String> lines) {
		boolean marker = false;
		for (int i = 0; i < lines.size(); i++) {
			Matcher matcher = MARKER_LINE.matcher(lines.get(i).trim());
			if (!matcher.matches()) {
				continue;
			}
			marker = true;
			// "[STS] AbC123xy" on one line.
			String inline = matcher.group(1).trim();
			if (BUILD_ID.matcher(inline).matches()) {
				return new SignLinkResult(true, inline);
			}
			// Otherwise the first non-empty line below the marker.
			for (int j = i + 1; j < lines.size(); j++) {
				String candidate = lines.get(j).trim();
				if (candidate.isEmpty()) {
					continue;
				}
				return new SignLinkResult(true, BUILD_ID.matcher(candidate).matches() ? candidate : null);
			}
		}
		return new SignLinkResult(marker, null);
	}
}
