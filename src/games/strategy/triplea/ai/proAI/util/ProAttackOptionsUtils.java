package games.strategy.triplea.ai.proAI.util;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.PlayerID;
import games.strategy.engine.data.Route;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.triplea.TripleAUnit;
import games.strategy.triplea.ai.proAI.ProAI;
import games.strategy.triplea.ai.proAI.ProAmphibData;
import games.strategy.triplea.ai.proAI.ProAttackTerritoryData;
import games.strategy.triplea.delegate.BattleDelegate;
import games.strategy.triplea.delegate.DelegateFinder;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.delegate.TransportTracker;
import games.strategy.util.CompositeMatch;
import games.strategy.util.CompositeMatchAnd;
import games.strategy.util.CompositeMatchOr;
import games.strategy.util.Match;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

/**
 * Pro AI attack options utilities.
 * 
 * <ol>
 * <li>Add support for considering carrier landing when calculating air routes</li>
 * </ol>
 * 
 * @author Ron Murhammer
 * @since 2014
 */
public class ProAttackOptionsUtils
{
	private final ProAI ai;
	private final ProTransportUtils transportUtils;
	
	public ProAttackOptionsUtils(final ProAI ai, final ProTransportUtils transportUtils)
	{
		this.ai = ai;
		this.transportUtils = transportUtils;
	}
	
	public void findAttackOptions(final PlayerID player, final boolean areNeutralsPassableByAir, final List<Territory> myUnitTerritories, final Map<Territory, ProAttackTerritoryData> moveMap,
				final Map<Unit, Set<Territory>> unitMoveMap, final Map<Unit, Set<Territory>> transportMoveMap, final Map<Territory, Set<Territory>> landRoutesMap,
				final List<ProAmphibData> transportMapList, final List<Territory> enemyTerritories)
	{
		final GameData data = ai.getGameData();
		final Match<Territory> territoryHasEnemyUnitsMatch = new CompositeMatchOr<Territory>(Matches.territoryHasEnemyUnits(player, data), Matches.territoryIsInList(enemyTerritories));
		final Match<Territory> territoryIsEnemyOwnedMatch = new CompositeMatchOr<Territory>(Matches.isTerritoryEnemy(player, data), Matches.territoryIsInList(enemyTerritories));
		findNavalMoveOptions(player, myUnitTerritories, moveMap, unitMoveMap, transportMoveMap, territoryHasEnemyUnitsMatch, Matches.UnitCanNotMoveDuringCombatMove.invert(), enemyTerritories, true);
		findLandMoveOptions(player, myUnitTerritories, moveMap, unitMoveMap, landRoutesMap, territoryIsEnemyOwnedMatch, Matches.UnitCanNotMoveDuringCombatMove.invert(), enemyTerritories, true);
		findAirMoveOptions(player, areNeutralsPassableByAir, myUnitTerritories, moveMap, unitMoveMap, territoryHasEnemyUnitsMatch, Matches.UnitCanNotMoveDuringCombatMove.invert(), true);
		findAmphibMoveOptions(player, myUnitTerritories, moveMap, transportMapList, landRoutesMap, territoryIsEnemyOwnedMatch, Matches.UnitCanNotMoveDuringCombatMove.invert(), enemyTerritories, true);
	}
	
	public void findDefendOptions(final PlayerID player, final boolean areNeutralsPassableByAir, final List<Territory> myUnitTerritories, final Map<Territory, ProAttackTerritoryData> moveMap,
				final Map<Unit, Set<Territory>> unitMoveMap, final Map<Unit, Set<Territory>> transportMoveMap, final Map<Territory, Set<Territory>> landRoutesMap,
				final List<ProAmphibData> transportMapList)
	{
		final GameData data = ai.getGameData();
		final BattleDelegate delegate = DelegateFinder.battleDelegate(data);
		findNavalMoveOptions(player, myUnitTerritories, moveMap, unitMoveMap, transportMoveMap, Matches.territoryHasNoEnemyUnits(player, data), Matches.UnitCanMove, new ArrayList<Territory>(), false);
		findLandMoveOptions(player, myUnitTerritories, moveMap, unitMoveMap, landRoutesMap, Matches.isTerritoryAllied(player, data), Matches.UnitCanMove, new ArrayList<Territory>(), false);
		final Match<Territory> canLand = new CompositeMatchAnd<Territory>(Matches.isTerritoryAllied(player, data), new Match<Territory>()
		{
			@Override
			public boolean match(final Territory o)
			{
				return !delegate.getBattleTracker().wasConquered(o);
			}
		});
		findAirMoveOptions(player, areNeutralsPassableByAir, myUnitTerritories, moveMap, unitMoveMap, canLand, Matches.UnitCanMove, false);
		findAmphibMoveOptions(player, myUnitTerritories, moveMap, transportMapList, landRoutesMap, Matches.isTerritoryAllied(player, data), Matches.UnitCanMove, new ArrayList<Territory>(), false);
	}
	
