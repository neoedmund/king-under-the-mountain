package technology.rocketjump.undermount.entities.model.physical.plant;

import technology.rocketjump.undermount.assets.entities.model.ColoringLayer;

import java.util.EnumMap;
import java.util.Map;

public class PlantSeasonSettings {

	private boolean growth = true;
	private Map<ColoringLayer, PlantSpeciesColor> colors = new EnumMap<>(ColoringLayer.class);
	private Integer switchToGrowthStage = null; // Can be used to switch to a decaying-type stage

	public boolean isGrowth() {
		return growth;
	}

	public void setGrowth(boolean growth) {
		this.growth = growth;
	}

	public Map<ColoringLayer, PlantSpeciesColor> getColors() {
		return colors;
	}

	public void setColors(Map<ColoringLayer, PlantSpeciesColor> colors) {
		this.colors = colors;
	}

	public Integer getSwitchToGrowthStage() {
		return switchToGrowthStage;
	}

	public void setSwitchToGrowthStage(Integer switchToGrowthStage) {
		this.switchToGrowthStage = switchToGrowthStage;
	}
}
