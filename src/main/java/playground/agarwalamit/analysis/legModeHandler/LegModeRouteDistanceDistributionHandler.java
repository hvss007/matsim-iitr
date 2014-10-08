/* *********************************************************************** *
 * project: org.matsim.*
 * LegModeDistanceDistribution.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */
package playground.agarwalamit.analysis.legModeHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.events.handler.PersonArrivalEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.TeleportationArrivalEvent;
import org.matsim.core.api.experimental.events.handler.TeleportationArrivalEventHandler;

/**
 * @author amit
 */
public class LegModeRouteDistanceDistributionHandler implements PersonDepartureEventHandler, LinkLeaveEventHandler, PersonArrivalEventHandler, TeleportationArrivalEventHandler {
	private final Logger log = Logger.getLogger(LegModeRouteDistanceDistributionHandler.class);

	private Network network;
	private Scenario scenario;
	private SortedMap<String, Map<Id<Person>, List<Double>>> mode2PersonId2distances;
	private SortedMap<String, Map<Id<Person>, Double>> mode2PersonId2OneTripdist;
	private SortedMap<String, Map<Id<Person>, Double>> mode2PersonId2TeleportDist;
	private List<String> mainModes = new ArrayList<String>();
	private Map<Id<Person>, String> personId2LegModes;
	private double maxDist = Double.NEGATIVE_INFINITY;
	private SortedMap<String, Double> mode2NumberOfLegs ;

	public LegModeRouteDistanceDistributionHandler(Scenario scenario){
		this.log.info("Route distance will be calculated based on events.");
		this.log.warn("During distance calculation, link from which person is departed or arrived will not be considered.");
		this.scenario = scenario;
		this.mainModes.addAll(this.scenario.getConfig().qsim().getMainModes());
		this.network = scenario.getNetwork();
		this.mode2PersonId2distances = new TreeMap<>();
		this.mode2PersonId2OneTripdist = new TreeMap<>();
		this.mode2PersonId2TeleportDist = new TreeMap<>();
		this.personId2LegModes = new HashMap<>();
		this.mode2NumberOfLegs = new TreeMap<String, Double>();
	}

	@Override
	public void reset(int iteration) {

	}

	@Override
	public void handleEvent(LinkLeaveEvent event) {
		Id<Person> personId = event.getPersonId();
		Id<Link> linkId = event.getLinkId();
		// if a person is in more than two groups, then which one is correct mode ?
		String mode = this.personId2LegModes.get(personId);
		Map<Id<Person>, Double> person2Dist = mode2PersonId2OneTripdist.get(mode);
		double distSoFar = person2Dist.get(personId);
		double distNew = distSoFar+ network.getLinks().get(linkId).getLength();
		person2Dist.put(personId, distNew);
	}

	@Override
	public void handleEvent(PersonDepartureEvent event) {
		String tavelMode = event.getLegMode();
		Id<Person> personId = event.getPersonId();
		this.personId2LegModes.put(personId, tavelMode);

		if(mainModes.contains(tavelMode)){
			//initialize one trip distance map
			if(mode2PersonId2OneTripdist.containsKey(tavelMode)){
				Map<Id<Person>, Double> personId2Dist = mode2PersonId2OneTripdist.get(tavelMode);
				if(!personId2Dist.containsKey(personId)){
					personId2Dist.put(personId, 0.0);
				} else {
					log.warn("Person is departing again.");
				}
			} else {
				Map<Id<Person>, Double> personId2Dist = new TreeMap<>();
				personId2Dist.put(personId, 0.0);
				mode2PersonId2OneTripdist.put(tavelMode, personId2Dist);
			}
		} else {
			//initialize teleporation dist map
			if(mode2PersonId2TeleportDist.containsKey(tavelMode)){
				Map<Id<Person>, Double> personId2Dist = mode2PersonId2TeleportDist.get(tavelMode);
				if(!personId2Dist.containsKey(personId)){
					personId2Dist.put(personId, 0.0);
				} else {
					log.warn("Person is departing again.");
				}
			} else {
				Map<Id<Person>, Double> personId2Dist = new TreeMap<>();
				personId2Dist.put(personId, 0.0);
				mode2PersonId2TeleportDist.put(tavelMode, personId2Dist);
			}
		}
		//initialize distances map
		if(mode2PersonId2distances.containsKey(tavelMode)){
			Map<Id<Person>, List<Double>> personId2Dists = mode2PersonId2distances.get(tavelMode);
			if(!personId2Dists.containsKey(personId)){
				personId2Dists.put(personId, new ArrayList<Double>());
			}
		} else {
			Map<Id<Person>, List<Double>> personId2Dists = new TreeMap<>();
			personId2Dists.put(personId, new ArrayList<Double>());
			mode2PersonId2distances.put(tavelMode, personId2Dists);
		}
	}

