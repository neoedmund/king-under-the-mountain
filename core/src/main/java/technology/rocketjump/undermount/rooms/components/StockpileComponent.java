package technology.rocketjump.undermount.rooms.components;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.badlogic.gdx.ai.msg.MessageDispatcher;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.RandomXS128;
import com.google.common.collect.Lists;
import org.pmw.tinylog.Logger;
import technology.rocketjump.undermount.entities.behaviour.furniture.SelectableDescription;
import technology.rocketjump.undermount.entities.components.ItemAllocation;
import technology.rocketjump.undermount.entities.components.ItemAllocationComponent;
import technology.rocketjump.undermount.entities.model.Entity;
import technology.rocketjump.undermount.entities.model.EntityType;
import technology.rocketjump.undermount.entities.model.physical.item.ItemEntityAttributes;
import technology.rocketjump.undermount.entities.model.physical.item.ItemType;
import technology.rocketjump.undermount.gamecontext.GameContext;
import technology.rocketjump.undermount.mapping.model.TiledMap;
import technology.rocketjump.undermount.mapping.tile.MapTile;
import technology.rocketjump.undermount.materials.model.GameMaterial;
import technology.rocketjump.undermount.messaging.MessageType;
import technology.rocketjump.undermount.persistence.JSONUtils;
import technology.rocketjump.undermount.persistence.SavedGameDependentDictionaries;
import technology.rocketjump.undermount.persistence.model.InvalidSaveException;
import technology.rocketjump.undermount.persistence.model.SavedGameStateHolder;
import technology.rocketjump.undermount.rooms.*;
import technology.rocketjump.undermount.ui.i18n.I18nString;
import technology.rocketjump.undermount.ui.i18n.I18nText;
import technology.rocketjump.undermount.ui.i18n.I18nTranslator;
import technology.rocketjump.undermount.ui.i18n.I18nWord;

import java.util.*;

public class StockpileComponent extends RoomComponent implements SelectableDescription {

	private StockpileGroup group;
	// This keeps track of allocations - null for empty spaces
	private final Map<GridPoint2, StockpileAllocation> allocations = new HashMap<>();

	public StockpileComponent(Room parent, MessageDispatcher messageDispatcher) {
		super(parent, messageDispatcher);
	}

	@Override
	public void destroy(Entity parentEntity, MessageDispatcher messageDispatcher, GameContext gameContext) {

	}

	@Override
	public RoomComponent clone(Room newParent) {
		StockpileComponent cloned = new StockpileComponent(newParent, messageDispatcher);
		cloned.group = this.group;
		// Copy over allocations, duplicates will be removed after
		for (Map.Entry<GridPoint2, StockpileAllocation> entry : this.allocations.entrySet()) {
			cloned.allocations.put(entry.getKey(), entry.getValue());
		}
		return cloned;
	}

	@Override
	public void mergeFrom(RoomComponent otherComponent) {
		StockpileComponent other = (StockpileComponent) otherComponent;
		for (Map.Entry<GridPoint2, StockpileAllocation> entry : other.allocations.entrySet()) {
			this.allocations.put(entry.getKey(), entry.getValue());
		}
		if (this.group == null) {
			this.group = other.group;
		}
	}

	@Override
	public void tileRemoved(GridPoint2 location) {
		allocations.remove(location);
		messageDispatcher.dispatchMessage(MessageType.REMOVE_HAULING_JOBS_TO_POSITION, location);
	}

	public void itemPickedUp(MapTile targetTile) {
		StockpileAllocation allocationAtTile = getAllocationAt(targetTile.getTilePosition());
		if (allocationAtTile != null) {
			allocationAtTile.refreshQuantityInTile(targetTile);
			if (allocationAtTile.getTotalQuantity() <= 0) {
				allocations.remove(targetTile.getTilePosition());
			}
		}
	}

	public void itemPlaced(MapTile targetTile, ItemEntityAttributes placedItemAttributes, int quantityPlaced) {
		StockpileAllocation existingAllocation = getAllocationAt(targetTile.getTilePosition());
		if (existingAllocation != null && existingAllocation.getItemType().equals(placedItemAttributes.getItemType()) &&
				existingAllocation.getGameMaterial().equals(placedItemAttributes.getMaterial(placedItemAttributes.getItemType().getPrimaryMaterialType()))) {
			// Matches existing allocation, cancel incoming hauling and refresh
			existingAllocation.decrementIncomingHaulingQuantity(quantityPlaced);
			existingAllocation.refreshQuantityInTile(targetTile);
		} else {
			// Placed an item which does match the existing allocation
			StockpileAllocation replacementAllocation = new StockpileAllocation(targetTile.getTilePosition());
			replacementAllocation.setGameMaterial(placedItemAttributes.getMaterial(placedItemAttributes.getItemType().getPrimaryMaterialType()));
			replacementAllocation.setItemType(placedItemAttributes.getItemType());
			replacementAllocation.refreshQuantityInTile(targetTile);

			this.allocations.put(targetTile.getTilePosition(), replacementAllocation);
		}
	}

