package sts.mod.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import sts.mod.api.StsConfig;

@Config(name = "sparethesympathy")
public class StsModConfig implements ConfigData {
	@ConfigEntry.Gui.Tooltip
	public String siteUrl = "http://localhost:3001";

	// Each armoury button anchors to the left or right edge of the Mechanical
	// Armory window; x/y are offsets from that anchor. Ctrl+dragging a button
	// in-game writes its offsets back here, so the config screen always shows
	// the current position.

	@ConfigEntry.Gui.Tooltip
	public boolean exportOnLeft = false;
	@ConfigEntry.Gui.Tooltip
	@ConfigEntry.BoundedDiscrete(min = -400, max = 400)
	public int exportX = 0;
	@ConfigEntry.Gui.Tooltip
	@ConfigEntry.BoundedDiscrete(min = -400, max = 400)
	public int exportY = 0;

	@ConfigEntry.Gui.Tooltip
	public boolean saveOnLeft = false;
	@ConfigEntry.Gui.Tooltip
	@ConfigEntry.BoundedDiscrete(min = -400, max = 400)
	public int saveX = 0;
	@ConfigEntry.Gui.Tooltip
	@ConfigEntry.BoundedDiscrete(min = -400, max = 400)
	public int saveY = 26;

	@ConfigEntry.Gui.Tooltip
	public boolean linkOnLeft = false;
	@ConfigEntry.Gui.Tooltip
	@ConfigEntry.BoundedDiscrete(min = -400, max = 400)
	public int linkX = 0;
	@ConfigEntry.Gui.Tooltip
	@ConfigEntry.BoundedDiscrete(min = -400, max = 400)
	public int linkY = 52;

	@Override
	public void validatePostLoad() throws ValidationException {
		this.siteUrl = StsConfig.normalizeSiteUrl(this.siteUrl);
		this.exportX = StsConfig.clampOffset(this.exportX);
		this.exportY = StsConfig.clampOffset(this.exportY);
		this.saveX = StsConfig.clampOffset(this.saveX);
		this.saveY = StsConfig.clampOffset(this.saveY);
		this.linkX = StsConfig.clampOffset(this.linkX);
		this.linkY = StsConfig.clampOffset(this.linkY);
	}
}