	private void findNavalMoveOptions(final PlayerID player, final List<Territory> myUnitTerritories, final Map<Territory, ProAttackTerritoryData> moveMap,
				final Map<Unit, Set<Territory>> unitMoveMap, final Map<Unit, Set<Territory>> transportMoveMap, final Match<Territory> moveToTerritoryMatch, final Match<Unit> canMoveUnitMatch,
				final List<Territory> enemyTerritories, final boolean isCombatMove)
	{
		final GameData data = ai.getGameData();
		final Match<Territory> canMoveTerritoryMatch = new CompositeMatchAnd<Territory>(Matches.territoryDoesNotCostMoneyToEnter(data),
					Matches.TerritoryIsPassableAndNotRestrictedAndOkByRelationships(
								player, data, isCombatMove, false, true, false, false));
		
		for (final Territory myUnitTerritory : myUnitTerritories)
		{
			// Find my naval units that have movement left
			final CompositeMatch<Unit> mySeaUnitMatch = new CompositeMatchAnd<Unit>(Matches.unitIsOwnedBy(player), Matches.UnitIsSea, Matches.unitHasMovementLeft, canMoveUnitMatch);
			final List<Unit> mySeaUnits = myUnitTerritory.getUnits().getMatches(mySeaUnitMatch);
			
			// Check each sea unit individually since they can have different ranges
			for (final Unit mySeaUnit : mySeaUnits)
			{
				// Find list of potential territories to move to
				final int range = TripleAUnit.get(mySeaUnit).getMovementLeft();
				final Match<Territory> possibleMoveSeaTerritoryMatch = new CompositeMatchAnd<Territory>(canMoveTerritoryMatch, Matches.territoryHasNonAllowedCanal(player,
							Collections.singletonList(mySeaUnit), data).invert());
				final Set<Territory> possibleMoveTerritories = data.getMap().getNeighbors(myUnitTerritory, range, possibleMoveSeaTerritoryMatch);
				possibleMoveTerritories.add(myUnitTerritory);
				final Set<Territory> potentialTerritories = new HashSet<Territory>(Match.getMatches(possibleMoveTerritories, moveToTerritoryMatch));
				
				for (final Territory potentialTerritory : potentialTerritories)
				{
					// Find route over water with no enemy units blocking
					final Match<Territory> canMoveSeaThroughMatch = new CompositeMatchAnd<Territory>(canMoveTerritoryMatch, Matches.territoryHasNoEnemyUnits(player, data), Matches.territoryIsInList(
								enemyTerritories).invert(), Matches.territoryHasNonAllowedCanal(player, Collections.singletonList(mySeaUnit), data).invert());
					final Route myRoute = data.getMap().getRoute_IgnoreEnd(myUnitTerritory, potentialTerritory, canMoveSeaThroughMatch);
					if (myRoute == null)
						continue;
					final int myRouteLength = myRoute.numberOfSteps();
					if (myRouteLength > range)
						continue;
					
					// Populate territories with sea unit
					if (moveMap.containsKey(potentialTerritory))
					{
						moveMap.get(potentialTerritory).addMaxUnit(mySeaUnit);
					}
					else
					{
						final ProAttackTerritoryData moveTerritoryData = new ProAttackTerritoryData(potentialTerritory);
						moveTerritoryData.addMaxUnit(mySeaUnit);
						moveMap.put(potentialTerritory, moveTerritoryData);
					}
					
					// Populate appropriate unit move options map
					final List<Unit> unitList = new ArrayList<Unit>();
					unitList.add(mySeaUnit);
					if (Match.allMatch(unitList, Matches.UnitIsTransport))
					{
						if (transportMoveMap.containsKey(mySeaUnit))
						{
							transportMoveMap.get(mySeaUnit).add(potentialTerritory);
						}
						else
						{
							final Set<Territory> unitMoveTerritories = new HashSet<Territory>();
							unitMoveTerritories.add(potentialTerritory);
							transportMoveMap.put(mySeaUnit, unitMoveTerritories);
						}
					}
					else
					{
						if (unitMoveMap.containsKey(mySeaUnit))
						{
							unitMoveMap.get(mySeaUnit).add(potentialTerritory);
						}
						else
						{
							final Set<Territory> unitMoveTerritories = new HashSet<Territory>();
							unitMoveTerritories.add(potentialTerritory);
							unitMoveMap.put(mySeaUnit, unitMoveTerritories);
						}
					}
				}
			}
		}
	}
	