	// Picks and allocates a position for the item
	public StockpileAllocationResponse requestAllocation(Entity item, TiledMap map) {
		ItemEntityAttributes attributes = (ItemEntityAttributes)item.getPhysicalEntityComponent().getAttributes();
		ItemType itemType = attributes.getItemType();
		final int maxStackSize = itemType.getMaxStackSize();
		GameMaterial itemMaterial = attributes.getMaterial(attributes.getItemType().getPrimaryMaterialType());

		int numUnallocated = item.getOrCreateComponent(ItemAllocationComponent.class).getNumUnallocated();
		int quantityToAllocate = Math.min(numUnallocated, itemType.getMaxHauledAtOnce());

		StockpileAllocation allocationToUse = null;

		List<GridPoint2> pointsToTraverse = new ArrayList<>(parent.getRoomTiles().keySet());
		// Randomly traverse to see if we can fit into existing
		Collections.shuffle(pointsToTraverse);
		// First try to find a matching allocation
		for (GridPoint2 position : pointsToTraverse) {
			MapTile tileAtPosition = map.getTile(position);
			StockpileAllocation allocationAtPosition = allocations.get(position);
			if (allocationAtPosition == null) {
				// No allocation here yet
				if (!tileAtPosition.isEmpty()) {
					Entity itemAlreadyInTile = tileAtPosition.getFirstItem();
					if (itemAlreadyInTile != null) {
						// There is already an item here but no existing allocation, so add a new allocation matching it
						// This is for pre-existing items where a stockpile is placed
						allocationAtPosition = new StockpileAllocation(position);
						ItemEntityAttributes attributesItemAlreadyInTile = (ItemEntityAttributes) itemAlreadyInTile.getPhysicalEntityComponent().getAttributes();

						allocationAtPosition.setGameMaterial(attributesItemAlreadyInTile.getMaterial(attributesItemAlreadyInTile.getItemType().getPrimaryMaterialType()));
						allocationAtPosition.setItemType(attributesItemAlreadyInTile.getItemType());
						allocationAtPosition.refreshQuantityInTile(tileAtPosition);
						allocations.put(position, allocationAtPosition);
					}
				}
			} else if (allocationAtPosition.getItemType().equals(attributes.getItemType()) &&
					allocationAtPosition.getGameMaterial().equals(itemMaterial) &&
					allocationAtPosition.getTotalQuantity() + quantityToAllocate <= maxStackSize &&
					containsSameType(tileAtPosition, allocationAtPosition)) {
				allocationToUse = allocationAtPosition;
				break;
			}
		}
		if (allocationToUse == null) {
			// Not found one yet so use a new allocation

			// Deterministically go through points to traverse for a new location
			pointsToTraverse = new ArrayList<>(parent.getRoomTiles().keySet());
			Random random = new RandomXS128(parent.getRoomId());
			Collections.shuffle(pointsToTraverse, random);

			for (GridPoint2 position : pointsToTraverse) {
				MapTile tileAtPosition = map.getTile(position);
				StockpileAllocation allocationAtPosition = allocations.get(position);
				if (allocationAtPosition == null) {
					if (tileAtPosition.isEmpty()) {
						allocationToUse = new StockpileAllocation(position);
						allocationToUse.setItemType(attributes.getItemType());
						allocationToUse.setGameMaterial(itemMaterial);
						allocations.put(position, allocationToUse);
						break;
					}
				}
			}
		}

		if (allocationToUse != null) {
			int spaceInAllocation = maxStackSize - allocationToUse.getTotalQuantity();
			if (quantityToAllocate == 0) {
				Logger.error("Quantity to requestAllocation in " + this.getClass().getSimpleName() + " is 0, investigate why");
				return null;
			}
			quantityToAllocate = Math.min(quantityToAllocate, spaceInAllocation);

			allocationToUse.incrementIncomingHaulingQuantity(quantityToAllocate);

			return new StockpileAllocationResponse(allocationToUse.getPosition(), quantityToAllocate);
		}

		return null;
	}

