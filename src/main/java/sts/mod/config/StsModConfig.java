package sts.mod.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import sts.mod.api.StsConfig;

@Config(name = "sparethesympathy")
public class StsModConfig implements ConfigData {
	@ConfigEntry.Gui.Tooltip
	public String siteUrl = "http://localhost:3001";

	@Override
	public void validatePostLoad() throws ValidationException {
		this.siteUrl = StsConfig.normalizeSiteUrl(this.siteUrl);
	}
}