	private void findLandMoveOptions(final PlayerID player, final List<Territory> myUnitTerritories, final Map<Territory, ProAttackTerritoryData> moveMap, final Map<Unit, Set<Territory>> unitMoveMap,
				final Map<Territory, Set<Territory>> landRoutesMap, final Match<Territory> moveToTerritoryMatch, final Match<Unit> canMoveUnitMatch, final List<Territory> enemyTerritories,
				final boolean isCombatMove)
	{
		final GameData data = ai.getGameData();
		final Match<Territory> canMoveTerritoryMatch = new CompositeMatchAnd<Territory>(Matches.territoryDoesNotCostMoneyToEnter(data),
					Matches.TerritoryIsPassableAndNotRestrictedAndOkByRelationships(
								player, data, isCombatMove, true, false, false, false));
		
		for (final Territory myUnitTerritory : myUnitTerritories)
		{
			// Find my land units that have movement left
			final CompositeMatch<Unit> myLandUnitMatch = new CompositeMatchAnd<Unit>(Matches.unitIsOwnedBy(player), Matches.UnitIsLand, Matches.unitHasMovementLeft, Matches.unitIsBeingTransported()
						.invert(), canMoveUnitMatch);
			final List<Unit> myLandUnits = myUnitTerritory.getUnits().getMatches(myLandUnitMatch);
			
			// Check each land unit individually since they can have different ranges
			for (final Unit myLandUnit : myLandUnits)
			{
				final int range = TripleAUnit.get(myLandUnit).getMovementLeft();
				final Set<Territory> possibleMoveTerritories = data.getMap().getNeighbors(myUnitTerritory, range, canMoveTerritoryMatch);
				possibleMoveTerritories.add(myUnitTerritory);
				final Set<Territory> potentialTerritories = new HashSet<Territory>(Match.getMatches(possibleMoveTerritories, moveToTerritoryMatch));
				for (final Territory potentialTerritory : potentialTerritories)
				{
					// Find route over land checking whether unit can blitz
					Match<Territory> canMoveThroughTerritoriesMatch = new CompositeMatchAnd<Territory>(canMoveTerritoryMatch, Matches.isTerritoryAllied(player, data), Matches.territoryIsInList(
								enemyTerritories).invert());
					if (isCombatMove && Matches.UnitCanBlitz.match(myLandUnit))
						canMoveThroughTerritoriesMatch = new CompositeMatchAnd<Territory>(canMoveTerritoryMatch, Matches.TerritoryIsBlitzable(player, data), Matches
									.territoryIsInList(enemyTerritories).invert());
					final Route myRoute = data.getMap().getRoute_IgnoreEnd(myUnitTerritory, potentialTerritory, canMoveThroughTerritoriesMatch);
					if (myRoute == null)
						continue;
					final int myRouteLength = myRoute.numberOfSteps();
					if (myRouteLength > range)
						continue;
					
					// Add to route map
					if (landRoutesMap.containsKey(potentialTerritory))
					{
						landRoutesMap.get(potentialTerritory).add(myUnitTerritory);
					}
					else
					{
						final Set<Territory> territories = new HashSet<Territory>();
						territories.add(myUnitTerritory);
						landRoutesMap.put(potentialTerritory, territories);
					}
					
					// Populate territories with land units
					if (moveMap.containsKey(potentialTerritory))
					{
						moveMap.get(potentialTerritory).addMaxUnit(myLandUnit);
					}
					else
					{
						final ProAttackTerritoryData moveTerritoryData = new ProAttackTerritoryData(potentialTerritory);
						moveTerritoryData.addMaxUnit(myLandUnit);
						moveMap.put(potentialTerritory, moveTerritoryData);
					}
					
					// Populate unit move options map
					if (unitMoveMap.containsKey(myLandUnit))
					{
						unitMoveMap.get(myLandUnit).add(potentialTerritory);
					}
					else
					{
						final Set<Territory> unitMoveTerritories = new HashSet<Territory>();
						unitMoveTerritories.add(potentialTerritory);
						unitMoveMap.put(myLandUnit, unitMoveTerritories);
					}
				}
			}
		}
	}
	