	private boolean containsSameType(MapTile tileAtPosition, StockpileAllocation allocationAtPosition) {
		Entity itemAtPosition = null;
		for (Entity entity : tileAtPosition.getEntities()) {
			if (entity.getType().equals(EntityType.PLANT)) {
				return false; // a plant has grown into the tile
			}
			if (entity.getType().equals(EntityType.ITEM)) {
				itemAtPosition = entity;
				break;
			}
		}

		if (itemAtPosition == null) {
			return true; // nothing here so can place allocation
		} else {
			ItemEntityAttributes attributes = (ItemEntityAttributes) itemAtPosition.getPhysicalEntityComponent().getAttributes();
			return attributes.getItemType().equals(allocationAtPosition.getItemType()) &&
					attributes.getMaterial(attributes.getItemType().getPrimaryMaterialType()).equals(allocationAtPosition.getGameMaterial());
		}
	}

	public void allocationCancelled(HaulingAllocation allocation, Entity itemEntity) {
		StockpileAllocation positionalAllocation = allocations.get(allocation.getTargetPosition());
		if (positionalAllocation == null) {
			// Stockpile must have been removed
			return;
		}

		ItemEntityAttributes attributes = (ItemEntityAttributes) itemEntity.getPhysicalEntityComponent().getAttributes();
		if (!positionalAllocation.getGameMaterial().equals(attributes.getMaterial(attributes.getItemType().getPrimaryMaterialType())) ||
				!positionalAllocation.getItemType().equals(attributes.getItemType())) {
			// Allocation is not the correct item type or material
			return;
		}

		ItemAllocation itemAllocation = allocation.getItemAllocation();
		positionalAllocation.decrementIncomingHaulingQuantity(itemAllocation.getAllocationAmount());
		if (positionalAllocation.getTotalQuantity() <= 0) {
			allocations.remove(allocation.getTargetPosition());
		}
	}

	@Override
	public List<I18nText> getDescription(I18nTranslator i18nTranslator, GameContext gameContext) {
		int parentSize = parent.getRoomTiles().size();
		int allocationSize = allocations.size();

		Map<String, I18nString> replacements = new HashMap<>();
		replacements.put("allocated", new I18nWord(String.valueOf(allocationSize)));
		replacements.put("total", new I18nWord(String.valueOf(parentSize)));
		return Lists.newArrayList(i18nTranslator.getTranslatedWordWithReplacements("ROOMS.COMPONENT.STOCKPILE.DESCRIPTION", replacements));
	}

	public StockpileAllocation getAllocationAt(GridPoint2 position) {
		return allocations.get(position);
	}

	public StockpileGroup getGroup() {
		return group;
	}

	public void setGroup(StockpileGroup group) {
		this.group = group;
	}

	@Override
	public void writeTo(JSONObject asJson, SavedGameStateHolder savedGameStateHolder) {
		if (group != null) {
			asJson.put("group", group.getName());
		}

		if (!allocations.isEmpty()) {
			JSONArray allocationsJson = new JSONArray();
			for (Map.Entry<GridPoint2, StockpileAllocation> entry : allocations.entrySet()) {
				JSONObject entryJson = new JSONObject(true);
				entryJson.put("position", JSONUtils.toJSON(entry.getKey()));
				if (entry.getValue() != null) {
					JSONObject allocationJson = new JSONObject(true);
					entry.getValue().writeTo(allocationJson, savedGameStateHolder);
					entryJson.put("allocation", allocationJson);
				}
				allocationsJson.add(entryJson);
			}
			asJson.put("allocations", allocationsJson);
		}
	}

	@Override
	public void readFrom(JSONObject asJson, SavedGameStateHolder savedGameStateHolder, SavedGameDependentDictionaries relatedStores) throws InvalidSaveException {
		String groupName = asJson.getString("group");
		if (groupName != null) {
			this.group = relatedStores.stockpileGroupDictionary.getByName(groupName);
			if (this.group == null) {
				throw new InvalidSaveException("Could not find stockpile group by name " + groupName);
			}
		}

		JSONArray allocationsJson = asJson.getJSONArray("allocations");
		if (allocationsJson != null) {
			for (int cursor = 0; cursor < allocationsJson.size(); cursor++) {
				JSONObject entryJson = allocationsJson.getJSONObject(cursor);
				GridPoint2 position = JSONUtils.gridPoint2(entryJson.getJSONObject("position"));
				StockpileAllocation allocation = null;
				JSONObject allocationJson = entryJson.getJSONObject("allocation");
				if (allocationJson != null) {
					allocation = new StockpileAllocation(position);
					allocation.readFrom(allocationJson, savedGameStateHolder, relatedStores);
				}

				allocations.put(position, allocation);
			}
		}
	}
}
