package technology.rocketjump.undermount.particles.model;

import technology.rocketjump.undermount.misc.Name;

public class ParticleEffectType {

	@Name
	private String name;
	private String pFile;
	private boolean isLooping; // if true this effect will loop endlessly until stopped
	private boolean usesTargetMaterialAsTintColor;
	private boolean isAffectedByLighting;
	private float distanceFromParentEntityOrientation; // effect is initialised according to parent entity position and orientation by this amount (distance)
	private boolean attachedToParent; // if true, adjusts world position according to parent entity

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getpFile() {
		return pFile;
	}

	public void setpFile(String pFile) {
		this.pFile = pFile;
	}

	public boolean isLooping() {
		return isLooping;
	}

	public void setIsLooping(boolean isLooping) {
		this.isLooping = isLooping;
	}

	public boolean isUsesTargetMaterialAsTintColor() {
		return usesTargetMaterialAsTintColor;
	}

	public void setUsesTargetMaterialAsTintColor(boolean usesTargetMaterialAsTintColor) {
		this.usesTargetMaterialAsTintColor = usesTargetMaterialAsTintColor;
	}

	public boolean isAffectedByLighting() {
		return isAffectedByLighting;
	}

	public void setIsAffectedByLighting(boolean affectedByLighting) {
		isAffectedByLighting = affectedByLighting;
	}

	public float getDistanceFromParentEntityOrientation() {
		return distanceFromParentEntityOrientation;
	}

	public void setDistanceFromParentEntityOrientation(float distanceFromParentEntityOrientation) {
		this.distanceFromParentEntityOrientation = distanceFromParentEntityOrientation;
	}

	public boolean isAttachedToParent() {
		return attachedToParent;
	}

	public void setAttachedToParent(boolean attachedToParent) {
		this.attachedToParent = attachedToParent;
	}
}
