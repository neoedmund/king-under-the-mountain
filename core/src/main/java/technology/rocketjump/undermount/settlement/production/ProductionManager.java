package technology.rocketjump.undermount.settlement.production;

import com.badlogic.gdx.ai.msg.MessageDispatcher;
import com.badlogic.gdx.ai.msg.Telegram;
import com.badlogic.gdx.ai.msg.Telegraph;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.pmw.tinylog.Logger;
import technology.rocketjump.undermount.crafting.CraftingRecipeDictionary;
import technology.rocketjump.undermount.crafting.model.CraftingRecipe;
import technology.rocketjump.undermount.entities.components.ItemAllocationComponent;
import technology.rocketjump.undermount.entities.model.Entity;
import technology.rocketjump.undermount.entities.model.physical.item.ItemEntityAttributes;
import technology.rocketjump.undermount.entities.model.physical.item.ItemType;
import technology.rocketjump.undermount.entities.model.physical.item.QuantifiedItemTypeWithMaterial;
import technology.rocketjump.undermount.gamecontext.GameContext;
import technology.rocketjump.undermount.gamecontext.Updatable;
import technology.rocketjump.undermount.messaging.MessageType;
import technology.rocketjump.undermount.messaging.types.ProductionAssignmentRequestMessage;
import technology.rocketjump.undermount.rendering.ScreenWriter;
import technology.rocketjump.undermount.settlement.ItemTracker;
import technology.rocketjump.undermount.settlement.SettlerTracker;

import java.util.*;

/**
 * This class is responsible for queueing up crafting and other production jobs across the settlement, to meet a set quota of required items
 */
@Singleton
public class ProductionManager implements Updatable, Telegraph {

	private static float UPDATE_PERIOD_IN_SECONDS = 1.313f;

	private final ItemTracker itemTracker;
	private final SettlerTracker settlerTracker;
	private final CraftingRecipeDictionary craftingRecipeDictionary;
	private final ScreenWriter screenWriter;

	private float timeSinceLastUpdate = 0f;
	private GameContext gameContext;

	@Inject
	public ProductionManager(ItemTracker itemTracker, SettlerTracker settlerTracker,
							 MessageDispatcher messageDispatcher, CraftingRecipeDictionary craftingRecipeDictionary, ScreenWriter screenWriter) {
		this.itemTracker = itemTracker;
		this.settlerTracker = settlerTracker;
		this.craftingRecipeDictionary = craftingRecipeDictionary;
		this.screenWriter = screenWriter;


		onContextChange(null);

		messageDispatcher.addListener(this, MessageType.REQUEST_PRODUCTION_ASSIGNMENT);
		messageDispatcher.addListener(this, MessageType.PRODUCTION_ASSIGNMENT_ACCEPTED);
		messageDispatcher.addListener(this, MessageType.PRODUCTION_ASSIGNMENT_CANCELLED);
		messageDispatcher.addListener(this, MessageType.PRODUCTION_ASSIGNMENT_COMPLETED);
	}

	@Override
	public boolean handleMessage(Telegram msg) {
		switch (msg.message) {
			case MessageType.REQUEST_PRODUCTION_ASSIGNMENT: {
				return handle((ProductionAssignmentRequestMessage) msg.extraInfo);
			}
			case MessageType.PRODUCTION_ASSIGNMENT_ACCEPTED: {
				ProductionAssignment assignment = (ProductionAssignment) msg.extraInfo;
				for (QuantifiedItemTypeWithMaterial output : assignment.targetRecipe.getOutput()) {
					if (output.isLiquid()) {
						// Skipping liquid outputs from being tracked
						break;
					}
					Map<Long, ProductionAssignment> productionAssignmentMap =
							gameContext.getSettlementState().productionAssignments.computeIfAbsent(output.getItemType(), (o) -> new HashMap<>());
					productionAssignmentMap.put(assignment.productionAssignmentId, assignment);
					int quantityRequired = gameContext.getSettlementState().requiredItemCounts.getOrDefault(output.getItemType(), 0);
					quantityRequired -= output.getQuantity();
					gameContext.getSettlementState().requiredItemCounts.put(output.getItemType(), quantityRequired);
				}
				return true;
			}
			case MessageType.PRODUCTION_ASSIGNMENT_CANCELLED:
			case MessageType.PRODUCTION_ASSIGNMENT_COMPLETED: {
				ProductionAssignment assignment = (ProductionAssignment) msg.extraInfo;
				if (assignment == null || assignment.targetRecipe == null) {
					Logger.error("PRODUCTION_ASSIGNMENT_COMPLETED with null targetRecipe");
				} else if (assignment.targetRecipe.getOutput().get(0).isLiquid()) {
					// Doing nothing for iquid outputs
				} else {
					for (QuantifiedItemTypeWithMaterial output : assignment.targetRecipe.getOutput()) {
						Map<Long, ProductionAssignment> productionAssignmentMap =
								gameContext.getSettlementState().productionAssignments.computeIfAbsent(output.getItemType(), (o) -> new HashMap<>());
						productionAssignmentMap.remove(assignment.productionAssignmentId);
					}
				}
				return true;
			}
			default:
				throw new IllegalArgumentException("Unexpected message type " + msg.message + " received by " + this.toString() + ", " + msg.toString());
		}
	}