	@Override
	public void handleEvent(PersonArrivalEvent event) {
		String travelMode = event.getLegMode();
		Id<Person> personId = event.getPersonId();
		if(!travelMode.equals(this.personId2LegModes.get(personId))) throw new RuntimeException("Person is leaving and arriving with different travel modes. Can not happen.");
		
		Map<Id<Person>, List<Double>> personId2Dists = mode2PersonId2distances.get(travelMode);
		if(mainModes.contains(travelMode)){
			if(personId2Dists.containsKey(personId) ){
				List<Double> dists = personId2Dists.get(personId); // it might happen, person is departing and arriving on same link.
				double tripDist = mode2PersonId2OneTripdist.get(travelMode).get(personId);
				if(maxDist<tripDist) maxDist = tripDist;
				dists.add(tripDist);
				personId2Dists.put(personId, dists);
				mode2PersonId2OneTripdist.get(travelMode).remove(personId);
			} else throw new RuntimeException("Person is not registered in the map and still arriving. This can not happen.");
		} else {
			List<Double> dists = personId2Dists.get(personId);
			double tripDist = mode2PersonId2TeleportDist.get(travelMode).get(personId);
			if(maxDist<tripDist) maxDist = tripDist;
			dists.add(tripDist);
			personId2Dists.put(personId, dists);
			mode2PersonId2TeleportDist.get(travelMode).remove(personId);
		}
	}

	public SortedMap<String,Map<Id<Person>,List<Double>>> getMode2PersonId2TravelDistances (){
		return this.mode2PersonId2distances;
	}
	
	public SortedSet<String> getUsedModes (){
		SortedSet<String> modes = new TreeSet<String>();
		modes.addAll(mode2PersonId2distances.keySet());
		return modes;
	}
	
	public double getLongestDistance(){
		return maxDist;
	}

	@Override
	public void handleEvent(TeleportationArrivalEvent event) {
		Id<Person> personId = event.getPersonId();
		String mode = this.personId2LegModes.get(personId);
		// if a person is in more than two groups, then which one is correct mode ?
		Map<Id<Person>, Double> person2Dist = mode2PersonId2TeleportDist.get(mode);
		double teleportDist = event.getDistance();
		person2Dist.put(personId, teleportDist);
	}
	
	/**
	 * @return  Total distance (summed for all trips for that person) for each person segregated w.r.t. travel modes.
	 */
	public SortedMap<String, Map<Id<Person>, Double>> getLegMode2PersonId2TotalTravelDistance(){
		SortedMap<String, Map<Id<Person>, Double>> mode2PersonId2TotalTravelDistance = new TreeMap<String, Map<Id<Person>,Double>>();
		for(String mode:this.mode2PersonId2distances.keySet()){
			double noOfLeg =0;
			Map<Id<Person>, Double> personId2TotalTravelDist = new HashMap<Id<Person>, Double>();
			for(Id<Person> id:this.mode2PersonId2distances.get(mode).keySet()){
				double travelDist=0;
				for(double d:this.mode2PersonId2distances.get(mode).get(id)){
					travelDist += d;
					noOfLeg++;
				}
				personId2TotalTravelDist.put(id, travelDist);
			}
			mode2PersonId2TotalTravelDistance.put(mode, personId2TotalTravelDist);
			this.mode2NumberOfLegs.put(mode, noOfLeg);
		}
		return mode2PersonId2TotalTravelDistance;
	}
	
	public SortedMap<String,Double> getTravelMode2NumberOfLegs(){
		getLegMode2PersonId2TotalTravelDistance();
		return this.mode2NumberOfLegs;
	}
}