	private void findAirMoveOptions(final PlayerID player, final boolean areNeutralsPassableByAir, final List<Territory> myUnitTerritories, final Map<Territory, ProAttackTerritoryData> moveMap,
				final Map<Unit, Set<Territory>> unitMoveMap, final Match<Territory> moveToTerritoryMatch, final Match<Unit> canMoveUnitMatch, final boolean isCombatMove)
	{
		final GameData data = ai.getGameData();
		final Match<Territory> canMoveTerritoryMatch = new CompositeMatchAnd<Territory>(Matches.territoryDoesNotCostMoneyToEnter(data),
					Matches.TerritoryIsPassableAndNotRestrictedAndOkByRelationships(
								player, data, isCombatMove, false, false, true, false));
		final Match<Territory> canLandTerritoryMatch = new CompositeMatchAnd<Territory>(Matches.airCanLandOnThisAlliedNonConqueredLandTerritory(player, data),
					Matches.TerritoryIsPassableAndNotRestrictedAndOkByRelationships(player, data, isCombatMove, false, false, true, true));
		
		for (final Territory myUnitTerritory : myUnitTerritories)
		{
			// Find my air units that have movement left
			final CompositeMatch<Unit> myAirUnitMatch = new CompositeMatchAnd<Unit>(Matches.unitIsOwnedBy(player), Matches.UnitIsAir, Matches.unitHasMovementLeft, canMoveUnitMatch);
			final List<Unit> myAirUnits = myUnitTerritory.getUnits().getMatches(myAirUnitMatch);
			
			// Check each air unit individually since they can have different ranges
			for (final Unit myAirUnit : myAirUnits)
			{
				final int range = TripleAUnit.get(myAirUnit).getMovementLeft();
				final Set<Territory> possibleMoveTerritories = data.getMap().getNeighbors(myUnitTerritory, range, canMoveTerritoryMatch);
				possibleMoveTerritories.add(myUnitTerritory);
				final Set<Territory> potentialTerritories = new HashSet<Territory>(Match.getMatches(possibleMoveTerritories, moveToTerritoryMatch));
				for (final Territory potentialTerritory : potentialTerritories)
				{
					// Find route ignoring impassable and territories with AA
					final CompositeMatch<Territory> canFlyOverMatch = new CompositeMatchAnd<Territory>(canMoveTerritoryMatch, Matches.territoryHasEnemyAAforAnything(player, data).invert());
					final Route myRoute = data.getMap().getRoute_IgnoreEnd(myUnitTerritory, potentialTerritory, canFlyOverMatch);
					if (myRoute == null)
						continue;
					final int myRouteLength = myRoute.numberOfSteps();
					final int remainingMoves = range - myRouteLength;
					if (remainingMoves < 0)
						continue;
					
					// If combat move and my remaining movement is less than the distance I already moved then need to check if I can land
					if (isCombatMove && remainingMoves < myRouteLength)
					{
						// TODO: add carriers to landing possibilities
						final Set<Territory> possibleLandingTerritories = data.getMap().getNeighbors(potentialTerritory, remainingMoves, canFlyOverMatch);
						final Set<Territory> landingTerritories = new HashSet<Territory>(Match.getMatches(possibleLandingTerritories, canLandTerritoryMatch));
						if (landingTerritories.isEmpty())
							continue;
					}
					
					// Populate enemy territories with air unit
					if (moveMap.containsKey(potentialTerritory))
					{
						moveMap.get(potentialTerritory).addMaxUnit(myAirUnit);
					}
					else
					{
						final ProAttackTerritoryData moveTerritoryData = new ProAttackTerritoryData(potentialTerritory);
						moveTerritoryData.addMaxUnit(myAirUnit);
						moveMap.put(potentialTerritory, moveTerritoryData);
					}
					
					// Populate unit attack options map
					if (unitMoveMap.containsKey(myAirUnit))
					{
						unitMoveMap.get(myAirUnit).add(potentialTerritory);
					}
					else
					{
						final Set<Territory> unitMoveTerritories = new HashSet<Territory>();
						unitMoveTerritories.add(potentialTerritory);
						unitMoveMap.put(myAirUnit, unitMoveTerritories);
					}
				}
			}
		}
	}
	
