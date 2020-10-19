package technology.rocketjump.undermount.messaging.types;

import technology.rocketjump.undermount.entities.ai.goap.actions.FurnitureAssignmentCallback;
import technology.rocketjump.undermount.entities.model.Entity;
import technology.rocketjump.undermount.entities.tags.Tag;

public class FurnitureAssignmentRequest {

	public final Entity requestingEntity;
	public final FurnitureAssignmentCallback callback;
	public final Class<? extends Tag> requiredTag;

	public FurnitureAssignmentRequest(Class<? extends Tag> requiredTag, Entity requestingEntity, FurnitureAssignmentCallback callback) {
		this.requestingEntity = requestingEntity;
		this.callback = callback;
		this.requiredTag = requiredTag;
	}
}