	private boolean handle(ProductionAssignmentRequestMessage requestMessage) {
		List<CraftingRecipe> recipesForCraftingType = craftingRecipeDictionary.getByCraftingType(requestMessage.craftingType);

		List<CraftingRecipe> requiredCraftingRecipes = new LinkedList<>();
		for (CraftingRecipe craftingRecipe : recipesForCraftingType) {
			for (QuantifiedItemTypeWithMaterial output : craftingRecipe.getOutput()) {
				if (output.getItemType() == null && output.isLiquid()) {
					// This recipe does not produce items, only liquid, so always produce it
					requiredCraftingRecipes.add(craftingRecipe);
					break;
				}

				int numRequired = gameContext.getSettlementState().requiredItemCounts.getOrDefault(output.getItemType(), 0);
				if (numRequired > 0) {
					requiredCraftingRecipes.add(craftingRecipe);
					break;
				}
			}
		}


		List<CraftingRecipe> availableCraftingRecipes = new ArrayList<>();
		for (CraftingRecipe craftingRecipe : requiredCraftingRecipes) {
			boolean allInputsAvailable = true;
			for (QuantifiedItemTypeWithMaterial input : craftingRecipe.getInput()) {
				if (input.isLiquid()) {
					// Just assume all liquid available for now
					break;
				}

				List<Entity> unallocatedItems;
				if (input.getMaterial() == null) {
					unallocatedItems = itemTracker.getItemsByType(input.getItemType(), true);
				} else {
					unallocatedItems = itemTracker.getItemsByTypeAndMaterial(input.getItemType(), input.getMaterial(), true);
				}

				int quantityFound = 0;
				for (Entity unallocatedItem : unallocatedItems) {
					quantityFound += unallocatedItem.getOrCreateComponent(ItemAllocationComponent.class).getNumUnallocated();
					if (quantityFound >= input.getQuantity()) {
						break;
					}
				}
				if (quantityFound < input.getQuantity()) {
					// Not enough of this item
					allInputsAvailable = false;
					break;
				}
			}

			if (allInputsAvailable) {
				availableCraftingRecipes.add(craftingRecipe);
			}
		}

		if (!availableCraftingRecipes.isEmpty()) {
			Collections.shuffle(availableCraftingRecipes, gameContext.getRandom());
		}

		requestMessage.callback.productionAssignmentCallback(availableCraftingRecipes);
		return true;
	}

	@Override
	public void update(float deltaTime) {
		timeSinceLastUpdate += deltaTime;
		if (timeSinceLastUpdate > UPDATE_PERIOD_IN_SECONDS) {
			doUpdate();
			timeSinceLastUpdate = 0f;
		}

//		if (GlobalSettings.DEV_MODE) {
//
//			for (Map.Entry<ItemType, ProductionQuota> productionQuotaEntry : gameContext.getSettlementState().itemTypeProductionQuotas.entrySet()) {
//				String message = productionQuotaEntry.getKey().getItemTypeName() + " quota: " + productionQuotaEntry.getValue().toString() +
//						" required: " + gameContext.getSettlementState().requiredItemCounts.get(productionQuotaEntry.getKey()) + " assignments: " +
//						gameContext.getSettlementState().productionAssignments.get(productionQuotaEntry.getKey()).size();
//				screenWriter.printLine(message);
//			}
//		}

	}

	private void doUpdate() {
		int numSettlers = settlerTracker.count();
		for (Map.Entry<ItemType, ProductionQuota> quotaEntry : gameContext.getSettlementState().itemTypeProductionQuotas.entrySet()) {
			ItemType itemType = quotaEntry.getKey();
			int requiredAmount = quotaEntry.getValue().getRequiredAmount(numSettlers);
			int currentAmount = 0;
			for (Entity item : itemTracker.getItemsByType(itemType, false)) {
				currentAmount += ((ItemEntityAttributes)item.getPhysicalEntityComponent().getAttributes()).getQuantity();
			}
			int inProduction = 0;
			for (ProductionAssignment productionAssignment : gameContext.getSettlementState().productionAssignments.computeIfAbsent(itemType, (o) -> new HashMap<>()).values()) {
				Optional<QuantifiedItemTypeWithMaterial> matchingOutput = productionAssignment.targetRecipe.getOutput().stream()
						.filter((output) -> output.getItemType().equals(itemType))
						.findFirst();
				if (matchingOutput.isPresent()) {
					inProduction += matchingOutput.get().getQuantity();
				}
			}


			int missingCount = Math.max(requiredAmount - (currentAmount + inProduction), 0);
			gameContext.getSettlementState().requiredItemCounts.put(itemType, missingCount);
		}

	}

	@Override
	public boolean runWhilePaused() {
		return true;
	}

	@Override
	public void onContextChange(GameContext gameContext) {
		this.gameContext = gameContext;
	}

	@Override
	public void clearContextRelatedState() {
	}
}
