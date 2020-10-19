package technology.rocketjump.undermount.entities.tags;

import com.badlogic.gdx.ai.msg.MessageDispatcher;
import technology.rocketjump.undermount.entities.behaviour.furniture.MushroomLogBehaviour;
import technology.rocketjump.undermount.entities.model.Entity;
import technology.rocketjump.undermount.gamecontext.GameContext;

public class MushroomLogBehaviourTag extends Tag {

	@Override
	public String getTagName() {
		return "MUSHROOM_LOG_BEHAVIOUR";
	}

	@Override
	public boolean isValid() {
		return true; // TODO implement this
	}

	@Override
	public void apply(Entity entity, TagProcessingUtils tagProcessingUtils, MessageDispatcher messageDispatcher, GameContext gameContext) {
		if (entity.getBehaviourComponent() == null) {
			// Don't apply to furniture which already doesn't have a BehaviourComponent e.g. when placing from UI
			return;
		}

		if (!entity.getBehaviourComponent().getClass().equals(MushroomLogBehaviour.class)) {
			// Only switch behaviour if already different
			MushroomLogBehaviour behaviourComponent = new MushroomLogBehaviour();

//			List<ItemType> relatedItemTypes = new ArrayList<>();
//			ItemType itemType = tagProcessingUtils.itemTypeDictionary.getByName(args.get(0));
//			if (itemType == null) {
//				Logger.error("Could not find item type " + args.get(0) + " specified in " + getTagName() + " tag");
//			} else {
//				relatedItemTypes.add(itemType);
//			}
//			behaviourComponent.setRelatedItemTypes(relatedItemTypes);
			behaviourComponent.setHarvestJobType(tagProcessingUtils.jobTypeDictionary.getByName("HARVESTING"));

			behaviourComponent.init(entity, messageDispatcher, gameContext);

			entity.replaceBehaviourComponent(behaviourComponent);
		}
	}
}