	private void findAmphibMoveOptions(final PlayerID player, final List<Territory> myUnitTerritories, final Map<Territory, ProAttackTerritoryData> moveMap,
				final List<ProAmphibData> transportMapList, final Map<Territory, Set<Territory>> landRoutesMap, final Match<Territory> moveAmphibToTerritoryMatch, final Match<Unit> canMoveUnitMatch,
				final List<Territory> enemyTerritories, final boolean isCombatMove)
	{
		final GameData data = ai.getGameData();
		final Match<Territory> canMoveTransportTerritoryMatch = new CompositeMatchAnd<Territory>(Matches.territoryDoesNotCostMoneyToEnter(data),
					Matches.TerritoryIsPassableAndNotRestrictedAndOkByRelationships(player, data, isCombatMove, false, true, false, false));
		final Match<Territory> canMoveLandTerritoryMatch = new CompositeMatchAnd<Territory>(Matches.territoryDoesNotCostMoneyToEnter(data),
					Matches.TerritoryIsPassableAndNotRestrictedAndOkByRelationships(player, data, isCombatMove, true, false, false, false));
		
		for (final Territory myUnitTerritory : myUnitTerritories)
		{
			// Find my transports and amphibious units that have movement left
			final CompositeMatch<Unit> myTransportUnitMatch = new CompositeMatchAnd<Unit>(Matches.unitIsOwnedBy(player), Matches.UnitIsTransport, Matches.unitHasMovementLeft);
			final List<Unit> myTransportUnits = myUnitTerritory.getUnits().getMatches(myTransportUnitMatch);
			final CompositeMatch<Unit> myUnitsToLoadMatch = new CompositeMatchAnd<Unit>(Matches.unitIsOwnedBy(player), Matches.UnitCanBeTransported, Matches.unitHasMovementLeft, canMoveUnitMatch);
			final CompositeMatch<Territory> myTerritoriesToLoadFromMatch = new CompositeMatchAnd<Territory>(Matches.territoryHasUnitsThatMatch(myUnitsToLoadMatch));
			final Match<Territory> unloadAmphibTerritoryMatch = new CompositeMatchAnd<Territory>(canMoveLandTerritoryMatch, moveAmphibToTerritoryMatch);
			
			// Check each transport unit individually since they can have different ranges
			for (final Unit myTransportUnit : myTransportUnits)
			{
				int movesLeft = TripleAUnit.get(myTransportUnit).getMovementLeft();
				final Match<Territory> canMoveSeaThroughMatch = new CompositeMatchAnd<Territory>(canMoveTransportTerritoryMatch, Matches.territoryHasNoEnemyUnits(player, data), Matches
							.territoryIsInList(enemyTerritories).invert(), Matches.territoryHasNonAllowedCanal(player, Collections.singletonList(myTransportUnit), data).invert());
				final ProAmphibData proTransportData = new ProAmphibData(myTransportUnit);
				transportMapList.add(proTransportData);
				final Set<Territory> currentTerritories = new HashSet<Territory>();
				currentTerritories.add(myUnitTerritory);
				
				// Find units to load and territories to unload
				while (movesLeft >= 0)
				{
					final Set<Territory> nextTerritories = new HashSet<Territory>();
					for (final Territory currentTerritory : currentTerritories)
					{
						// Find neighbors I can move to
						final Set<Territory> possibleNeighborTerritories = data.getMap().getNeighbors(currentTerritory, canMoveSeaThroughMatch);
						nextTerritories.addAll(possibleNeighborTerritories);
						
						// Get loaded units or get units that can be loaded into current territory if no enemies present
						final List<Unit> units = new ArrayList<Unit>();
						Set<Territory> myUnitsToLoadTerritories = new HashSet<Territory>();
						if (TransportTracker.isTransporting(myTransportUnit))
						{
							units.addAll(TransportTracker.transporting(myTransportUnit));
						}
						else if (Matches.territoryHasEnemyUnits(player, data).invert().match(currentTerritory))
						{
							myUnitsToLoadTerritories = data.getMap().getNeighbors(currentTerritory, myTerritoriesToLoadFromMatch);
							for (final Territory myUnitsToLoadTerritory : myUnitsToLoadTerritories)
							{
								units.addAll(myUnitsToLoadTerritory.getUnits().getMatches(myUnitsToLoadMatch));
							}
						}
						
						// If there are any units to be transported
						if (!units.isEmpty())
						{
							// Find all water territories I can move to
							Set<Territory> possibleMoveTerritories = new HashSet<Territory>();
							if (movesLeft > 0)
							{
								possibleMoveTerritories = data.getMap().getNeighbors(currentTerritory, movesLeft, canMoveSeaThroughMatch);
							}
							possibleMoveTerritories.add(currentTerritory);
							
							// Find all water territories adjacent to possible unload land territories
							final List<Territory> possibleUnloadTerritories = Match.getMatches(possibleMoveTerritories, Matches.territoryHasNeighborMatching(data, unloadAmphibTerritoryMatch));
							
							// Loop through possible unload territories
							final Set<Territory> moveTerritories = new HashSet<Territory>();
							for (final Territory possibleUnloadTerritory : possibleUnloadTerritories)
							{
								moveTerritories.addAll(data.getMap().getNeighbors(possibleUnloadTerritory, unloadAmphibTerritoryMatch));
							}
							
							// Add to transport map
							proTransportData.addTerritories(moveTerritories, myUnitsToLoadTerritories);
						}
					}
					currentTerritories.clear();
					currentTerritories.addAll(nextTerritories);
					movesLeft--;
				}
			}
		}
		
		// Remove any territories from transport map that I can move to on land
		for (final ProAmphibData proTransportData : transportMapList)
		{
			final Map<Territory, Set<Territory>> transportMap = proTransportData.getTransportMap();
			final List<Territory> transportsToRemove = new ArrayList<Territory>();
			for (final Territory t : transportMap.keySet())
			{
				final Set<Territory> transportMoveTerritories = transportMap.get(t);
				final Set<Territory> landMoveTerritories = landRoutesMap.get(t);
				if (landMoveTerritories != null)
				{
					transportMoveTerritories.removeAll(landMoveTerritories);
					if (transportMoveTerritories.isEmpty())
						transportsToRemove.add(t);
				}
			}
			for (final Territory t : transportsToRemove)
				transportMap.remove(t);
		}
		
		// Add transport units to attack map
		for (final ProAmphibData proTransportData : transportMapList)
		{
			final Map<Territory, Set<Territory>> transportMap = proTransportData.getTransportMap();
			final Unit transport = proTransportData.getTransport();
			for (final Territory moveTerritory : transportMap.keySet())
			{
				// Get units to transport
				final Set<Territory> territoriesCanLoadFrom = transportMap.get(moveTerritory);
				List<Unit> alreadyAddedToMaxAmphibUnits = new ArrayList<Unit>();
				if (moveMap.containsKey(moveTerritory))
					alreadyAddedToMaxAmphibUnits = moveMap.get(moveTerritory).getMaxAmphibUnits();
				final List<Unit> amphibUnits = transportUtils.getUnitsToTransportFromTerritories(player, transport, territoriesCanLoadFrom, alreadyAddedToMaxAmphibUnits);
				
				// Add amphib units to attack map
				if (moveMap.containsKey(moveTerritory))
				{
					moveMap.get(moveTerritory).addMaxAmphibUnits(amphibUnits);
				}
				else
				{
					final ProAttackTerritoryData moveTerritoryData = new ProAttackTerritoryData(moveTerritory);
					moveTerritoryData.addMaxAmphibUnits(amphibUnits);
					moveMap.put(moveTerritory, moveTerritoryData);
				}
			}
		}
	}
	
}